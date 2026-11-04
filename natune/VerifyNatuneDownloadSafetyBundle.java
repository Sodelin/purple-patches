import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22c;
import java.io.File;
import java.util.*;

/** Compare the combined cache baseline to the full Tidal/failure candidate. */
public final class VerifyNatuneDownloadSafetyBundle {
    static final String CONSTRUCTOR = "Lmoe/rukamori/archivetune/playback/g;-><init>(Landroid/content/Context;Lmoe/rukamori/archivetune/db/q0;Lr8/qpb;Lr8/ud1;Lr8/ud1;)V";
    static final String FAILURE = "Lr8/qe3;->b(Lr8/yd3;Lr8/hc3;Ljava/lang/Exception;)V";
    static final String PURGE = "Lmoe/rukamori/archivetune/playback/g;->a(Lmoe/rukamori/archivetune/playback/g;Ljava/lang/String;)V";
    static final String WRAPPER = "Lr8/wl7;-><init>(Landroid/content/Context;Lr8/am2;)V";
    static void need(boolean v, String reason) { VerifyNatuneMobilePatch.need(v, reason); }
    static Method find(File apk, String signature) throws Exception {
        Method found = null;
        var dex = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault());
        for (String name : dex.getDexEntryNames())
            for (var cls : dex.getEntry(name).getDexFile().getClasses())
                for (var method : cls.getMethods())
                    if (VerifyNatuneMobilePatch.signature(method).equals(signature)) {
                        need(found == null, "Duplicate selected method"); found = method;
                    }
        need(found != null && found.getImplementation() != null, "Selected method missing");
        return found;
    }
    static List<Instruction> instructions(Method method) {
        var out = new ArrayList<Instruction>(); method.getImplementation().getInstructions().forEach(out::add); return out;
    }
    static boolean call(Instruction insn, String target) {
        return insn instanceof ReferenceInstruction ref && ref.getReference() instanceof MethodReference method &&
            VerifyNatuneMobilePatch.signature(method).equals(target);
    }
    static VerifyNatuneMobilePatch.Body normalize(Method method, int skipStart, int skipCount, int replace, Instruction replacement) {
        var code = instructions(method); var labels = new HashMap<Integer,Integer>();
        var switches = new HashMap<Integer,List<Integer>>(); var addresses = new int[code.size()];
        need(method.getImplementation().getTryBlocks().isEmpty(), "Selected method has unreviewed try regions");
        int address=0, ordinal=0;
        for (int i=0;i<code.size();i++) {
            var insn=code.get(i); addresses[i]=address;
            if (i<skipStart || i>=skipStart+skipCount) {
                labels.put(address, ordinal);
                if (insn.getOpcode()==Opcode.PACKED_SWITCH || insn.getOpcode()==Opcode.SPARSE_SWITCH)
                    switches.computeIfAbsent(address+((OffsetInstruction)insn).getCodeOffset(), k->new ArrayList<>()).add(address);
                if (insn.getOpcode()!=Opcode.NOP) ordinal++;
            }
            address+=insn.getCodeUnits();
        }
        labels.put(address,ordinal);
        var nodes=new ArrayList<String>();
        for (int i=0;i<code.size();i++) {
            if ((i>=skipStart && i<skipStart+skipCount) || code.get(i).getOpcode()==Opcode.NOP) continue;
            nodes.add(VerifyNatuneMobilePatch.node(i==replace ? replacement : code.get(i), addresses[i], labels, switches));
        }
        return new VerifyNatuneMobilePatch.Body(method.getImplementation().getRegisterCount(), nodes, List.of());
    }
    static VerifyNatuneMobilePatch.Body removeWrapper(Method method) {
        var code=instructions(method); int callIndex=-1;
        for (int i=0;i<code.size();i++) if (call(code.get(i),WRAPPER)) { need(callIndex<0,"Duplicate wrapper");callIndex=i; }
        need(callIndex>0 && callIndex+1<code.size(),"Missing wrapper");
        var create=code.get(callIndex-1);var invoke=code.get(callIndex);var store=code.get(callIndex+1);
        need(create.getOpcode()==Opcode.NEW_INSTANCE && ((OneRegisterInstruction)create).getRegisterA()==1 &&
            ((ReferenceInstruction)create).getReference() instanceof TypeReference &&
            ((TypeReference)((ReferenceInstruction)create).getReference()).getType().equals("Lr8/wl7;"),"Wrong wrapper type");
        need(invoke.getOpcode()==Opcode.INVOKE_DIRECT && invoke instanceof FiveRegisterInstruction,"Wrong wrapper call");
        var r=(FiveRegisterInstruction)invoke;
        need(r.getRegisterCount()==3 && r.getRegisterC()==1 && r.getRegisterD()==2 && r.getRegisterE()==3,"Wrong wrapper arguments");
        need(store.getOpcode()==Opcode.IPUT_OBJECT && ((TwoRegisterInstruction)store).getRegisterA()==1 &&
            ((TwoRegisterInstruction)store).getRegisterB()==5 && ((ReferenceInstruction)store).getReference() instanceof FieldReference,
            "Wrong wrapper assignment");
        var field=(FieldReference)((ReferenceInstruction)store).getReference();
        need(field.getDefiningClass().equals("Lr8/be1;") && field.getName().equals("C") && field.getType().equals("Lr8/am2;"),"Wrong upstream field");
        return normalize(method,callIndex-1,2,callIndex+1,new ImmutableInstruction22c(Opcode.IPUT_OBJECT,3,5,field));
    }
    static VerifyNatuneMobilePatch.Body withoutOriginalPurge(Method method) {
        var code=instructions(method); int target=-1, address=0;
        for (int i=0;i<code.size();i++) {
            if (call(code.get(i),PURGE)) { need(target<0 && address==41,"Failure purge moved"); target=i; }
            address+=code.get(i).getCodeUnits();
        }
        need(target>=0 && code.get(target).getOpcode()==Opcode.INVOKE_STATIC,"Failure purge missing");
        return normalize(method,target,1,-1,null);
    }
    public static void main(String[] args) throws Exception {
        need(args.length==2,"Expected combined baseline and full bundle APKs");
        File baselineFile=new File(args[0]), fullFile=new File(args[1]);
        var baseline=VerifyNatuneMobilePatch.snapshot(baselineFile,false);
        var full=VerifyNatuneMobilePatch.snapshot(fullFile,false);
        var expected=new TreeMap<>(baseline.methods()); var actual=new TreeMap<>(full.methods());
        var before=expected.get(FAILURE);var after=actual.get(CONSTRUCTOR);
        need(before!=null && after!=null,"Selected snapshots absent");
        expected.put(FAILURE,new VerifyNatuneMobilePatch.MethodState(before.flags(),before.annotations(),before.parameterAnnotations(),
            withoutOriginalPurge(find(baselineFile,FAILURE))));
        actual.put(CONSTRUCTOR,new VerifyNatuneMobilePatch.MethodState(after.flags(),after.annotations(),after.parameterAnnotations(),
            removeWrapper(find(fullFile,CONSTRUCTOR))));
        VerifyNatuneMobilePatch.compare(new VerifyNatuneMobilePatch.Snapshot(expected,baseline.classes()),
            new VerifyNatuneMobilePatch.Snapshot(actual,full.classes()),args,
            "Verified Tidal wrapper and failure-only purge removal; other program and resources preserved.");
    }
}
