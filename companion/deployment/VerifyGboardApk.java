import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipFile;

/** Off-phone verification through the same proven DEX library as Morphe. */
public final class VerifyGboardApk {
    record MethodKey(String name, List<String> parameters, String result) {}
    record FieldKey(String name, String type) {}
    record Members(Map<MethodKey, Boolean> methods, Set<FieldKey> fields) {}

    static Map<String, ClassDef> classes(File apk) throws Exception {
        var container = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault());
        Map<String, ClassDef> result = new TreeMap<>();
        for (String name : container.getDexEntryNames()) {
            var entry = container.getEntry(name);
            if (entry == null) throw new IllegalStateException("Missing DEX entry");
            for (var type : entry.getDexFile().getClasses())
                if (result.put(type.getType(), type) != null)
                    throw new IllegalStateException("Duplicate DEX class: " + type.getType());
        }
        return result;
    }
    static MethodKey key(Method method) {
        var parameters = new ArrayList<String>();
        for (var type : method.getParameterTypes()) parameters.add(type.toString());
        return new MethodKey(method.getName(), List.copyOf(parameters), method.getReturnType());
    }
    static FieldKey key(Field field) { return new FieldKey(field.getName(), field.getType()); }
    static Members members(ClassDef type) {
        Map<MethodKey, Boolean> methods = new HashMap<>();
        Set<FieldKey> fields = new HashSet<>();
        for (var method : type.getMethods()) {
            if (methods.put(key(method), method.getImplementation() != null) != null)
                throw new IllegalStateException("Duplicate method in " + type.getType());
        }
        for (var field : type.getFields())
            if (!fields.add(key(field))) throw new IllegalStateException("Duplicate field in " + type.getType());
        return new Members(methods, fields);
    }
    static void requireParents(String owner, String oldSuper, String newSuper,
            List<String> oldInterfaces, List<String> newInterfaces) {
        if (!Objects.equals(oldSuper, newSuper) || !new HashSet<>(newInterfaces).containsAll(oldInterfaces))
            throw new IllegalStateException("Original superclass or interface lost: " + owner);
    }
    static void requireMembers(String owner, Members original, Members patched) {
        for (var field : original.fields())
            if (!patched.fields().contains(field))
                throw new IllegalStateException("Original field lost: " + owner + " " + field);
        for (var method : original.methods().entrySet()) {
            Boolean newBody = patched.methods().get(method.getKey());
            if (newBody == null || (method.getValue() && !newBody))
                throw new IllegalStateException("Original method or body lost: " + owner + " " + method.getKey());
        }
    }
    static void expectFailure(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new IllegalStateException("Verifier accepted a missing original member or parent");
    }
    static void selfTest() {
        var concrete = new MethodKey("run", List.of("I"), "V");
        var abstractMethod = new MethodKey("other", List.of(), "Z");
        var field = new FieldKey("state", "I");
        var original = new Members(Map.of(concrete, true, abstractMethod, false), Set.of(field));
        requireParents("fixture", "LParent;", "LParent;", List.of("LI;"), List.of("LI;", "LExtra;"));
        requireMembers("fixture", original, original);
        expectFailure(() -> requireParents("fixture", "LParent;", "LOther;", List.of(), List.of()));
        expectFailure(() -> requireParents("fixture", "LParent;", "LParent;", List.of("LI;"), List.of()));
        expectFailure(() -> requireMembers("fixture", original, new Members(original.methods(), Set.of())));
        expectFailure(() -> requireMembers("fixture", original, new Members(Map.of(abstractMethod, false), original.fields())));
        expectFailure(() -> requireMembers("fixture", original, new Members(Map.of(concrete, false, abstractMethod, false), original.fields())));
        System.out.println("Stock API verifier controls passed: retained members and parents; missing fields, methods, bodies and parents rejected.");
    }
    static byte[] digest(InputStream input) throws Exception {
        try (input) {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] block = new byte[65536]; int count;
            while ((count = input.read(block)) != -1) digest.update(block, 0, count);
            return digest.digest();
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-test")) { selfTest(); return; }
        if (args.length != 4) throw new IllegalArgumentException("Expected original APK, patched APK, badging and manifest");
        selfTest();
        var badging = java.nio.file.Files.readString(java.nio.file.Path.of(args[2]));
        var identity = java.util.regex.Pattern.compile("^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", java.util.regex.Pattern.MULTILINE).matcher(badging);
        if (!identity.find() || identity.group(1).equals("com.google.android.inputmethod.latin")
                || !identity.group(3).equals("18.5.4.993608717-release-arm64-v8a"))
            throw new IllegalStateException("Output clone package/version not verified");
        var manifest = java.nio.file.Files.readString(java.nio.file.Path.of(args[3]));
        for (String required : new String[]{"dev.purple.patches.gboard.enabled", "dev.purple.patches.gboard.PurpleBootstrapReceiver",
                "dev.purple.patches.gboard.PurplePermissionActivity", "com.termux.permission.RUN_COMMAND", "android.permission.RECORD_AUDIO"})
            if (!manifest.contains(required)) throw new IllegalStateException("Missing manifest integration: " + required);
        System.out.println("Verified clone package=" + identity.group(1) + "; version=" + identity.group(3));
        var original = classes(new File(args[0]));
        var patched = classes(new File(args[1]));
        if (original.isEmpty() || !patched.keySet().containsAll(original.keySet()))
            throw new IllegalStateException("Original DEX classes were lost");
        if (!patched.containsKey("Ldev/purple/patches/gboard/PurpleAiRuntime;"))
            throw new IllegalStateException("Purple runtime not present in output DEX");
        long methods = 0, fields = 0;
        for (var entry : original.entrySet()) {
            var oldType = entry.getValue(); var newType = patched.get(entry.getKey());
            requireParents(entry.getKey(), oldType.getSuperclass(), newType.getSuperclass(),
                    oldType.getInterfaces(), newType.getInterfaces());
            var before = members(oldType); var after = members(newType);
            requireMembers(entry.getKey(), before, after);
            methods += before.methods().size(); fields += before.fields().size();
        }
        int libraries = 0, assets = 0;
        try (var source = new ZipFile(args[0]); var output = new ZipFile(args[1])) {
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement(); String name = entry.getName();
                boolean library = name.startsWith("lib/") && name.endsWith(".so");
                boolean asset = name.startsWith("assets/") && !name.startsWith("assets/dexopt/");
                if (entry.isDirectory() || (!library && !asset)) continue;
                var retained = output.getEntry(name);
                if (retained == null || !MessageDigest.isEqual(digest(source.getInputStream(entry)), digest(output.getInputStream(retained))))
                    throw new IllegalStateException("Original library or asset changed: " + name);
                if (library) libraries++; else assets++;
            }
        }
        System.out.println("Preserved original DEX classes=" + original.size() + "; output classes=" + patched.size()
                + "; exact original native libraries=" + libraries + "; exact original assets=" + assets);
        System.out.println("Preserved original method signatures=" + methods + "; field signatures=" + fields
                + "; original concrete methods retain a body; original superclass/interfaces retained.");
        System.out.println("Method-body behavior and live typing remain unverified.");
    }
}
