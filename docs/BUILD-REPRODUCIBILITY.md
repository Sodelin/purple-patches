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

The alpha.14 source change needs two independent hosted builds and a fresh
patched-APK inspection before any reproducibility or compatibility claim. Even
matching artifacts on one runner image would not pin every SDK, JDK, action or
transitive dependency, or prove cross-platform reproducibility. The existing
public alpha.1 feed, device/model/privacy holds and original artifacts are not
changed by this source work.
