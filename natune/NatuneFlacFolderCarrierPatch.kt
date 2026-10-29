package dev.purple.patches.natune

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.rawResourcePatch

private object NatuneFlacCarrierResources
private val natuneFlacCarrierResources = rawResourcePatch {
    execute {
        val entries = listApkEntries()
        val additions = mapOf(
            "lib/arm64-v8a/libpurple_natune_flac.so" to "/purple/natune/libpurple_natune_flac.so",
            "assets/purple/natune/FLAC-LICENSE.txt" to "/purple/natune/FLAC-LICENSE.txt",
        )
        check(entries.any { it.startsWith("lib/arm64-v8a/") }) { "Original ARM64 libraries unavailable" }
        check(additions.keys.none { it in entries }) { "Original APK already has a carrier path" }
        additions.forEach { (destination, resource) ->
            val input = NatuneFlacCarrierResources::class.java.getResourceAsStream(resource)
                ?: error("Pinned FLAC carrier resource unavailable")
            input.use {
                val output = get(destination, false)
                check(output.parentFile.isDirectory || output.parentFile.mkdirs()) { "Carrier directory unavailable" }
                output.outputStream().use { stream -> it.copyTo(stream) }
            }
        }
    }
}

/** Build-only carrier. It does not add a user action or bind the journal. */
@Suppress("unused")
val natuneFlacFolderCarrierPatch = bytecodePatch(
    name = "NaTune FLAC folder carrier (development)",
    description = "Experimental native FLAC and folder-copy source carrier. No Move action or app-data handoff is included.",
    default = false,
) {
    compatibleWith(Compatibility(
        name = "Natune", packageName = "moe.rukamori.archivetune", apkFileType = ApkFileType.APK,
        signatures = setOf("6db8326a550861f0f96b9adcb706207d75ac4480de454fb8af95daa5d6c7f9d9"),
        targets = listOf(AppTarget(version = "N202610191717", versionCode = 6900, isExperimental = true)),
    ))
    dependsOn(natuneFlacCarrierResources)
    extendWith("extensions/extension.mpe")
}
