import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.HashSet;

/** Public APK reconnaissance only: no arbitrary strings, credentials or code execution. */
public final class InspectNatune {
    static boolean inheritedMethod(String owner, String parameters, String result,
                                   Map<String,ClassDef> classes, Set<String> seen) {
        if (!seen.add(owner)) return false;
        var clazz = classes.get(owner);
        if (clazz == null) return false;
        for (var method : clazz.getMethods()) {
            if (String.join("", method.getParameterTypes()).equals(parameters) && method.getReturnType().equals(result))
                return true;
        }
        for (String parent : clazz.getInterfaces())
            if (inheritedMethod(parent, parameters, result, classes, seen)) return true;
        return clazz.getSuperclass() != null && inheritedMethod(clazz.getSuperclass(), parameters, result, classes, seen);
    }
    static final Set<String> CODEC_ANCHORS = Set.of("audio", "video", "audio/raw", "audio/flac", "audio/ac4");
    static void codecBody(Method method, int maximum) {
        var body=method.getImplementation();
        if(body==null)return;
        System.out.println("CODEC_METHOD\t"+signature(method)+"\tflags="+method.getAccessFlags()+
            "\tregisters="+body.getRegisterCount()+"\tunits="+
            java.util.stream.StreamSupport.stream(body.getInstructions().spliterator(),false).mapToInt(x->x.getCodeUnits()).sum()+
            "\ttry_blocks="+body.getTryBlocks().size());
        int offset=0,index=0;
        for(var instruction:body.getInstructions()) {
            if(index++>=maximum)break;
            var row=new StringBuilder("CODEC_NODE\t"+offset+"\t"+instruction.getOpcode().name());
            if(instruction instanceof OneRegisterInstruction r)row.append("\tA=").append(r.getRegisterA());
            if(instruction instanceof TwoRegisterInstruction r)row.append("\tB=").append(r.getRegisterB());
            if(instruction instanceof WideLiteralInstruction r)row.append("\tliteral=").append(r.getWideLiteral());
            if(instruction instanceof OffsetInstruction r)row.append("\ttarget=").append(offset+r.getCodeOffset());
            if(instruction instanceof ReferenceInstruction r) {
                var ref=r.getReference();
                if(ref instanceof FieldReference f)row.append("\tfield=").append(f);
                if(ref instanceof MethodReference m)row.append("\tmethod=").append(signature(m));
                if(ref instanceof StringReference s && CODEC_ANCHORS.contains(s.getString()))row.append("\tanchor=").append(s.getString());
            }
            System.out.println(row);offset+=instruction.getCodeUnits();
        }
    }
    static void inspectCodecHooks(Map<String,ClassDef> classes) {
        var formatTypes=new TreeSet<String>();var owners=new TreeSet<String>();
        for(var clazz:classes.values())for(var method:clazz.getMethods()) {
            var args=method.getParameterTypes();
            if(method.getReturnType().equals("V") && args.size()==2 && args.get(0).toString().startsWith("Lr8/") &&
                args.get(1).toString().equals("Landroid/media/MediaFormat;")) {
                owners.add(clazz.getType());formatTypes.add(args.get(0).toString());
                System.out.println("CODEC_OUTPUT_FORMAT\t"+signature(method));
            }
        }
        var predicates=new TreeSet<String>();
        for(String owner:owners) {
            var clazz=classes.get(owner);
            System.out.println("CODEC_CLASS\t"+owner+"\tsuper="+clazz.getSuperclass());
            for(var method:clazz.getMethods()) {
                if(owner.equals("Lr8/ge7;")) System.out.println("CODEC_AUDIO_INVENTORY\t"+signature(method));
                if(method.getParameterTypes().stream().noneMatch(p->formatTypes.contains(p.toString())))continue;
                codecBody(method,40);
                if(method.getImplementation()!=null)for(var instruction:method.getImplementation().getInstructions())
                    if(instruction instanceof ReferenceInstruction r && r.getReference() instanceof MethodReference m &&
                       m.getReturnType().equals("Z") && String.join("",m.getParameterTypes()).equals("Ljava/lang/String;"))
                        predicates.add(signature(m));
            }
        }
        // Verify the actual optimized decoder-reuse fence rather than assuming
        // upstream source survived R8 unchanged across format transitions.
        var codecInfo=classes.get("Lr8/je7;");
        if(codecInfo!=null)for(var method:codecInfo.getMethods())
            if(method.getName().equals("b") && String.join("",method.getParameterTypes()).equals("Lr8/yf4;Lr8/yf4;"))
                codecBody(method,120);
        var audioRenderer=classes.get("Lr8/ge7;");
        if(audioRenderer!=null)for(var method:audioRenderer.getMethods())
            if(method.getName().equals("e0"))codecBody(method,60);
        for(String type:formatTypes) {
            var clazz=classes.get(type);if(clazz==null)continue;
            for(var field:clazz.getFields())if(field.getType().equals("Ljava/lang/String;"))
                System.out.println("CODEC_FORMAT_STRING_FIELD\t"+type+"->"+field.getName()+":"+field.getType());
        }
        for(var clazz:classes.values())for(var method:clazz.getMethods())
            if(predicates.contains(signature(method)))codecBody(method,20);
        for(String type:List.of("Landroidx/media3/decoder/ffmpeg/FfmpegAudioRenderer;",
            "Landroidx/media3/decoder/flac/LibflacAudioRenderer;","Landroidx/media3/decoder/opus/LibopusAudioRenderer;"))
            System.out.println("CODEC_NAMED_EXTENSION\t"+type+"\tpresent="+classes.containsKey(type));
    }
    static void inspectStreamTypes(File apk) throws Exception {
        var classes = new TreeMap<String,ClassDef>();
        var dex = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault());
        for (String name : dex.getDexEntryNames()) {
            var entry = dex.getEntry(name);
            if (entry == null) throw new IllegalStateException("Missing DEX");
            for (var clazz : entry.getDexFile().getClasses())
                if (classes.put(clazz.getType(), clazz) != null) throw new IllegalStateException("Duplicate class");
        }
        var sources = new TreeSet<String>();
        for (String type : classes.keySet())
            if (inheritedMethod(type, "Lr8/km2;", "J", classes, new HashSet<>()) &&
                inheritedMethod(type, "[BII", "I", classes, new HashSet<>())) sources.add(type);
        int interfaces = 0, factories = 0;
        for (String type : sources) {
            var clazz = classes.get(type);
            System.out.println("STREAM_SOURCE\t" + type);
            if ((clazz.getAccessFlags() & 0x200) != 0) {
                interfaces++;
                System.out.println("STREAM_INTERFACE\t" + type);
                for (var method : clazz.getMethods()) System.out.println("STREAM_METHOD\t" + signature(method));
            }
        }
        for (var clazz : classes.values())
            for (var method : clazz.getMethods())
                if (method.getParameterTypes().isEmpty() && sources.contains(method.getReturnType())) {
                    factories++;
                    System.out.println("STREAM_FACTORY\t" + signature(method));
                }
        var spec = classes.get("Lr8/km2;");
        if (spec == null) throw new IllegalStateException("Exact DataSpec class missing");
        for (var field : spec.getFields())
            System.out.println("STREAM_SPEC_FIELD\t" + field.getName() + ':' + field.getType() + "\tflags=" + field.getAccessFlags());
        for (var method : spec.getMethods()) {
            var body = method.getImplementation();
            if (body == null) continue;
            System.out.println("SPEC_METHOD\t" + signature(method) + "\tflags=" + method.getAccessFlags() +
                               "\tregisters=" + body.getRegisterCount());
            int offset = 0;
            for (var instruction : body.getInstructions()) {
                var row = new StringBuilder("SPEC_NODE\t" + offset + "\t" + instruction.getOpcode().name());
                if (instruction instanceof OneRegisterInstruction r) row.append("\tA=").append(r.getRegisterA());
                if (instruction instanceof TwoRegisterInstruction r) row.append("\tB=").append(r.getRegisterB());
                if (instruction instanceof ThreeRegisterInstruction r) row.append("\tC=").append(r.getRegisterC());
                if (instruction instanceof WideLiteralInstruction r) row.append("\tliteral=").append(r.getWideLiteral());
                if (instruction instanceof OffsetInstruction r) row.append("\ttarget=").append(offset + r.getCodeOffset());
                if (instruction instanceof ReferenceInstruction r) {
                    var reference = r.getReference();
                    if (reference instanceof FieldReference f) row.append("\tfield=").append(f);
                    if (reference instanceof MethodReference m) row.append("\tmethod=").append(signature(m));
                    // No arbitrary string constants, URI/header values or app data.
                }
                System.out.println(row);offset += instruction.getCodeUnits();
            }
        }
        if (interfaces != 1 || !sources.contains("Lr8/dm2;"))
            throw new IllegalStateException("Pinned stream interface hierarchy changed");
        System.out.println("STREAM_TOTAL\tsources=" + sources.size() + "\tinterfaces=" + interfaces + "\tfactories=" + factories);
        inspectCodecHooks(classes);
    }
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
        inspectStreamTypes(new File(args[0]));
    }
}
