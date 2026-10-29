"""Verify the explicitly selected public/private route without Manager or inference."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.error
import urllib.request
from datetime import datetime
from release_checks import require
from stage_candidate_release import REPO, api, gh

ROOT = Path(__file__).resolve().parents[1]
URL = 'https://raw.githubusercontent.com/Sodelin/purple-patches/main/channels/candidate.json'


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise RuntimeError('Unexpected raw-feed redirect; credentials not forwarded')


def verify(visibility='public', component='gboard'):
    require(component in ('gboard', 'natune'), 'Unknown patch component')
    feed_path = 'channels/candidate.json' if component == 'gboard' else 'channels/natune-candidate.json'
    url = f'https://raw.githubusercontent.com/{REPO}/main/{feed_path}'
    require(visibility in ('public', 'private'), 'Unknown repository visibility')
    private = visibility == 'private'
    def metadata(path):
        if private:
            return api(path)
        with urllib.request.urlopen('https://api.github.com/' + path, timeout=30) as response:
            return json.load(response)
    require(metadata(f'repos/{REPO}')['private'] is private,
            'Repository visibility differs from selected verification mode')
    opener = urllib.request.build_opener(NoRedirect)
    body = None
    try:
        with opener.open(url, timeout=30) as response:
            anonymous_status = response.status
            body = response.read(16385)
    except urllib.error.HTTPError as error:
        anonymous_status = error.code
    if private:
        require(anonymous_status in (403, 404), 'Private source unexpectedly available anonymously')
        token = gh('auth', 'token').decode().strip()
        request = urllib.request.Request(url, headers={'Authorization': 'Bearer ' + token})
        with opener.open(request, timeout=30) as response:
            body = response.read(16385)
    else:
        require(anonymous_status == 200, 'Public feed is not anonymously readable')
    require(len(body) <= 16384 and body == (ROOT / feed_path).read_bytes(), 'Feed bytes differ')
    feed = json.loads(body)
    stamp = datetime.fromisoformat(feed['created_at'])
    require(stamp.tzinfo is None, 'Manager LocalDateTime requires offset-free timestamp')
    if component == 'gboard':
        selected = json.loads((ROOT / 'docs/selected-release-assets.json').read_text())
        candidate = next(c for c in selected['candidates'] if c['tag'].startswith('patches-v'))
    else:
        candidate = json.loads((ROOT / 'natune/published-candidate.json').read_text())
    release = metadata(f"repos/{REPO}/releases/tags/{candidate['tag']}")
    require(release['id'] == candidate['release_id'] and not release['draft'] and release['prerelease'],
            'Release identity or state differs')
    name = f"patches-{feed['version']}.mpp"
    require(feed['download_url'] == f"https://github.com/{REPO}/releases/download/{candidate['tag']}/{name}",
            'Download URL differs')
    asset = next(a for a in release['assets'] if a['name'] == name)
    pin = candidate['assets'][name]
    if private:
        payload = gh('api', f"repos/{REPO}/releases/assets/{asset['id']}",
                     '-H', 'Accept: application/octet-stream')
    else:
        # Release assets redirect to GitHub's asset host; no credentials are
        # attached, so this public redirect never forwards a bearer.
        with urllib.request.urlopen(feed['download_url'], timeout=60) as response:
            payload = response.read(pin['bytes'] + 1)
    require(len(payload) == pin['bytes'] and hashlib.sha256(payload).hexdigest() == pin['sha256'],
            'Asset bytes differ')
    require(metadata(f'repos/{REPO}')['private'] is private, 'Repository visibility changed')
    return {'source_url': url, 'component': component, 'private': private, 'anonymous_http_status': anonymous_status,
            'verification_mode': visibility, 'selected_feed_matches_versioned_source': True, 'published_prerelease_tag': candidate['tag'],
            'actual_versioned_release_route_resolves': True, 'main_mpp_full_bytes_verified': pin,
            'credential_profile': 'Existing gh identity; no Manager credential read/copied/changed' if private else 'Anonymous public requests only; no credentials accessed',
            'manager_device_import_verified': False, 'device_actions': False, 'model_invocations': False,
            'limits': 'Remote feed and exact versioned asset path verified; actual phone Manager import/update and app acceptance remain unverified.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--visibility', choices=('public', 'private'), default='public')
    parser.add_argument('--component', choices=('gboard', 'natune'), default='gboard')
    args = parser.parse_args()
    print(json.dumps(verify(args.visibility, args.component), indent=2))
