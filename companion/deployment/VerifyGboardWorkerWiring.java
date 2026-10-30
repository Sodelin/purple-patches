import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Static checks of selected compiled references and branch layout, not Android execution. */
public final class VerifyGboardWorkerWiring {
    private static final String PREFIX = "Ldev/purple/patches/gboard/";
    private static final String RUNTIME = PREFIX + "PurpleAiRuntime;";
    private static final String SNAPSHOT = PREFIX + "PurpleAiRuntime$Snapshot;";
    private static final String ACCESS = PREFIX + "PurpleAccessPoints";
    private static final String WORKER = PREFIX + "WorkerStart;";
    private static final String STARTER = PREFIX + "WorkerStart$Starter;";
    private static final String CAPTURE = PREFIX + "WhisperCapture;";
    private static final String LIFECYCLE = "Ldev/jason/gboardpatches/extension/editingaccesspoints/GboardEditingAccessPointsRuntime;";

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static boolean matches(MethodReference method, String owner, String name,
            String result, String... parameters) {
        if (!owner.equals(method.getDefiningClass()) || !name.equals(method.getName())
                || !result.equals(method.getReturnType()) || method.getParameterTypes().size() != parameters.length)
            return false;
        for (int i = 0; i < parameters.length; i++)
            if (!parameters[i].contentEquals(method.getParameterTypes().get(i))) return false;
        return true;
    }

    private static Method method(Map<String, ClassDef> classes, String owner, String name,
            String result, String... parameters) {
        ClassDef type = classes.get(owner);
        require(type != null, "Missing class " + owner);
        Method selected = null;
        for (Method candidate : type.getMethods()) if (matches(candidate, owner, name, result, parameters)) {
            require(selected == null, "Duplicate method " + owner + "->" + name);
            selected = candidate;
        }
        require(selected != null && selected.getImplementation() != null, "Missing body " + owner + "->" + name);
        return selected;
    }

    private static boolean invokes(Instruction instruction, String owner, String name,
            String result, String... parameters) {
        Opcode op = instruction.getOpcode();
        if (op != Opcode.INVOKE_STATIC && op != Opcode.INVOKE_STATIC_RANGE
                && op != Opcode.INVOKE_VIRTUAL && op != Opcode.INVOKE_VIRTUAL_RANGE
                && op != Opcode.INVOKE_INTERFACE && op != Opcode.INVOKE_INTERFACE_RANGE
                && op != Opcode.INVOKE_DIRECT && op != Opcode.INVOKE_DIRECT_RANGE) return false;
        return instruction instanceof ReferenceInstruction ref
                && ref.getReference() instanceof MethodReference call
                && matches(call, owner, name, result, parameters);
    }

    private static int countCalls(Method method, String owner, String name, String result, String... parameters) {
        int count = 0;
        for (Instruction instruction : method.getImplementation().getInstructions())
            if (invokes(instruction, owner, name, result, parameters)) count++;
        return count;
    }

    private static void verifyFailureBranch(Method caller) {
        List<Instruction> code = new ArrayList<>();
        List<Integer> offsets = new ArrayList<>();
        int offset = 0;
        for (Instruction instruction : caller.getImplementation().getInstructions()) {
            code.add(instruction); offsets.add(offset); offset += instruction.getCodeUnits();
        }
        int launches = 0;
        for (int i = 0; i < code.size(); i++) {
            if (!invokes(code.get(i), WORKER, "launch", "Z", "Ljava/lang/Runnable;", "Ljava/lang/String;")) continue;
            launches++;
            require(i + 2 < code.size() && code.get(i + 1).getOpcode() == Opcode.MOVE_RESULT
                    && code.get(i + 1) instanceof OneRegisterInstruction,
                    caller.getName() + ": launch result is not consumed as expected");
            Instruction branch = code.get(i + 2);
            require(branch.getOpcode() == Opcode.IF_NEZ && branch instanceof OneRegisterInstruction
                    && branch instanceof OffsetInstruction
                    && ((OneRegisterInstruction) branch).getRegisterA()
                       == ((OneRegisterInstruction) code.get(i + 1)).getRegisterA(),
                    caller.getName() + ": launch result is not followed by the expected success branch");
            int target = offsets.get(i + 2) + ((OffsetInstruction) branch).getCodeOffset();
            require(target > offsets.get(i + 2) && offsets.contains(target),
                    caller.getName() + ": success branch is not a forward instruction target");
            int cleanup = 0;
            for (int j = i + 3; j < code.size() && offsets.get(j) < target; j++) {
                if (invokes(code.get(j), RUNTIME, "workerStartFailed", "V", "J",
                        "Ljava/util/concurrent/atomic/AtomicBoolean;", "Landroid/app/AlertDialog;",
                        CAPTURE, "Ljava/lang/String;")) cleanup++;
            }
            require(cleanup == 1, caller.getName() + ": missing or ambiguous cleanup reference on failure fallthrough");
            System.out.println(caller.getName() + ": launch at " + offsets.get(i)
                    + ", success branch at " + offsets.get(i + 2) + " to " + target
                    + ", one failure-fallthrough cleanup reference");
        }
        require(launches == 1, caller.getName() + ": expected exactly one worker launch");
    }

    private static int ownerCalls(Map<String, ClassDef> classes, String ownerPrefix, String targetOwner,
            String name, String result, String... parameters) {
        int found = 0;
        for (ClassDef type : classes.values()) if (type.getType().startsWith(ownerPrefix))
            for (Method caller : type.getMethods()) if (caller.getImplementation() != null) {
                int count = countCalls(caller, targetOwner, name, result, parameters);
                if (count > 0) System.out.println(type.getType() + "->" + caller.getName()
                        + " invokes " + targetOwner + "->" + name + " " + count + " time(s)");
                found += count;
            }
        return found;
    }

    private static void verify(Map<String, ClassDef> classes) {
        Method delegate = method(classes, WORKER, "launch", "Z", "Ljava/lang/Runnable;", "Ljava/lang/String;");
        method(classes, WORKER, "launch", "Z", STARTER, "Ljava/lang/Runnable;", "Ljava/lang/String;");
        require(countCalls(delegate, WORKER, "launch", "Z", STARTER, "Ljava/lang/Runnable;", "Ljava/lang/String;") == 1,
                "Worker entry does not reference the three-argument helper");
        verifyFailureBranch(method(classes, RUNTIME, "chooseTranslationEffort", "V", SNAPSHOT, "Ljava/lang/String;"));
        verifyFailureBranch(method(classes, RUNTIME, "submit", "V", SNAPSHOT, "Ljava/lang/String;", "Ljava/lang/String;"));
        require(ownerCalls(classes, LIFECYCLE, RUNTIME, "start", "V", "Ljava/lang/Object;") == 1,
                "Keyboard lifecycle start reference is missing or ambiguous");
        require(ownerCalls(classes, LIFECYCLE, RUNTIME, "end", "V") == 1,
                "Keyboard lifecycle end reference is missing or ambiguous");
        require(ownerCalls(classes, ACCESS, RUNTIME, "click", "V", "Z") == 1,
                "Access-point callback reference is missing or ambiguous");
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 1, "Expected one patched APK");
        var dex = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        Map<String, ClassDef> classes = new HashMap<>();
        for (String name : dex.getDexEntryNames()) {
            var entry = dex.getEntry(name);
            require(entry != null, "Missing DEX entry " + name);
            for (ClassDef type : entry.getDexFile().getClasses())
                require(classes.put(type.getType(), type) == null, "Duplicate class " + type.getType());
        }
        verify(classes);
        System.out.println("COMPILED_WORKER_WIRING_PRESENT; no runtime, UI, privacy or complete control-flow acceptance is established");
    }
}
