# Public repository status

On 2026-10-29 Nolan explicitly asked to make `Sodelin/purple-patches` public.
The repository visibility was changed through GitHub's supported repository
edit operation and independently read back through an unauthenticated request
to `https://api.github.com/repos/Sodelin/purple-patches` (`private=false`).

Before the change, all 140 locally reachable distinct Git blobs (676639 bytes)
were scanned for known credential/private-key patterns and sensitive record
filenames. Thirteen published release assets were read and checked, including
1000 source-archive members. No matching credentials or private-record files
were found. This is a bounded review, not proof against every possible secret
format. The draft mixed candidate remains a draft; it was not promoted.
Exact published asset lengths/hashes are in `public-readiness-review.json`.

Historical private-custody and authenticated-source records remain dated
evidence; they do not describe current repository visibility. The public
candidate feed URL is unchanged:
`https://raw.githubusercontent.com/Sodelin/purple-patches/main/channels/candidate.json`.
Public availability does not imply actual phone Manager import/update, live
Whisper large-v3 acceptance, Gboard device regression or stable-release quality.
Existing model/privacy/installation holds persist. No app setting, token,
model asset, source-owner scope or phone runtime was changed.

The current development OAuth credential still cannot create/update GitHub
workflow files. Natune's new inspection workflow/source are retained locally;
the attempted push was refused and no inspection run was started. Making the
repository public does not grant missing workflow permissions. This is distinct
from Morphe's optional download token; neither credential was broadened.
