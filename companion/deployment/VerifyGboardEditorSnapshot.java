import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Static checks on the built APK; these do not simulate an Android editor. */
public final class VerifyGboardEditorSnapshot {
    private static final String POLICY = "Ldev/purple/patches/gboard/SessionPolicy;";
    private static final String SNAPSHOT = "Ldev/purple/patches/gboard/PurpleAiRuntime$Snapshot;";
    private static final String INSERTION = "Ldev/purple/patches/gboard/SessionPolicy$InsertionAttempt;";
    private static final String RUNTIME = "Ldev/purple/patches/gboard/PurpleAiRuntime;";
    private static final String BOOTSTRAP = "Ldev/purple/patches/gboard/SessionPolicy$BootstrapAttempt;";
    private static final String RECEIVER = "Ldev/purple/patches/gboard/PurpleBootstrapReceiver;";

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }

    private static Method method(Map<String, ClassDef> classes, String owner, String name) {
        ClassDef type = classes.get(owner);
        require(type != null, "Missing class " + owner);
        Method found = null;
        for (Method candidate : type.getMethods()) {
            if (!candidate.getName().equals(name)) continue;
            require(found == null, "Ambiguous method " + owner + "->" + name);
            found = candidate;
        }
        require(found != null && found.getImplementation() != null, "Missing method body " + owner + "->" + name);
        return found;
    }

    private static List<MethodReference> calls(Method method) {
        List<MethodReference> result = new ArrayList<>();
        for (var instruction : method.getImplementation().getInstructions()) {
            if (instruction instanceof ReferenceInstruction ref && ref.getReference() instanceof MethodReference target)
                result.add(target);
        }
        return result;
    }

    private static List<Integer> positions(List<MethodReference> calls, String owner, String name) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < calls.size(); i++) {
            var call = calls.get(i);
            if (call.getDefiningClass().equals(owner) && call.getName().equals(name)) result.add(i);
        }
        return result;
    }

    /** Linkage only: presence of references is not control-flow or Android execution proof. */
    private static void verifyBootstrap(Map<String, ClassDef> classes) {
        for (String name : new String[]{"begin", "invalidate", "consume", "pending"})
            method(classes, BOOTSTRAP, name);
        var end = calls(method(classes, RUNTIME, "end"));
        var clear = calls(method(classes, RUNTIME, "clearPendingBootstrap"));
        var connect = calls(method(classes, RUNTIME, "connect"));
        var result = calls(method(classes, RUNTIME, "bootstrapResult"));
        var receive = calls(method(classes, RECEIVER, "onReceive"));
        require(!positions(end, RUNTIME, "clearPendingBootstrap").isEmpty(), "Session end lacks pending bootstrap cleanup reference");
        require(!positions(clear, BOOTSTRAP, "invalidate").isEmpty()
                        && !positions(clear, "Landroid/app/PendingIntent;", "cancel").isEmpty(),
                "Cleanup lacks bootstrap invalidation or PendingIntent cancellation reference");
        require(!positions(connect, POLICY, "bootstrapOfferCurrent").isEmpty()
                        && !positions(connect, BOOTSTRAP, "begin").isEmpty(),
                "Connection lacks offered-session or bootstrap begin reference");
        require(!positions(result, BOOTSTRAP, "consume").isEmpty()
                        && !positions(result, BOOTSTRAP, "pending").isEmpty(),
                "Bootstrap result lacks session consumption or pending-state reference");
        require(!positions(receive, RUNTIME, "bootstrapResult").isEmpty(), "Receiver does not reference bootstrap delivery");
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
        var capture = calls(method(classes, SNAPSHOT, "capture"));
        require(!positions(capture, POLICY, "captureEditor").isEmpty(), "Snapshot does not call editor capture policy");
        var editorPolicy = calls(method(classes, POLICY, "captureEditor"));
        require(!positions(editorPolicy, POLICY, "validEditorRead").isEmpty(), "Editor capture lacks read validation");
        var matches = calls(method(classes, SNAPSHOT, "matches"));
        var captures = positions(matches, SNAPSHOT, "capture");
        var connections = positions(matches, "Landroid/inputmethodservice/InputMethodService;", "getCurrentInputConnection");
        var sensitive = positions(matches, POLICY, "sensitive");
        require(captures.size() == 1 && connections.size() >= 2 && sensitive.size() >= 2,
                "Expected original and post-capture connection/sensitive checks");
        int at = captures.get(0);
        require(connections.get(0) < at && connections.get(connections.size()-1) > at
                        && sensitive.get(0) < at && sensitive.get(sensitive.size()-1) > at,
                "Expected checks on both sides of snapshot capture");
        require(!positions(matches, POLICY, "sameAnchor").isEmpty(), "Anchor comparison missing");
        System.out.println("Verified editor policy, capture, anchor and post-capture recheck references in APK DEX.");
        String version = System.getenv("BUNDLE_VERSION");
        var alpha = java.util.regex.Pattern.compile("0\\.1\\.0-alpha\\.([0-9]+)")
                .matcher(version == null ? "" : version);
        require(alpha.matches(), "Unsupported candidate version for editor verification");
        int revision = Integer.parseInt(alpha.group(1));
        if (revision >= 9) {
            method(classes, INSERTION, "attempt");
            boolean oneShot = false, commit = false, buttonDisabled = false, showListener = false;
            for (ClassDef type : classes.values())
                if (type.getType().startsWith("Ldev/purple/patches/gboard/PurpleAiRuntime"))
                    for (Method candidate : type.getMethods()) {
                        if (candidate.getImplementation() == null) continue;
                        for (MethodReference call : calls(candidate)) {
                            if (call.getDefiningClass().equals(INSERTION) && call.getName().equals("attempt")) oneShot = true;
                            if (call.getDefiningClass().equals("Landroid/view/inputmethod/InputConnection;") && call.getName().equals("commitText")) commit = true;
                            if (call.getName().equals("setEnabled")) buttonDisabled = true;
                            if (call.getName().equals("setOnShowListener")) showListener = true;
                        }
                    }
            require(oneShot && commit && buttonDisabled && showListener,
                    "Missing one-shot insertion, editor commit or preview button references");
            System.out.println("Verified preview and one-shot insertion references; no Android UI behavior is proved.");
        }
        if (revision >= 10) {
            verifyBootstrap(classes);
            var missing = new HashMap<>(classes);
            missing.remove(BOOTSTRAP);
            boolean refused = false;
            try { verifyBootstrap(missing); } catch (IllegalStateException expected) { refused = true; }
            require(refused, "Bootstrap checker accepted an absent policy class");
            System.out.println("Verified bootstrap session/linkage references and missing-class negative control; no Android execution is proved.");
        }
    }
}
