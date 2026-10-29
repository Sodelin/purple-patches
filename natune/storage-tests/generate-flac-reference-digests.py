"""Compute public synthetic PCM reference digests on the hosted runner only."""
import os
import hashlib
from pathlib import Path
import sys

if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_OS") != "Linux":
    raise SystemExit("Hosted synthetic fixtures only")
root = Path(sys.argv[1]).resolve()
runner = Path(os.environ["RUNNER_TEMP"]).resolve()
if not root.is_relative_to(runner) or root == runner:
    raise SystemExit("Expected a runner-temporary fixture directory")
root.mkdir(parents=True, exist_ok=False)
for bits in (4, 8, 12, 16, 20, 24, 31, 32):
    width = (bits + 7) // 8
    mask = (1 << bits) - 1
    pcm = bytearray()
    for frame in range(8000):
        for channel in range(2):
            sample = ((frame * 104729 + channel * 7919) & mask) - (1 << (bits - 1))
            pcm.extend(sample.to_bytes(width, "little", signed=True))
    # RFC 9639 hashes signed, interleaved, byte-aligned little-endian PCM.
    (root / f"fixture-{bits}.md5").write_text(hashlib.md5(pcm).hexdigest() + "\n")
