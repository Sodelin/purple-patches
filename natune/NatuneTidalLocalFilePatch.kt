package dev.purple.patches.natune

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22c
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

/** Wrap the existing HTTP downloader in Media3's scheme-aware factory. */
@Suppress("unused")
val natuneTidalLocalFilePatch = bytecodePatch(
    name = "Route Natune local Tidal downloads",
    description = "Allows resolved local audio files through the download cache while preserving the HTTP downloader.",
    default = false,
) {
    compatibleWith(Compatibility(name = "Natune", packageName = "moe.rukamori.archivetune",
        apkFileType = ApkFileType.APK,
        signatures = setOf("6db8326a550861f0f96b9adcb706207d75ac4480de454fb8af95daa5d6c7f9d9"),
        targets = listOf(AppTarget("N202611030452", 6928, isExperimental = true))))
    execute {
        fun selected(owner: String, name: String, args: List<String>, result: String, flags: Int, registers: Int, units: Int) =
            Fingerprint(definingClass = owner, name = name, parameters = args, returnType = result,
                custom = { m, _ -> m.accessFlags == flags && m.implementation?.let {
                    it.registerCount == registers && it.instructions.sumOf { i -> i.codeUnits } == units
                } == true }).method
        fun creates(method: com.android.tools.smali.dexlib2.iface.Method, type: String): Boolean =
            method.implementation!!.instructions.any { insn ->
                insn.opcode == Opcode.NEW_INSTANCE &&
                    ((insn as? ReferenceInstruction)?.reference as? TypeReference)?.type == type
            }
        val schemeFactory = selected("Lr8/wl7;", "y", emptyList(), "Lr8/cm2;", 1, 3, 18)
        val httpFactory = selected("Lr8/yz7;", "y", emptyList(), "Lr8/cm2;", 1, 3, 44)
        val schemeReader = selected("Lr8/vu2;", "g", listOf("Lr8/jm2;"), "J", 17, 8, 314)
        selected("Lr8/wl7;", "<init>", listOf("Landroid/content/Context;", "Lr8/am2;"), "V", 65537, 4, 16)
        check(creates(schemeFactory, "Lr8/vu2;") && creates(httpFactory, "Lr8/mo8;") &&
            schemeReader.implementation!!.instructions.any { insn ->
                ((insn as? ReferenceInstruction)?.reference as? StringReference)?.string == "file"
            }) { "Scheme-aware or HTTP data-source binding changed" }
        val method = Fingerprint(definingClass = "Lmoe/rukamori/archivetune/playback/g;", name = "<init>",
            parameters = listOf("Landroid/content/Context;", "Lmoe/rukamori/archivetune/db/q0;", "Lr8/qpb;", "Lr8/ud1;", "Lr8/ud1;"),
            returnType = "V", custom = { m, _ -> m.accessFlags == 65537 && m.implementation?.let {
                it.registerCount == 22 && it.tryBlocks.isEmpty() && it.instructions.sumOf { i -> i.codeUnits } == 470
            } == true }).method
        val code = method.implementation!!.instructions.toList()
        var offset = 0
        val addresses = code.map { insn -> val here = offset; offset += insn.codeUnits; here }
        fun at(address: Int) = addresses.indexOf(address).also { check(it >= 0) { "Download factory site moved" } }
        val context = code[at(2)] as? TwoRegisterInstruction ?: error("Context move changed")
        check(code[at(2)].opcode == Opcode.MOVE_OBJECT_FROM16 && context.registerA == 2 && context.registerB == 17) {
            "Original context register changed"
        }
        val create = code[at(274)]
        val createType = (create as? ReferenceInstruction)?.reference as? TypeReference
        check(create.opcode == Opcode.NEW_INSTANCE && (create as OneRegisterInstruction).registerA == 3 &&
            createType?.type == "Lr8/yz7;") { "HTTP factory type changed" }
        val init = code[at(276)]
        val initRef = (init as? ReferenceInstruction)?.reference as? MethodReference
        val initRegs = init as? FiveRegisterInstruction ?: error("HTTP factory constructor changed")
        check(init.opcode == Opcode.INVOKE_DIRECT && initRef?.definingClass == "Lr8/yz7;" && initRef.name == "<init>" &&
            initRef.parameterTypes.map { it.toString() } == listOf("Landroid/content/Context;") && initRef.returnType == "V" &&
            initRegs.registerCount == 2 && initRegs.registerC == 3 && initRegs.registerD == 2) { "HTTP factory constructor changed" }
        val original = code[at(295)]
        val field = (original as? ReferenceInstruction)?.reference as? FieldReference
        val regs = original as? TwoRegisterInstruction ?: error("Cache upstream assignment changed")
        check(original.opcode == Opcode.IPUT_OBJECT && regs.registerA == 3 && regs.registerB == 5 &&
            field?.definingClass == "Lr8/be1;" && field.name == "C" && field.type == "Lr8/am2;") { "Wrong cache upstream" }
        val readFactory = code[at(293)]
        val readField = (readFactory as? ReferenceInstruction)?.reference as? FieldReference
        val readRegs = readFactory as? TwoRegisterInstruction ?: error("Cache reader assignment changed")
        check(readFactory.opcode == Opcode.IPUT_OBJECT && readRegs.registerA == 1 && readRegs.registerB == 5 &&
            readField?.definingClass == "Lr8/be1;" && readField.name == "s" && readField.type == "Lr8/wn0;" &&
            code[at(297)].opcode == Opcode.CONST_4 && (code[at(297)] as OneRegisterInstruction).registerA == 1 &&
            (code[at(297)] as WideLiteralInstruction).wideLiteral == 2L) { "Scratch register lifetime changed" }
        // No branch may enter the original assignment past the inserted wrapper.
        val owners = mutableMapOf<Int, MutableList<Int>>()
        code.forEachIndexed { i, insn ->
            if (insn is OffsetInstruction) {
                val from = addresses[i]; val dest = from + insn.codeOffset
                check(dest != 295) { "Branch bypasses URI factory wrapper" }
                if (insn.opcode == Opcode.PACKED_SWITCH || insn.opcode == Opcode.SPARSE_SWITCH)
                    owners.getOrPut(dest) { mutableListOf() }.add(from)
            }
        }
        code.forEachIndexed { i, insn ->
            if (insn is SwitchPayload) (owners[addresses[i]] ?: error("Unowned switch payload")).forEach { from ->
                insn.switchElements.forEach { check(from + it.offset != 295) { "Switch bypasses URI factory wrapper" } }
            }
        }
        // v1's previous FileDataSource factory has already been stored at 293;
        // the next original instruction overwrites v1. v2 is Context, v3 HTTP.
        method.replaceInstruction(at(295), BuilderInstruction22c(Opcode.IPUT_OBJECT, 1, 5, field))
        method.addInstructions(at(295), """
            new-instance v1, Lr8/wl7;
            invoke-direct {v1, v2, v3}, Lr8/wl7;-><init>(Landroid/content/Context;Lr8/am2;)V
        """.trimIndent())
    }
}
