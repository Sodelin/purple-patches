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
    }
}
