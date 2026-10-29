"""Preserve the independently verified Natune MPP as an immutable prerelease.

Default is read-only verification. --publish stages exact bytes, verifies every
remote asset, then publishes a prerelease. No APK, installation, model or app
credential is used. Reuse the existing GitHub development identity in RAM.
"""
import argparse
import io
import json
import subprocess
import urllib.parse
import urllib.request
import zipfile
from stage_candidate_release import ROOT, REPO, api, gh, digest

TAG = 'natune-patches-v0.1.0-alpha.1'
TEMPLATE = 'e721536ab19e30d16ce9d31ae1830ca299aad383'
MEMBERS = {'patches-0.1.0-alpha.1.mpp', 'patches-list.json',
           'patches-0.1.0-alpha.1-sources.mpp', 'patches-0.1.0-alpha.1-javadoc.mpp',
           'purple-natune-provenance.json', 'target-proof.json',
           'patch-result.json', 'static-preservation.txt', 'SHA256SUMS'}


def collect():
    proof = json.loads((ROOT / 'natune/build-verification.json').read_text())
    assert proof['result'] == 'success' and proof['whole_apk_resources_preserved'] is True
    run = api(f"repos/{REPO}/actions/runs/{proof['run_id']}")
    assert run['status'] == 'completed' and run['conclusion'] == 'success'
    assert run['head_sha'] == proof['source_commit'] and run['path'] == '.github/workflows/build-natune.yml'
    raw = gh('api', f"repos/{REPO}/actions/artifacts/{proof['artifact_id']}/zip")
    assert digest(raw) == proof['artifact_zip_sha256'], 'Artifact changed'
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        assert len(archive.namelist()) == len(MEMBERS) and set(archive.namelist()) == MEMBERS
        assets = {name: archive.read(name) for name in MEMBERS}
    assert set(proof['artifact_files_sha256']) == MEMBERS - {'SHA256SUMS'}
    for name, pin in proof['artifact_files_sha256'].items():
        assert digest(assets[name]) == pin, 'Member changed: ' + name
    metadata = json.loads(assets['purple-natune-provenance.json'])
    assert metadata['overlay_commit'] == proof['source_commit'] and metadata['template_commit'] == TEMPLATE
    assert metadata['target'] == json.loads((ROOT / 'natune/target.json').read_text())
    patches = json.loads(assets['patches-list.json'])
    assert patches['version'] == '0.1.0-alpha.1' and len(patches['patches']) == 1
    patch = patches['patches'][0]
    assert patch['name'] == 'Allow full-quality audio on mobile data' and patch['default'] is False
    assert len(patch['compatiblePackages']) == 1
    package = patch['compatiblePackages'][0]
    assert package['packageName'] == metadata['target']['package']
    assert len(package['targets']) == 1 and package['targets'][0]['version'] == metadata['target']['version_name']
    assert package['targets'][0]['isExperimental'] is True
    # Corresponding authored source plus exact upstream template source, in RAM.
    assets['purple-natune-source.tar.gz'] = subprocess.check_output(
        ['git', 'archive', '--format=tar.gz', '--prefix=purple-patches/', proof['source_commit']], cwd=ROOT)
    assets['morphe-template-source.tar.gz'] = gh('api', f'repos/MorpheApp/morphe-patches-template/tarball/{TEMPLATE}')
    assets['build-verification.json'] = (json.dumps(proof, indent=2) + '\n').encode()
    assets['SHA256SUMS'] = ''.join(f'{digest(body)}  {name}\n' for name, body in sorted(assets.items())
                                 if name != 'SHA256SUMS').encode()
    return assets, proof


def publish(assets, proof):
    assert api(f'repos/{REPO}')['private'] is False, 'Expected Nolan-selected public repository'
    releases = api(f'repos/{REPO}/releases?per_page=100')
    matches = [r for r in releases if r['tag_name'] == TAG]
    assert len(matches) <= 1
    if matches:
        release = matches[0]
    else:
        release = api(f'repos/{REPO}/releases', {
            'tag_name': TAG, 'target_commitish': proof['source_commit'],
            'name': 'Purple Natune 0.1.0-alpha.1 — experimental mobile-data patch',
            'draft': True, 'prerelease': True,
            'body': 'Optional, default-off mobile audio policy for Natune N202610191717 only. '
                    'Exact APK, actual patch application and independent DEX/resource preservation verified off phone. '
                    'This increment does NOT force FLAC or add providers. No phone acceptance. '
                    'Includes MPP, generated compatibility list, exact source, provenance and hashes. '
                    'No patched APK, model, credentials or signing key.'})
    assert release['prerelease'] is True and release['target_commitish'] == proof['source_commit']
    current = {a['name']: a for a in release['assets']}
    assert not set(current) - set(assets), 'Unexpected release assets'
    for name, asset in current.items():
        assert gh('api', f"repos/{REPO}/releases/assets/{asset['id']}", '-H',
                  'Accept: application/octet-stream') == assets[name], 'Existing asset changed'
    if not release['draft']:
        assert set(current) == set(assets), 'Published releases are immutable'
    endpoint = release['upload_url'].split('{', 1)[0]
    assert endpoint.startswith(f'https://uploads.github.com/repos/{REPO}/releases/')
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            raise RuntimeError('Unexpected authenticated upload redirect')
    for name, body in assets.items():
        if name in current:
            continue
        assert release['draft'] is True
        token = gh('auth', 'token').decode().strip()
        request = urllib.request.Request(endpoint + '?name=' + urllib.parse.quote(name), data=body,
            headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/octet-stream',
                     'Content-Length': str(len(body))}, method='POST')
        # Never retry an uncertain mutation. Reconcile the existing draft first.
        with urllib.request.build_opener(NoRedirect).open(request, timeout=120) as response:
            uploaded = json.load(response)
        assert uploaded['name'] == name and uploaded['size'] == len(body)
    verified = api(f"repos/{REPO}/releases/{release['id']}")
    assert {a['name'] for a in verified['assets']} == set(assets)
    for asset in verified['assets']:
        assert gh('api', f"repos/{REPO}/releases/assets/{asset['id']}", '-H',
                  'Accept: application/octet-stream') == assets[asset['name']], 'Remote readback failed'
    if verified['draft']:
        gh('api', f"repos/{REPO}/releases/{release['id']}", '--method', 'PATCH', '--input', '-',
           data=json.dumps({'draft': False, 'prerelease': True, 'make_latest': 'false'}).encode())
    final = api(f'repos/{REPO}/releases/tags/{TAG}')
    assert final['id'] == release['id'] and final['draft'] is False and final['prerelease'] is True
    return final


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--publish', action='store_true')
    args = parser.parse_args()
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT), 'Commit reviewable source first'
    assets, proof = collect()
    release = publish(assets, proof) if args.publish else None
    print(json.dumps({'source_commit': proof['source_commit'], 'tag': TAG,
        'release_id': release['id'] if release else None, 'published_prerelease': bool(release),
        'assets': {name: {'bytes': len(body), 'sha256': digest(body)} for name, body in sorted(assets.items())},
        'phone_acceptance': False, 'strict_flac_implemented': False, 'additional_providers_implemented': False}, indent=2))
