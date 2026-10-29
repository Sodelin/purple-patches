import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.Method;
import java.io.File;
import java.util.*;

/** Static comparison of the development carrier, not Android execution. */
public final class VerifyNatuneFlacCarrier {
    private static final String PREFIX = "Ldev/purple/patches/natune/";
    private static void need(boolean value, String reason) { VerifyNatuneMobilePatch.need(value, reason); }

    public static void main(String[] args) throws Exception {
        need(args.length == 2, "Expected original and carrier APK");
        VerifyNatuneMobilePatch.initializerControls();
        var original = VerifyNatuneMobilePatch.snapshot(new File(args[0]), false);
        var selected = VerifyNatuneMobilePatch.snapshot(new File(args[1]), false);
        var classes = new TreeMap<>(selected.classes());
        var bodies = new TreeMap<>(selected.methods());
        Set<String> required = Set.of(PREFIX + "VerifiedStreamCopy;", PREFIX + "NatuneCachedSource;",
            PREFIX + "TemporarySpaceBudget;", PREFIX + "NatuneCoroutineStop;",
            PREFIX + "SharedDocumentDestination;", PREFIX + "MediaStoreAudioDestination;",
            PREFIX + "NatuneFolderCopy;", PREFIX + "FlacIntegrity;");
        var permitted = new HashSet<>(required); permitted.add(PREFIX + "R;");
        var found = new HashSet<String>();
        for (String name : new ArrayList<>(classes.keySet())) {
            if (original.classes().containsKey(name)) continue;
            String outer = name.contains("$") ? name.substring(0, name.indexOf('$')) + ";" : name;
            need(permitted.contains(outer), "Unexpected extension class: " + name);
            found.add(outer); classes.remove(name);
        }
        need(found.containsAll(required), "Selected folder/FLAC extension classes missing");
        bodies.keySet().removeIf(name -> {
            int separator = name.indexOf("->");
            return separator > 0 && !original.classes().containsKey(name.substring(0, separator));
        });

        var methods = new HashMap<String, Method>();
        var dex = DexFileFactory.loadDexContainer(new File(args[1]), Opcodes.getDefault());
        for (String entry : dex.getDexEntryNames())
            for (var clazz : dex.getEntry(entry).getDexFile().getClasses())
                for (var method : clazz.getMethods())
                    need(methods.put(VerifyNatuneMobilePatch.signature(method), method) == null, "Duplicate method");
        Method nativeDecode = methods.get(PREFIX + "FlacIntegrity;->decode(" + PREFIX + "FlacIntegrity$Reader;)[J");
        need(nativeDecode != null && (nativeDecode.getAccessFlags() & 0x108) == 0x108 &&
            nativeDecode.getImplementation() == null, "Native JNI entry changed");
        need(methods.containsKey(PREFIX + "FlacIntegrity$Reader;->read([B)I") &&
            methods.containsKey(PREFIX + "FlacIntegrity$Reader;->check()V"), "JNI reader method changed");
        need(methods.keySet().stream().anyMatch(name -> name.startsWith(PREFIX + "NatuneFolderCopy$Attempt;->runFlac(")),
            "Strict folder-copy entry missing");

        Map<String,String> additions = Map.of(
            "lib/arm64-v8a/libpurple_natune_flac.so", "72ee124f75f3be57c18df3a542076f126a3e5c3e477bf368ef4fcc6e2e9b8244",
            "assets/purple/natune/FLAC-LICENSE.txt", "7866ee98760fc1f0156b4fe6bf530257e02be487ab3fd94e2b63799dd32d6b2c");
        VerifyNatuneMobilePatch.compare(original, new VerifyNatuneMobilePatch.Snapshot(bodies, classes), args,
            "Verified carrier entries and JNI names; original program semantics and non-DEX resource bytes preserved within the stated comparison.", additions);
        System.out.println("NATUNE_FLAC_CARRIER_STATIC app_hook=false encrypted_owner=false android_execution=false");
    }
}
