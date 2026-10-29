import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.File;
import java.util.*;

/** Independent inspection of the experimental settings APK, never its execution. */
public final class VerifyNatuneRuntimeSettingsPatch {
    static final String TYPE = "Ldev/purple/patches/natune/RuntimeSettings;";
    static final String APP = "Lmoe/rukamori/archivetune/App;->onCreate()V";
    static final String SCREEN = "Lmoe/rukamori/archivetune/ui/screens/settings/l4;->b(Lr8/f48;Ljava/lang/String;Lr8/si4;Lr8/o72;I)V";
    static final String APPEND = "Ldev/purple/patches/natune/NatuneSettingsNavigation;->append(Ljava/util/List;Landroid/content/Context;)Ljava/util/List;";
    static void need(boolean value, String why) { VerifyNatuneMobilePatch.need(value, why); }
    static List<Instruction> instructions(Method method) {
        need(method != null && method.getImplementation() != null, "Required method absent");
        var result = new ArrayList<Instruction>();
        method.getImplementation().getInstructions().forEach(result::add);
        return result;
    }
    static void call(Instruction instruction, Opcode opcode, String target, int... registers) {
        VerifyNatuneLocalFlacPatch.call(instruction, opcode, target, registers);
    }
    static void reg(Instruction instruction, Opcode opcode, int register) {
        need(instruction.getOpcode() == opcode && instruction instanceof OneRegisterInstruction &&
            ((OneRegisterInstruction) instruction).getRegisterA() == register, "Wrong register or opcode");
    }
    static void marker(Method method) {
        var code = instructions(method);
        need(method.getAccessFlags() == 9 && method.getImplementation().getRegisterCount() == 1 && code.size() == 2,
            "Unexpected feature marker shape");
        reg(code.get(0), Opcode.CONST_4, 0);
        need(code.get(0) instanceof WideLiteralInstruction &&
            ((WideLiteralInstruction) code.get(0)).getWideLiteral() == 1, "Feature marker not enabled");
        reg(code.get(1), Opcode.RETURN, 0);
    }
    static void wrapper(Method method) {
        var code = instructions(method);
        int count = method.getImplementation().getRegisterCount();
        need(count >= 2 && count <= 16 && method.getAccessFlags() == 9 && code.size() >= 5,
            "Unexpected audio wrapper shape");
        int argument = count - 1; // static method, one Context parameter
        call(code.get(0), Opcode.INVOKE_STATIC, "Lr8/hg6;->p(Landroid/content/Context;)Z", argument);
        reg(code.get(1), Opcode.MOVE_RESULT, 0);
        call(code.get(2), Opcode.INVOKE_STATIC, TYPE + "->mobilePolicy(Landroid/content/Context;Z)Z", argument, 0);
        reg(code.get(3), Opcode.MOVE_RESULT, 0);
        reg(code.get(4), Opcode.RETURN, 0);
    }
    static void appEntry(Method method) {
        var code = instructions(method);
        need(method.getAccessFlags() == 17 && method.getImplementation().getRegisterCount() == 22 &&
            method.getImplementation().getTryBlocks().size() == 4, "Application entry shape changed");
        var first = code.get(0);
        need(first.getOpcode() == Opcode.INVOKE_STATIC_RANGE && first instanceof RegisterRangeInstruction &&
            first instanceof ReferenceInstruction, "Missing app bootstrap");
        var invoke = (RegisterRangeInstruction) first;
        var reference = ((ReferenceInstruction) first).getReference();
        need(invoke.getStartRegister() == 21 && invoke.getRegisterCount() == 1 && reference instanceof MethodReference &&
            VerifyNatuneMobilePatch.signature((MethodReference) reference).equals(TYPE + "->initialize(Landroid/content/Context;)V"),
            "Wrong app bootstrap or application instance");
    }
    static void guard(Method method, VerifyNatuneLocalFlacPatch.Hook hook) {
        var code = instructions(method);
        need(code.size() > 3 + hook.prefix(), "Missing codec body");
        call(code.get(0), Opcode.INVOKE_STATIC, TYPE + "->requireFlac()Z");
        reg(code.get(1), Opcode.MOVE_RESULT, 0);
        reg(code.get(2), Opcode.IF_EQZ, 0);
        int offset = 0;
        for (int i = 2; i < 3 + hook.prefix(); i++) offset += code.get(i).getCodeUnits();
        need(code.get(2) instanceof OffsetInstruction &&
            ((OffsetInstruction) code.get(2)).getCodeOffset() == offset, "Disabled switch must enter original codec body");
        VerifyNatuneLocalFlacPatch.guard(code.subList(3, code.size()), hook);
    }
    static int settingsEntry(Method method) {
        var code=instructions(method);
        need(method.getImplementation().getTryBlocks().isEmpty(),"Unreviewed settings exception region");
        int found=-1;
        for(int i=0;i<code.size();i++) {
            var instruction=code.get(i);
            if(instruction instanceof ReferenceInstruction ref && ref.getReference() instanceof MethodReference target &&
                    VerifyNatuneMobilePatch.signature(target).equals(APPEND)) {
                need(found<0 && i>=2 && i+2<code.size(),"Duplicate or misplaced settings entry");
                call(instruction,Opcode.INVOKE_STATIC,APPEND,4,6);
                reg(code.get(i+1),Opcode.MOVE_RESULT_OBJECT,4);
                reg(code.get(i-1),Opcode.MOVE_RESULT_OBJECT,4);
                call(code.get(i-2),Opcode.INVOKE_STATIC,
                    "Lr8/ld6;->p(Lr8/f48;ZZLandroid/content/Context;Lr8/ry1;)Ljava/util/List;",3,10,5,6,8);
                call(code.get(i+2),Opcode.INVOKE_VIRTUAL,"Lr8/el4;->m0(Ljava/lang/Object;)V",0,4);
                found=i;
            }
        }
        need(found>=0,"Missing exact settings-list entry");
        return found;
    }
    // Compare the original body after removing ONLY the independently checked
    // two-instruction insertion. Keep physical addresses for branches and map
    // them to original instruction ordinals; any jump into the insertion fails.
    static VerifyNatuneMobilePatch.Body withoutSettingsEntry(Method method,int start) {
        var code=instructions(method);
        var addresses=new int[code.size()];
        var labels=new HashMap<Integer,Integer>();
        var switches=new HashMap<Integer,List<Integer>>();
        int address=0,index=0;
        for(int i=0;i<code.size();i++) {
            var instruction=code.get(i);addresses[i]=address;
            if(i<start || i>=start+2) {
                labels.put(address,index);
                if(instruction.getOpcode()==Opcode.PACKED_SWITCH || instruction.getOpcode()==Opcode.SPARSE_SWITCH) {
                    int payload=address+((OffsetInstruction)instruction).getCodeOffset();
                    switches.computeIfAbsent(payload,k->new ArrayList<>()).add(address);
                }
                if(instruction.getOpcode()!=Opcode.NOP)index++;
            }
            address+=instruction.getCodeUnits();
        }
        labels.put(address,index);
        var nodes=new ArrayList<String>();
        for(int i=0;i<code.size();i++) {
            if((i>=start && i<start+2) || code.get(i).getOpcode()==Opcode.NOP)continue;
            nodes.add(VerifyNatuneMobilePatch.node(code.get(i),addresses[i],labels,switches));
        }
        return new VerifyNatuneMobilePatch.Body(method.getImplementation().getRegisterCount(),nodes,List.of());
    }
    static void settingsAbi(Map<String,ClassDef> classes,Map<String,Method> methods) {
        var group=classes.get("Lr8/b0b;");var item=classes.get("Lr8/c0b;");
        var callback=classes.get("Lr8/si4;");var unit=classes.get("Lr8/wxc;");
        need(group!=null && item!=null && callback!=null && unit!=null &&
            (group.getAccessFlags()&1)!=0 && (item.getAccessFlags()&1)!=0 &&
            (callback.getAccessFlags()&0x201)==0x201 && (unit.getAccessFlags()&1)!=0,"Settings ABI classes unavailable");
        need(methods.containsKey("Lr8/b0b;-><init>(Ljava/lang/String;Ljava/util/List;)V") &&
            methods.containsKey("Lr8/c0b;-><init>(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZJLjava/util/List;Ljava/util/List;Lr8/si4;I)V") &&
            methods.containsKey("Lr8/si4;->a()Ljava/lang/Object;"),"Settings constructor/callback ABI differs");
        Map<String,String> fields=Map.of("Lr8/b0b;->b","Ljava/util/List;", "Lr8/c0b;->a","Ljava/lang/String;",
            "Lr8/c0b;->b","I", "Lr8/c0b;->g","J", "Lr8/wxc;->a","Lr8/wxc;");
        var found=new HashSet<String>();
        for(var clazz:List.of(group,item,unit))for(var field:clazz.getFields()) {
            String key=clazz.getType()+"->"+field.getName();
            if(fields.containsKey(key)) {
                need(fields.get(key).equals(field.getType()) && (field.getAccessFlags()&1)!=0 &&
                    (!key.equals("Lr8/wxc;->a") || (field.getAccessFlags()&8)!=0),"Settings public field ABI differs");
                found.add(key);
            }
        }
        need(found.equals(fields.keySet()),"Settings public field missing");
    }
    public static void main(String[] args) throws Exception {
        need(args.length == 2 || args.length == 3, "Expected original and patched APK, plus compiled extension for combined profile");
        boolean carrier = args.length == 3;
        var methods = new HashMap<String, Method>();
        var classesByName = new HashMap<String, ClassDef>();
        var dex = DexFileFactory.loadDexContainer(new File(args[1]), Opcodes.getDefault());
        for (String entry : dex.getDexEntryNames())
            for (var clazz : dex.getEntry(entry).getDexFile().getClasses()) {
                need(classesByName.put(clazz.getType(),clazz)==null,"Duplicate class");
                for (var method : clazz.getMethods())
                    need(methods.put(VerifyNatuneMobilePatch.signature(method), method) == null, "Duplicate method");
            }

        appEntry(methods.get(APP));
        wrapper(methods.get(TYPE + "->lowData(Landroid/content/Context;)Z"));
        marker(methods.get(TYPE + "->mobilePatchPresent()Z"));
        marker(methods.get(TYPE + "->flacPatchPresent()Z"));
        settingsAbi(classesByName,methods);
        var screen=methods.get(SCREEN);
        int settingsInsertion=settingsEntry(screen);
        var prefixes = new HashMap<String, Integer>();
        prefixes.put(APP, 1);
        if (carrier) prefixes.put(TYPE + "->lowData(Landroid/content/Context;)Z", 5);
        for (var hook : VerifyNatuneLocalFlacPatch.HOOKS) {
            var method = methods.get(hook.key());
            need(method != null && method.getAccessFlags() == 17 &&
                method.getImplementation().getRegisterCount() == hook.registers() &&
                method.getImplementation().getTryBlocks().isEmpty(), "Codec method shape changed");
            guard(method, hook);
            prefixes.put(hook.key(), 3 + hook.prefix());
        }

        VerifyNatuneMobilePatch.initializerControls();
        var original = VerifyNatuneMobilePatch.snapshot(new File(args[0]), false);
        var selected = VerifyNatuneMobilePatch.snapshot(new File(args[1]), false, prefixes, true);
        var classes = new TreeMap<>(selected.classes());
        var bodies = new TreeMap<>(selected.methods());
        Set<String> required = Set.of(TYPE, "Ldev/purple/patches/natune/PurpleSettingsPage;",
            "Ldev/purple/patches/natune/NatuneSettingsNavigation;");
        if (carrier) {
            var extension = VerifyNatuneMobilePatch.snapshot(new File(args[2]), false);
            need(extension.classes().keySet().containsAll(required) &&
                extension.classes().keySet().containsAll(VerifyNatuneFlacCarrier.REQUIRED_CLASSES),
                "Combined compiled extension is incomplete");
            need(Collections.disjoint(original.classes().keySet(), extension.classes().keySet()),
                "Compiled extension collides with an original class");
            var addedClasses = new TreeSet<>(classes.keySet());
            addedClasses.removeAll(original.classes().keySet());
            need(addedClasses.equals(extension.classes().keySet()), "Combined extension class set changed");
            for (String name : addedClasses)
                need(extension.classes().get(name).equals(classes.remove(name)), "Combined extension class changed: " + name);
            var addedMethods = new TreeSet<>(bodies.keySet());
            addedMethods.removeAll(original.methods().keySet());
            need(addedMethods.equals(extension.methods().keySet()), "Combined extension method set changed");
            Set<String> checkedMarkers = Set.of(TYPE + "->mobilePatchPresent()Z", TYPE + "->flacPatchPresent()Z");
            for (String name : extension.methods().keySet()) {
                if (!checkedMarkers.contains(name)) {
                    need(extension.methods().get(name).equals(bodies.get(name)), "Combined extension method changed: " + name);
                } else {
                    var before = extension.methods().get(name); var after = bodies.get(name);
                    need(after != null && before.flags() == after.flags() && before.annotations().equals(after.annotations()) &&
                        before.parameterAnnotations().equals(after.parameterAnnotations()), "Combined marker metadata changed: " + name);
                }
            }
            VerifyNatuneFlacCarrier.checkMethods(methods);
            System.out.println("Combined compiled extension classes=" + extension.classes().size() +
                "; methods=" + extension.methods().size());
        } else {
            // Android's extension build can also emit its generated namespace R.
            Set<String> permitted = Set.of(TYPE, "Ldev/purple/patches/natune/PurpleSettingsPage;",
                "Ldev/purple/patches/natune/NatuneSettingsNavigation;",
                "Ldev/purple/patches/natune/R;");
            var found = new HashSet<String>();
            for (String name : new ArrayList<>(classes.keySet())) {
                if (original.classes().containsKey(name)) continue;
                String outer = name.contains("$") ? name.substring(0, name.indexOf('$')) + ";" : name;
                need(permitted.contains(outer), "Unexpected extension class: " + name);
                found.add(outer);
                classes.remove(name);
            }
            need(found.containsAll(required), "Missing selected settings extension classes");
        }
        bodies.keySet().removeIf(name -> {
            int separator = name.indexOf("->");
            return separator > 0 && !original.classes().containsKey(name.substring(0, separator));
        });
        var screenState=bodies.get(SCREEN);
        need(screenState!=null,"Missing original settings screen");
        bodies.put(SCREEN,new VerifyNatuneMobilePatch.MethodState(screenState.flags(),screenState.annotations(),
            screenState.parameterAnnotations(),withoutSettingsEntry(screen,settingsInsertion)));
        VerifyNatuneMobilePatch.compare(original, new VerifyNatuneMobilePatch.Snapshot(bodies, classes), args,
            "Verified exact settings entry, bootstrap, stock-result wrapper and four optional FLAC guards; original remaining program/resources preserved.",
            carrier ? VerifyNatuneFlacCarrier.RESOURCE_ADDITIONS : Map.of());
        System.out.println("RUNTIME_SETTINGS_BYTECODE source_apk_compared=true navigation_bound=true device_test=false");
    }
}
