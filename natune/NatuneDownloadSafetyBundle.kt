package dev.purple.patches.natune

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

/** Selects the reviewed candidates in dependency order; no independent mutation. */
@Suppress("unused")
val natuneDownloadSafetyBundle = bytecodePatch(
    name = "Natune audio and download safeguards",
    description = "Combines audio controls, cache coverage, local Tidal file routing and failure-cache preservation.",
    default = false,
) {
    compatibleWith(Compatibility(name = "Natune", packageName = "moe.rukamori.archivetune",
        apkFileType = ApkFileType.APK,
        signatures = setOf("6db8326a550861f0f96b9adcb706207d75ac4480de454fb8af95daa5d6c7f9d9"),
        targets = listOf(AppTarget("N202611030452", 6928, isExperimental = true))))
    dependsOn(natuneCombinedCacheCoveragePatch, natuneTidalLocalFilePatch, natuneFailureCachePreservationPatch)
    execute { }
}
