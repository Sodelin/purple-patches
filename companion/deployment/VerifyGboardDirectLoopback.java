import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Static DEX references only; no Android network request is made. */
public final class VerifyGboardDirectLoopback {
    private static final String BRIDGE = "Ldev/purple/patches/gboard/BridgeClient;";
    private static final String POLICY = "Ldev/purple/patches/gboard/SessionPolicy;";
    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
    private static Method method(Map<String, ClassDef> classes, String owner, String name) {
        ClassDef type=classes.get(owner); require(type!=null,"Missing "+owner);
        Method found=null;
        for (Method candidate:type.getMethods()) if (name.equals(candidate.getName())) {
            require(found==null,"Ambiguous "+owner+"->"+name); found=candidate;
        }
        require(found!=null && found.getImplementation()!=null,"Missing body "+owner+"->"+name);
        return found;
    }
    private static boolean calls(Method method, String owner, String name) {
        for (var instruction:method.getImplementation().getInstructions())
            if (instruction instanceof ReferenceInstruction reference
                    && reference.getReference() instanceof MethodReference call
                    && owner.equals(call.getDefiningClass()) && name.equals(call.getName())) return true;
        return false;
    }
    public static void main(String[] args) throws Exception {
        require(args.length==1,"Expected one patched APK");
        var dex=DexFileFactory.loadDexContainer(new File(args[0]),Opcodes.getDefault());
        Map<String,ClassDef> classes=new HashMap<>();
        for (String name:dex.getDexEntryNames()) {
            var entry=dex.getEntry(name); require(entry!=null,"Missing DEX entry "+name);
            for (var type:entry.getDexFile().getClasses())
                require(classes.put(type.getType(),type)==null,"Duplicate class "+type.getType());
        }
        Method request=method(classes,BRIDGE,"request");
        int direct=0,other=0; boolean noProxy=false;
        for (var instruction:request.getImplementation().getInstructions()) {
            if (!(instruction instanceof ReferenceInstruction reference)) continue;
            if (reference.getReference() instanceof FieldReference field
                    && "Ljava/net/Proxy;".equals(field.getDefiningClass())
                    && "NO_PROXY".equals(field.getName()) && "Ljava/net/Proxy;".equals(field.getType())) noProxy=true;
            if (reference.getReference() instanceof MethodReference call
                    && "Ljava/net/URL;".equals(call.getDefiningClass()) && "openConnection".equals(call.getName())) {
                if (call.getParameterTypes().size()==1
                        && "Ljava/net/Proxy;".contentEquals(call.getParameterTypes().get(0))) direct++;
                else other++;
            }
        }
        require(noProxy && direct==1 && other==0,"Expected one explicit URL.openConnection(Proxy) and NO_PROXY");
        require(calls(method(classes,BRIDGE,"<init>"),POLICY,"localEndpoint"),"Missing loopback endpoint policy reference");
        System.out.println("GBOARD_DIRECT_LOOPBACK_DEX_REFERENCES_PRESENT; Android routing remains unverified");
    }
}
