import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Checks selected DEX references and exception ranges; it does not execute Android code. */
public final class VerifyGboardAccessPointFallback {
    private static final String ACCESS = "Ldev/purple/patches/gboard/PurpleAccessPoints;";
    private static final String RUNTIME = "Ldev/purple/patches/gboard/PurpleAiRuntime;";
    private static final String UPSTREAM = "Ldev/jason/gboardpatches/extension/accesspoint/GboardAccessPointContributionsRuntime;";

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }

    private static Method method(Map<String, ClassDef> classes, String owner, String name) {
        ClassDef type = classes.get(owner);
        require(type != null, "Missing " + owner);
        Method selected = null;
        for (Method candidate : type.getMethods()) if (name.equals(candidate.getName())) {
            require(selected == null, "Ambiguous " + owner + "->" + name);
            selected = candidate;
        }
        require(selected != null && selected.getImplementation() != null, "Missing body " + owner + "->" + name);
        return selected;
    }

    private static boolean calls(Method method, String owner, String name) {
        for (var instruction : method.getImplementation().getInstructions())
            if (instruction instanceof ReferenceInstruction reference
                    && reference.getReference() instanceof MethodReference call
                    && owner.equals(call.getDefiningClass()) && name.equals(call.getName())) return true;
        return false;
    }

    private static void requireGuardedEnable(Method method) {
        int offset = 0, matches = 0;
        var body = method.getImplementation();
        for (var instruction : body.getInstructions()) {
            if (instruction instanceof ReferenceInstruction reference
                    && reference.getReference() instanceof MethodReference call
                    && RUNTIME.equals(call.getDefiningClass()) && "enabled".equals(call.getName())
                    && call.getParameterTypes().size() == 1
                    && "Landroid/content/Context;".contentEquals(call.getParameterTypes().get(0))
                    && "Z".equals(call.getReturnType())) {
                matches++;
                boolean guarded = false;
                for (var block : body.getTryBlocks()) {
                    long end = (long) block.getStartCodeAddress() + block.getCodeUnitCount();
                    if (block.getStartCodeAddress() <= offset && offset < end)
                        for (var handler : block.getExceptionHandlers())
                            if ("Ljava/lang/LinkageError;".equals(handler.getExceptionType())) guarded = true;
                }
                require(guarded, method.getName() + " calls enabled outside a LinkageError handler");
            }
            offset += instruction.getCodeUnits();
        }
        require(matches == 1, "Expected exactly one enabled call in " + method.getName());
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
        requireGuardedEnable(method(classes, ACCESS, "extend"));
        requireGuardedEnable(method(classes, ACCESS, "register"));
        require(calls(method(classes, UPSTREAM, "includeOrderCatalog"), ACCESS, "extend"), "Missing catalog hook");
        require(calls(method(classes, UPSTREAM, "afterControllerCreated"), ACCESS, "register"), "Missing registration hook");
        System.out.println("GBOARD_ACCESS_POINT_DEX_GUARDS_PRESENT; runtime behavior remains unverified");
    }
}
