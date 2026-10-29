# Public repository verification — 29 October 2026

Nolan requested public visibility for `Sodelin/purple-patches`. The repository
is public. Anonymous requests verified the versioned candidate feed and the
complete published MPP (8,205,100 bytes; SHA256
`af2077907783247f667940e02659fe8b574ec36fd26df8af67e22469de3f11fe`).
`tools/verify_candidate_source.py --visibility public` passed without credentials.
This establishes publication and download access, not phone acceptance.

Natune diagnostic build 40863571367 at source 77cd8ea compiled and applied the
mobile-data patch, but failed independent preservation verification. Twelve
classes had field-initializer representation differences; diagnostics showed
explicit encoded Boolean/null values becoming absent. Semantic equivalence
has not yet been established for every difference, so the gate remains failed.
No candidate from that run was accepted or installed. Full FLAC enforcement
and additional providers remain unfinished.

Later Natune run40864921834 at9e3add7 PASSED the independent preservation gate,
including all27129classes/114583methods,24native libraries,12assets and1884
non-DEX/signature files. Source-backed static-default normalization and14
refusal/positive controls resolve the earlier representation mismatch without
ignoring changed field values. `natune/build-verification.json` retains the
selected build and failures. The experimental prerelease and separate Natune
feed were subsequently published and anonymously verified in
`natune/public-source-verification.json`. Phone acceptance remains unverified.

The unique migration `parent-routing-and-cache-return1` message was consumed
during this PATCHES turn. This proves inbound delivery of that message through
the reported CLI route, not universal delivery or old-queue replay. Existing
source, control, device and model holds remain unchanged.
