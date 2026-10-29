import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import java.io.File;
import java.util.*;

/** Find the pinned APK's settings-list construction without loading app code. */
public final class InspectNatuneSettings {
    static final Set<String> ANCHORS=Set.of("storage_folder_pick", "download_source", "account_logout", "backup_restore");
    static String sig(MethodReference method) {
        return method.getDefiningClass()+"->"+method.getName()+"("+String.join("",method.getParameterTypes())+")"+method.getReturnType();
    }
    static void need(boolean value,String why){if(!value)throw new IllegalStateException(why);}
    static void dump(Method method,int from,int to){
        if(method.getImplementation()==null)return;
        var code=new ArrayList<Instruction>();method.getImplementation().getInstructions().forEach(code::add);
        int offset=0;
        for(int i=0;i<code.size();i++){
            var ins=code.get(i);
            if(i>=from&&i<to){
                var out=new StringBuilder("SETTINGS_CODE\t"+sig(method)+"\t"+offset+"\t"+ins.getOpcode());
                if(ins instanceof OneRegisterInstruction x)out.append("\tA=").append(x.getRegisterA());
                if(ins instanceof TwoRegisterInstruction x)out.append("\tB=").append(x.getRegisterB());
                if(ins instanceof ThreeRegisterInstruction x)out.append("\tC=").append(x.getRegisterC());
                if(ins instanceof FiveRegisterInstruction x)out.append("\targc=").append(x.getRegisterCount())
                    .append("\tregs=").append(List.of(x.getRegisterC(),x.getRegisterD(),x.getRegisterE(),x.getRegisterF(),x.getRegisterG()));
                if(ins instanceof RegisterRangeInstruction x)out.append("\targc=").append(x.getRegisterCount()).append("\tstart=").append(x.getStartRegister());
                if(ins instanceof WideLiteralInstruction x)out.append("\tliteral=").append(x.getWideLiteral());
                if(ins instanceof OffsetInstruction x)out.append("\ttarget=").append(offset+x.getCodeOffset());
                if(ins instanceof ReferenceInstruction x && !(x.getReference() instanceof StringReference))out.append("\tref=").append(x.getReference());
                System.out.println(out);
            }
            offset+=ins.getCodeUnits();
        }
    }
    public static void main(String[] args)throws Exception{
        need(args.length==1,"Expected pinned original APK");
        var classes=new TreeMap<String,ClassDef>();
        var container=DexFileFactory.loadDexContainer(new File(args[0]),Opcodes.getDefault());
        for(String entry:container.getDexEntryNames())for(var clazz:container.getEntry(entry).getDexFile().getClasses())
            need(classes.put(clazz.getType(),clazz)==null,"Duplicate class");
        var candidates=new ArrayList<Method>();
        for(var clazz:classes.values())for(var method:clazz.getMethods()){
            if(method.getImplementation()==null)continue;
            var anchors=new TreeSet<String>();
            for(var instruction:method.getImplementation().getInstructions())
                if(instruction instanceof ReferenceInstruction ref && ref.getReference() instanceof StringReference text &&
                    ANCHORS.contains(text.getString()))anchors.add(text.getString());
            if(anchors.size()<2)continue;
            candidates.add(method);
            var body=method.getImplementation();
            var instructions=new ArrayList<Instruction>();body.getInstructions().forEach(instructions::add);
            int units=instructions.stream().mapToInt(Instruction::getCodeUnits).sum();
            System.out.println("SETTINGS_CANDIDATE\t"+sig(method)+"\tflags="+method.getAccessFlags()+
                "\tregisters="+body.getRegisterCount()+"\tunits="+units+"\ttry="+body.getTryBlocks().size()+"\tanchors="+anchors);
            var constructors=new TreeMap<String,Integer>();int offset=0;
            for(var ins:instructions){
                if(ins instanceof ReferenceInstruction ref && ref.getReference() instanceof MethodReference m && m.getName().equals("<init>"))
                    constructors.merge(sig(m),1,Integer::sum);
                if(ins.getOpcode()==Opcode.RETURN_OBJECT && ins instanceof OneRegisterInstruction r)
                    System.out.println("SETTINGS_RETURN\t"+offset+"\tregister="+r.getRegisterA());
                offset+=ins.getCodeUnits();
            }
            constructors.entrySet().stream().filter(e->e.getValue()>=3)
                .sorted(Comparator.<Map.Entry<String,Integer>>comparingInt(Map.Entry::getValue).reversed())
                .limit(12).forEach(e->System.out.println("SETTINGS_CONSTRUCTOR\t"+e.getValue()+"\t"+e.getKey()));
            var types=new TreeSet<String>();
            for(var e:constructors.entrySet())if(e.getValue()>=3)types.add(e.getKey().substring(0,e.getKey().indexOf("->")));
            for(String type:types){
                var selected=classes.get(type);if(selected==null)continue;
                System.out.println("SETTINGS_CLASS\t"+type+"\tflags="+selected.getAccessFlags()+"\tsuper="+selected.getSuperclass());
                for(var field:selected.getFields())System.out.println("SETTINGS_FIELD\t"+type+"->"+field.getName()+":"+field.getType()+"\tflags="+field.getAccessFlags());
                for(var m:selected.getMethods())if(m.getName().equals("<init>"))
                    System.out.println("SETTINGS_INIT\t"+sig(m)+"\tflags="+m.getAccessFlags());
            }
            for(String type:List.of("Lr8/b0b;","Lr8/c0b;")){
                var selected=classes.get(type);if(selected==null)continue;
                for(var constructor:selected.getMethods())if(constructor.getName().equals("<init>"))dump(constructor,0,200);
            }
            int end=instructions.size();dump(method,Math.max(0,end-16),end);
        }
        need(!candidates.isEmpty(),"Reviewed source anchors did not survive in one method");
        for(String type:List.of("Lr8/si4;","Lr8/gj4;")){
            var clazz=classes.get(type);if(clazz==null)continue;
            System.out.println("SETTINGS_INTERFACE\t"+type+"\tflags="+clazz.getAccessFlags()+"\tsuper="+clazz.getSuperclass()+"\tinterfaces="+clazz.getInterfaces());
            for(var method:clazz.getMethods())System.out.println("SETTINGS_INTERFACE_METHOD\t"+sig(method)+"\tflags="+method.getAccessFlags());
        }
        for(var clazz:classes.values())for(var method:clazz.getMethods()){
            if(method.getImplementation()==null)continue;
            var code=new ArrayList<Instruction>();method.getImplementation().getInstructions().forEach(code::add);
            for(int i=0;i<code.size();i++){
                var ins=code.get(i);
                if(ins instanceof ReferenceInstruction ref && ref.getReference() instanceof MethodReference target &&
                    candidates.stream().anyMatch(c->sig(c).equals(sig(target)))){
                    System.out.println("SETTINGS_CALLER\t"+sig(method)+"\t"+sig(target));
                    dump(method,Math.max(0,i-6),Math.min(code.size(),i+8));
                }
            }
        }
        System.out.println("SETTINGS_INSPECTION\tcandidates="+candidates.size()+"\tdevice_execution=false");
    }
}
