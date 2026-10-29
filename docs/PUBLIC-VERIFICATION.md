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

The unique migration `parent-routing-and-cache-return1` message was consumed
during this PATCHES turn. This proves inbound delivery of that message through
the reported CLI route, not universal delivery or old-queue replay. Existing
source, control, device and model holds remain unchanged.
