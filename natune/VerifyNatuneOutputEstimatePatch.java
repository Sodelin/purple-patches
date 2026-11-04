import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import java.io.File;
import java.util.*;

/** Checks the selected label-only patch and compares the remaining APK program/resources. */
public final class VerifyNatuneOutputEstimatePatch {
    static final String METHOD = "Lmoe/rukamori/archivetune/ui/utils/o;->i(Lr8/pl0;Ljava/lang/Integer;Lr8/o72;I)V";
    record Change(int offset, int register, String before, String after) {}
    static final List<Change> CHANGES = List.of(
        new Change(148, 6, "No conversion — source matches the ", "Reported rate: "),
        new Change(150, 9, " Hz output route", " Hz; actual output unknown"),
        new Change(157, 9, " Hz → ", " Hz; reported rate "),
        new Change(159, 10, " Hz at the output route", " Hz; actual output unknown"),
        new Change(161, 11, "Android resamples ", "Source ")
    );
    static void need(boolean value, String message) { VerifyNatuneMobilePatch.need(value, message); }
    static Method method(File apk) throws Exception {
        Method found = null;
        var container = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault());
        for (String entry : container.getDexEntryNames()) {
            for (var clazz : container.getEntry(entry).getDexFile().getClasses()) {
                for (var candidate : clazz.getMethods()) {
                    if (!VerifyNatuneMobilePatch.signature(candidate).equals(METHOD)) continue;
                    need(found == null, "Duplicate output UI method");
                    found = candidate;
                }
            }
        }
        need(found != null && found.getImplementation() != null, "Missing output UI method");
        return found;
    }
    static String node(Instruction instruction) {
        return VerifyNatuneMobilePatch.node(instruction, 0, Map.of(), Map.of());
    }
    static String match(Method method, String value, int register, Integer exactOffset) {
        String result = null;
        int offset = 0;
        for (var instruction : method.getImplementation().getInstructions()) {
            boolean string = instruction.getOpcode() == Opcode.CONST_STRING || instruction.getOpcode() == Opcode.CONST_STRING_JUMBO;
            if (string && instruction instanceof ReferenceInstruction ref && ref.getReference() instanceof StringReference text &&
                    text.getString().equals(value) && instruction instanceof OneRegisterInstruction reg && reg.getRegisterA() == register) {
                if (exactOffset == null || exactOffset == offset) {
                    need(result == null, "Ambiguous output string site");
                    result = node(instruction);
                }
            }
            offset += instruction.getCodeUnits();
        }
        need(result != null, "Expected output string site absent");
        return result;
    }
    public static void main(String[] args) throws Exception {
        need(args.length == 2, "Expected original and patched APK");
        File original = new File(args[0]), patched = new File(args[1]);
        Method before = method(original), after = method(patched);
        var expected = VerifyNatuneMobilePatch.snapshot(original, false);
        var actual = VerifyNatuneMobilePatch.snapshot(patched, false);
        var expectedState = expected.methods().get(METHOD);
        var body = expectedState.body();
        var instructions = new ArrayList<>(body.instructions());
        for (var change : CHANGES) {
            String oldNode = match(before, change.before(), change.register(), change.offset());
            String newNode = match(after, change.after(), change.register(), null);
            int index = instructions.indexOf(oldNode);
            need(index >= 0 && instructions.lastIndexOf(oldNode) == index, "Original normalized site ambiguous");
            instructions.set(index, newNode);
        }
        var methods = new TreeMap<>(expected.methods());
        methods.put(METHOD, new VerifyNatuneMobilePatch.MethodState(expectedState.flags(), expectedState.annotations(),
            expectedState.parameterAnnotations(), new VerifyNatuneMobilePatch.Body(body.registers(), instructions, body.handlers())));
        VerifyNatuneMobilePatch.compare(new VerifyNatuneMobilePatch.Snapshot(methods, expected.classes()), actual, args,
            "Verified five output-label changes; remaining program and resources preserved.");
    }
}
