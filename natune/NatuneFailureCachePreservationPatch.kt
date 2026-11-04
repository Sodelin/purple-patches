package dev.purple.patches.natune

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** Preserve existing cached music when a download attempt fails. */
@Suppress("unused")
val natuneFailureCachePreservationPatch = bytecodePatch(
    name = "Preserve Natune cache on download failure",
    description = "Avoids purging song cache entries on a failed download; explicit removal remains unchanged.",
    default = false,
) {
    compatibleWith(Compatibility(name = "Natune", packageName = "moe.rukamori.archivetune",
        apkFileType = ApkFileType.APK,
        signatures = setOf("6db8326a550861f0f96b9adcb706207d75ac4480de454fb8af95daa5d6c7f9d9"),
        targets = listOf(AppTarget("N202611030452", 6928, isExperimental = true))))
    execute {
        val method = Fingerprint(definingClass = "Lr8/qe3;", name = "b",
            parameters = listOf("Lr8/yd3;", "Lr8/hc3;", "Ljava/lang/Exception;"), returnType = "V",
            custom = { m, _ -> m.accessFlags == 17 && m.implementation?.let {
                it.registerCount == 7 && it.tryBlocks.isEmpty() && it.instructions.sumOf { i -> i.codeUnits } == 69
            } == true }).method
        val code = method.implementation!!.instructions.toList()
        var offset = 0
        val index = code.indexOfFirst { i -> val here = offset; offset += i.codeUnits; here == 41 }
        check(index >= 0) { "Failure cleanup site moved" }
        val call = code[index]
        val ref = (call as? ReferenceInstruction)?.reference as? MethodReference
        val regs = call as? FiveRegisterInstruction ?: error("Failure cleanup layout changed")
        check(call.opcode == Opcode.INVOKE_STATIC && ref?.definingClass == "Lmoe/rukamori/archivetune/playback/g;" &&
            ref.name == "a" && ref.parameterTypes.map { it.toString() } ==
                listOf("Lmoe/rukamori/archivetune/playback/g;", "Ljava/lang/String;") && ref.returnType == "V" &&
            regs.registerCount == 2 && regs.registerC == 3 && regs.registerD == 6) { "Unexpected failure cleanup target" }
        // The matching removal callback has a separate call and is untouched.
        // URL-cache invalidation, failure status and all other listener logic stay intact.
        method.replaceInstruction(index, BuilderInstruction10x(Opcode.NOP))
    }
}
