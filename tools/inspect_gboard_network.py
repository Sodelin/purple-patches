"""Record public original/output manifest and network-resource metadata off phone."""
import json
import re
import subprocess
import sys


def dump(aapt, apk, *args):
    return subprocess.check_output([aapt, 'dump', *args, apk], text=True)


def manifest(aapt, apk):
    # aapt2's --file argument follows the APK path for xmltree.
    text = subprocess.check_output([aapt, 'dump', 'xmltree', apk, '--file', 'AndroidManifest.xml'], text=True)
    wanted = re.compile(r'\b(?:networkSecurityConfig|usesCleartextTraffic|targetSdkVersion)\b')
    return [line.strip() for line in text.splitlines() if wanted.search(line)]


def purple_resources(aapt, apk):
    lines = dump(aapt, apk, 'resources').splitlines()
    positions = [i for i, line in enumerate(lines) if re.search(r'xml/purple_keyboard_network(?:_[0-9]+)?\b', line)]
    return ['\n'.join(lines[i:min(i + 5, len(lines))]) for i in positions]


def main():
    if len(sys.argv) != 4:
        raise SystemExit('Expected aapt2, original APK and patched APK')
    aapt, original, patched = sys.argv[1:]
    before = manifest(aapt, original)
    after = manifest(aapt, patched)
    if not any('networkSecurityConfig' in line for line in after):
        raise SystemExit('Patched manifest does not expose a network security configuration')
    result = {
        'schema': 'purple.gboard-network-metadata.v1',
        'original_manifest_attributes': before,
        'patched_manifest_attributes': after,
        'patched_purple_xml_resource_entries': purple_resources(aapt, patched),
        'scope': 'AAPT2 metadata from the exact public original and unsigned output; not an effective-policy comparison or Android runtime test.',
    }
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
