# Translation reasoning choice

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
request. Alpha.11 build and Android behavior remain unverified. Dev.5 passed its
[hosted controls and Android cross-build](companion-022-dev5-verification.json),
including single-choice catalog fixtures. No live translation or phone UI has
been tested under the current model/device holds.

The extra choice is per request in this candidate; it does not persist a
preference or change the normal Gboard translator. Before release, verify the
actual language/effort dialogs, account/model availability, cancellation,
selection changes, preview/insertion and stock keyboard behavior on an
authorized device. The separate lost-start-response issue remains open.
