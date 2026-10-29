package dev.purple.patches.natune

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21s
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** Exact release sites proven in policy-hooks.json, not source-name guesses. */
private class AudioSite(
    owner: String,
    name: String,
    parameters: List<String>,
    returnType: String,
    val registers: Int,
    val codeUnits: Int,
    val callCodeUnit: Int,
    val resultRegister: Int,
) {
    val fingerprint = Fingerprint(
        definingClass = owner,
        name = name,
        parameters = parameters,
        returnType = returnType,
        custom = { method, _ ->
            method.accessFlags == 17 && method.implementation?.let {
                it.registerCount == registers && it.instructions.sumOf { instruction -> instruction.codeUnits } == codeUnits
            } == true
        },
    )
}

private val audioSites = listOf(
    AudioSite("Lmoe/rukamori/archivetune/playback/MusicService\$prefetchNextMediaItemStream\$2;",
        "invokeSuspend", listOf("Ljava/lang/Object;"), "Ljava/lang/Object;", 23, 287, 73, 20),
    AudioSite("Lmoe/rukamori/archivetune/playback/MusicService;",
        "k2", listOf("Lr8/km2;", "Z"), "Lr8/km2;", 58, 2320, 26, 4),
    AudioSite("Lmoe/rukamori/archivetune/playback/MusicService;",
        "onMediaItemTransition", listOf("Lr8/yh7;", "I"), "V", 20, 1263, 1216, 2),
    AudioSite("Lmoe/rukamori/archivetune/playback/b;",
        "a", listOf("Lr8/km2;"), "Lr8/km2;", 21, 491, 196, 1),
    AudioSite("Lmoe/rukamori/archivetune/playback/g;",
        "g", listOf("Ljava/lang/String;", "Lr8/cf2;"), "Ljava/lang/Object;", 25, 468, 290, 2),
)

@Suppress("unused")
val natuneFullQualityMobilePatch = bytecodePatch(
    name = "Allow full-quality audio on mobile data",
    description = "Allows preferred audio sources and full configured audio quality on cellular/metered connections. Keeps global low-data UI/artwork settings. This patch alone does not enforce FLAC or add providers.",
    default = false,
) {
    compatibleWith(Compatibility(
        name = "Natune",
        packageName = "moe.rukamori.archivetune",
        apkFileType = ApkFileType.APK,
        targets = listOf(AppTarget(version = "N202610191717", isExperimental = true)),
    ))
    execute {
        // Resolve and check every site before mutating any method. A changed
        // APK or another source modifying these methods must fail admission.
        val changes = audioSites.map { site ->
            check(site.codeUnits > 0) { "Missing reviewed release code size" }
            val method = site.fingerprint.method
            val instructions = method.implementation!!.instructions.toList()
            var offset = 0
            val index = instructions.indexOfFirst { instruction ->
                val here = offset
                offset += instruction.codeUnits
                here == site.callCodeUnit
            }
            check(index >= 0 && index + 1 < instructions.size) { "Audio policy call offset changed" }
            val call = instructions[index]
            check(call.opcode == Opcode.INVOKE_STATIC) { "Audio policy invocation changed" }
            val reference = (call as? ReferenceInstruction)?.reference as? MethodReference
            check(reference != null && reference.definingClass == "Lr8/hg6;" && reference.name == "p" &&
                reference.parameterTypes.map { it.toString() } == listOf("Landroid/content/Context;") &&
                reference.returnType == "Z") { "Different audio policy helper" }
            val result = instructions[index + 1]
            check(result.opcode == Opcode.MOVE_RESULT &&
                (result as OneRegisterInstruction).registerA == site.resultRegister) { "Audio policy result changed" }
            Triple(method, index + 1, site.resultRegister)
        }
        // const/16 accepts result registers above v15 (prefetch uses v20).
        // Leave the original helper and every non-audio caller untouched.
        changes.forEach { (method, index, register) ->
            method.replaceInstruction(index, BuilderInstruction21s(Opcode.CONST_16, register, 0))
        }
    }
}
