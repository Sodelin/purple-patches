"""Read-only bounded policy scan of the exact observed public Natune 6928 APK."""
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path
from inspect_dex import Dex

EXPECTED = 'b6e6c4fed044416cd8c8b232ebda6bdca8b6c2626637a400ffc19d8800026d60'
NETWORK_HELPER = 'Lr8/mg6;->v(Landroid/content/Context;Z)Z'
CALL = 'Lr8/mg6;->u(Landroid/content/Context;)Z'

def inspect(path):
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest != EXPECTED:
        raise ValueError('APK differs from observed installed target')
    report = {'apk_sha256': digest, 'dex': [], 'methods_with_code': 0,
              'network_helper': None, 'audio_callers': []}
    with zipfile.ZipFile(path) as archive:
        dexes = [e for e in archive.infolist() if re.fullmatch(r'classes(?:[2-9]|[1-9][0-9]+)?\.dex', e.filename)]
        if not dexes or len({e.filename for e in dexes}) != len(dexes) or sum(e.file_size for e in dexes) > 150_000_000:
            raise ValueError('Invalid bounded DEX closure')
        for entry in dexes:
            dex = Dex(archive.read(entry))
            count, candidates = dex.inspect()
            report['dex'].append(entry.filename); report['methods_with_code'] += count
            for candidate in candidates:
                if candidate['signature'] == NETWORK_HELPER and candidate['network_pair']:
                    report['network_helper'] = {'dex': entry.filename, 'code_sha256': candidate['code_sha256']}
            for caller in dex.references_to({CALL}):
                if caller['signature'].startswith('Lmoe/rukamori/archivetune/playback/'):
                    report['audio_callers'].append({'dex': entry.filename, **caller})
    if report['network_helper'] is None:
        raise ValueError('Expected network helper absent')
    return report

if __name__ == '__main__':
    print(json.dumps(inspect(Path(sys.argv[1])), indent=2))
