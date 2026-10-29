# Translation reasoning choice

Alpha.18 source also preserves language script and region in the target picker;
see [translation language choices](TRANSLATION-LANGUAGE-CHOICE.md). This does
not change the selected Sol model or the explicit Medium/Max effort boundary.

The alpha.6 source asks the user to choose **Medium** or **Max** after choosing
the target language. It sends that exact selection with `gpt-6.1-sol` through
the existing local Codex companion; it does not substitute another effort or
model. Voice dictation does not use the reasoning choice. The editor anchor is
checked again after both choices and before the result can be inserted.

Companion dev.5 source accepts either approved choice when the installed CLI
model catalog advertises it; it no longer disables both merely because one is
missing. Through alpha.10 the chooser displays both, so selecting an unavailable
one is rejected by the companion rather than silently replaced. Alpha.11 source
requests the local catalog on a background thread and shows only the explicitly
permitted Medium/Max levels actually advertised for the single selected Sol
model. An unavailable or ambiguous catalog refuses before a model request; the
user still chooses an effort explicitly. The response is bound to the current
keyboard operation and editor anchor, and Cancel disconnects the outstanding
read. A canceled metadata read may already have reached the local service.
A successful bridge health result must also advertise protocol `1` and the
selected data-path marker before the catalog is requested. The reviewed
[Android JSON parser source](https://android.googlesource.com/platform/libcore/+/60eb73fc9f0636c3d0bda5baba641a5f88f8476f/json/src/main/java/org/json/JSONTokener.java)
parses an in-range integral JSON number as an `Integer`, as used by this check;
the phone's actual parser has not been tested. A health marker is not proof of
privacy or an account entitlement.
A catalog listing does not establish account entitlement or a successful
request. The [alpha.11 hosted build and synthetic catalog controls](gboard-alpha11-build-verification.json)
passed, including the actual bridge adapter with controlled JSON stubs; the
matching [patched APK inspection](gboard-alpha11-apk-verification.json) found
the catalog/chooser references. The Android parser, UI and real account
availability remain unverified. Dev.5 passed its
[hosted controls and Android cross-build](companion-022-dev5-verification.json),
including single-choice catalog fixtures. No live translation or phone UI has
been tested under the current model/device holds.

The pinned upstream patch attaches its editor lifecycle hooks to
[`InputMethodService` lifecycle methods](https://github.com/jasonwu1994/Gboard-patches/blob/2eb71854accc7e906d3e4f5495b2a54ed1e2af47/patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/editingaccesspoints/GboardEditingAccessPointsTargets.kt#L9-L20).
The reviewed [AOSP input-method wrapper](https://android.googlesource.com/platform/frameworks/base/+/64eaba03c5db8b431aef8a88d40593631951fe99/core/java/android/inputmethodservice/IInputMethodWrapper.java)
dispatches its incoming input-method calls on the process main thread. This is
source context for the UI lifecycle design, not proof of every call path in the
installed keyboard or a substitute for device race and interruption tests.

The extra choice is per request in this candidate; it does not persist a
preference or change the normal Gboard translator. Before release, verify the
actual language/effort dialogs, account/model availability, cancellation,
selection changes, preview/insertion and stock keyboard behavior on an
authorized device. The separate lost-start-response issue remains open.
