package dev.purple.patches.natune

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

private const val SETTINGS = "Ldev/purple/patches/natune/RuntimeSettings;"
private val appStart = Fingerprint(
    definingClass = "Lmoe/rukamori/archivetune/App;", name = "onCreate", parameters = emptyList(), returnType = "V",
    custom = { method, _ -> method.accessFlags == 17 && method.implementation?.let {
        it.registerCount == 22 && it.tryBlocks.size == 4 &&
            it.instructions.sumOf { i -> i.codeUnits } == 813
    } == true },
)
private val originalSettingsScreen = Fingerprint(
    definingClass = "Lmoe/rukamori/archivetune/ui/screens/settings/l4;", name = "b",
    parameters = listOf("Lr8/f48;", "Ljava/lang/String;", "Lr8/si4;", "Lr8/o72;", "I"), returnType = "V",
)
private fun settingsMethod(name: String, args: List<String> = emptyList()) = Fingerprint(
    definingClass = SETTINGS, name = name, parameters = args, returnType = "Z",
)

@Suppress("unused")
val natuneRuntimeSettingsPatch = bytecodePatch(
    name = "Runtime switches for Natune audio policies",
    description = "Adds Purple audio controls to Natune settings, preserving the original policy when disabled. Phone acceptance remains experimental.",
    default = false,
) {
    compatibleWith(Compatibility(name = "Natune", packageName = "moe.rukamori.archivetune", apkFileType = ApkFileType.APK,
        signatures = setOf("6db8326a550861f0f96b9adcb706207d75ac4480de454fb8af95daa5d6c7f9d9"),
        targets = listOf(AppTarget("N202610191717", 6900, isExperimental = true))))
    dependsOn(natuneFullQualityMobilePatch, natuneLocalFlacPatch)
    extendWith("extensions/extension.mpe")
    execute {
        val app = appStart.method
        val screen = originalSettingsScreen.method
        val screenBody = screen.implementation ?: error("Original settings screen unavailable")
        check(screenBody.tryBlocks.isEmpty()) { "Settings entry has unreviewed exception ranges" }
        val screenCode = screenBody.instructions.toList()
        var screenOffset = 0
        val screenAddresses = screenCode.map { instruction ->
            val here = screenOffset
            screenOffset += instruction.codeUnits
            here
        }
        val settingsCall = screenAddresses.indexOf(376)
        check(settingsCall >= 0 && settingsCall + 2 < screenCode.size) { "Original settings entry moved" }
        val call = screenCode[settingsCall] as? FiveRegisterInstruction ?: error("Settings builder invocation changed")
        val callTarget = (screenCode[settingsCall] as? ReferenceInstruction)?.reference as? MethodReference
        check(screenCode[settingsCall].opcode == Opcode.INVOKE_STATIC && call.registerCount == 5 &&
            listOf(call.registerC,call.registerD,call.registerE,call.registerF,call.registerG) == listOf(3,10,5,6,8) &&
            callTarget?.definingClass == "Lr8/ld6;" && callTarget.name == "p" &&
            callTarget.parameterTypes.map { it.toString() } == listOf("Lr8/f48;", "Z", "Z", "Landroid/content/Context;", "Lr8/ry1;") &&
            callTarget.returnType == "Ljava/util/List;") { "Original settings builder or context changed" }
        check(screenCode[settingsCall+1].opcode == Opcode.MOVE_RESULT_OBJECT &&
            (screenCode[settingsCall+1] as OneRegisterInstruction).registerA == 4 &&
            screenAddresses[settingsCall+2] == 380) { "Settings result changed" }
        val next = (screenCode[settingsCall+2] as? ReferenceInstruction)?.reference as? MethodReference
        check(screenCode[settingsCall+2].opcode == Opcode.INVOKE_VIRTUAL && next?.definingClass == "Lr8/el4;" &&
            next.name == "m0" && next.parameterTypes.map { it.toString() } == listOf("Ljava/lang/Object;") &&
            next.returnType == "V") { "Settings remember boundary changed" }
        // A branch into the inserted prefix could use an unrelated context/list.
        // Include switch payload targets as well as ordinary branch offsets.
        val switchOwners = mutableMapOf<Int,MutableList<Int>>()
        screenCode.forEachIndexed { index,instruction ->
            if(instruction is OffsetInstruction) {
                val source = screenAddresses[index]
                val target = source + instruction.codeOffset
                check(target != 380) { "Unreviewed branch enters settings insertion" }
                if(instruction.opcode == Opcode.PACKED_SWITCH || instruction.opcode == Opcode.SPARSE_SWITCH)
                    switchOwners.getOrPut(target) { mutableListOf() }.add(source)
            }
        }
        screenCode.forEachIndexed { index,instruction ->
            if(instruction is SwitchPayload) {
                val owners = switchOwners[screenAddresses[index]] ?: error("Unowned settings switch payload")
                owners.forEach { owner -> instruction.switchElements.forEach { element ->
                    check(owner + element.offset != 380) { "Unreviewed switch enters settings insertion" }
                } }
            }
        }
        val wrapper = settingsMethod("lowData", listOf("Landroid/content/Context;")).method
        check(wrapper.implementation!!.registerCount in 2..16 && wrapper.implementation!!.tryBlocks.isEmpty()) {
            "Original helper wrapper scratch layout changed"
        }
        val markers = listOf("mobilePatchPresent", "flacPatchPresent").map { name ->
            val method = settingsMethod(name).method
            val code = method.implementation!!.instructions.toList()
            check(method.accessFlags == 9 && method.implementation!!.registerCount == 1 && code.size == 2 &&
                code[0].opcode == Opcode.CONST_4 && (code[0] as OneRegisterInstruction).registerA == 0 &&
                (code[0] as WideLiteralInstruction).wideLiteral == 0L && code[1].opcode == Opcode.RETURN) {
                "Compiled feature marker changed"
            }
            method
        }
        // Dependencies already applied their exact-release checks. Recheck the
        // selected changed sites before converting them to runtime decisions.
        val mobile = audioSites.map { site ->
            val method = site.fingerprint.method
            val code = method.implementation!!.instructions.toList()
            var offset = 0
            val index = code.indexOfFirst { i -> val here = offset; offset += i.codeUnits; here == site.callCodeUnit }
            check(index >= 0 && index + 1 < code.size) { "Missing selected mobile site" }
            val call = code[index] as? FiveRegisterInstruction ?: error("Changed invocation layout")
            val ref = (code[index] as? ReferenceInstruction)?.reference as? MethodReference
            check(code[index].opcode == Opcode.INVOKE_STATIC && ref?.definingClass == "Lr8/hg6;" &&
                ref.name == "p" && call.registerCount == 1 && ref.returnType == "Z" &&
                ref.parameterTypes.map { it.toString() } == listOf("Landroid/content/Context;")) { "Original helper changed" }
            check(code[index + 1].opcode == Opcode.CONST_16 &&
                (code[index + 1] as OneRegisterInstruction).registerA == site.resultRegister &&
                (code[index + 1] as WideLiteralInstruction).wideLiteral == 0L) { "Selected mobile override changed" }
            Triple(method, index, Pair(call, site.resultRegister))
        }
        val codec = localCodecSites.map { site ->
            val method = site.fingerprint.method
            Triple(method, if (site.name == "Q") 9 else 8, method.getInstruction(0))
        } + Triple(localInputChange.method, 15, localInputChange.method.getInstruction(0))
        // Context is initialized before original Application.onCreate work.
        // Its existing four handler regions and original body remain intact.
        app.addInstructions(0, "invoke-static/range {p0 .. p0}, $SETTINGS->initialize(Landroid/content/Context;)V")
        // The original builder has just returned v4 and still holds its actual
        // UI Context in v6. Preserve the memoized original list and screen.
        screen.addInstructions(settingsCall+2, """
            invoke-static {v4, v6}, Ldev/purple/patches/natune/NatuneSettingsNavigation;->append(Ljava/util/List;Landroid/content/Context;)Ljava/util/List;
            move-result-object v4
        """.trimIndent())
        wrapper.addInstructions(0, """
            invoke-static {p0}, Lr8/hg6;->p(Landroid/content/Context;)Z
            move-result v0
            invoke-static {p0, v0}, $SETTINGS->mobilePolicy(Landroid/content/Context;Z)Z
            move-result v0
            return v0
        """.trimIndent())
        markers.forEach { it.replaceInstruction(0, BuilderInstruction11n(Opcode.CONST_4, 0, 1)) }
        val target = ImmutableMethodReference(SETTINGS, "lowData", listOf("Landroid/content/Context;"), "Z")
        mobile.forEach { (method, index, pair) ->
            val (call, register) = pair
            method.replaceInstruction(index, BuilderInstruction35c(Opcode.INVOKE_STATIC, call.registerCount,
                call.registerC, call.registerD, call.registerE, call.registerF, call.registerG, target))
            method.replaceInstruction(index + 1, BuilderInstruction11x(Opcode.MOVE_RESULT, register))
        }
        codec.forEach { (method, length, _) ->
            val original = method.getInstruction(length)
            method.addInstructionsWithLabels(0, """
                invoke-static {}, $SETTINGS->requireFlac()Z
                move-result v0
                if-eqz v0, :purple_stock
            """.trimIndent(), ExternalLabel("purple_stock", original))
        }
    }
}
