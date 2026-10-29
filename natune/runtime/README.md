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
