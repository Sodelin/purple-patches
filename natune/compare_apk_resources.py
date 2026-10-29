"""Summarize changed public APK entries without printing their contents."""
import hashlib
import re
import sys
from zipfile import ZipFile


def ignored(name):
    return (bool(re.fullmatch(r'classes(?:[2-9][0-9]*|1[0-9]+)?\.dex', name))
            or name == 'META-INF/MANIFEST.MF'
            or bool(re.fullmatch(r'META-INF/[^/]+\.(?:SF|RSA|DSA|EC)', name)))


with ZipFile(sys.argv[1]) as original, ZipFile(sys.argv[2]) as patched:
    changed = []
    for entry in original.infolist():
        if entry.is_dir() or ignored(entry.filename):
            continue
        try:
            after = patched.read(entry.filename)
        except KeyError:
            changed.append((entry.filename, 'missing'))
            continue
        before = original.read(entry.filename)
        a, b = hashlib.sha256(before).hexdigest(), hashlib.sha256(after).hexdigest()
        if a != b:
            changed.append((entry.filename, f'{len(before)}:{a} -> {len(after)}:{b}'))
    print('Changed original non-DEX entries:', len(changed))
    for name, detail in changed:
        print(name, detail)
