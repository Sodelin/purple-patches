import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.Method;
import java.io.File;
import java.util.*;

/** Static comparison of the development carrier, not Android execution. */
public final class VerifyNatuneFlacCarrier {
    private static final String PREFIX = "Ldev/purple/patches/natune/";
    private static void need(boolean value, String reason) { VerifyNatuneMobilePatch.need(value, reason); }
    static final Set<String> REQUIRED_CLASSES = Set.of(PREFIX + "VerifiedStreamCopy;", PREFIX + "NatuneCachedSource;",
        PREFIX + "TemporarySpaceBudget;", PREFIX + "NatuneCoroutineStop;",
        PREFIX + "SharedDocumentDestination;", PREFIX + "MediaStoreAudioDestination;",
        PREFIX + "NatuneFolderCopy;", PREFIX + "FlacIntegrity;");
    static final Map<String,String> RESOURCE_ADDITIONS = Map.of(
        "lib/arm64-v8a/libpurple_natune_flac.so", "a13763b6b5638d4d7e9a9bb53876841b72a65875e2760fad9f6d1480cb673ae7",
        "assets/purple/natune/FLAC-LICENSE.txt", "7866ee98760fc1f0156b4fe6bf530257e02be487ab3fd94e2b63799dd32d6b2c");

    static void checkMethods(Map<String,Method> methods) {
        Method nativeDecode = methods.get(PREFIX + "FlacIntegrity;->decode(" + PREFIX + "FlacIntegrity$Reader;)[J");
        need(nativeDecode != null && (nativeDecode.getAccessFlags() & 0x108) == 0x108 &&
            nativeDecode.getImplementation() == null, "Native JNI entry changed");
        need(methods.containsKey(PREFIX + "FlacIntegrity$Reader;->read([B)I") &&
            methods.containsKey(PREFIX + "FlacIntegrity$Reader;->check()V"), "JNI reader method changed");
        need(methods.keySet().stream().anyMatch(name -> name.startsWith(PREFIX + "NatuneFolderCopy$Attempt;->runFlac(")),
            "Strict folder-copy entry missing");
    }

    public static void main(String[] args) throws Exception {
        need(args.length == 3, "Expected original APK, carrier APK and compiled extension DEX");
        VerifyNatuneMobilePatch.initializerControls();
        var original = VerifyNatuneMobilePatch.snapshot(new File(args[0]), false);
        var selected = VerifyNatuneMobilePatch.snapshot(new File(args[1]), false);
        var extension = VerifyNatuneMobilePatch.snapshot(new File(args[2]), false);
        var classes = new TreeMap<>(selected.classes());
        var bodies = new TreeMap<>(selected.methods());
        need(extension.classes().keySet().containsAll(REQUIRED_CLASSES), "Selected folder/FLAC extension classes missing");
        var additions = new TreeSet<>(classes.keySet());
        additions.removeAll(original.classes().keySet());
        need(Collections.disjoint(original.classes().keySet(), extension.classes().keySet()),
            "Compiled extension collides with an original class");
        need(additions.equals(extension.classes().keySet()), "Added classes differ from compiled extension");
        for (String name : additions) {
            need(extension.classes().get(name).equals(classes.remove(name)), "Compiled extension class changed: " + name);
        }
        var addedMethods = new TreeSet<>(bodies.keySet());
        addedMethods.removeAll(original.methods().keySet());
        need(addedMethods.equals(extension.methods().keySet()), "Added methods differ from compiled extension");
        for (String name : extension.methods().keySet())
            need(extension.methods().get(name).equals(bodies.get(name)), "Compiled extension method changed: " + name);
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
        checkMethods(methods);
        VerifyNatuneMobilePatch.compare(original, new VerifyNatuneMobilePatch.Snapshot(bodies, classes), args,
            "Verified compiled extension, carrier entries and JNI names; original program semantics and non-DEX resource bytes preserved within the stated comparison.", RESOURCE_ADDITIONS);
        System.out.println("Compiled extension classes=" + extension.classes().size() + "; methods=" + extension.methods().size());
        System.out.println("NATUNE_FLAC_CARRIER_STATIC app_hook=false encrypted_owner=false android_execution=false");
    }
}
