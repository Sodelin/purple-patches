"""Export only the hash-verified selected MPP; never install/import/activate it."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
from release_checks import require

ROOT = Path(__file__).resolve().parents[1]
REPO = 'Sodelin/purple-patches'
NAME = 'patches-0.1.0-alpha.1.mpp'
PIN = 'af2077907783247f667940e02659fe8b574ec36fd26df8af67e22469de3f11fe'


def export(folder):
    release = json.loads(subprocess.check_output(['gh', 'api', f'repos/{REPO}/releases/425693294']))
    asset = next(a for a in release['assets'] if a['name'] == NAME)
    body = subprocess.check_output(['gh', 'api', f"repos/{REPO}/releases/assets/{asset['id']}",
                                    '-H', 'Accept: application/octet-stream'])
    require(len(body) == 8205100 and hashlib.sha256(body).hexdigest() == PIN, 'Selected bundle bytes differ')
    def free():
        v = os.statvfs(folder.parent)
        return v.f_bavail * v.f_frsize
    before = free()
    folder.mkdir(exist_ok=True)
    for name, data in [(NAME, body), ('SHA256SUMS', (PIN + '  ' + NAME + '\n').encode())]:
        target = folder / name
        if target.exists():
            require(target.read_bytes() == data, 'Refuse differing existing deliverable')
        else:
            # Exclusive creation preserves user files. A failed write is retained
            # for explicit inspection and is never silently accepted or replaced.
            with target.open('xb') as stream:
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
        require(target.read_bytes() == data, 'Local readback mismatch')
    return {'file': str(folder / NAME), 'bytes': len(body), 'sha256': PIN,
            'source_private_release_id': release['id'], 'full_local_readback_verified': True,
            'free_before_bytes': before, 'free_after_bytes': free(),
            'selected_file_allocation_bytes': (folder / NAME).stat().st_blocks * 512,
            'limits': 'Selected MPP only. No APK installation, Manager import, default keyboard switch, service start, model download or inference.'}


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(export(args.destination), indent=2))
