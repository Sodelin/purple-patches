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
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
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
private fun settingsMethod(name: String, args: List<String> = emptyList()) = Fingerprint(
    definingClass = SETTINGS, name = name, parameters = args, returnType = "Z",
)

@Suppress("unused")
val natuneRuntimeSettingsPatch = bytecodePatch(
    name = "Runtime switches for Natune audio policies",
    description = "Makes both experimental audio policies read private runtime preferences and preserves the original behavior when disabled. Settings navigation and phone acceptance remain experimental.",
    default = false,
) {
    compatibleWith(Compatibility(name = "Natune", packageName = "moe.rukamori.archivetune", apkFileType = ApkFileType.APK,
        signatures = setOf("6db8326a550861f0f96b9adcb706207d75ac4480de454fb8af95daa5d6c7f9d9"),
        targets = listOf(AppTarget("N202610191717", 6900, isExperimental = true))))
    dependsOn(natuneFullQualityMobilePatch, natuneLocalFlacPatch)
    extendWith("extensions/extension.mpe")
    execute {
        val app = appStart.method
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
