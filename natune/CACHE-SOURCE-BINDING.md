# Exact native cache-source binding

Target remains N202610191717/code6900 and the original signer/digest in
target.json. NatuneCachedSource.java is source for that target's Android
extension. It is not yet called by a selected Morphe UI hook or installed app.
No model, cache removal, export job or phone trial accompanies this increment.

## Observed ABI and original ownership

Successful public-APK inspection runs40887293242 and40888097008 are retained
in storage-hook-inspection.json, including original bytecode and full artifact
hashes. The pinned original AppModule.kt:60–151 provides the corresponding
LazyCache source and lock order. Gradle selects Media3 1.10.1.

| Actual APK member | Source role |
| --- | --- |
| r8.dd6.b:Object | Original LazyCache lock, public final in the optimized APK |
| r8.dd6.c:r8.c7b | Original initialized delegate, public volatile |
| r8.dd6.p(String):TreeSet | Get a copy of cached spans under wrapper lock, then delegate monitor |
| r8.dd6.d(String):r8.vu2 | Content metadata under the same owner |
| r8.dd6.q():r8.c7b | Creates the cache when uninitialized; deliberately not called by the reader |
| r8.c7b | Actual SimpleCache implementation, with explicit monitor-enter/exit and handlers |
| r8.d7b.f:String, s:long, z:long | Original cache key, position and span length |
| r8.d7b.A:boolean, B:File | Cached status and committed file |
| r8.d7b.C:long | Last-touch time, not used as content length |
| r8.vu2.b(long,String):long | Original metadata long getter, including exo_len |
| playback/g.z:r8.ae3 | Original download manager, as observed by the delete coroutine |
| r8.ae3.c(String):void | Sends Handler message8 and returns; not a removal-completion acknowledgement |

CacheSpan's actual constructor assigns position and length from distinct long
parameters. ContentMetadata's actual getter decodes the stored bytes with
ByteBuffer.getLong. The upstream1.10.1 ContentMetadata.KEY_CONTENT_LENGTH is
exo_len; this bridge reads no other metadata key, URL, header or account value.

## Implemented reader

withSource captures the actual original wrapper lock and initialized delegate,
then synchronizes in their original order. It does not synchronize on the wrapper
object as a substitute. It checks exact class/member types and uses public Java
reflection, without setAccessible, package-private access or class construction.
The selecting Morphe patch must enforce the target APK's original compatibility
pins; matching member names alone is not target/signature admission.

The snapshot requires a positive original content length, nonempty cached
coverage from byte0 to that exact end, one selected source key, positive segment
lengths and matching regular file sizes. Gaps, overlaps, unknown length, holes
and extra bytes refuse before the destination is opened. The stream reads the
committed span files in order, keeping one FileInputStream open at a time.

The original wrapper/delegate, span object identities, file lengths and modified
times are rechecked. Reads require the same thread and both original monitors.
The lease closes its stream and invalidates itself when its synchronous callback
returns or fails. An escaped lease/stream cannot later reopen or read on another
thread. VerifiedStreamCopy then performs complete destination readback and hash
verification; this reader has no source-removal method.

These monitors exclude the original cache operations that take those locks.
They are not a process sandbox, an inode-level immutable filesystem snapshot or
proof about arbitrary same-process/same-UID writers. File length/time is not a
cryptographic file-identity proof. The byte verification and actual known cache
lock ownership must remain separate from any stronger all-writer privacy claim.

## Remaining move integration

Holding the cache owner during disk/provider IO can delay another cache caller.
Run on the owned background path, reject moving an active track, and measure
playback/UI effects during a separately authorized device window. Do not claim
bounded blocking-provider IO just because cancellation checks are cooperative.
An optimistic descriptor-backed lease may be needed if measured latency affects
playback; it needs its own original immutable-span/custody proof.

Before removal, bind original DownloadManager request identity and settlement,
index the completed content URI against the original song/rendition, retain the
required encrypted claim/result/recovery state and recheck current readers and
owners. Calling ae3.c is not enough to settle a request; it enqueues work.
Do not hold a cache monitor while waiting for a manager callback that needs the
same monitor. UI insertion, transaction/restart recovery and deletion remain
unbound. Test fixture classes under storage-tests/fixtures must never be copied
into an extension or APK.

## Controlled verification scope

The hosted shared-storage workflow compiles the actual reader and tests public
synthetic span files plus explicit r8 ABI stand-ins. It checks complete streaming,
refused incomplete/changed inputs, original Java monitor lifetime, another
thread's refusal and lease/stream escape. These are logic/locking controls,
not tests on the installed Cache, a private library, SAF provider or media file.
Retain the actual terminal run and report before claiming those controls pass.

Primary source:

- https://github.com/NatuneGroup/ArchiveTune/blob/b7e4dd444fea202a5c32e96f7b1199a74fe3ae70/app/src/main/kotlin/moe/rukamori/archivetune/di/AppModule.kt
- https://github.com/androidx/media/blob/1.10.1/libraries/datasource/src/main/java/androidx/media3/datasource/cache/ContentMetadata.java
