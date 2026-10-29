# Translation reasoning choice

The alpha.6 source asks the user to choose **Medium** or **Max** after choosing
the target language. It sends that exact selection with `gpt-6.1-sol` through
the existing local Codex companion; it does not substitute another effort or
model. Voice dictation does not use the reasoning choice. The editor anchor is
checked again after both choices and before the result can be inserted.

Companion dev.5 source accepts either approved choice when the installed CLI
model catalog advertises it; it no longer disables both merely because one is
missing. The current chooser displays both, so selecting an unavailable one is
rejected by the companion rather than silently replaced. A catalog listing does
not establish account entitlement or a successful request. Dev.5 passed its
[hosted controls and Android cross-build](companion-022-dev5-verification.json),
including single-choice catalog fixtures. No live translation or phone UI has
been tested under the current model/device holds.

The extra choice is per request in this candidate; it does not persist a
preference or change the normal Gboard translator. Before release, verify the
actual language/effort dialogs, account/model availability, cancellation,
selection changes, preview/insertion and stock keyboard behavior on an
authorized device. The separate lost-start-response issue remains open.
