"""Acquire the exact public Natune input ON HOSTED RUNNERS ONLY."""
import hashlib
import json
import os
from pathlib import Path
import sys
import urllib.request


def fetch(destination):
    if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_OS') != 'Linux':
        raise RuntimeError('Off-phone hosted acquisition only')
    pin = json.loads(Path(__file__).with_name('target.json').read_text())
    api = 'https://api.github.com/repos/' + pin['repository']
    request = urllib.request.Request(api + '/releases/tags/' + pin['release_tag'],
                                     headers={'User-Agent': 'Purple-Patches-Natune'})
    with urllib.request.urlopen(request, timeout=30) as response:
        release = json.load(response)
    if release['id'] != pin['release_id'] or release['tag_name'] != pin['release_tag']:
        raise RuntimeError('Release identity changed')
    asset = next(x for x in release['assets'] if x['id'] == pin['asset_id'])
    if (asset['name'], asset['size'], asset.get('digest')) != (
            pin['asset_name'], pin['asset_bytes'], 'sha256:' + pin['sha256']):
        raise RuntimeError('Public asset metadata changed')
    expected = ('https://github.com/' + pin['repository'] + '/releases/download/' +
                pin['release_tag'] + '/' + pin['asset_name'])
    if asset['browser_download_url'] != expected:
        raise RuntimeError('Unexpected asset route')
    digest = hashlib.sha256()
    total = 0
    try:
        with urllib.request.urlopen(expected, timeout=60) as response, destination.open('xb') as output:
            for block in iter(lambda: response.read(1024 * 1024), b''):
                total += len(block)
                if total > pin['asset_bytes']:
                    raise RuntimeError('Oversized APK')
                digest.update(block)
                output.write(block)
        if total != pin['asset_bytes'] or digest.hexdigest() != pin['sha256']:
            raise RuntimeError('APK bytes/hash mismatch')
    except BaseException:
        # Only remove the newly created owned output, never an existing input.
        if total and destination.exists():
            destination.unlink()
        raise
    print(json.dumps({'public_asset_verified': True, 'sha256': digest.hexdigest(), 'bytes': total}))


if __name__ == '__main__':
    fetch(Path(sys.argv[1]))
