import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Public APK reconnaissance only: no arbitrary strings, credentials or code execution. */
public final class InspectNatune {
    static final Set<String> ANCHORS = Set.of(
        "Low-data mode active; skipping Tidal/Qobuz for %s",
        "No enabled download source resolved ",
        "LowDataMode",
        "lowDataMode",
        "low_data_mode"
    );
    static String signature(MethodReference method) {
        return method.getDefiningClass() + "->" + method.getName() + "(" +
            String.join("", method.getParameterTypes()) + ")" + method.getReturnType();
    }
    public static void main(String[] args) throws Exception {
        var dex = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        int classes = 0, methods = 0, candidates = 0;
        for (String entryName : dex.getDexEntryNames()) {
            var entry = dex.getEntry(entryName);
            if (entry == null) throw new IllegalStateException("Missing DEX entry");
            for (var clazz : entry.getDexFile().getClasses()) {
                classes++;
                for (Method method : clazz.getMethods()) {
                    methods++;
                    if (method.getImplementation() == null) continue;
                    var calls = new LinkedHashSet<String>();
                    var anchors = new ArrayList<String>();
                    boolean metered = false, cellular = false;
                    int index = 0;
                    for (var instruction : method.getImplementation().getInstructions()) {
                        if (instruction instanceof ReferenceInstruction ri) {
                            var ref = ri.getReference();
                            if (ref instanceof MethodReference target) {
                                String sig = signature(target);
                                if (target.getDefiningClass().equals("Landroid/net/ConnectivityManager;") &&
                                    target.getName().equals("isActiveNetworkMetered")) metered = true;
                                if (target.getDefiningClass().equals("Landroid/net/NetworkCapabilities;") &&
                                    target.getName().equals("hasTransport")) cellular = true;
                                if (target.getDefiningClass().startsWith("Landroid/net/") ||
                                    target.getDefiningClass().endsWith("/LowDataModeKt;")) calls.add(sig);
                            } else if (ref instanceof StringReference text) {
                                for (String anchor : ANCHORS) {
                                    if (text.getString().startsWith(anchor)) anchors.add(index + ":" + anchor);
                                }
                            }
                        }
                        index++;
                    }
                    if (!anchors.isEmpty() || (metered && cellular && method.getReturnType().equals("Z"))) {
                        candidates++;
                        System.out.println("METHOD\t" + signature(method));
                        System.out.println("FLAGS\t" + method.getAccessFlags() + "\tREGISTERS\t" +
                            method.getImplementation().getRegisterCount() + "\tINSTRUCTIONS\t" + index);
                        System.out.println("NETWORK_PAIR\t" + metered + "\t" + cellular);
                        for (String anchor : anchors) System.out.println("ANCHOR\t" + anchor);
                        for (String call : calls) System.out.println("CALL\t" + call);
                    }
                }
            }
        }
        System.out.println("TOTAL\tclasses=" + classes + "\tmethods=" + methods + "\tcandidates=" + candidates);
        if (candidates == 0) throw new IllegalStateException("No reviewed policy anchors survived release compilation");
    }
}
