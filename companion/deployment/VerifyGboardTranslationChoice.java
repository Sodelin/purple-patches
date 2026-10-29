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
        Method chooser = method(classes, RUNTIME, "chooseTranslationEffort");
        require(hasString(chooser, "Medium") && hasString(chooser, "Max")
                && hasString(chooser, "medium") && hasString(chooser, "max"),
                "Translation effort chooser values are missing");
        boolean guardedSubmit = false;
        for (ClassDef type : classes.values()) if (type.getType().startsWith("Ldev/purple/patches/gboard/PurpleAiRuntime"))
            for (Method candidate : type.getMethods())
                if (calls(candidate, SNAPSHOT, "matches") && calls(candidate, RUNTIME, "submit")) guardedSubmit = true;
        require(guardedSubmit, "No guarded translation submit reference found");
        System.out.println("Verified translation effort chooser, policy, guarded submit and Sol request references in APK DEX.");
    }
}
