package dev.purple.patches.natune

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

/** The upstream screen uses a supported maximum as if it were a measured route. */
@Suppress("unused")
val natuneOutputEstimatePatch = bytecodePatch(
    name = "Clarify Natune output rate estimate",
    description = "Labels the displayed output rate as a report rather than a measured active route.",
    default = false,
) {
    compatibleWith(Compatibility(name = "Natune", packageName = "moe.rukamori.archivetune",
        apkFileType = ApkFileType.APK,
        signatures = setOf("6db8326a550861f0f96b9adcb706207d75ac4480de454fb8af95daa5d6c7f9d9"),
        targets = listOf(AppTarget("N202611030452", 6928, isExperimental = true))))
    execute {
        val method = Fingerprint(
            definingClass = "Lmoe/rukamori/archivetune/ui/utils/o;", name = "i",
            parameters = listOf("Lr8/pl0;", "Ljava/lang/Integer;", "Lr8/o72;", "I"), returnType = "V",
            custom = { m, _ -> m.accessFlags == 25 && m.implementation?.let {
                it.registerCount == 24 && it.instructions.sumOf { insn -> insn.codeUnits } == 457
            } == true },
        ).method
        val code = method.implementation!!.instructions.toList()
        val replacements = listOf(
            Triple(148, "No conversion — source matches the ", "Reported rate: "),
            Triple(150, " Hz output route", " Hz; actual output unknown"),
            Triple(157, " Hz → ", " Hz; reported rate "),
            Triple(159, " Hz at the output route", " Hz; actual output unknown"),
            Triple(161, "Android resamples ", "Source "),
        )
        // Resolve all five exact original sites before modifying any of them.
        val changes = replacements.map { (address, original, replacement) ->
            var offset = 0
            val index = code.indexOfFirst { instruction ->
                val here = offset
                offset += instruction.codeUnits
                here == address
            }
            check(index >= 0 && code[index].opcode == Opcode.CONST_STRING) { "Output label site changed" }
            val reference = (code[index] as? ReferenceInstruction)?.reference as? StringReference
            check(reference?.string == original) { "Output label text changed" }
            Triple(index, (code[index] as OneRegisterInstruction).registerA, replacement)
        }
        changes.forEach { (index, register, value) ->
            method.replaceInstruction(index, BuilderInstruction21c(Opcode.CONST_STRING, register, ImmutableStringReference(value)))
        }
    }
}
