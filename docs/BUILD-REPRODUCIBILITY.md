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

The alpha.15 workflow selected the observed `ubuntu-24.04` label, exact Temurin
`21.0.13+9`, and the three action commit hashes observed in both alpha.14 builds.
Its hosted build `40951142999` succeeded, but its `ANDROID_HOME` platform
inspection predicted `android-37.2-beta3` because the pinned Morphe plugin picks
the lexicographically greatest available platform. The resulting artifact ZIP
SHA-256 was `9633f101e45ac3fc5888d8fb666e6d78226a58d74546729a2bf7626910495922`;
main MPP SHA-256 was `068e3a27cdc9cddd6b487a1abfe224e1c28be07bdcceba12351756f24d68e1f5`.
The inspected prediction did not instrument the plugin's actual selection.

The alpha.16 workflow uses a temporary hosted SDK view containing only the
already-installed Android 36 platform, records its `android.jar` hash and
checks that the view and bytes remain unchanged after the build. It also adds
the source-integrity test to automatic push triggers. The two
[alpha.16 builds](gboard-alpha16-reproducibility.json) of source
`907a886adb8b2ff7bc8c3d8df86aef92c7134baa` produced identical bytes for all
seven artifact members, including the main, source and documentation MPPs.
Their outer GitHub ZIP wrappers differed. Both runs reported the same SDK jar
hash and Temurin `21.0.13+9`; plugin selection is inferred from its reviewed
source and the one-platform view rather than measured inside the plugin. The
separate [exact-APK inspection](gboard-alpha16-apk-verification.json) passed.

Android's [SDK platform release notes](https://developer.android.com/tools/releases/platforms)
identify API 36 as stable. Google's
[repository metadata](https://dl.google.com/android/repository/repository2-1.xml),
read 2026-10-29, lists multiple `platforms;android-36` packages, including
`platform-36_r02.zip` and extension variants. The observed hosted jar has not
been independently matched to any official archive. The moving runner image,
SDK bytes and transitive dependencies are not all pinned; these two runs are
not a promise of reproducibility on a future image. The private copies are
documented in [GBOARD-ALPHA16-CUSTODY.md](GBOARD-ALPHA16-CUSTODY.md). The
workflow does not publish a release or change the phone.

Two alpha.17 builds of source `17f325c481f7f84550013924496ff088d99f2ad2`
also matched all seven artifact member bytes under the same observed SDK jar
and JDK; their outer ZIP wrappers differed. See the
[alpha.17 comparison](gboard-alpha17-reproducibility.json) and
[private custody](GBOARD-ALPHA17-CUSTODY.md). This repeats the scoped check for
the result-preservation source change and does not close the same future-image,
SDK-provenance or device-acceptance gaps.

Two alpha.18 builds of source `a5c0cb15edf7d1d18592c079c2c16e4d2d66daeb`
also matched all seven artifact member bytes under the same observed SDK jar
and JDK; their outer ZIP wrappers differed. See the
[alpha.18 comparison](gboard-alpha18-reproducibility.json) and
[private custody](GBOARD-ALPHA18-CUSTODY.md). This does not resolve future-runner,
SDK-provenance or Android acceptance gaps.
