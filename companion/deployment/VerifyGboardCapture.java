import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Static APK linkage checks only; real recorder behavior requires a device. */
public final class VerifyGboardCapture {
    private static final String SESSION = "Ldev/purple/patches/gboard/CaptureSession;";
    private static final String WRAPPER = "Ldev/purple/patches/gboard/WhisperCapture;";

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
    private static Method find(Map<String, ClassDef> classes, String type, String name) {
        var owner = classes.get(type);
        require(owner != null, "Missing " + type);
        Method result = null;
        for (var candidate : owner.getMethods()) if (candidate.getName().equals(name)) {
            require(result == null, "Ambiguous " + type + "->" + name);
            result = candidate;
        }
        require(result != null && result.getImplementation() != null, "Missing body " + type + "->" + name);
        return result;
    }
    private static boolean calls(Method method, String type, String name, int parameters) {
        if (method.getImplementation() == null) return false;
        for (var instruction : method.getImplementation().getInstructions())
            if (instruction instanceof ReferenceInstruction ref && ref.getReference() instanceof MethodReference target
                    && target.getDefiningClass().equals(type) && target.getName().equals(name)
                    && target.getParameterTypes().size() == parameters) return true;
        return false;
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
        var recording = find(classes, WRAPPER, "record");
        require(calls(recording, SESSION, "record", 3), "Whisper capture does not call the finite session");
        require(calls(find(classes, WRAPPER, "stop"), SESSION, "stop", 0), "Stop does not signal the finite session");
        var session = find(classes, SESSION, "record");
        require(calls(session, "Ldev/purple/patches/gboard/CaptureSession$Source;", "start", 0)
                && calls(session, "Ldev/purple/patches/gboard/CaptureSession$Source;", "read", 3)
                && calls(session, "Ldev/purple/patches/gboard/CaptureSession$Source;", "close", 0),
                "Finite session source lifecycle is missing");
        require(calls(session, SESSION, "elapsed", 2)
                && calls(session, "Ldev/purple/patches/gboard/CaptureSession$Clock;", "pause", 1)
                && calls(session, "Ljava/util/Arrays;", "fill", 2),
                "Finite session deadline, wait or cleanup reference is missing");
        boolean fourArgumentRead = false;
        for (var type : classes.values()) if (type.getType().startsWith("Ldev/purple/patches/gboard/WhisperCapture$"))
            for (var method : type.getMethods())
                if (calls(method, "Landroid/media/AudioRecord;", "read", 4)) fourArgumentRead = true;
        require(fourArgumentRead, "Android four-argument audio read is missing");
        System.out.println("Verified finite capture, source lifecycle, deadline/cleanup and Android read references in APK DEX.");
    }
}
