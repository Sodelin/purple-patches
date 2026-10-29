# Translation reasoning choice

The alpha.6 source asks the user to choose **Medium** or **Max** after choosing
the target language. It sends that exact selection with `gpt-6.1-sol` through
the existing local Codex companion; it does not substitute another effort or
model. Voice dictation does not use the reasoning choice. The editor anchor is
checked again after both choices and before the result can be inserted.

The existing companion still requires its installed CLI model catalog to
advertise both Medium and Max before enabling Sol. A catalog listing does not
establish account entitlement or a successful request. A missing option is not
silently replaced. No live translation or phone UI has been tested under the
current model/device holds.

The extra choice is per request in this candidate; it does not persist a
preference or change the normal Gboard translator. Before release, verify the
actual language/effort dialogs, account/model availability, cancellation,
selection changes, preview/insertion and stock keyboard behavior on an
authorized device. The separate lost-start-response issue remains open.
