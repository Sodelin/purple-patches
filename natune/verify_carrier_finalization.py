"""Compare all logical ZIP contents before and after carrier finalization."""
import hashlib
import sys
from zipfile import ZipFile


def entries(apk):
    result = {}
    for entry in apk.infolist():
        if entry.filename in result:
            raise ValueError('Duplicate ZIP entry')
        result[entry.filename] = entry
    return result


def digest(apk, entry):
    value = hashlib.sha256()
    with apk.open(entry) as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(block)
    return value.digest()


with ZipFile(sys.argv[1]) as before, ZipFile(sys.argv[2]) as after:
    original, final = entries(before), entries(after)
    if original.keys() != final.keys():
        raise SystemExit('APK entry set changed during finalization')
    for name, entry in original.items():
        if entry.file_size != final[name].file_size or digest(before, entry) != digest(after, final[name]):
            raise SystemExit('APK entry content changed during finalization: ' + name)
    print('FINALIZATION_CONTENTS entries=' + str(len(original)) +
          ' identical=true signing_performed=false device_execution=false')
