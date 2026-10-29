import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload;
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
    static final Set<String> CODEC_ANCHORS = Set.of("audio", "video", "audio/raw", "audio/flac", "audio/ac4", "qobuz:", "tidal:", "downloaded", "root");
    static void codecBody(Method method, int maximum) {
        var body=method.getImplementation();
        if(body==null)return;
        System.out.println("CODEC_METHOD\t"+signature(method)+"\tflags="+method.getAccessFlags()+
            "\tregisters="+body.getRegisterCount()+"\tunits="+
            java.util.stream.StreamSupport.stream(body.getInstructions().spliterator(),false).mapToInt(x->x.getCodeUnits()).sum()+
            "\ttry_blocks="+body.getTryBlocks().size());
        for (var region : body.getTryBlocks()) for (var handler : region.getExceptionHandlers())
            System.out.println("CODEC_CATCH\tstart="+region.getStartCodeAddress()+
                "\tunits="+region.getCodeUnitCount()+"\ttype="+handler.getExceptionType()+
                "\thandler="+handler.getHandlerCodeAddress());
        int offset=0,index=0;
        for(var instruction:body.getInstructions()) {
            if(index++>=maximum) {
                System.out.println("CODEC_TRUNCATED\t"+signature(method)+"\tlimit="+maximum);
                break;
            }
            var row=new StringBuilder("CODEC_NODE\t"+offset+"\t"+instruction.getOpcode().name());
            if(instruction instanceof OneRegisterInstruction r)row.append("\tA=").append(r.getRegisterA());
            if(instruction instanceof TwoRegisterInstruction r)row.append("\tB=").append(r.getRegisterB());
            if(instruction instanceof ThreeRegisterInstruction r)row.append("\tC=").append(r.getRegisterC());
            if(instruction instanceof FiveRegisterInstruction r)row.append("\targc=").append(r.getRegisterCount())
                .append("\tC=").append(r.getRegisterC()).append("\tD=").append(r.getRegisterD())
                .append("\tE=").append(r.getRegisterE()).append("\tF=").append(r.getRegisterF())
                .append("\tG=").append(r.getRegisterG());
            if(instruction instanceof RegisterRangeInstruction r)row.append("\targc=").append(r.getRegisterCount())
                .append("\tstart=").append(r.getStartRegister());
            if(instruction instanceof WideLiteralInstruction r)row.append("\tliteral=").append(r.getWideLiteral());
            if(instruction instanceof OffsetInstruction r)row.append("\ttarget=").append(offset+r.getCodeOffset());
            if(instruction instanceof SwitchPayload payload) for (var element : payload.getSwitchElements())
                row.append("\tswitch=").append(element.getKey()).append(":").append(element.getOffset());
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
        for(String owner:List.of("Lmoe/rukamori/archivetune/App;", "Lmoe/rukamori/archivetune/MainActivity;")) {
            var clazz=classes.get(owner);
            if(clazz!=null)for(var method:clazz.getMethods())if(method.getName().equals("onCreate"))codecBody(method,40);
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
        inspectStorageHooks(classes);
        inspectRemovalGraph(classes);
        inspectLibraryHooks(classes);
        inspectCompiledManifest(apk);
    }

    /** Use the hosted SDK to inspect only this public APK's MusicService. */
    static void inspectCompiledManifest(File apk) throws Exception {
        String sdk = System.getenv("ANDROID_HOME");
        if (sdk == null || sdk.isEmpty()) throw new IllegalStateException("Hosted Android SDK unavailable");
        File[] versions = new File(sdk, "build-tools").listFiles(File::isDirectory);
        if (versions == null) throw new IllegalStateException("Hosted Android build tools unavailable");
        java.util.Arrays.sort(versions, java.util.Comparator.comparing(File::getName));
        File aapt2 = null;
        for (File version : versions) {
            File candidate = new File(version, "aapt2");
            if (candidate.isFile() && candidate.canExecute()) aapt2 = candidate;
        }
        if (aapt2 == null) throw new IllegalStateException("Hosted manifest reader unavailable");
        Process process = new ProcessBuilder(aapt2.getAbsolutePath(), "dump", "xmltree",
            apk.getAbsolutePath(), "--file", "AndroidManifest.xml").redirectErrorStream(true).start();
        byte[] bytes = process.getInputStream().readNBytes(2_000_001);
        if (bytes.length > 2_000_000) { process.destroyForcibly(); throw new IllegalStateException("Manifest output too large"); }
        if (process.waitFor() != 0) throw new IllegalStateException("Public APK manifest inspection failed");
        String[] lines = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).split("\\R");
        int found = 0;
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].trim().startsWith("E: service")) continue;
            int indentation = lines[i].length() - lines[i].stripLeading().length();
            int end = i + 1;
            while (end < lines.length) {
                String line = lines[end];
                if (line.trim().startsWith("E: ") && line.length() - line.stripLeading().length() <= indentation) break;
                end++;
            }
            boolean target = false;
            for (int j = i; j < end; j++) if (lines[j].contains(".playback.MusicService")) target = true;
            if (target) {
                System.out.println("LIBRARY_MANIFEST_BEGIN");
                for (int j = i; j < end; j++) System.out.println("LIBRARY_MANIFEST_LINE\t" + lines[j].trim());
                System.out.println("LIBRARY_MANIFEST_END");
                found++;
            }
            i = end - 1;
        }
        System.out.println("LIBRARY_MANIFEST_TOTAL\t" + found);
    }

    /** Inspect publicly compiled browser entrypoints, without opening the app. */
    static void inspectLibraryHooks(Map<String,ClassDef> classes) {
        String callback = "Lmoe/rukamori/archivetune/playback/MediaLibrarySessionCallback;";
        for (String type : List.of("Lmoe/rukamori/archivetune/playback/MusicService;", callback)) {
            var owner = classes.get(type);
            System.out.println("LIBRARY_CLASS\t" + type + "\tpresent=" + (owner != null));
            if (owner == null) continue;
            for (var method : owner.getMethods())
                if (Set.of("onConnect", "onGetChildren", "onGetItem", "onGetLibraryRoot").contains(method.getName())) {
                    System.out.println("LIBRARY_METHOD\t" + signature(method));
                    codecBody(method, 350);
                }
        }
        int children = 0;
        for (var owner : classes.values()) {
            if (!owner.getType().contains("MediaLibrarySessionCallback$onGetChildren")) continue;
            System.out.println("LIBRARY_CHILDREN_CLASS\t" + owner.getType());
            for (var method : owner.getMethods()) if (method.getName().equals("invokeSuspend")) {
                codecBody(method, 1200);
                children++;
            }
        }
        System.out.println("LIBRARY_CHILDREN_TOTAL\t" + children);
    }

    /**
     * Locate the actual exact-release Cache.removeResource callsites and their
     * program callers. This emits public descriptors and selected code only;
     * it does not read user cache keys, APK private state or arbitrary strings.
     */
    static void inspectRemovalGraph(Map<String,ClassDef> classes) {
        var removers = new TreeMap<String,Method>();
        for (var clazz : classes.values()) for (var method : clazz.getMethods()) {
            var body = method.getImplementation();
            if (body == null) continue;
            for (var instruction : body.getInstructions()) {
                if (!(instruction instanceof ReferenceInstruction r) ||
                    !(r.getReference() instanceof MethodReference ref)) continue;
                if (ref.getDefiningClass().equals("Lr8/td1;") && ref.getName().equals("l") &&
                    String.join("",ref.getParameterTypes()).equals("Ljava/lang/String;") &&
                    ref.getReturnType().equals("V")) {
                    removers.put(signature(method),method);
                }
            }
        }
        if (removers.isEmpty() || removers.size() > 64)
            throw new IllegalStateException("Exact cache-removal graph unavailable or unexpectedly broad");
        var dispatched = new TreeSet<String>(removers.keySet());
        var owners = new TreeSet<String>();
        for (var entry : removers.entrySet()) {
            var method = entry.getValue();
            var owner = classes.get(method.getDefiningClass());
            if (owner == null) throw new IllegalStateException("Removal owner unavailable");
            owners.add(owner.getType());
            System.out.println("CACHE_REMOVE_SITE\t" + entry.getKey());
            codecBody(method, 300);
            // Resolve the downloader's specific remove() interface. Generic
            // Kotlin lambda interfaces have many unrelated implementors; their
            // call sites do not identify a particular cache-removal lambda.
            if (method.getName().equals("remove") && method.getParameterTypes().isEmpty() &&
                method.getReturnType().equals("V")) {
                for (String type : owner.getInterfaces()) {
                    var iface = classes.get(type);
                    if (iface == null) continue;
                    for (var candidate : iface.getMethods()) {
                        if (candidate.getName().equals(method.getName()) &&
                            candidate.getParameterTypes().isEmpty() && candidate.getReturnType().equals("V")) {
                            dispatched.add(signature(candidate));
                            System.out.println("CACHE_REMOVE_DISPATCH\t" + signature(candidate) + "\timplementation=" + entry.getKey());
                        }
                    }
                }
            }
        }
        var constructorRefs = new TreeSet<String>();
        for (String type : List.of("Lr8/uk9;", "Lr8/sx4;")) {
            var owner = classes.get(type);
            if (owner == null || !owners.contains(type))
                throw new IllegalStateException("Expected downloader removal owner unavailable: " + type);
            for (var method : owner.getMethods()) if (method.getName().equals("<init>")) {
                constructorRefs.add(signature(method));
                System.out.println("CACHE_REMOVE_CONSTRUCTOR\t" + signature(method));
                codecBody(method, 200);
            }
        }
        if (constructorRefs.isEmpty()) throw new IllegalStateException("Downloader constructors unavailable");
        for (String type : owners) {
            var owner = classes.get(type);
            System.out.println("CACHE_REMOVE_OWNER\t" + type + "\tsuper=" + owner.getSuperclass() +
                "\tinterfaces=" + owner.getInterfaces());
            for (var field : owner.getFields())
                System.out.println("CACHE_REMOVE_FIELD\t" + type + "->" + field.getName() + ":" + field.getType() +
                    "\tflags=" + field.getAccessFlags());
            for (var method : owner.getMethods())
                System.out.println("CACHE_REMOVE_METHOD\t" + signature(method) + "\tflags=" + method.getAccessFlags());
        }
        int callers = 0;
        var constructorCallers = new TreeMap<String,Method>();
        for (var clazz : classes.values()) for (var method : clazz.getMethods()) {
            var body = method.getImplementation();
            if (body == null) continue;
            var targets = new TreeSet<String>();
            for (var instruction : body.getInstructions())
                if (instruction instanceof ReferenceInstruction r && r.getReference() instanceof MethodReference ref) {
                    if (dispatched.contains(signature(ref))) targets.add(signature(ref));
                    if (constructorRefs.contains(signature(ref))) {
                        constructorCallers.put(signature(method), method);
                        System.out.println("CACHE_REMOVE_CONSTRUCTOR_CALLER\t" + signature(method) + "\t" + signature(ref));
                    }
                }
            for (String target : targets) {
                System.out.println("CACHE_REMOVE_CALLER\t" + signature(method) + "\t" + target);
                if (target.equals("Lr8/bf3;->remove()V")) codecBody(method, 300);
                callers++;
            }
        }
        if (constructorCallers.size() > 32) throw new IllegalStateException("Unexpectedly broad downloader constructor graph");
        for (var entry : constructorCallers.entrySet()) {
            System.out.println("CACHE_REMOVE_CONSTRUCTOR_CALLER_BODY\t" + entry.getKey());
            codecBody(entry.getValue(), 200);
        }
        var task = classes.get("Lr8/zd3;");
        if (task == null) throw new IllegalStateException("Download task unavailable");
        for (var field : task.getFields())
            System.out.println("DOWNLOAD_TASK_FIELD\t" + task.getType() + "->" + field.getName() + ":" + field.getType());
        var handler = classes.get("Lr8/xd3;");
        if (handler == null) throw new IllegalStateException("Download manager handler unavailable");
        System.out.println("DOWNLOAD_HANDLER\t" + handler.getType() + "\tsuper=" + handler.getSuperclass());
        for (var method : handler.getMethods())
            if (method.getName().equals("handleMessage")) codecBody(method, 1000);
        System.out.println("CACHE_REMOVE_TOTAL\tsites=" + removers.size() + "\towners=" + owners.size() +
            "\tcallers=" + callers + "\tconstructor_callers=" + constructorCallers.size());
    }
    static final Set<String> STORAGE_ANCHORS = Set.of(
        "export_tmp", "Could not open destination stream", "Download cache is empty for this song"
    );
    static void inspectStorageHooks(Map<String,ClassDef> classes) {
        // The installed export coroutine calls this exact optimized resolver.
        // Inspect its public DEX body without reading any user cache keys.
        var exportResolver = classes.get("Lmoe/rukamori/archivetune/ui/menu/q0;");
        if (exportResolver == null) throw new IllegalStateException("Export resolver unavailable");
        int exactResolvers = 0;
        for (var method : exportResolver.getMethods()) {
            if (method.getName().equals("c") &&
                String.join("",method.getParameterTypes()).equals("Lr8/td1;Ljava/lang/String;") &&
                method.getReturnType().equals("Ljava/util/NavigableSet;")) {
                System.out.println("STORAGE_RENDITION_RESOLVER\t" + signature(method));
                codecBody(method, 400);
                exactResolvers++;
            }
        }
        if (exactResolvers != 1) throw new IllegalStateException("Export resolver layout changed");
        int matches = 0;
        var owners = new TreeSet<String>();
        for (var clazz : classes.values()) for (var method : clazz.getMethods()) {
            var body = method.getImplementation();
            if (body == null) continue;
            var anchors = new TreeSet<String>();
            for (var instruction : body.getInstructions())
                if (instruction instanceof ReferenceInstruction r &&
                    r.getReference() instanceof StringReference s && STORAGE_ANCHORS.contains(s.getString()))
                    anchors.add(s.getString());
            if (anchors.isEmpty()) continue;
            matches++; owners.add(clazz.getType());
            System.out.println("STORAGE_ANCHORED\t" + signature(method) + "\tanchors=" + anchors);
            // Read public code only. codecBody omits non-whitelisted strings;
            // the original tag is retained for compatibility with old readers.
            codecBody(method, 180);
            for (var instruction : body.getInstructions())
                if (instruction instanceof ReferenceInstruction r && r.getReference() instanceof MethodReference m)
                    System.out.println("STORAGE_CALL\t" + signature(method) + "\t" + signature(m));
        }
        for (String owner : owners) {
            var clazz = classes.get(owner);
            for (var field : clazz.getFields())
                System.out.println("STORAGE_OWNER_FIELD\t" + owner + "->" + field.getName() + ":" + field.getType() +
                    "\tflags=" + field.getAccessFlags());
        }
        // The actual export call is td1.p(String)TreeSet after optimization;
        // the original NavigableSet interface shape alone found no cache type.
        // Inspect the observed receiver and its original owner, not an assumed
        // public Media3 ABI. No field values or app objects are accessed.
        var receiverTypes = new TreeSet<>(List.of("Lr8/td1;", "Lr8/d7b;", "Lr8/vu2;",
            "Lr8/ae3;", "Lmoe/rukamori/archivetune/playback/g;"));
        for (var clazz : classes.values())
            if (clazz.getInterfaces().contains("Lr8/td1;")) receiverTypes.add(clazz.getType());
        for (String type : receiverTypes) {
            var clazz = classes.get(type);
            if (clazz == null) throw new IllegalStateException("Observed storage receiver missing");
            System.out.println("STORAGE_RECEIVER\t" + type + "\tflags=" + clazz.getAccessFlags() +
                "\tsuper=" + clazz.getSuperclass() + "\tinterfaces=" + clazz.getInterfaces());
            for (var field : clazz.getFields())
                System.out.println("STORAGE_RECEIVER_FIELD\t" + type + "->" + field.getName() + ":" + field.getType() +
                    "\tflags=" + field.getAccessFlags());
            for (var method : clazz.getMethods()) {
                System.out.println("STORAGE_RECEIVER_METHOD\t" + signature(method) + "\tflags=" + method.getAccessFlags());
                if (clazz.getInterfaces().contains("Lr8/td1;") &&
                    (Set.of("p", "l", "b", "o", "c", "j", "d", "q").contains(method.getName()) ||
                     method.getReturnType().equals("Lr8/td1;"))) codecBody(method, 60);
                if ((type.equals("Lr8/d7b;") && Set.of("<init>", "compareTo").contains(method.getName())) ||
                    (type.equals("Lr8/vu2;") && method.getName().equals("b")) ||
                    (type.equals("Lr8/ae3;") && method.getName().equals("c"))) codecBody(method, 100);
            }
        }
        for (var clazz : classes.values())
            if (clazz.getType().contains("ExportDownloadedSongsScreenKt$") &&
                clazz.getType().contains("deleteSelected"))
                for (var method : clazz.getMethods()) {
                    if (!method.getName().equals("invokeSuspend")) continue;
                    System.out.println("STORAGE_DELETE_COROUTINE\t" + signature(method));
                    codecBody(method, 180);
                    if (method.getImplementation() != null)
                        for (var instruction : method.getImplementation().getInstructions())
                            if (instruction instanceof ReferenceInstruction r &&
                                r.getReference() instanceof MethodReference m)
                                System.out.println("STORAGE_DELETE_CALL\t" + signature(m));
                }
        var utility = classes.get("Lmoe/rukamori/archivetune/playback/g;");
        for (var method : utility.getMethods())
            if (method.getName().equals("a") && method.getReturnType().equals("V")) codecBody(method, 160);
        // Resolve native coroutine cancellation from the actual Continuation
        // interface, without assuming upstream Kotlin names survived R8.
        var continuation = classes.get("Lr8/cf2;");
        if (continuation == null) throw new IllegalStateException("Original continuation missing");
        var contextTypes = new TreeSet<String>();
        for (var method : continuation.getMethods()) {
            System.out.println("STORAGE_CONTINUATION_METHOD\t" + signature(method));
            if (method.getParameterTypes().isEmpty() && method.getReturnType().startsWith("Lr8/"))
                contextTypes.add(method.getReturnType());
        }
        int cancellationCandidates = 0;
        for (var clazz : classes.values()) for (var method : clazz.getMethods()) {
            if ((method.getAccessFlags() & 8) == 0 || method.getImplementation() == null ||
                !method.getReturnType().equals("V") || method.getParameterTypes().size() != 1 ||
                !contextTypes.contains(method.getParameterTypes().get(0).toString())) continue;
            System.out.println("STORAGE_CONTEXT_VOID_CANDIDATE\t" + signature(method));
            codecBody(method, 60); cancellationCandidates++;
        }
        System.out.println("STORAGE_CONTEXT_TOTAL\tcandidates=" + cancellationCandidates);
        // Resolve the actual optimized Cache-like interface, rather than
        // compiling an assumed upstream Media3 class name into an extension.
        for (var clazz : classes.values()) {
            if ((clazz.getAccessFlags() & 0x200) == 0) continue;
            boolean cacheShape = false;
            for (var method : clazz.getMethods())
                if (String.join("", method.getParameterTypes()).equals("Ljava/lang/String;") &&
                    method.getReturnType().equals("Ljava/util/NavigableSet;")) cacheShape = true;
            if (!cacheShape) continue;
            System.out.println("STORAGE_CACHE_INTERFACE\t" + clazz.getType());
            for (var method : clazz.getMethods())
                System.out.println("STORAGE_CACHE_METHOD\t" + signature(method) + "\tflags=" + method.getAccessFlags());
        }
        if (matches == 0) throw new IllegalStateException("Pinned export source anchors missing");
        System.out.println("STORAGE_TOTAL\tanchored_methods=" + matches + "\towner_classes=" + owners.size());
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
