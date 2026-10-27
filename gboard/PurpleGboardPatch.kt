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
val purpleGboardActionsPatch = GboardTargetAdmission.admittedContributions("editing_access_points").let { admitted ->
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
        var baseCleartext = "false"
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
            networkResource=app.getAttributeNS(ANDROID_NS,"networkSecurityConfig")
            baseCleartext=app.getAttributeNS(ANDROID_NS,"usesCleartextTraffic").ifBlank { "false" }
            check(baseCleartext in setOf("true","false")) { "Unsupported referenced cleartext policy; preserve original and port explicitly" }
            if (networkResource.isBlank()) {
                networkResource="@xml/purple_keyboard_network"
                app.setAttributeNS(ANDROID_NS,"android:networkSecurityConfig",networkResource)
            }
        }
        check(networkResource.matches(Regex("@xml/[A-Za-z0-9_]+"))) { "Unsupported network resource" }
        val path="res/xml/${networkResource.removePrefix("@xml/")}.xml"
        // Preserve stock trust anchors, domain policies and certificate pins.
        if (networkResource=="@xml/purple_keyboard_network") {
            get(path).apply { parentFile.mkdirs(); writeText("<network-security-config><base-config cleartextTrafficPermitted=\"$baseCleartext\"/></network-security-config>") }
        }
        document(path).use { doc ->
            val root=doc.documentElement
            check(root.tagName=="network-security-config")
            val existing=root.childElements("domain-config").firstOrNull { config ->
                config.childElements("domain").any { it.textContent=="127.0.0.1" }
            }
            val local=existing ?: doc.createElement("domain-config").also(root::appendChild)
            local.setAttribute("cleartextTrafficPermitted","true")
            if (existing==null) {
                val domain=doc.createElement("domain"); domain.textContent="127.0.0.1"
                domain.setAttribute("includeSubdomains","false"); local.appendChild(domain)
            }
        }
    }
}

}
}
