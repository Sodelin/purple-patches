# Gboard build reproducibility

Two successful hosted builds of exact source `bc81100e7851c7cc0ca345e8189d16916a6bb3b0`
produced different alpha.13 main MPP hashes. The complete comparison is in
[gboard-alpha13-reproducibility.json](gboard-alpha13-reproducibility.json).
Their 661 main archive members had the same names, order and ZIP metadata; only
the `META-INF/MANIFEST.MF` content differed, and all 660 other compressed entry
streams matched. The changed field was the millisecond `Timestamp`.

The public [Morphe Gradle plugin v1.3.3 source](https://github.com/MorpheApp/morphe-patches-gradle-plugin/blob/52be641ed3b965a20c33bd43e0cbe9efd308bc64/src/main/kotlin/app/morphe/patches/gradle/PatchesPlugin.kt)
sets that manifest field from the current clock. The build used version 1.3.3;
the fetched plugin artifact has not independently been matched to that source.
The existing [Gradle wrapper properties](https://github.com/jasonwu1994/Gboard-patches/blob/2eb71854accc7e906d3e4f5495b2a54ed1e2af47/gradle/wrapper/gradle-wrapper.properties)
pin the Gradle distribution hash. Gradle documents reproducible archive order
and entry timestamps separately from volatile file content; see its
[archive documentation](https://docs.gradle.org/current/userguide/working_with_files.html#sec:reproducible_archives).

Alpha.14 source selects the source commit time in milliseconds for the MPP
manifest instead of the build clock. It also snapshots the exact committed
overlay, version, build helper and workflow inputs before applying the overlay,
and refuses uncommitted, ignored extra or linked overlay files. The verified
overlay bytes are used for the build and recorded in provenance. This is a
cooperative source-integrity check, not protection against a hostile process
that replaces already-loaded build code or modifies the runner. The new
timestamp's intended meaning is **source commit time**, not build completion
time; Manager's separate feed `created_at` remains commit-derived as before.

Two hosted alpha.14 builds of exact source `81f042630155be28730f6c0f08c9912d6b8c6b33`
produced byte-identical main, source and documentation MPPs and identical
contents for every artifact member. The GitHub download wrappers differed in
entry order and timestamps, while their compressed member streams matched.
Both runs used the same observed runner image and JDK; this is a scoped
repeatability result, not proof of future or cross-platform reproducibility.
The separate [patched-APK static inspection](gboard-alpha14-apk-verification.json)
passed; it does not establish phone behavior. Full comparison and controls are
in [gboard-alpha14-reproducibility.json](gboard-alpha14-reproducibility.json).

To repeat the read-only artifact comparison from a checkout with the exact
source commit and GitHub access, run:

```sh
python tools/compare_gboard_builds.py --first-run 40948413452 --second-run 40948916651 \
  --source-commit 81f042630155be28730f6c0f08c9912d6b8c6b33 --version 0.1.0-alpha.14
```

The command verifies run identities, checksums, exact committed overlay and
build-input hashes, timestamp and feed metadata before comparing bytes. It reads
artifacts in memory and does not publish them. This command uses GitHub's
temporary artifacts; both ZIPs have also been privately archived as recorded
in [GBOARD-ALPHA14-CUSTODY.md](GBOARD-ALPHA14-CUSTODY.md), but the command
does not yet read those Drive copies. Its in-memory fixture controls also
passed. The runner image, action references, SDK, JDK patch version and
transitive dependencies are not all independently pinned by this change;
future builds still need their own verification. The existing public alpha.1
feed, device/model/privacy holds and original artifacts remain unchanged.

The first alpha.14 build attempt, run `40948310599` from source `e0edeeb`,
failed its synthetic timestamp test because Git rendered UTC as `Z` rather
than `+00:00`. Its source-integrity refusal fixture passed; no bundle was
built. The corrected test compares parsed instants and keeps this failure as
history rather than counting it as a successful build.
