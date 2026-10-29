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


def network_resource(aapt, apk, selected):
    lines = dump(aapt, apk, 'resources').splitlines()
    header = re.compile(r'^\s*resource\s+(0x[0-9a-fA-F]+)\s+(\S+)')
    entries = []
    for i, line in enumerate(lines):
        match = header.search(line)
        if match is None or match.group(1).lower() != selected.lower():
            continue
        end = i + 1
        while end < len(lines) and header.search(lines[end]) is None:
            end += 1
        block = '\n'.join(lines[i:end])
        files = sorted(set(re.findall(r'\bres/xml[^\s]*?\.xml\b', block)))
        entries.append({'header': line.strip(), 'files': files})
    return entries


def main():
    if len(sys.argv) != 4:
        raise SystemExit('Expected aapt2, original APK and patched APK')
    aapt, original, patched = sys.argv[1:]
    before = manifest(aapt, original)
    after = manifest(aapt, patched)
    config = [line for line in after if 'networkSecurityConfig' in line]
    if len(config) != 1:
        raise SystemExit('Patched manifest does not expose a network security configuration')
    match = re.search(r'networkSecurityConfig[^=]*=@(0x[0-9a-fA-F]+)', config[0])
    if match is None:
        raise SystemExit('Patched manifest network resource ID was not decoded')
    selected = network_resource(aapt, patched, match.group(1))
    if len(selected) != 1:
        raise SystemExit('Selected network resource could not be uniquely resolved')
    trees = []
    for path in selected[0]['files']:
        tree = subprocess.check_output([aapt, 'dump', 'xmltree', patched, '--file', path], text=True)
        trees.append({'path': path, 'xmltree': tree})
    if not any('networkSecurityConfig' in line for line in before) and not trees:
        raise SystemExit('Generated network configuration has no decoded XML variant')
    result = {
        'schema': 'purple.gboard-network-metadata.v1',
        'original_manifest_attributes': before,
        'patched_manifest_attributes': after,
        'patched_selected_network_resource': selected[0],
        'patched_selected_xml_trees': trees,
        'scope': 'AAPT2 metadata and compiled XML from the exact public original and unsigned output; not an effective-policy comparison or Android runtime test.',
    }
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
