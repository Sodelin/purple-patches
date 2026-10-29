import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Static APK linkage checks; does not exercise an Android UI or live model. */
public final class VerifyGboardTranslationChoice {
    private static final String RUNTIME = "Ldev/purple/patches/gboard/PurpleAiRuntime;";
    private static final String SNAPSHOT = "Ldev/purple/patches/gboard/PurpleAiRuntime$Snapshot;";
    private static final String BRIDGE = "Ldev/purple/patches/gboard/BridgeClient;";
    private static final String POLICY = "Ldev/purple/patches/gboard/SessionPolicy;";
    private static final String PERMISSION = "Ldev/purple/patches/gboard/PurplePermissionActivity;";
    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
    private static Method method(Map<String, ClassDef> classes, String owner, String name) {
        ClassDef type = classes.get(owner); require(type != null, "Missing " + owner);
        Method found = null;
        for (Method candidate : type.getMethods()) if (candidate.getName().equals(name)) {
            require(found == null, "Ambiguous " + owner + "->" + name); found = candidate;
        }
        require(found != null && found.getImplementation() != null, "Missing " + owner + "->" + name);
        return found;
    }
    private static boolean hasString(Method method, String value) {
        if (method.getImplementation() == null) return false;
        for (var instruction : method.getImplementation().getInstructions())
            if (instruction instanceof ReferenceInstruction ref && ref.getReference() instanceof StringReference text
                    && text.getString().equals(value)) return true;
        return false;
    }
    private static boolean calls(Method method, String owner, String name) {
        if (method.getImplementation() == null) return false;
        for (var instruction : method.getImplementation().getInstructions())
            if (instruction instanceof ReferenceInstruction ref && ref.getReference() instanceof MethodReference call
                    && call.getDefiningClass().equals(owner) && call.getName().equals(name)) return true;
        return false;
    }
    private static boolean callsName(Method method, String name) {
        if (method.getImplementation() == null) return false;
        for (var instruction : method.getImplementation().getInstructions())
            if (instruction instanceof ReferenceInstruction ref && ref.getReference() instanceof MethodReference call
                    && call.getName().equals(name)) return true;
        return false;
    }
    public static void main(String[] args) throws Exception {
        require(args.length == 1, "Expected one patched APK");
        var dex = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        Map<String, ClassDef> classes = new HashMap<>();
        for (String name : dex.getDexEntryNames()) {
            var entry = dex.getEntry(name); require(entry != null, "Missing DEX entry " + name);
            for (var type : entry.getDexFile().getClasses())
                require(classes.put(type.getType(), type) == null, "Duplicate class " + type.getType());
        }
        Method policy = method(classes, POLICY, "translationEffortAllowed");
        require(hasString(policy, "medium") && hasString(policy, "max"), "Missing permitted effort values");
        Method bridge = method(classes, BRIDGE, "execute");
        require(calls(bridge, POLICY, "translationEffortAllowed") && hasString(bridge, "effort")
                && hasString(bridge, "gpt-6.1-sol") && calls(bridge, "Lorg/json/JSONObject;", "put"),
                "Translation request lacks effort-policy or Sol JSON references");
        String version = System.getenv("BUNDLE_VERSION");
        var alpha = java.util.regex.Pattern.compile("0\\.1\\.0-alpha\\.([0-9]+)")
                .matcher(version == null ? "" : version);
        require(alpha.matches(), "Unsupported candidate version for translation verification");
        int revision = Integer.parseInt(alpha.group(1));
        Method chooser = method(classes, RUNTIME,
                revision >= 11 ? "showTranslationEfforts" : "chooseTranslationEffort");
        require(hasString(chooser, "Medium") && hasString(chooser, "Max")
                && hasString(chooser, "medium") && hasString(chooser, "max"),
                "Translation effort chooser values are missing");
        if (revision >= 11) {
            Method choices = method(classes, BRIDGE, "translationEfforts");
            Method parser = method(classes, BRIDGE, "translationCatalog");
            Method catalogue = method(classes, POLICY, "translationCatalog");
            require(hasString(choices, "/models") && calls(choices, BRIDGE, "translationCatalog")
                    && calls(parser, POLICY, "translationCatalog")
                    && hasString(catalogue, "gpt-6.1-sol")
                    && calls(catalogue, POLICY, "translationEffortChoices"),
                    "Translation catalogue lacks the selected model, source or choice policy reference");
            boolean loads = false, displays = false;
            for (ClassDef type : classes.values()) if (type.getType().startsWith("Ldev/purple/patches/gboard/PurpleAiRuntime"))
                for (Method candidate : type.getMethods()) {
                    loads |= calls(candidate, BRIDGE, "translationEfforts");
                    displays |= calls(candidate, RUNTIME, "showTranslationEfforts");
                }
            require(loads && displays, "Translation catalogue is not linked to the chooser");
            System.out.println("Verified local Sol catalogue and filtered-effort chooser references; no Android UI or account availability is proved.");
        }
        boolean guardedSubmit = false;
        for (ClassDef type : classes.values()) if (type.getType().startsWith("Ldev/purple/patches/gboard/PurpleAiRuntime"))
            for (Method candidate : type.getMethods())
                if (calls(candidate, SNAPSHOT, "matches") && calls(candidate, RUNTIME, "submit")) guardedSubmit = true;
        require(guardedSubmit, "No guarded translation submit reference found");
        System.out.println("Verified translation effort chooser, policy, guarded submit and Sol request references in APK DEX.");
        if (!version.equals("0.1.0-alpha.6")) {
            Method scope = method(classes, POLICY, "permissionsFor");
            require(hasString(scope, "local-tools") && hasString(scope, "microphone")
                    && hasString(scope, "com.termux.permission.RUN_COMMAND")
                    && hasString(scope, "android.permission.RECORD_AUDIO"),
                    "Missing separate permission scopes or permissions");
            Method activity = method(classes, PERMISSION, "onCreate");
            require(calls(activity, POLICY, "permissionsFor") && callsName(activity, "requestPermissions")
                    && callsName(activity, "checkSelfPermission")
                    && hasString(activity, "dev.purple.patches.gboard.permission_scope"),
                    "Permission activity does not reference the scoped request policy");
            Method connect = method(classes, RUNTIME, "connect");
            Method click = method(classes, RUNTIME, "click");
            require(hasString(connect, "local-tools") && calls(connect, RUNTIME, "permissions")
                    && hasString(click, "microphone") && calls(click, RUNTIME, "permissions"),
                    "Local-tools and microphone actions do not reference separate scopes");
            require(hasString(connect, "com.termux.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL")
                    && hasString(connect, "0"), "Missing best-effort Termux logging extra");
            System.out.println("Verified scoped permission and best-effort logging-extra references in APK DEX; runtime privacy is not established.");
        }
    }
}
