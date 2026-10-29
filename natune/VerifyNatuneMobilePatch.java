import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21s;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.value.*;
import com.android.tools.smali.dexlib2.immutable.value.*;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.File;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

/** Compare all method instructions/handlers after normalizing DEX indexes and padding. */
public final class VerifyNatuneMobilePatch {
    record Site(String method, int offset, int register) {}
    static final List<Site> SITES = List.of(
        new Site("Lmoe/rukamori/archivetune/playback/MusicService$prefetchNextMediaItemStream$2;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;", 73, 20),
        new Site("Lmoe/rukamori/archivetune/playback/MusicService;->k2(Lr8/km2;Z)Lr8/km2;", 26, 4),
        new Site("Lmoe/rukamori/archivetune/playback/MusicService;->onMediaItemTransition(Lr8/yh7;I)V", 1216, 2),
        new Site("Lmoe/rukamori/archivetune/playback/b;->a(Lr8/km2;)Lr8/km2;", 196, 1),
        new Site("Lmoe/rukamori/archivetune/playback/g;->g(Ljava/lang/String;Lr8/cf2;)Ljava/lang/Object;", 290, 2)
    );
    record Body(int registers, List<String> instructions, List<String> handlers) {}
    record MethodState(int flags, Object annotations, Object parameterAnnotations, Body body) {}
    record FieldState(int flags, Object initialValue, Object annotations) {}
    record ClassState(int flags, String superclass, Object interfaces, Object annotations, Map<String, FieldState> fields) {}
    record Snapshot(Map<String, MethodState> methods, Map<String, ClassState> classes) {}

    static void need(boolean value, String reason) {
        if (!value) throw new IllegalStateException(reason);
    }
    // DEX class_def_item permits omitted trailing static values. Normalize only
    // type-correct false/null/positive-zero float defaults observed in this pinned rewrite. Every
    // other initializer retains exact EncodedValue equality (including true).
    static Object initialValue(int flags, String type, EncodedValue value) {
        if ((flags & 8) == 0 || value == null) return value;
        if (type.equals("Z") && value instanceof BooleanEncodedValue b && !b.getValue()) return null;
        if (type.equals("F") && value instanceof FloatEncodedValue f && Float.floatToRawIntBits(f.getValue()) == 0) return null;
        if ((type.startsWith("L") || type.startsWith("[")) && value instanceof NullEncodedValue) return null;
        return value;
    }
    static void initializerControls() {
        need(initialValue(8, "Z", ImmutableBooleanEncodedValue.FALSE_VALUE) == null, "false default control");
        need(initialValue(8, "Ljava/lang/String;", ImmutableNullEncodedValue.INSTANCE) == null, "null default control");
        need(initialValue(8, "[I", ImmutableNullEncodedValue.INSTANCE) == null, "array null control");
        need(initialValue(8, "Z", ImmutableBooleanEncodedValue.TRUE_VALUE) != null, "true must not equal absent");
        need(initialValue(0, "Z", ImmutableBooleanEncodedValue.FALSE_VALUE) != null, "instance value must stay exact");
        need(initialValue(8, "I", ImmutableBooleanEncodedValue.FALSE_VALUE) != null, "wrong type must stay distinct");
        need(initialValue(8, "Z", ImmutableNullEncodedValue.INSTANCE) != null, "primitive null must stay distinct");
        need(initialValue(8, "I", new ImmutableIntEncodedValue(7)) != null, "nonzero must stay exact");
        need(initialValue(8, "Ljava/lang/String;", new ImmutableStringEncodedValue("")) != null, "empty string is not null");
        need(!new FieldState(8, null, Set.of()).equals(new FieldState(9, null, Set.of())), "field access change control");
        need(initialValue(8, "F", new ImmutableFloatEncodedValue(0.0f)) == null, "positive float zero default");
        need(initialValue(8, "F", new ImmutableFloatEncodedValue(-0.0f)) != null, "negative float zero must stay exact");
        need(initialValue(8, "F", new ImmutableFloatEncodedValue(Float.NaN)) != null, "NaN must stay exact");
        need(initialValue(8, "D", new ImmutableFloatEncodedValue(0.0f)) != null, "wrong float type must stay distinct");
        System.out.println("Initializer preservation controls passed: 14");
    }
    static String signature(MethodReference method) {
        return method.getDefiningClass() + "->" + method.getName() + "(" +
            String.join("", method.getParameterTypes()) + ")" + method.getReturnType();
    }
    static void token(StringBuilder out, Object value) {
        String text = String.valueOf(value);
        out.append(text.length()).append(':').append(text);
    }
    static int label(Map<Integer,Integer> labels, int address) {
        Integer value = labels.get(address);
        need(value != null, "Branch/handler does not target an instruction boundary");
        return value;
    }
    static String node(Instruction instruction, int address, Map<Integer,Integer> labels,
                       Map<Integer,List<Integer>> switchOwners) {
        var out = new StringBuilder();
        var opcode = instruction.getOpcode();
        token(out, switch (opcode) {
            case GOTO, GOTO_16, GOTO_32 -> "GOTO";
            case CONST_STRING, CONST_STRING_JUMBO -> "CONST_STRING";
            default -> opcode.name();
        });
        if (instruction instanceof FiveRegisterInstruction r) {
            int[] registers = {r.getRegisterC(), r.getRegisterD(), r.getRegisterE(), r.getRegisterF(), r.getRegisterG()};
            token(out, r.getRegisterCount());
            for (int i = 0; i < r.getRegisterCount(); i++) token(out, registers[i]);
        } else if (instruction instanceof RegisterRangeInstruction r) {
            token(out, r.getRegisterCount()); token(out, r.getStartRegister());
        } else if (instruction instanceof ThreeRegisterInstruction r) {
            token(out, r.getRegisterA()); token(out, r.getRegisterB()); token(out, r.getRegisterC());
        } else if (instruction instanceof TwoRegisterInstruction r) {
            token(out, r.getRegisterA()); token(out, r.getRegisterB());
        } else if (instruction instanceof OneRegisterInstruction r) token(out, r.getRegisterA());
        if (instruction instanceof WideLiteralInstruction r) token(out, r.getWideLiteral());
        if (instruction instanceof ReferenceInstruction r) token(out, r.getReference());
        if (instruction instanceof DualReferenceInstruction r) token(out, r.getReference2());
        if (instruction instanceof OffsetInstruction r) token(out, label(labels, address + r.getCodeOffset()));
        if (instruction instanceof ArrayPayload r) {
            token(out, r.getElementWidth()); token(out, r.getArrayElements());
        }
        if (instruction instanceof SwitchPayload r) {
            var owners = switchOwners.get(address);
            need(owners != null && !owners.isEmpty(), "Unreferenced switch payload");
            for (int owner : owners) {
                token(out, label(labels, owner));
                for (var element : r.getSwitchElements()) {
                    token(out, element.getKey()); token(out, label(labels, owner + element.getOffset()));
                }
            }
        }
        return out.toString();
    }
    static Body body(Method method, Site expectedChange) {
        return body(method, expectedChange, 0);
    }
    static Body body(Method method, Site expectedChange, int verifiedPrefixInstructions) {
        return body(method, expectedChange, verifiedPrefixInstructions, null);
    }
    // A runtime-settings caller independently verifies its added prefix, then
    // normalizes only the exact rewritten audio call back to the original.
    static Body body(Method method, Site expectedChange, int verifiedPrefixInstructions, Site runtimeChange) {
        var implementation = method.getImplementation();
        if (implementation == null) return null;
        var instructions = new ArrayList<Instruction>();
        implementation.getInstructions().forEach(instructions::add);
        int removedUnits = 0;
        if (verifiedPrefixInstructions > 0) {
            need(verifiedPrefixInstructions < instructions.size(), "Missing original method body");
            for (int i = 0; i < verifiedPrefixInstructions; i++) removedUnits += instructions.get(i).getCodeUnits();
            for (var block : implementation.getTryBlocks()) {
                need(block.getStartCodeAddress() >= removedUnits, "Try range overlaps added prefix");
                for (var handler : block.getExceptionHandlers())
                    need(handler.getHandlerCodeAddress() >= removedUnits, "Handler targets added prefix");
            }
            instructions.subList(0, verifiedPrefixInstructions).clear();
        }
        if (runtimeChange != null) {
            int address = 0;
            boolean changed = false;
            for (int i = 0; i < instructions.size(); i++) {
                var instruction = instructions.get(i);
                if (address == runtimeChange.offset()) {
                    need(instruction.getOpcode() == Opcode.INVOKE_STATIC && instruction instanceof FiveRegisterInstruction &&
                        instruction instanceof ReferenceInstruction, "Runtime audio call layout changed");
                    var call = (FiveRegisterInstruction) instruction;
                    var reference = ((ReferenceInstruction) instruction).getReference();
                    need(reference instanceof MethodReference && signature((MethodReference) reference).equals(
                        "Ldev/purple/patches/natune/RuntimeSettings;->lowData(Landroid/content/Context;)Z") &&
                        call.getRegisterCount() == 1 && i + 1 < instructions.size(), "Wrong runtime audio target");
                    var result = instructions.get(i + 1);
                    need(result.getOpcode() == Opcode.MOVE_RESULT && result instanceof OneRegisterInstruction &&
                        ((OneRegisterInstruction) result).getRegisterA() == runtimeChange.register(), "Wrong runtime audio result");
                    instructions.set(i, new ImmutableInstruction35c(Opcode.INVOKE_STATIC, call.getRegisterCount(),
                        call.getRegisterC(), call.getRegisterD(), call.getRegisterE(), call.getRegisterF(), call.getRegisterG(),
                        new ImmutableMethodReference("Lr8/hg6;", "p", List.of("Landroid/content/Context;"), "Z")));
                    changed = true;
                    break;
                }
                address += instruction.getCodeUnits();
            }
            need(changed, "Runtime audio call missing at original offset");
        }
        int[] originalWidths = instructions.stream().mapToInt(Instruction::getCodeUnits).toArray();
        var labels = new HashMap<Integer,Integer>();
        var switches = new HashMap<Integer,List<Integer>>();
        int address = 0, index = 0;
        for (Instruction instruction : instructions) {
            labels.put(address, index);
            if (instruction.getOpcode() == Opcode.PACKED_SWITCH || instruction.getOpcode() == Opcode.SPARSE_SWITCH) {
                int payload = address + ((OffsetInstruction) instruction).getCodeOffset();
                switches.computeIfAbsent(payload, k -> new ArrayList<>()).add(address);
            }
            address += instruction.getCodeUnits();
            if (instruction.getOpcode() != Opcode.NOP) index++;
        }
        labels.put(address, index);
        var nodes = new ArrayList<String>();
        address = 0;
        boolean changed = false;
        for (int i = 0; i < instructions.size(); i++) {
            var instruction = instructions.get(i);
            if (instruction.getOpcode() != Opcode.NOP) nodes.add(node(instruction, address, labels, switches));
            if (expectedChange != null && address == expectedChange.offset()) {
                need(instruction.getOpcode() == Opcode.INVOKE_STATIC, "Original policy call changed");
                var target = (MethodReference) ((ReferenceInstruction) instruction).getReference();
                need(signature(target).equals("Lr8/hg6;->p(Landroid/content/Context;)Z"), "Different policy helper");
                var result = instructions.get(i + 1);
                need(result.getOpcode() == Opcode.MOVE_RESULT &&
                     ((OneRegisterInstruction) result).getRegisterA() == expectedChange.register(), "Different original result register");
                instructions.set(i + 1, new BuilderInstruction21s(Opcode.CONST_16, expectedChange.register(), 0));
                changed = true;
            }
            // Original addresses/labels are needed even after replacing the next node.
            address += originalWidths[i];
        }
        if (expectedChange != null) need(changed, "Missing expected audio policy call");
        var handlers = new ArrayList<String>();
        for (var block : implementation.getTryBlocks()) {
            var row = new StringBuilder();
            token(row, label(labels, block.getStartCodeAddress() - removedUnits));
            token(row, label(labels, block.getStartCodeAddress() + block.getCodeUnitCount() - removedUnits));
            for (var handler : block.getExceptionHandlers()) {
                token(row, handler.getExceptionType()); token(row, label(labels, handler.getHandlerCodeAddress() - removedUnits));
            }
            handlers.add(row.toString());
        }
        return new Body(implementation.getRegisterCount(), nodes, handlers);
    }
    static Snapshot snapshot(File apk, boolean expectedChanges) throws Exception {
        return snapshot(apk, expectedChanges, Map.of());
    }
    // A caller must independently verify each exact prefix before supplying its
    // length here. The remaining complete body still uses the ordinary verifier.
    static Snapshot snapshot(File apk, boolean expectedChanges, Map<String,Integer> verifiedPrefixes) throws Exception {
        return snapshot(apk, expectedChanges, verifiedPrefixes, false);
    }
    static Snapshot snapshot(File apk, boolean expectedChanges, Map<String,Integer> verifiedPrefixes, boolean runtimeChanges) throws Exception {
        var methods = new TreeMap<String, MethodState>();
        var classes = new TreeMap<String, ClassState>();
        var dex = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault());
        for (String entryName : dex.getDexEntryNames()) {
            var entry = dex.getEntry(entryName);
            need(entry != null, "Missing DEX");
            for (var clazz : entry.getDexFile().getClasses()) {
                var fields = new TreeMap<String, FieldState>();
                for (var field : clazz.getFields()) fields.put(field.getName() + ':' + field.getType(),
                    new FieldState(field.getAccessFlags(), initialValue(field.getAccessFlags(), field.getType(), field.getInitialValue()), field.getAnnotations()));
                need(classes.put(clazz.getType(), new ClassState(clazz.getAccessFlags(), clazz.getSuperclass(),
                     clazz.getInterfaces(), clazz.getAnnotations(), fields)) == null, "Duplicate class");
                for (var method : clazz.getMethods()) {
                    String key = signature(method);
                    Site site = expectedChanges ? SITES.stream().filter(x -> x.method().equals(key)).findFirst().orElse(null) : null;
                    Site runtimeSite = runtimeChanges ? SITES.stream().filter(x -> x.method().equals(key)).findFirst().orElse(null) : null;
                    need(methods.put(key, new MethodState(method.getAccessFlags(), method.getAnnotations(), method.getParameters().stream().map(p -> p.getAnnotations()).toList(), body(method, site, verifiedPrefixes.getOrDefault(key, 0), runtimeSite))) == null,
                         "Duplicate method");
                }
            }
        }
        return new Snapshot(methods, classes);
    }
    public static void main(String[] args) throws Exception {
        initializerControls();
        var expected = snapshot(new File(args[0]), true);
        var actual = snapshot(new File(args[1]), false);
        compare(expected, actual, args, "Verified exact five audio result overrides; global low-data helper and every other method preserved.");
    }
    static void compare(Snapshot expected, Snapshot actual, String[] args, String report) throws Exception {
        compare(expected, actual, args, report, Map.of());
    }
    static void compare(Snapshot expected, Snapshot actual, String[] args, String report,
            Map<String,String> permittedAdditions) throws Exception {
        need(expected.classes().keySet().equals(actual.classes().keySet()), "Class set changed");
        int classDifferences = 0;
        for (String name : expected.classes().keySet()) {
            var before = expected.classes().get(name);
            var after = actual.classes().get(name);
            if (before.equals(after)) continue;
            if (++classDifferences <= 8) {
                System.err.println("CLASS_DIFF " + name + " flags=" + (before.flags() != after.flags()) +
                    " parent=" + !Objects.equals(before.superclass(), after.superclass()) +
                    " interfaces=" + !before.interfaces().equals(after.interfaces()) +
                    " annotations=" + !before.annotations().equals(after.annotations()) +
                    " field_set=" + !before.fields().keySet().equals(after.fields().keySet()));
                for (String field : before.fields().keySet()) {
                    var a = before.fields().get(field); var b = after.fields().get(field);
                    if (b != null && !a.equals(b)) {
                        System.err.println("FIELD_DIFF " + field + " flags=" + (a.flags() != b.flags()) +
                            " annotations=" + !a.annotations().equals(b.annotations()) +
                            " initial_representation=" + (a.initialValue() == null ? "absent" : a.initialValue().getClass().getSimpleName()) +
                            "->" + (b.initialValue() == null ? "absent" : b.initialValue().getClass().getSimpleName()));
                        break;
                    }
                }
            }
        }
        need(classDifferences == 0, "Class/field/annotation structure changed: " + classDifferences + " classes");
        need(expected.methods().keySet().equals(actual.methods().keySet()), "Method set changed");
        for (String name : expected.methods().keySet())
            need(expected.methods().get(name).equals(actual.methods().get(name)), "Unexpected method semantics: " + name);
        for (Site site : SITES) need(expected.methods().containsKey(site.method()), "Missing audio site");
        int nativeFiles = 0, assets = 0, preservedEntries = 0;
        try (var source = new ZipFile(args[0]); var patched = new ZipFile(args[1])) {
            var foundAdditions = new HashSet<String>();
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name.matches("classes(?:[2-9][0-9]*|1[0-9]+)?\\.dex") ||
                    name.equals("META-INF/MANIFEST.MF") || name.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)")) continue;
                var retained = patched.getEntry(name);
                need(retained != null, "Original asset missing");
                byte[] a, b;
                try (var input = source.getInputStream(entry)) { a = MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()); }
                try (var input = patched.getInputStream(retained)) { b = MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()); }
                need(MessageDigest.isEqual(a, b), "Original asset bytes changed");
                preservedEntries++;
                if (name.startsWith("lib/")) nativeFiles++;
                if (name.startsWith("assets/")) assets++;
            }
            var outputEntries = patched.entries();
            while (outputEntries.hasMoreElements()) {
                var entry = outputEntries.nextElement();
                String name = entry.getName();
                if (source.getEntry(name) != null || name.matches("classes(?:[2-9][0-9]*|1[0-9]+)?\\.dex")) continue;
                if (entry.isDirectory() && permittedAdditions.keySet().stream().anyMatch(p -> p.startsWith(name))) continue;
                String expectedHash = permittedAdditions.get(name);
                need(expectedHash != null && foundAdditions.add(name), "Unexpected or duplicate non-DEX entry added");
                try (var input = patched.getInputStream(entry)) {
                    String actualHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
                    need(expectedHash.equals(actualHash), "Added carrier entry differs");
                }
            }
            need(foundAdditions.equals(permittedAdditions.keySet()), "Selected carrier entries missing");
        }
        System.out.println(report);
        System.out.println("Classes=" + expected.classes().size() + "; methods=" + expected.methods().size() +
            "; retained native files=" + nativeFiles + "; retained assets=" + assets + "; all retained non-DEX/signature entries=" + preservedEntries);
        System.out.println("Compared instructions/references/registers/constants/branch targets/try handlers, class and field metadata; debug items and APK signing are outside this comparison.");
    }
}
