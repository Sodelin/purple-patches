# Runtime settings extension source increment

`RuntimeSettings.java` and `PurpleSettingsActivity.java` implement native Android
configuration/accessor/page source for the existing two Natune policy features.
They read/write only the owned patch preference file; reset touches no music or
original app settings. Disabling the mobile policy is designed to return the
actual supplied stock result, rather than reconstructing network status.

Patch-presence marker methods intentionally return false until actual selected
Morphe hooks bind them. These are source hook sites, not claims that features
are installed. Application bootstrap, exact original-result wrapper, FLAC guard
switches, marker rewriting, manifest/native settings navigation and independent
APK/resource comparisons remain required. The original application entry now
has actual release inspection: App.onCreate22registers/813code units/4try blocks.
Any added prefix must preserve those original handler boundaries, not loosen
zero-try guard checks. No app bootstrap or UI has been injected by this source.

`materialize.py --runtime-settings` selects a new development profile and copies
only these Android Java sources into the official extension module. It excludes
an otherwise unnecessary second Kotlin standard library. This profile has not
yet been built or adopted; current selected feeds and existing patch behavior
are unchanged. No phone install/UI/permission/model action accompanies it.

The separately implemented VerifiedStreamCopy and SharedDocumentDestination
provide bounded streaming, full destination readback and the real Android SAF
stream boundary for future Move to folder integration. Hosted run40886068466
passed49 core controls and real SDK compilation. They are not an installed UI,
a Media3 source lease, a verified provider grant or a cache-deletion authority.
See ../SHARED-STORAGE.md and ../shared-storage-verification.json for the exact
remaining binding and acceptance gates. These classes add no model/network
client, listener, grant, runtime record or source deletion path.
