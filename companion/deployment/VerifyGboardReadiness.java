import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Static linkage checks only, not Android execution or control-flow proof. */
public final class VerifyGboardReadiness {
    private static final String BRIDGE = "Ldev/purple/patches/gboard/BridgeClient;";
    private static final String POLICY = "Ldev/purple/patches/gboard/SessionPolicy;";
    private static final String RUNTIME = "Ldev/purple/patches/gboard/PurpleAiRuntime";
    private static final String CAPTURE = "Ldev/purple/patches/gboard/WhisperCapture;";

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
    private static Method method(Map<String, ClassDef> classes, String owner, String name) {
        ClassDef type = classes.get(owner);
        require(type != null, "Missing " + owner);
        Method found = null;
        for (Method candidate : type.getMethods()) if (candidate.getName().equals(name)) {
            require(found == null, "Ambiguous " + owner + "->" + name);
            found = candidate;
        }
        require(found != null && found.getImplementation() != null, "Missing method " + owner + "->" + name);
        return found;
    }
    private static boolean hasString(Method method, String value) {
        for (var instruction : method.getImplementation().getInstructions())
            if (instruction instanceof ReferenceInstruction ref && ref.getReference() instanceof StringReference text
                    && text.getString().equals(value)) return true;
        return false;
    }
    private static List<MethodReference> calls(Method method) {
        List<MethodReference> found = new ArrayList<>();
        if (method.getImplementation() != null)
            for (var instruction : method.getImplementation().getInstructions())
                if (instruction instanceof ReferenceInstruction ref && ref.getReference() instanceof MethodReference call)
                    found.add(call);
        return found;
    }
    private static int position(List<MethodReference> calls, String owner, String name) {
        for (int i = 0; i < calls.size(); i++)
            if (calls.get(i).getDefiningClass().equals(owner) && calls.get(i).getName().equals(name)) return i;
        return -1;
    }
    public static void main(String[] args) throws Exception {
        require(args.length == 1, "Expected one patched APK");
        var dex = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        Map<String, ClassDef> classes = new HashMap<>();
        for (String name : dex.getDexEntryNames()) {
            var entry = dex.getEntry(name);
            require(entry != null, "Missing DEX entry " + name);
            for (var type : entry.getDexFile().getClasses())
                require(classes.put(type.getType(), type) == null, "Duplicate class " + type.getType());
        }
        Method verify = method(classes, BRIDGE, "verify");
        require(hasString(verify, "keyboard_data_path") && hasString(verify, "whisper_preflight")
                && hasString(verify, "whisper_family"), "Expected health fields missing from bridge verification");
        require(position(calls(verify), POLICY, "whisperPreflightSupported") >= 0,
                "Bridge does not call the voice-preflight policy");
        String version = System.getenv("BUNDLE_VERSION");
        var alpha = java.util.regex.Pattern.compile("0\\.1\\.0-alpha\\.([0-9]+)")
                .matcher(version == null ? "" : version);
        require(alpha.matches(), "Unsupported candidate version for readiness verification");
        if (Integer.parseInt(alpha.group(1)) >= 11) {
            require(hasString(verify, "ok") && hasString(verify, "protocol")
                            && position(calls(verify), POLICY, "bridgeHealth") >= 0,
                    "Bridge lacks successful-protocol health policy references");
            Method health = method(classes, POLICY, "bridgeHealth");
            require(hasString(health, "memory-only-v1"), "Expected keyboard data-path marker missing");
        }
        Method policy = method(classes, POLICY, "whisperPreflightSupported");
        require(hasString(policy, "full-v3-pin-engine-sha-v1"), "Expected voice-preflight capability missing");
        boolean orderedReferences = false;
        for (ClassDef type : classes.values()) if (type.getType().startsWith(RUNTIME))
            for (Method candidate : type.getMethods()) {
                var refs = calls(candidate);
                int check = position(refs, BRIDGE, "verify");
                int record = position(refs, CAPTURE, "record");
                if (check >= 0 && record > check) orderedReferences = true;
            }
        require(orderedReferences, "No runtime method contains verify before capture references");
        System.out.println("Verified voice health fields, policy and verify-before-capture references in APK DEX.");
    }
}
