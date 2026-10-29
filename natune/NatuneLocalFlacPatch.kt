package dev.purple.patches.natune

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** Parsed extractor Format, never filename/provider MIME or decoded PCM output. */
private class LocalCodecSite(
    val name: String,
    val parameters: List<String>,
    val result: String,
    val registers: Int,
    val units: Int,
    val formatRegister: Int,
    val refusal: String,
) {
    val fingerprint = Fingerprint(
        definingClass = "Lr8/ge7;", name = name, parameters = parameters, returnType = result,
        custom = { method, _ -> method.accessFlags == 17 && method.implementation?.let {
            it.registerCount == registers && it.tryBlocks.isEmpty() &&
                it.instructions.sumOf { instruction -> instruction.codeUnits } == units
        } == true },
    )
}

private val localCodecSites = listOf(
    LocalCodecSite("z0", listOf("Lr8/hv3;", "Lr8/yf4;"), "I", 21, 270, 20,
        "const/4 v0, 0x1\nreturn v0"), // FORMAT_UNSUPPORTED_SUBTYPE
    LocalCodecSite("y0", listOf("Lr8/yf4;"), "Z", 6, 52, 5,
        "const/4 v0, 0x0\nreturn v0"), // no passthrough/offload bypass
    // R8 narrowed the actual override to ArrayList. Collections.emptyList()
    // would violate this release's return type, even though upstream says List.
    LocalCodecSite("Q", listOf("Lr8/hv3;", "Lr8/yf4;", "Z"), "Ljava/util/ArrayList;", 7, 72, 5,
        "new-instance v0, Ljava/util/ArrayList;\n" +
            "invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V\nreturn-object v0"),
)

@Suppress("unused")
val natuneLocalFlacPatch = bytecodePatch(
    name = "Require FLAC for local audio decoding",
    description = "Rejects non-FLAC parsed audio formats in local decoder selection and hardware bypass. Includes cached playback. Does not validate whole downloads, recording identity or remote Cast, and does not add providers. Experimental; unavailable FLAC fails playback rather than falling back to lossy audio.",
    default = false,
) {
    compatibleWith(Compatibility(
        name = "Natune", packageName = "moe.rukamori.archivetune", apkFileType = ApkFileType.APK,
        signatures = setOf("6db8326a550861f0f96b9adcb706207d75ac4480de454fb8af95daa5d6c7f9d9"),
        targets = listOf(AppTarget(version = "N202610191717", versionCode = 6900, isExperimental = true)),
    ))
    execute {
        val selected = localCodecSites.map { site ->
            val method = site.fingerprint.method
            val instructions = method.implementation!!.instructions.toList()
            val parameterWords = 1 + site.parameters.sumOf { if (it == "J" || it == "D") 2 else 1 }
            check(site.registers - parameterWords >= 2) { "Missing independent local scratch registers" }
            val actualFormatRegister = site.registers - parameterWords + 1 +
                site.parameters.takeWhile { it != "Lr8/yf4;" }.sumOf { if (it == "J" || it == "D") 2 else 1 }
            check(actualFormatRegister == site.formatRegister) { "Format argument layout changed" }
            val references = instructions.mapNotNull { (it as? ReferenceInstruction)?.reference }
            if (site.name != "y0") check(references.any {
                it is FieldReference && it.definingClass == "Lr8/yf4;" && it.name == "o" && it.type == "Ljava/lang/String;"
            }) { "Parsed sample MIME field changed" }
            if (site.name == "y0") check(references.any {
                it is MethodReference && it.definingClass == "Lr8/ge7;" && it.name == "D0" &&
                    it.parameterTypes.map { p -> p.toString() } == listOf("Lr8/yf4;") && it.returnType == "I"
            }) { "Hardware bypass capability path changed" }
            check(instructions.first().opcode != Opcode.NOP) { "Unexpected release entry padding" }
            site to method
        }
        // Validate all sites first; leave the complete original FLAC method body
        // and its labels/register count intact. v0/v1 are original local scratch.
        selected.forEach { (site, method) ->
            val original = method.getInstruction(0)
            method.addInstructionsWithLabels(0, """
                move-object/from16 v0, v${site.formatRegister}
                iget-object v0, v0, Lr8/yf4;->o:Ljava/lang/String;
                const-string v1, "audio/flac"
                invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v0
                if-nez v0, :purple_original
                ${site.refusal}
            """.trimIndent(), ExternalLabel("purple_original", original))
        }
    }
}
