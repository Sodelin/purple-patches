import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.File;
import java.util.*;

/** Verify the optional cache guard without executing either APK. */
public final class VerifyNatuneCacheCoveragePatch {
    static final String TARGET = "Lmoe/rukamori/archivetune/playback/b;->a(Lr8/jm2;)Lr8/jm2;";
    static final String GUARD = "Ldev/purple/patches/natune/CacheRangeGuard;->complete(Ljava/lang/Object;Ljava/lang/String;J)Z";
    static void need(boolean value, String reason) { VerifyNatuneMobilePatch.need(value, reason); }
    static Method target(File apk) throws Exception {
        Method found = null;
        var container = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault());
        for (String entry : container.getDexEntryNames())
            for (var cls : container.getEntry(entry).getDexFile().getClasses())
                for (var method : cls.getMethods())
                    if (VerifyNatuneMobilePatch.signature(method).equals(TARGET)) {
                        need(found == null, "Duplicate cache resolver"); found = method;
                    }
        need(found != null && found.getImplementation() != null, "Missing cache resolver");
        return found;
    }
    static VerifyNatuneMobilePatch.Body withoutGuard(Method method) {
        var code = new ArrayList<Instruction>();
        method.getImplementation().getInstructions().forEach(code::add);
        int start = -1;
        for (int i = 0; i < code.size(); i++) {
            if (code.get(i) instanceof ReferenceInstruction r && r.getReference() instanceof MethodReference m &&
                    VerifyNatuneMobilePatch.signature(m).equals(GUARD)) {
                need(start < 0 && i > 0 && i + 2 < code.size(), "Duplicate or misplaced guard");
                start = i - 1;
            }
        }
        need(start >= 0, "Cache guard absent");
        var read = code.get(start);
        need(read.getOpcode() == Opcode.IGET_OBJECT && read instanceof TwoRegisterInstruction &&
            ((TwoRegisterInstruction)read).getRegisterA() == 0 && ((TwoRegisterInstruction)read).getRegisterB() == 10 &&
            read instanceof ReferenceInstruction && ((ReferenceInstruction)read).getReference() instanceof FieldReference,
            "Wrong cache receiver");
        var field = (FieldReference)((ReferenceInstruction)read).getReference();
        need(field.getDefiningClass().equals("Lmoe/rukamori/archivetune/playback/g;") &&
            field.getName().equals("c") && field.getType().equals("Lr8/ud1;"), "Wrong cache field");
        var invoke = code.get(start + 1);
        need(invoke.getOpcode() == Opcode.INVOKE_STATIC && invoke instanceof FiveRegisterInstruction, "Wrong guard call");
        var regs = (FiveRegisterInstruction)invoke;
        need(regs.getRegisterCount() == 4 && regs.getRegisterC() == 0 && regs.getRegisterD() == 13 &&
            regs.getRegisterE() == 7 && regs.getRegisterF() == 8, "Wrong guard arguments");
        need(code.get(start+2).getOpcode() == Opcode.MOVE_RESULT &&
            ((OneRegisterInstruction)code.get(start+2)).getRegisterA() == 0, "Wrong guard result");
        need(code.get(start+3).getOpcode() == Opcode.IF_EQZ &&
            ((OneRegisterInstruction)code.get(start+3)).getRegisterA() == 0, "Wrong guard branch");

        int[] addresses = new int[code.size()];
        var labels = new HashMap<Integer,Integer>();
        var switches = new HashMap<Integer,List<Integer>>();
        int address = 0, ordinal = 0;
        for (int i=0; i<code.size(); i++) {
            Instruction insn = code.get(i); addresses[i] = address;
            if (i < start || i >= start+4) {
                labels.put(address, ordinal);
                if (insn.getOpcode() == Opcode.PACKED_SWITCH || insn.getOpcode() == Opcode.SPARSE_SWITCH)
                    switches.computeIfAbsent(address+((OffsetInstruction)insn).getCodeOffset(), k -> new ArrayList<>()).add(address);
                if (insn.getOpcode() != Opcode.NOP) ordinal++;
            }
            address += insn.getCodeUnits();
        }
        labels.put(address, ordinal);
        int destination = addresses[start+3] + ((OffsetInstruction)code.get(start+3)).getCodeOffset();
        int targetIndex = -1;
        for (int i=0; i<addresses.length; i++) if (addresses[i] == destination) targetIndex = i;
        need(targetIndex >= 0 && code.get(targetIndex).getOpcode() == Opcode.MOVE_WIDE_FROM16 &&
            ((TwoRegisterInstruction)code.get(targetIndex)).getRegisterA() == 5 &&
            ((TwoRegisterInstruction)code.get(targetIndex)).getRegisterB() == 17, "Guard failure must continue the cache loop");
        var nodes = new ArrayList<String>();
        for (int i=0; i<code.size(); i++) {
            if ((i>=start && i<start+4) || code.get(i).getOpcode()==Opcode.NOP) continue;
            nodes.add(VerifyNatuneMobilePatch.node(code.get(i), addresses[i], labels, switches));
        }
        var handlers = new ArrayList<String>();
        for (var block : method.getImplementation().getTryBlocks()) {
            var row = new StringBuilder();
            VerifyNatuneMobilePatch.token(row, VerifyNatuneMobilePatch.label(labels, block.getStartCodeAddress()));
            VerifyNatuneMobilePatch.token(row, VerifyNatuneMobilePatch.label(labels, block.getStartCodeAddress()+block.getCodeUnitCount()));
            for (var h : block.getExceptionHandlers()) {
                VerifyNatuneMobilePatch.token(row, h.getExceptionType());
                VerifyNatuneMobilePatch.token(row, VerifyNatuneMobilePatch.label(labels, h.getHandlerCodeAddress()));
            }
            handlers.add(row.toString());
        }
        return new VerifyNatuneMobilePatch.Body(method.getImplementation().getRegisterCount(), nodes, handlers);
    }
    public static void main(String[] args) throws Exception {
        need(args.length == 3, "Expected original APK, patched APK and compiled extension DEX");
        File originalFile = new File(args[0]), patchedFile = new File(args[1]);
        var original = VerifyNatuneMobilePatch.snapshot(originalFile, false);
        var selected = VerifyNatuneMobilePatch.snapshot(patchedFile, false);
        var extension = VerifyNatuneMobilePatch.snapshot(new File(args[2]), false);
        need(extension.classes().containsKey("Ldev/purple/patches/natune/CacheRangeGuard;"), "Missing cache guard extension");
        var classes = new TreeMap<>(selected.classes());
        var methods = new TreeMap<>(selected.methods());
        var addedClasses = new TreeSet<>(classes.keySet()); addedClasses.removeAll(original.classes().keySet());
        var addedMethods = new TreeSet<>(methods.keySet()); addedMethods.removeAll(original.methods().keySet());
        if (Collections.disjoint(original.classes().keySet(), extension.classes().keySet())) {
            need(addedClasses.equals(extension.classes().keySet()), "Unexpected extension class set");
            for (String name : addedClasses) need(extension.classes().get(name).equals(classes.remove(name)), "Extension class changed");
            need(addedMethods.equals(extension.methods().keySet()), "Unexpected extension method set");
            for (String name : addedMethods) need(extension.methods().get(name).equals(methods.remove(name)), "Extension method changed");
        } else {
            // Combined profile: the verified baseline already includes the extension
            // and has patch-selected feature markers. Compare all of it unchanged.
            need(original.classes().keySet().containsAll(extension.classes().keySet()) &&
                addedClasses.isEmpty() && addedMethods.isEmpty(), "Partial or changed baseline extension");
        }
        var state = methods.get(TARGET);
        need(state != null, "Missing cache resolver snapshot");
        methods.put(TARGET, new VerifyNatuneMobilePatch.MethodState(state.flags(), state.annotations(),
            state.parameterAnnotations(), withoutGuard(target(patchedFile))));
        VerifyNatuneMobilePatch.compare(original, new VerifyNatuneMobilePatch.Snapshot(methods, classes), args,
            "Verified cache range guard and loop continuation; remaining program and resources preserved.");
    }
}
