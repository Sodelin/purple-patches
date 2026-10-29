"""Create public synthetic PCM inputs on the hosted runner only."""
import os
from pathlib import Path
import sys
import wave

if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_OS") != "Linux":
    raise SystemExit("Hosted synthetic fixtures only")
root = Path(sys.argv[1]).resolve()
runner = Path(os.environ["RUNNER_TEMP"]).resolve()
if not root.is_relative_to(runner) or root == runner:
    raise SystemExit("Expected a runner-temporary fixture directory")
root.mkdir(parents=True, exist_ok=False)
for bits in (8, 16, 24, 32):
    width = bits // 8
    mask = (1 << bits) - 1
    pcm = bytearray()
    for frame in range(8000):
        for channel in range(2):
            sample = ((frame * 104729 + channel * 7919) & mask) - (1 << (bits - 1))
            if bits == 8:
                pcm.append(sample + 128)  # WAV 8-bit PCM is unsigned.
            else:
                pcm.extend(sample.to_bytes(width, "little", signed=True))
    with wave.open(str(root / f"fixture-{bits}.wav"), "wb") as output:
        output.setnchannels(2)
        output.setsampwidth(width)
        output.setframerate(8000)
        output.writeframes(pcm)
