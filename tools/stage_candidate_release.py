"""Preserve verified candidate assets in a PRIVATE GitHub draft, using RAM only.

No APK, weights, credentials, installed files or public update feed is published.
Run from a clean repository after committing this tool. Existing drafts are
verified by exact asset bytes; a mismatched release or asset is never replaced.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import urllib.parse
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPO = 'Sodelin/purple-patches'
TAG = 'v0.1.0-alpha.1'
UPSTREAM = '2eb71854accc7e906d3e4f5495b2a54ed1e2af47'


def gh(*args, data=None):
    return subprocess.check_output(['gh', *args], input=data, cwd=ROOT)


def api(path, data=None):
    args = ['api', path]
    if data is not None:
        args += ['--method', 'POST', '--input', '-']
    return json.loads(gh(*args, data=None if data is None else json.dumps(data).encode()))


def digest(body):
    return hashlib.sha256(body).hexdigest()


def verified_members(record, selected):
    record = json.loads((ROOT / 'docs' / record).read_text())
    body = gh('api', f"repos/{REPO}/actions/artifacts/{record['artifact_id']}/zip")
    assert len(body) == record['zip_bytes'], 'Artifact size changed'
    with zipfile.ZipFile(io.BytesIO(body)) as archive:
        pins = record['files']
        if isinstance(pins, list):
            pins = {p['file']: p for p in pins}
        result = {}
        for name in selected:
            payload = archive.read(name)
            assert len(payload) == pins[name]['bytes'], 'Member size changed'
            assert digest(payload) == pins[name]['sha256'], 'Member SHA256 changed'
            result[name] = payload
    return result, record['source_commit']


def collect():
    assets, gboard_source = verified_members('gboard-artifact.json', [
        'patches-0.1.0-alpha.1.mpp', 'patches-list.json', 'purple-provenance.json'])
    native, native_source = verified_members('native-artifact.json', [
        'purple-keyboard-bridge', 'purple-gboard-service'])
    assets.update(native)
    # Exact corresponding authored sources, not the possibly newer delivery HEAD.
    for label, commit in [('gboard-overlay', gboard_source), ('companion', native_source)]:
        assets[f'{label}-source.tar.gz'] = subprocess.check_output(
            ['git', 'archive', '--format=tar.gz', '--prefix=purple-patches/', commit], cwd=ROOT)
    assets['gboard-upstream-source.tar.gz'] = gh(
        'api', f'repos/jasonwu1994/Gboard-patches/tarball/{UPSTREAM}')
    manifest = {
        'candidate': TAG, 'status': 'PRIVATE DRAFT; phone acceptance pending',
        'gboard_source': gboard_source, 'companion_source': native_source,
        'upstream_source': UPSTREAM,
        'assets': {n: {'bytes': len(b), 'sha256': digest(b)} for n, b in sorted(assets.items())},
        'limits': ['Unsigned APK is intentionally excluded', 'No full-v3 weights included',
                   'No installation or live model acceptance', 'Draft URLs are not a usable Manager update feed'],
    }
    assets['candidate-manifest.json'] = (json.dumps(manifest, indent=2) + '\n').encode()
    assets['SHA256SUMS'] = ''.join(f'{digest(b)}  {n}\n' for n, b in sorted(assets.items())).encode()
    return assets


def collect_native():
    assets, source = verified_members('native-0.2.1-artifact.json', [
        'purple-keyboard-bridge', 'purple-gboard-service', 'response-schema.json'])
    assets['companion-0.2.1-source.tar.gz'] = subprocess.check_output(
        ['git', 'archive', '--format=tar.gz', '--prefix=purple-patches/', source], cwd=ROOT)
    manifest = {'version': '0.2.1', 'source_commit': source,
        'compatible_patch_bundle': '0.1.0-alpha.1', 'status': 'PRIVATE DRAFT; activation held',
        'schema_deployment': 'response-schema.json beside executable, owner-private0600; exact compiled bytes required',
        'assets': {n: {'bytes': len(b), 'sha256': digest(b)} for n, b in sorted(assets.items())}}
    assets['candidate-manifest.json'] = (json.dumps(manifest, indent=2) + '\n').encode()
    assets['SHA256SUMS'] = ''.join(f'{digest(b)}  {n}\n' for n, b in sorted(assets.items())).encode()
    return assets


def stage(assets, tag=TAG, name=None, body=None):
    repo = api(f'repos/{REPO}')
    assert repo['private'] is True, 'Refuse public repository'
    releases = api(f'repos/{REPO}/releases?per_page=100')
    existing = [r for r in releases if r['tag_name'] == tag]
    assert len(existing) <= 1, 'Ambiguous release'
    if existing:
        release = existing[0]
    else:
        release = api(f'repos/{REPO}/releases', {
            'tag_name': tag, 'target_commitish': subprocess.check_output(
                ['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
            'name': name or 'Purple Gboard 0.1.0-alpha.1 — private verification candidate',
            'draft': True, 'prerelease': True,
            'body': body or ('Verified Morphe bundle and Android ARM64 companion, with corresponding source and SHA256SUMS. '
                    'Exact stable APKMirror Gboard release patched and statically checked off phone. '
                    'Phone connection, privacy writer coverage, full Whisper large-v3 and device regression remain pending. '
                    'This private draft is not a working Morphe update source. No APK, model weights or keys included.')})
    assert release['draft'] is True and release['prerelease'] is True, 'Refuse changing published release'
    current = {a['name']: a for a in release['assets']}
    assert not set(current) - set(assets), 'Unexpected existing assets'
    # Check all existing assets before adding any missing asset; never overwrite.
    for name, asset in current.items():
        body = gh('api', f"repos/{REPO}/releases/assets/{asset['id']}", '-H', 'Accept: application/octet-stream')
        assert body == assets[name], 'Existing draft asset differs: ' + name
    endpoint = release['upload_url'].split('{', 1)[0]
    assert endpoint.startswith(f'https://uploads.github.com/repos/{REPO}/releases/'), 'Unexpected upload host/path'
    for name, body in assets.items():
        if name not in current:
            # No automatic retry after an uncertain mutation. Re-run verifies custody.
            # GitHub uploads require a fixed Content-Length; gh stdin uses chunked
            # transport here and is rejected. Keep bytes and token in RAM.
            token = gh('auth', 'token').decode().strip()
            request = urllib.request.Request(endpoint + '?name=' + urllib.parse.quote(name),
                data=body, headers={'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/octet-stream', 'Content-Length': str(len(body))},
                method='POST')
            class NoRedirect(urllib.request.HTTPRedirectHandler):
                def redirect_request(self, *args, **kwargs):
                    raise RuntimeError('Unexpected upload redirect')
            with urllib.request.build_opener(NoRedirect).open(request, timeout=120) as stream:
                response = json.load(stream)
            assert response['name'] == name and response['size'] == len(body), 'Upload metadata mismatch'
    final = api(f"repos/{REPO}/releases/{release['id']}")
    assert final['draft'] is True and {a['name'] for a in final['assets']} == set(assets)
    # Independent full remote readback, not only returned metadata or server digest.
    for asset in final['assets']:
        body = gh('api', f"repos/{REPO}/releases/assets/{asset['id']}", '-H', 'Accept: application/octet-stream')
        assert body == assets[asset['name']], 'Remote readback mismatch'
    print(json.dumps({'release_id': final['id'], 'tag': tag, 'draft': True,
        'private': True, 'assets_verified': len(assets), 'total_bytes': sum(map(len, assets.values())),
        'sha256': {n: digest(b) for n, b in sorted(assets.items())}}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    group=parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--stage-private-draft', action='store_true')
    group.add_argument('--stage-native-021', action='store_true')
    args=parser.parse_args()
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT), 'Commit source first'
    if args.stage_native_021:
        stage(collect_native(), tag='companion-v0.2.1-candidate',
              name='Purple Gboard companion 0.2.1 — private Android transport correction',
              body='Corrects actual Android anonymous /proc/self/fd input denial with pipe audio and fixed authored schema. '
                   '28 public native controls and ARM64 cross-build passed. Compatible with retained Gboard MPP0.1.0-alpha.1. '
                   'Full remote byte verification and corresponding source retained. '
                   'No installation, model invocation, microphone or privacy-writer acceptance. Activation remains held.')
    else:
        stage(collect())
