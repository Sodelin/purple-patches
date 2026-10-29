# Sharing Purple Patches through Morphe

Research checked 2026-10-29 against Manager 2.2.0 source and Morphe's official
patch template. The repository `Sodelin/purple-patches` became public later that day at Nolan’s
explicit request. Anonymous feed and full-bundle access were verified. No app
credential was inspected or changed. The earlier private-source findings remain
applicable to private sources, not the current visibility of this repository.

Morphe accepts GitHub/GitLab repository sources, direct bundle JSON URLs, and
local `.mpp` files. Remote sources can update automatically; local bundles need
manual replacement. One repository can carry patches for multiple apps.

Public GitHub visibility is **not required for personal use**. Manager 2.2.0's
existing private-repository/raw-feed/release-asset support is documented in
`MORPHE-SOURCE.md` and the source-backed authentication review in the parent
workspace. Private sources require each recipient to have suitable read access.
A public source is more convenient for general sharing because recipients do
not need private-repository membership/credentials. Community-directory listing
is separate from installing a source and from Morphe endorsing our patches.

Recommended project name: **Purple Patches** (`purple-patches`). It already
matches this repository and does not imply authorship by Morphe. The template
explicitly asks third-party authors not to imply Morphe-project authorship.
Gboard and Natune remain identifiable components with their own compatibility
and acceptance status; do not present Natune as delivered while hooks are being
investigated or Gboard as phone-accepted while its holds remain.

Distribute authored patch source and versioned `.mpp` bundles plus the generated
patch list, compatibility metadata, changelog and bundle JSON. Users apply the
bundle to their original app locally. Publishing patched third-party APKs is
not required for this workflow. Preserve inherited source licenses/notices.
Tokens, keys, model weights, app preferences and private phone records are never
release content. A public launch needs a deliberate source/history/artifact
review and the user's selected visibility decision; do not expose the existing
private repository merely to make a patch easier to import.

For future general releases, use/adapt the official template's existing
semantic-release workflow rather than inventing another release publisher.
Its documented flow develops on dev, creates prereleases, then merges into
main for stable releases; generated lists/manifests/changelogs are maintained
by that workflow. Its suggested PR automation permission is template
development automation, not a permission needed by Morphe's download token.
The previously verified private Gboard candidate is historical and remains
distinct from adopting the official automated release flow for new components.

Primary references:

- https://github.com/MorpheApp/morphe-manager/blob/2c2bdc6a8368fa790ca2e3a57b050070a2c36eea/docs/patch-sources.md
- https://github.com/MorpheApp/morphe-manager/blob/2c2bdc6a8368fa790ca2e3a57b050070a2c36eea/app/src/main/java/app/morphe/manager/network/api/MorpheAPI.kt
- https://github.com/MorpheApp/morphe-patches-template/tree/e721536ab19e30d16ce9d31ae1830ca299aad383
- https://morphe-patches.software/
