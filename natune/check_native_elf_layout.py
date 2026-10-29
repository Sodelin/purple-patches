"""Check the selected Android ARM64 ELF's 16 KiB load/RELRO layout."""
import struct
import sys
from pathlib import Path

PAGE = 16384
data = Path(sys.argv[1]).read_bytes()
if data[:6] != b'\x7fELF\x02\x01' or len(data) < 64:
    raise SystemExit('Expected little-endian ELF64')
if struct.unpack_from('<H', data, 18)[0] != 183:
    raise SystemExit('Expected Android ARM64 ELF')
phoff = struct.unpack_from('<Q', data, 32)[0]
phsize, phnum = struct.unpack_from('<HH', data, 54)
if phsize < 56 or phnum == 0 or phoff + phsize * phnum > len(data):
    raise SystemExit('Invalid program header table')
loads, relro = [], []
for index in range(phnum):
    kind, _, offset, virtual, _, _, memory_size, alignment = struct.unpack_from(
        '<IIQQQQQQ', data, phoff + index * phsize)
    if kind == 1:  # PT_LOAD
        if alignment < PAGE or alignment % PAGE or offset % PAGE != virtual % PAGE:
            raise SystemExit('ELF load segment is not 16 KiB compatible')
        loads.append(alignment)
    elif kind == 0x6474e552:  # PT_GNU_RELRO
        if (virtual + memory_size) % PAGE:
            raise SystemExit('ELF RELRO end is not 16 KiB aligned')
        relro.append(virtual + memory_size)
if not loads or not relro:
    raise SystemExit('Expected load and RELRO segments')
print('ELF_ARM64_16K load_segments=' + str(len(loads)) +
      ' relro_segments=' + str(len(relro)) + ' device_execution=false')
