"""Verify the private feed/asset network route without invoking Manager or a model."""
import hashlib
import json
from pathlib import Path
import urllib.error
import urllib.request
from datetime import datetime
from stage_candidate_release import REPO, api, gh

ROOT = Path(__file__).resolve().parents[1]
URL = 'https://raw.githubusercontent.com/Sodelin/purple-patches/main/channels/candidate.json'


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise RuntimeError('Unexpected raw-feed redirect; credentials not forwarded')


def verify():
    assert api(f'repos/{REPO}')['private'] is True
    opener = urllib.request.build_opener(NoRedirect)
    try:
        with opener.open(URL, timeout=30) as response:
            anonymous_status = response.status
    except urllib.error.HTTPError as error:
        anonymous_status = error.code
    assert anonymous_status in (403, 404), 'Private source unexpectedly available anonymously'
    token = gh('auth', 'token').decode().strip()
    request = urllib.request.Request(URL, headers={'Authorization': 'Bearer ' + token})
    with opener.open(request, timeout=30) as response:
        body = response.read(16385)
    assert len(body) <= 16384 and body == (ROOT / 'channels/candidate.json').read_bytes()
    feed = json.loads(body)
    stamp = datetime.fromisoformat(feed['created_at'])
    assert stamp.tzinfo is None, 'Manager LocalDateTime requires offset-free timestamp'
    selected = json.loads((ROOT / 'docs/selected-release-assets.json').read_text())
    candidate = next(c for c in selected['candidates'] if c['tag'].startswith('patches-v'))
    release = api(f"repos/{REPO}/releases/tags/{candidate['tag']}")
    assert release['id'] == candidate['release_id'] and not release['draft'] and release['prerelease']
    name = f"patches-{feed['version']}.mpp"
    assert feed['download_url'] == f"https://github.com/{REPO}/releases/download/{candidate['tag']}/{name}"
    asset = next(a for a in release['assets'] if a['name'] == name)
    payload = gh('api', f"repos/{REPO}/releases/assets/{asset['id']}",
                 '-H', 'Accept: application/octet-stream')
    pin = candidate['assets'][name]
    assert len(payload) == pin['bytes'] and hashlib.sha256(payload).hexdigest() == pin['sha256']
    assert api(f'repos/{REPO}')['private'] is True
    return {'source_url': URL, 'private': True, 'anonymous_http_status': anonymous_status,
            'authenticated_feed_matches_versioned_source': True, 'published_prerelease_tag': candidate['tag'],
            'actual_versioned_release_route_resolves': True, 'main_mpp_full_bytes_verified': pin,
            'credential_profile': 'Existing gh identity; no Manager credential read/copied/changed',
            'manager_device_import_verified': False, 'device_actions': False, 'model_invocations': False,
            'limits': 'Remote feed and actual versioned asset path verified; phone Manager PAT/import/update and keyboard acceptance remain unverified.'}


if __name__ == '__main__':
    print(json.dumps(verify(), indent=2))
