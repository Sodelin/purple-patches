# Android app to existing encrypted Move journal

The installed NaTune app and Termux run under distinct Android app identities.
`NatuneFolderCopy` needs the existing Termux-owned journal to retain each
original claim and outcome, but the app cannot be assumed to read Termux private
files or invoke its installed codec. The current folder-copy owner interface is
unbound: an in-process Java callback is not an authenticated bridge or durable
journal. No new key, listener, controller or plaintext journal is selected.

## Candidate existing platform route and concrete privacy issue

Termux documents an existing `RUN_COMMAND` intent for authorized external apps.
Its documented requirements include the Termux command permission and an
`allow-external-apps` setting. Neither availability nor these settings/permissions
have been checked on this phone; no setting, service, app or device action was
performed. The documented route can pass command input and receive a result,
but does not by itself establish a scoped NaTune journal caller or safe recovery.

Review of the complete public Termux source bodies at commit
`8629e632fcb95da272221be327db653fb24befe9` found that
`RunCommandService` can log the received intent with `logVerboseExtended`, and
`IntentUtils.getIntentString` includes the extra values. The same service reads
the stdin extra and its execution-input representation includes stdin. These
paths therefore can expose plaintext request data when the relevant logging is
enabled. Source inspection is not evidence of the installed version, enabled
log level, or an actual leak on the phone. Passing raw song identifiers, URIs,
claims or recovery records in intent extras, arguments or stdin is not selected
without establishing a compatible privacy boundary. The exact full-source hashes
and links are in `app-journal-route-source-review.json`.

The existing journal is Termux-owned and reuses the existing featurectl identity.
A real app handoff still needs an authenticated original caller/operation,
confidential input and result handling, atomic shared-budget admission,
encrypted-before-persistence events, failure/UNKNOWN recovery and a way to stop
the original attempt without replay. Public-key material, permissions and any
installed transport are not inferred from public source. An existing approved
bridge may satisfy these requirements if its actual owner and interface support
it; this review does not create or select one.

Android Bridge's existing-owner source response
`0341aaf20dfcab7cb4f0ae0f3be6bf4aefe15023:coordination/NATUNE-APP-JOURNAL-ROUTE-RETURN-20261029.md`
reports no qualifying selected receiver in the sources it checked. It also
identified the original-ID and MediaStore-destination representation gaps.
The successor source validates the journal's 64-lowercase-hex original ID before
reservation, and adds a schema 3 MediaStore selection with volume, normalized
folder, MIME and actual collection URI. See `MOVE-JOURNAL.md`; these source
changes still need hosted verification and a real authenticated owner binding.

Primary source: [Termux RUN_COMMAND documentation](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)

## Next integration boundary

Connect the actual app/UI selection to the selected-span lease and the real
journal owner, preserving the original operation ID and user stop. Only the
existing journal can authorize another admission after recovery; the Java
budget is its mirror. Verify the exact installed transport and privacy behavior
using a separately admitted, synthetic, non-media operation before phone use.
No local copy, remote transfer or source deletion is authorized merely by the
source or the simulated folder-copy controls.
