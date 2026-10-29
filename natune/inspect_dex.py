"""Read-only Natune DEX inspection; Python stdlib, no compiler or dependencies.

Only the SHA-pinned public APK is accepted. This is a bounded reference reader,
not a general DEX validator/disassembler. Never executes APK classes or exports
arbitrary embedded strings. Format references: source.android.com/docs/core/
runtime/{dex-format,dalvik-bytecode}. Hosted dexlib inspection stays independent.
"""
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zipfile
import zlib

sys.dont_write_bytecode = True
ANCHORS = (
    b'Low-data mode active; skipping Tidal/Qobuz for %s',
    b'No enabled download source resolved ',
    b'low_data_mode', b'lowDataMode', b'LowDataMode',
)


class InvalidDex(ValueError):
    pass


def require(ok):
    if not ok:
        raise InvalidDex('Unsupported or malformed DEX; no partial proof')


def uleb(data, offset):
    value = 0
    for shift in range(0, 35, 7):
        require(offset < len(data))
        byte = data[offset]
        offset += 1
        require(shift != 28 or byte <= 15)
        value |= (byte & 127) << shift
        if byte < 128:
            return value, offset
    raise InvalidDex('Invalid ULEB')


WIDTHS = {}
for low, high, width in (
    (0x00, 0x01, 1), (0x02, 0x02, 2), (0x03, 0x03, 3),
    (0x04, 0x04, 1), (0x05, 0x05, 2), (0x06, 0x06, 3),
    (0x07, 0x07, 1), (0x08, 0x08, 2), (0x09, 0x09, 3),
    (0x0a, 0x12, 1), (0x13, 0x13, 2), (0x14, 0x14, 3),
    (0x15, 0x16, 2), (0x17, 0x17, 3), (0x18, 0x18, 5),
    (0x19, 0x1a, 2), (0x1b, 0x1b, 3), (0x1c, 0x1c, 2),
    (0x1d, 0x1e, 1), (0x1f, 0x20, 2), (0x21, 0x21, 1),
    (0x22, 0x23, 2), (0x24, 0x26, 3), (0x27, 0x28, 1),
    (0x29, 0x29, 2), (0x2a, 0x2c, 3), (0x2d, 0x3d, 2),
    (0x44, 0x6d, 2), (0x6e, 0x72, 3), (0x74, 0x78, 3),
    (0x7b, 0x8f, 1), (0x90, 0xaf, 2), (0xb0, 0xcf, 1),
    (0xd0, 0xe2, 2), (0xfa, 0xfb, 4), (0xfc, 0xfd, 3),
    (0xfe, 0xff, 2),
):
    WIDTHS.update({op: width for op in range(low, high + 1)})


def instructions(data):
    require(len(data) % 2 == 0)
    units = memoryview(data).cast('H')
    require(sys.byteorder == 'little')
    cursor = 0
    while cursor < len(units):
        word = units[cursor]
        opcode = word & 255
        reference = None
        if opcode == 0 and word != 0:
            require(cursor % 2 == 0 and cursor + 2 <= len(units))
            count = units[cursor + 1]
            if word == 0x0100:
                width = 4 + 2 * count
            elif word == 0x0200:
                width = 2 + 4 * count
            elif word == 0x0300:
                require(cursor + 4 <= len(units) and count in (1, 2, 4, 8))
                size = units[cursor + 2] | units[cursor + 3] << 16
                width = 4 + (count * size + 1) // 2
            else:
                raise InvalidDex('Unknown instruction payload')
        else:
            require(opcode in WIDTHS)
            width = WIDTHS[opcode]
        require(cursor + width <= len(units))
        if opcode in (0x1a, 0x1b):
            reference = ('string', units[cursor + 1] | (units[cursor + 2] << 16 if opcode == 0x1b else 0))
        elif opcode in (*range(0x6e, 0x73), *range(0x74, 0x79), 0xfa, 0xfb):
            reference = ('method', units[cursor + 1])
        yield cursor, opcode, width, reference
        cursor += width


class Dex:
    def __init__(self, data):
        self.data = data
        require(len(data) >= 112 and data[:4] == b'dex\n' and data[7] == 0)
        require(data[4:7] in (b'035', b'037', b'038', b'039', b'040'))
        require(self.u32(32) == len(data) and self.u32(36) == 112 and self.u32(40) == 0x12345678)
        require(self.u32(8) == zlib.adler32(data[12:]) and data[12:32] == hashlib.sha1(data[32:]).digest())
        self.strings = []
        for pos in self.table(56, 4):
            offset = self.u32(pos)
            _, offset = uleb(data, offset)
            end = data.find(b'\0', offset)
            require(end >= offset)
            self.strings.append(data[offset:end])
        self.types = [self.string(self.u32(pos)).decode('utf-8', 'replace') for pos in self.table(64, 4)]
        self.protos = []
        for pos in self.table(72, 12):
            parameters = self.u32(pos + 8)
            args = []
            if parameters:
                count = self.u32(parameters)
                self.bounds(parameters + 4, count * 2)
                args = [self.type(self.u16(parameters + 4 + 2 * i)) for i in range(count)]
            self.protos.append('(' + ''.join(args) + ')' + self.type(self.u32(pos + 4)))
        self.methods = []
        for pos in self.table(88, 8):
            owner, proto, name = self.u16(pos), self.u16(pos + 2), self.u32(pos + 4)
            require(proto < len(self.protos))
            self.methods.append(self.type(owner) + '->' + self.string(name).decode('utf-8', 'replace') + self.protos[proto])

    def bounds(self, start, size):
        require(0 <= start <= len(self.data) and 0 <= size <= len(self.data) - start)

    def u32(self, offset):
        self.bounds(offset, 4)
        return struct.unpack_from('<I', self.data, offset)[0]

    def u16(self, offset):
        self.bounds(offset, 2)
        return struct.unpack_from('<H', self.data, offset)[0]

    def table(self, header_offset, width):
        count, offset = self.u32(header_offset), self.u32(header_offset + 4)
        self.bounds(offset, count * width)
        return range(offset, offset + count * width, width)

    def string(self, index):
        require(index < len(self.strings))
        return self.strings[index]

    def type(self, index):
        require(index < len(self.types))
        return self.types[index]

    def definitions(self):
        for pos in self.table(96, 32):
            offset = self.u32(pos + 24)
            if not offset:
                continue
            counts = []
            for _ in range(4):
                count, offset = uleb(self.data, offset)
                counts.append(count)
            for _ in range(counts[0] + counts[1]):
                _, offset = uleb(self.data, offset)
                _, offset = uleb(self.data, offset)
            for count in counts[2:]:
                index = 0
                for _ in range(count):
                    delta, offset = uleb(self.data, offset)
                    flags, offset = uleb(self.data, offset)
                    code, offset = uleb(self.data, offset)
                    index += delta
                    require(index < len(self.methods))
                    if code:
                        require(code % 4 == 0)
                        self.bounds(code, 16)
                        length = self.u32(code + 12) * 2
                        self.bounds(code + 16, length)
                        yield index, flags, self.u16(code), self.data[code + 16:code + 16 + length]

    def inspect(self):
        methods = 0
        results = []
        for index, flags, registers, code in self.definitions():
            methods += 1
            calls, anchors = set(), []
            metered = cellular = False
            for offset, opcode, width, ref in instructions(code):
                if not ref:
                    continue
                if ref[0] == 'string':
                    text = self.string(ref[1])
                    for anchor in ANCHORS:
                        if text.startswith(anchor):
                            anchors.append({'code_unit': offset, 'anchor': anchor.decode()})
                else:
                    require(ref[1] < len(self.methods))
                    target = self.methods[ref[1]]
                    metered |= target == 'Landroid/net/ConnectivityManager;->isActiveNetworkMetered()Z'
                    cellular |= target == 'Landroid/net/NetworkCapabilities;->hasTransport(I)Z'
                    if target.startswith('Landroid/net/') or '/LowDataModeKt;' in target:
                        calls.add(target)
            signature = self.methods[index]
            if anchors or metered and cellular and signature.endswith(')Z'):
                results.append({'signature': signature, 'flags': flags, 'registers': registers,
                                'code_sha256': hashlib.sha256(code).hexdigest(), 'code_units': len(code) // 2,
                                'network_pair': metered and cellular, 'anchors': anchors, 'network_calls': sorted(calls)})
        return methods, results

    def references_to(self, signatures):
        results = []
        targets = {i for i, value in enumerate(self.methods) if value in signatures}
        if not targets:
            return results
        for index, flags, registers, code in self.definitions():
            units = memoryview(code).cast('H')
            references = []
            for offset, opcode, width, ref in instructions(code):
                if ref and ref[0] == 'method' and ref[1] in targets:
                    following = offset + width
                    result_register = None
                    if following < len(units) and units[following] & 255 == 0x0a:
                        result_register = units[following] >> 8
                    references.append({'code_unit': offset, 'target': self.methods[ref[1]],
                                       'invoke_opcode': opcode, 'move_result_register': result_register})
            if references:
                results.append({'signature': self.methods[index], 'flags': flags, 'registers': registers,
                                'code_sha256': hashlib.sha256(code).hexdigest(), 'calls': references})
        return results


def inspect_apk(apk):
    pin = json.loads(Path(__file__).with_name('target.json').read_text())
    digest = hashlib.sha256()
    with apk.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    require(apk.stat().st_size == pin['asset_bytes'] and digest.hexdigest() == pin['sha256'])
    report = {'apk_sha256': digest.hexdigest(), 'apk_executed': False, 'dex_files': [], 'methods_with_code': 0, 'candidates': [], 'policy_callers': []}
    with zipfile.ZipFile(apk) as archive:
        entries = [x for x in archive.infolist() if re.fullmatch(r'classes(?:[2-9]|[1-9][0-9]+)?\.dex', x.filename)]
        require(entries and sum(x.file_size for x in entries) <= 150000000)
        require(len({x.filename for x in entries}) == len(entries))
        for entry in entries:
            dex = Dex(archive.read(entry))
            methods, candidates = dex.inspect()
            report['dex_files'].append(entry.filename)
            report['methods_with_code'] += methods
            report['candidates'].extend({'dex': entry.filename, **x} for x in candidates)
            # Only trace the exact observed policy class, never scan private app
            # state. The first inspected release proved q(Context, boolean).
            policy_methods = {x for x in dex.methods if x.startswith('Lr8/hg6;->') and x.endswith(')Z')}
            report['policy_callers'].extend({'dex': entry.filename, **x} for x in dex.references_to(policy_methods))
    require(report['candidates'])
    return report


if __name__ == '__main__':
    print(json.dumps(inspect_apk(Path(sys.argv[1])), indent=2))
