# Gboard alpha.10 and companion dev.6 custody

The verified [alpha.10 bundle](gboard-alpha10-build-verification.json),
[patched unsigned APK](gboard-alpha10-apk-verification.json) and
[dev.6 companion](companion-022-dev6-verification.json) are hosted workflow
artifacts, not installation recommendations. All were read and hashed in
memory against their recorded checksums and source. On 29 October their hosted
expiration times were, respectively, 2026-11-05 19:18:44 UTC, 19:20:24 UTC,
and 19:09:13 UTC. These are provider metadata, not permanent custody.

An existing private Drive folder was found and its unshared metadata was
rechecked. No alpha.10 or dev.6 archive has yet been uploaded there. The phone
was below the user's 20 GiB engineering reserve. A small public fixture showed
that the available libc can create a memory-backed file, but another same-UID
process was denied access through `/proc/<pid>/fd`; the Python build does not
expose `os.memfd_create` and `/dev/shm` is absent. This does not prove every
streamed upload route impossible. No phone archive or user original was created
or removed for this check. The bounded next custody step is a verified streaming
upload through the existing private Drive route, or an authorized off-device
restore-and-upload route, followed by full remote byte/hash readback; retain
the source and exact recorded hashes while that is unresolved.

The public source commits and reproducible hosted workflow remain available.
An artifact, backup or static inspection does not establish the bearer-privacy,
caller authority, editor, model or actual phone acceptance gates. See
[ACCEPTANCE.md](ACCEPTANCE.md).
