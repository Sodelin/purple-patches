package dev.purple.patches.gboard

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.shared.ANDROID_NS
import dev.jason.gboardpatches.patches.gboard.shared.childElements
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.accesspoint.gboardAccessPointContributions1803Patch
import dev.jason.gboardpatches.patches.gboard.features.editingaccesspoints.gboardEditingAccessPointsLifecyclePatch
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import org.w3c.dom.Element
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardTargetAdmission

@Suppress("unused")
val purpleGboardActionsPatch = GboardTargetAdmission.admittedContributions("purple_gboard_ai").let { admitted ->
resourcePatch(
    name = "Whisper 3 dictation and AI Translate".takeIf { admitted.isNotEmpty() },
    description = "Adds optional Whisper 3 and gpt-6.1-sol translation actions through your authenticated local tools. Preserves stock Gboard actions. Requires the memory-only companion runtime.",
    default = false,
) {
    if (admitted.isNotEmpty()) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch, gboardAccessPointContributions1803Patch,
        gboardEditingAccessPointsLifecyclePatch)
    finalize {
        var networkResource = ""
        var baseCleartext = ""
        var networkPlan: PurpleNetworkResource.Plan? = null
        document("AndroidManifest.xml").use { doc ->
            val root = doc.documentElement
            val app = root.childElements("application").single()
            fun permission(name: String) {
                if (root.childElements("uses-permission").none { it.getAttributeNS(ANDROID_NS,"name") == name }) {
                    val node = doc.createElement("uses-permission")
                    node.setAttributeNS(ANDROID_NS,"android:name",name)
                    root.insertBefore(node,app)
                }
            }
            permission("android.permission.INTERNET")
            permission("android.permission.RECORD_AUDIO")
            permission("com.termux.permission.RUN_COMMAND")
            fun component(type: String, name: String) {
                if (app.childElements(type).none { it.getAttributeNS(ANDROID_NS,"name") == name }) {
                    val node=doc.createElement(type)
                    node.setAttributeNS(ANDROID_NS,"android:name",name)
                    node.setAttributeNS(ANDROID_NS,"android:exported","false")
                    if (type=="activity") node.setAttributeNS(ANDROID_NS,"android:excludeFromRecents","true")
                    app.appendChild(node)
                }
            }
            component("receiver","dev.purple.patches.gboard.PurpleBootstrapReceiver")
            component("activity","dev.purple.patches.gboard.PurplePermissionActivity")
            if (app.childElements("meta-data").none { it.getAttributeNS(ANDROID_NS,"name") == "dev.purple.patches.gboard.enabled" }) {
                val marker=doc.createElement("meta-data")
                marker.setAttributeNS(ANDROID_NS,"android:name","dev.purple.patches.gboard.enabled")
                marker.setAttributeNS(ANDROID_NS,"android:value","true")
                app.appendChild(marker)
            }
            val existingNetworkResource=app.getAttributeNS(ANDROID_NS,"networkSecurityConfig")
            baseCleartext=app.getAttributeNS(ANDROID_NS,"usesCleartextTraffic")
            networkPlan=PurpleNetworkResource.select(existingNetworkResource, get("res"))
            networkResource=networkPlan!!.resource
            if (networkPlan!!.create) {
                // Validate before changing the manifest; the platform decides absent defaults.
                PurpleNetworkResource.initialXml(baseCleartext)
                app.setAttributeNS(ANDROID_NS,"android:networkSecurityConfig",networkResource)
            }
        }
        check(networkResource.matches(Regex("@xml/[A-Za-z0-9_]+"))) { "Unsupported network resource" }
        // Preserve stock trust anchors, domain policies and certificate pins.
        PurpleNetworkResource.prepare(get("res"), networkPlan!!, baseCleartext)
        val resourceName=networkResource.removePrefix("@xml/")
        for (path in PurpleNetworkResource.variants(get("res"), resourceName)) {
            document(path).use { doc -> PurpleNetworkResource.allowLoopback(doc.documentElement) }
        }
    }
}

}
}
