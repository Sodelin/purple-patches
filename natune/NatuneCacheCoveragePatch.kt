package dev.purple.patches.natune

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** Fail closed before reusing a partial cache whose span lengths happen to sum high enough. */
@Suppress("unused")
val natuneCacheCoveragePatch = bytecodePatch(
    name = "Verify Natune download cache coverage",
    description = "Requires the complete expected cached range before reusing cached audio.",
    default = false,
) {
    compatibleWith(Compatibility(name = "Natune", packageName = "moe.rukamori.archivetune",
        apkFileType = ApkFileType.APK,
        signatures = setOf("6db8326a550861f0f96b9adcb706207d75ac4480de454fb8af95daa5d6c7f9d9"),
        targets = listOf(AppTarget("N202611030452", 6928, isExperimental = true))))
    extendWith("extensions/extension.mpe")
    execute {
        val method = Fingerprint(
            definingClass = "Lmoe/rukamori/archivetune/playback/b;", name = "a",
            parameters = listOf("Lr8/jm2;"), returnType = "Lr8/jm2;",
            custom = { m, _ -> m.accessFlags == 17 && m.implementation?.let {
                it.registerCount == 21 && it.instructions.sumOf { i -> i.codeUnits } == 491
            } == true },
        ).method
        val code = method.implementation!!.instructions.toList()
        var offset = 0
        val addresses = code.map { i -> val here = offset; offset += i.codeUnits; here }
        fun at(address: Int): Int = addresses.indexOf(address).also { check(it >= 0) { "Cache site moved" } }
        val branch = code[at(145)]
        check(branch.opcode == Opcode.IF_LTZ && (branch as OneRegisterInstruction).registerA == 0 &&
            (branch as OffsetInstruction).codeOffset == 21) { "Cache length branch changed" }
        val cacheRead = code[at(76)] as? ReferenceInstruction ?: error("Cache receiver changed")
        val cacheField = cacheRead.reference as? FieldReference ?: error("Cache field changed")
        check(code[at(76)].opcode == Opcode.IGET_OBJECT && cacheField.definingClass == "Lmoe/rukamori/archivetune/playback/g;" &&
            cacheField.name == "c" && cacheField.type == "Lr8/ud1;" &&
            (code[at(76)] as TwoRegisterInstruction).registerA == 0 &&
            (code[at(76)] as TwoRegisterInstruction).registerB == 10) { "Unexpected cache owner" }
        val next = code[at(147)] as? ReferenceInstruction ?: error("Cache match moved")
        val target = next.reference as? MethodReference ?: error("Cache comparison moved")
        check(code[at(147)].opcode == Opcode.INVOKE_STATIC && target.definingClass == "Lr8/co5;" &&
            target.name == "i" && target.returnType == "Z" &&
            target.parameterTypes.map { it.toString() } == listOf("Ljava/lang/Object;", "Ljava/lang/Object;")) { "Cache success path changed" }
        check(code[at(166)].opcode == Opcode.MOVE_WIDE_FROM16 &&
            (code[at(166)] as TwoRegisterInstruction).registerA == 5 &&
            (code[at(166)] as TwoRegisterInstruction).registerB == 17) { "Cache continuation changed" }
        val switchOwners = mutableMapOf<Int, MutableList<Int>>()
        code.forEachIndexed { i, insn ->
            if (insn is OffsetInstruction) {
                val source = addresses[i]
                val destination = source + insn.codeOffset
                check(destination != 147) { "Unreviewed branch bypasses cache coverage guard" }
                if (insn.opcode == Opcode.PACKED_SWITCH || insn.opcode == Opcode.SPARSE_SWITCH)
                    switchOwners.getOrPut(destination) { mutableListOf() }.add(source)
            }
        }
        code.forEachIndexed { i, insn ->
            if (insn is SwitchPayload) {
                val owners = switchOwners[addresses[i]] ?: error("Unowned switch payload")
                owners.forEach { owner -> insn.switchElements.forEach { element ->
                    check(owner + element.offset != 147) { "Unreviewed switch bypasses cache coverage guard" }
                } }
            }
        }
        // On entry v10 is the original DownloadUtil, v13 the selected cache key,
        // v7/v8 the positive expected length, and v0 is a dead comparison result.
        // The reflection adapter catches failure and only reads the cache. It
        // returns false to continue the original next-source path on uncertainty.
        method.addInstructionsWithLabels(at(147), """
            iget-object v0, v10, Lmoe/rukamori/archivetune/playback/g;->c:Lr8/ud1;
            invoke-static {v0, v13, v7, v8}, Ldev/purple/patches/natune/CacheRangeGuard;->complete(Ljava/lang/Object;Ljava/lang/String;J)Z
            move-result v0
            if-eqz v0, :purple_incomplete
        """.trimIndent(), ExternalLabel("purple_incomplete", code[at(166)]))
    }
}
