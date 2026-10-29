"""Check the selected stored ARM64 library's actual APK data offset."""
import struct
import sys
import zipfile

NAME = 'lib/arm64-v8a/libpurple_natune_flac.so'
PAGE = 16384
with zipfile.ZipFile(sys.argv[1]) as apk, open(sys.argv[1], 'rb') as raw:
    entries = [entry for entry in apk.infolist() if entry.filename == NAME]
    if len(entries) != 1:
        raise SystemExit('Expected exactly one selected native library')
    entry = entries[0]
    raw.seek(entry.header_offset)
    header = raw.read(30)
    if len(header) != 30 or header[:4] != b'PK\x03\x04':
        raise SystemExit('Invalid native library ZIP header')
    name_length, extra_length = struct.unpack_from('<HH', header, 26)
    name = raw.read(name_length)
    if name != NAME.encode('utf-8'):
        raise SystemExit('Native library local/central names differ')
    offset = entry.header_offset + 30 + name_length + extra_length
    if entry.compress_type != zipfile.ZIP_STORED or offset % PAGE:
        raise SystemExit('Native library must be stored and 16 KiB aligned: compression=' +
                         str(entry.compress_type) + ' data_offset=' + str(offset))
    print('APK_ARM64_FLAC stored=true data_offset=' + str(offset) +
          ' alignment=16384 signing=false device_execution=false')
