"""Verify exact candidate assets, then optionally publish PRIVATE prereleases.

No device calls, visibility/access changes, signing keys, models or installation.
Source-controlled pins, not a release's self-declared checksums, are authoritative.
All candidates pass full byte verification before the first publication mutation.
"""
import argparse
import json
from pathlib import Path
import subprocess
from stage_candidate_release import REPO, api, gh, digest

ROOT = Path(__file__).resolve().parents[1]


def verify_selected():
    assert api(f'repos/{REPO}')['private'] is True, 'Refuse public repository'
    selected = json.loads((ROOT / 'docs/selected-release-assets.json').read_text())
    verified = []
    for candidate in selected['candidates']:
        release = api(f"repos/{REPO}/releases/{candidate['release_id']}")
        assert release['tag_name'] == candidate['tag'], 'Wrong component tag'
        assert release['prerelease'] is True, 'Refuse stable-release promotion'
        assert {a['name'] for a in release['assets']} == set(candidate['assets']), 'Asset set differs'
        for asset in release['assets']:
            payload = gh('api', f"repos/{REPO}/releases/assets/{asset['id']}",
                         '-H', 'Accept: application/octet-stream')
            pin = candidate['assets'][asset['name']]
            assert len(payload) == pin['bytes'] and digest(payload) == pin['sha256'], 'Changed asset bytes'
        verified.append((candidate, release))
    return verified


def publish(verified):
    for candidate, release in verified:
        assert api(f'repos/{REPO}')['private'] is True, 'Repository visibility changed'
        if release['draft']:
            payload = json.dumps({'draft': False, 'prerelease': True, 'make_latest': 'false'}).encode()
            changed = json.loads(gh('api', f"repos/{REPO}/releases/{release['id']}",
                                   '--method', 'PATCH', '--input', '-', data=payload))
            assert changed['draft'] is False and changed['prerelease'] is True
        # Manager resolves versioned assets through this exact tag endpoint.
        tag = api(f"repos/{REPO}/releases/tags/{candidate['tag']}")
        assert tag['id'] == release['id'] and tag['draft'] is False and tag['prerelease'] is True
    assert api(f'repos/{REPO}')['private'] is True


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--publish', action='store_true')
    args = parser.parse_args()
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT), 'Commit reviewable source first'
    verified = verify_selected()
    if args.publish:
        publish(verified)
    print(json.dumps({'private': True, 'published_prereleases': bool(args.publish),
        'candidates': [{'id': r['id'], 'tag': c['tag'], 'assets_verified_full_bytes': len(r['assets'])}
                       for c, r in verified],
        'device_actions': False, 'model_invocations': False,
        'limits': 'Private candidate distribution only; no actual Manager import or phone acceptance.'}, indent=2))
