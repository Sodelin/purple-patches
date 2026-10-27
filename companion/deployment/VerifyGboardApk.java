import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/** Off-phone verification through the same proven DEX library as Morphe. */
public final class VerifyGboardApk {
    static Set<String> classes(File apk) throws Exception {
        var container = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault());
        var result = new TreeSet<String>();
        for (String name : container.getDexEntryNames()) {
            var entry = container.getEntry(name);
            if (entry == null) throw new IllegalStateException("Missing DEX entry");
            for (var type : entry.getDexFile().getClasses()) result.add(type.getType());
        }
        return result;
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
        if (original.isEmpty() || !patched.containsAll(original))
            throw new IllegalStateException("Original DEX classes were lost");
        if (!patched.contains("Ldev/purple/patches/gboard/PurpleAiRuntime;"))
            throw new IllegalStateException("Purple runtime not present in output DEX");
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
    }
}
