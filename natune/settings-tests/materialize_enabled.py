"""Fixture-only projection of the two independently verified DEX markers."""
import os
from pathlib import Path
import sys

if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_OS') != 'Linux':
    raise RuntimeError('Use only the disposable hosted runner')
runner = Path(os.environ['RUNNER_TEMP']).resolve()
target = Path(sys.argv[1]).resolve()
if not target.is_relative_to(runner) or target == runner or target.name != 'RuntimeSettings.java':
    raise RuntimeError('Expected a settings source inside runner temporary storage')
source = Path(__file__).resolve().parents[1] / 'runtime/dev/purple/patches/natune/RuntimeSettings.java'
text = source.read_text()
for name in ('mobilePatchPresent', 'flacPatchPresent'):
    old = f'public static boolean {name}() {{ return false; }}'
    if text.count(old) != 1:
        raise RuntimeError('Selected feature marker changed')
    text = text.replace(old, f'public static boolean {name}() {{ return true; }}')
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(text)
