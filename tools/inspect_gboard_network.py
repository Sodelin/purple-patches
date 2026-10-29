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


def matching_original_resource_names(aapt, apk, selected_header):
    header = re.compile(r'^\s*resource\s+0x[0-9a-fA-F]+\s+(\S+)')
    selected = header.search(selected_header)
    if selected is None:
        raise ValueError('Selected resource header is invalid')
    name = selected.group(1).rsplit(':', 1)[-1]
    matches = []
    for line in dump(aapt, apk, 'resources').splitlines():
        value = header.search(line)
        if value is not None and value.group(1).rsplit(':', 1)[-1] == name:
            matches.append(line.strip())
    return matches


def verify_new_false_base(before, after, trees, original_matches):
    """Check the exact observed shape only when original has no network resource."""
    if any('networkSecurityConfig' in line for line in before):
        return 'Original already selects a network resource; effective policy not compared'
    if not any('usesCleartextTraffic' in line and line.endswith('=false') for line in before):
        return 'Original has no explicit false cleartext value; effective policy not compared'
    if original_matches:
        raise ValueError('Generated network resource name existed in the original resource table')
    if not any('usesCleartextTraffic' in line and line.endswith('=false') for line in after):
        raise ValueError('Patched manifest lost the original false cleartext flag')
    if len(trees) != 1:
        raise ValueError('Expected exactly one generated XML variant for this target')
    lines = [line.strip() for line in trees[0]['xmltree'].splitlines() if line.strip()]
    if (len(lines) != 8
            or not lines[0].startswith('E: network-security-config ')
            or not lines[1].startswith('E: base-config ')
            or lines[2] != 'A: cleartextTrafficPermitted=false'
            or not lines[3].startswith('E: domain-config ')
            or lines[4] != 'A: cleartextTrafficPermitted=true'
            or not lines[5].startswith('E: domain ')
            or lines[6] != 'A: includeSubdomains=false'
            or lines[7] != "T: '127.0.0.1'"):
        raise ValueError('Generated compiled network XML differs from the selected isolated policy')
    return 'Original name absent; explicit false base and only IPv4 loopback allowed in compiled XML'


def self_test():
    before = ['A: android:usesCleartextTraffic(0x010104ec)=false']
    after = ['A: android:usesCleartextTraffic(0x010104ec)=false']
    tree = ("E: network-security-config (line=1)\nE: base-config (line=1)\n"
            "A: cleartextTrafficPermitted=false\nE: domain-config (line=1)\n"
            "A: cleartextTrafficPermitted=true\nE: domain (line=1)\n"
            "A: includeSubdomains=false\nT: '127.0.0.1'\n")
    assert verify_new_false_base(before, after, [{'xmltree': tree}], []).startswith('Original name absent')
    for modified, matches in ((tree.replace('Permitted=false', 'Permitted=true', 1), []),
                              (tree.replace('includeSubdomains=false', 'includeSubdomains=true'), []),
                              (tree.replace('127.0.0.1', '192.168.1.1'), []),
                              (tree + 'E: domain (line=1)\n', []),
                              (tree, ['resource 0x1 xml/purple_keyboard_network'])):
        try:
            verify_new_false_base(before, after, [{'xmltree': modified}], matches)
        except ValueError:
            continue
        raise AssertionError('Network verification accepted a negative control')
    assert 'not compared' in verify_new_false_base(['networkSecurityConfig'], after, [], [])
    print('Compiled network XML verifier controls passed')


def main():
    if sys.argv[1:] == ['--self-test']:
        self_test()
        return
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
    original_matches = matching_original_resource_names(aapt, original, selected[0]['header'])
    assertion = verify_new_false_base(before, after, trees, original_matches)
    result = {
        'schema': 'purple.gboard-network-metadata.v1',
        'original_manifest_attributes': before,
        'patched_manifest_attributes': after,
        'patched_selected_network_resource': selected[0],
        'patched_selected_xml_trees': trees,
        'original_matching_resource_names': original_matches,
        'exact_target_static_assertion': assertion,
        'scope': 'AAPT2 metadata and compiled XML from the exact public original and unsigned output; not an effective-policy comparison or Android runtime test.',
    }
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
