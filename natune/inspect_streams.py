"""Map exact public Natune stream types without executing any APK class.

Exports type/method identifiers and code fingerprints only, never arbitrary
embedded strings, app state or installer paths. Reuses the reviewed DEX reader.
"""
import hashlib
import json
from pathlib import Path
import re
import sys
import zipfile
from inspect_dex import Dex, instructions, require

SPEC = 'Lr8/km2;'  # Exact DataSpec descriptor in the admitted APK.


def inspect(apk):
    pin = json.loads(Path(__file__).with_name('target.json').read_text())
    with apk.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    require(digest == pin['sha256'] and apk.stat().st_size == pin['asset_bytes'])
    classes, signatures, definitions = {}, set(), []
    with zipfile.ZipFile(apk) as archive:
        for item in archive.infolist():
            if not re.fullmatch(r'classes(?:[2-9]|[1-9][0-9]+)?\.dex', item.filename):
                continue
            dex = Dex(archive.read(item))
            signatures.update(dex.methods)
            for pos in dex.table(96, 32):
                name = dex.type(dex.u32(pos))
                parent = dex.u32(pos + 8)
                interfaces = dex.u32(pos + 12)
                implemented = []
                if interfaces:
                    count = dex.u32(interfaces)
                    dex.bounds(interfaces + 4, count * 2)
                    implemented = [dex.type(dex.u16(interfaces + 4 + i * 2)) for i in range(count)]
                require(name not in classes)
                classes[name] = {'flags': dex.u32(pos + 4), 'superclass': None if parent == 0xffffffff else dex.type(parent),
                                 'interfaces': implemented}
            for index, flags, registers, code in dex.definitions():
                calls = []
                for offset, opcode, width, ref in instructions(code):
                    if ref and ref[0] == 'method':
                        require(ref[1] < len(dex.methods))
                        calls.append({'offset': offset, 'opcode': opcode, 'target': dex.methods[ref[1]]})
                definitions.append({'signature': dex.methods[index], 'flags': flags, 'registers': registers,
                                    'code_units': len(code) // 2, 'code_sha256': hashlib.sha256(code).hexdigest(), 'calls': calls})
    opened = {x.split('->')[0] for x in signatures if x.endswith('(' + SPEC + ')J')}
    readers = {x.split('->')[0] for x in signatures if x.endswith('([BII)I')}
    def inherits(owner, targets, seen=None):
        if owner in targets:
            return True
        seen = set() if seen is None else seen
        if owner in seen:
            return False
        seen.add(owner)
        row = classes.get(owner, {})
        return any(inherits(parent, targets, seen) for parent in
                   row.get('interfaces', []) + ([row['superclass']] if row.get('superclass') else []))
    # DataSource inherits read() from DataReader. Requiring both declarations
    # on the same owner would miss the actual abstract DataSource interface.
    sources = {x for x in classes if inherits(x, opened) and inherits(x, readers)}
    factories = {x for x in signatures if any(x.endswith('()' + source) for source in sources)}
    stream_methods = {x for x in signatures if x.split('->')[0] in sources}
    selected = []
    for row in definitions:
        own = row['signature'].split('->')[0]
        if own in sources or row['signature'] in factories:
            selected.append(row)
        elif own.startswith('Lmoe/rukamori/archivetune/playback/'):
            calls = [x for x in row['calls'] if x['target'] in stream_methods or x['target'] in factories]
            if calls:
                selected.append({**row, 'calls': calls})
    related = sources | {x['signature'].split('->')[0] for x in selected}
    return {'apk_sha256': digest, 'data_spec': SPEC, 'source_types': sorted(sources),
            'source_interfaces': sorted(x for x in sources if classes.get(x, {}).get('flags', 0) & 0x200),
            'source_method_signatures': sorted(stream_methods),
            'factory_methods': sorted(factories), 'classes': {x: classes[x] for x in sorted(related) if x in classes},
            'definitions': selected, 'apk_executed': False, 'application_state_read': False}


if __name__ == '__main__':
    print(json.dumps(inspect(Path(sys.argv[1])), indent=2))
