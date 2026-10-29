import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.File;
import java.util.*;

/** Independent inspection of the experimental settings APK, never its execution. */
public final class VerifyNatuneRuntimeSettingsPatch {
    static final String TYPE = "Ldev/purple/patches/natune/RuntimeSettings;";
    static final String APP = "Lmoe/rukamori/archivetune/App;->onCreate()V";
    static void need(boolean value, String why) { VerifyNatuneMobilePatch.need(value, why); }
    static List<Instruction> instructions(Method method) {
        need(method != null && method.getImplementation() != null, "Required method absent");
        var result = new ArrayList<Instruction>();
        method.getImplementation().getInstructions().forEach(result::add);
        return result;
    }
    static void call(Instruction instruction, Opcode opcode, String target, int... registers) {
        VerifyNatuneLocalFlacPatch.call(instruction, opcode, target, registers);
    }
    static void reg(Instruction instruction, Opcode opcode, int register) {
        need(instruction.getOpcode() == opcode && instruction instanceof OneRegisterInstruction &&
            ((OneRegisterInstruction) instruction).getRegisterA() == register, "Wrong register or opcode");
    }
    static void marker(Method method) {
        var code = instructions(method);
        need(method.getAccessFlags() == 9 && method.getImplementation().getRegisterCount() == 1 && code.size() == 2,
            "Unexpected feature marker shape");
        reg(code.get(0), Opcode.CONST_4, 0);
        need(code.get(0) instanceof WideLiteralInstruction &&
            ((WideLiteralInstruction) code.get(0)).getWideLiteral() == 1, "Feature marker not enabled");
        reg(code.get(1), Opcode.RETURN, 0);
    }
    static void wrapper(Method method) {
        var code = instructions(method);
        int count = method.getImplementation().getRegisterCount();
        need(count >= 2 && count <= 16 && method.getAccessFlags() == 9 && code.size() >= 5,
            "Unexpected audio wrapper shape");
        int argument = count - 1; // static method, one Context parameter
        call(code.get(0), Opcode.INVOKE_STATIC, "Lr8/hg6;->p(Landroid/content/Context;)Z", argument);
        reg(code.get(1), Opcode.MOVE_RESULT, 0);
        call(code.get(2), Opcode.INVOKE_STATIC, TYPE + "->mobilePolicy(Landroid/content/Context;Z)Z", argument, 0);
        reg(code.get(3), Opcode.MOVE_RESULT, 0);
        reg(code.get(4), Opcode.RETURN, 0);
    }
    static void appEntry(Method method) {
        var code = instructions(method);
        need(method.getAccessFlags() == 17 && method.getImplementation().getRegisterCount() == 22 &&
            method.getImplementation().getTryBlocks().size() == 4, "Application entry shape changed");
        var first = code.get(0);
        need(first.getOpcode() == Opcode.INVOKE_STATIC_RANGE && first instanceof RegisterRangeInstruction &&
            first instanceof ReferenceInstruction, "Missing app bootstrap");
        var invoke = (RegisterRangeInstruction) first;
        var reference = ((ReferenceInstruction) first).getReference();
        need(invoke.getStartRegister() == 21 && invoke.getRegisterCount() == 1 && reference instanceof MethodReference &&
            VerifyNatuneMobilePatch.signature((MethodReference) reference).equals(TYPE + "->initialize(Landroid/content/Context;)V"),
            "Wrong app bootstrap or application instance");
    }
    static void guard(Method method, VerifyNatuneLocalFlacPatch.Hook hook) {
        var code = instructions(method);
        need(code.size() > 3 + hook.prefix(), "Missing codec body");
        call(code.get(0), Opcode.INVOKE_STATIC, TYPE + "->requireFlac()Z");
        reg(code.get(1), Opcode.MOVE_RESULT, 0);
        reg(code.get(2), Opcode.IF_EQZ, 0);
        int offset = 0;
        for (int i = 2; i < 3 + hook.prefix(); i++) offset += code.get(i).getCodeUnits();
        need(code.get(2) instanceof OffsetInstruction &&
            ((OffsetInstruction) code.get(2)).getCodeOffset() == offset, "Disabled switch must enter original codec body");
        VerifyNatuneLocalFlacPatch.guard(code.subList(3, code.size()), hook);
    }
    public static void main(String[] args) throws Exception {
        need(args.length == 2, "Expected original and patched APK");
        var methods = new HashMap<String, Method>();
        var dex = DexFileFactory.loadDexContainer(new File(args[1]), Opcodes.getDefault());
        for (String entry : dex.getDexEntryNames())
            for (var clazz : dex.getEntry(entry).getDexFile().getClasses())
                for (var method : clazz.getMethods())
                    need(methods.put(VerifyNatuneMobilePatch.signature(method), method) == null, "Duplicate method");

        appEntry(methods.get(APP));
        wrapper(methods.get(TYPE + "->lowData(Landroid/content/Context;)Z"));
        marker(methods.get(TYPE + "->mobilePatchPresent()Z"));
        marker(methods.get(TYPE + "->flacPatchPresent()Z"));
        var prefixes = new HashMap<String, Integer>();
        prefixes.put(APP, 1);
        for (var hook : VerifyNatuneLocalFlacPatch.HOOKS) {
            var method = methods.get(hook.key());
            need(method != null && method.getAccessFlags() == 17 &&
                method.getImplementation().getRegisterCount() == hook.registers() &&
                method.getImplementation().getTryBlocks().isEmpty(), "Codec method shape changed");
            guard(method, hook);
            prefixes.put(hook.key(), 3 + hook.prefix());
        }

        VerifyNatuneMobilePatch.initializerControls();
        var original = VerifyNatuneMobilePatch.snapshot(new File(args[0]), false);
        var selected = VerifyNatuneMobilePatch.snapshot(new File(args[1]), false, prefixes, true);
        var classes = new TreeMap<>(selected.classes());
        var bodies = new TreeMap<>(selected.methods());
        Set<String> permitted = Set.of(TYPE, "Ldev/purple/patches/natune/PurpleSettingsActivity;");
        var found = new HashSet<String>();
        for (String name : new ArrayList<>(classes.keySet())) {
            if (original.classes().containsKey(name)) continue;
            String outer = name.contains("$") ? name.substring(0, name.indexOf('$')) + ";" : name;
            need(permitted.contains(outer), "Unexpected extension class: " + name);
            found.add(outer);
            classes.remove(name);
        }
        need(found.equals(permitted), "Missing selected settings extension classes");
        bodies.keySet().removeIf(name -> {
            int separator = name.indexOf("->");
            return separator > 0 && !original.classes().containsKey(name.substring(0, separator));
        });
        VerifyNatuneMobilePatch.compare(original, new VerifyNatuneMobilePatch.Snapshot(bodies, classes), args,
            "Verified exact settings bootstrap, stock-result wrapper and four optional FLAC guards; original remaining program/resources preserved.");
        System.out.println("RUNTIME_SETTINGS_BYTECODE source_apk_compared=true navigation_bound=false device_test=false");
    }
}
