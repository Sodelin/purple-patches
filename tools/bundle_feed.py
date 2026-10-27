"""Morphe Manager 2.2.0 JSON-source metadata; no publication side effects."""
from datetime import datetime, timezone
import json
import re
from urllib.parse import urlsplit


def bundle_feed(version, created_at, download_url=''):
    if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?', version):
        raise ValueError('Invalid candidate version')
    stamp = datetime.fromisoformat(created_at.replace('Z', '+00:00'))
    if stamp.tzinfo is None:
        raise ValueError('Source commit timestamp must carry its timezone')
    # MorpheAsset.createdAt is kotlinx.datetime.LocalDateTime, not Instant.
    # UTC conversion comes BEFORE removing the offset for its JSON serializer.
    created = stamp.astimezone(timezone.utc).replace(tzinfo=None).isoformat(timespec='seconds')
    if download_url:
        parsed = urlsplit(download_url)
        expected = f'/Sodelin/purple-patches/releases/download/v{version}/patches-{version}.mpp'
        if (parsed.scheme, parsed.netloc, parsed.path, parsed.query, parsed.fragment) != (
                'https', 'github.com', expected, '', ''):
            raise ValueError('Download must select our exact versioned main MPP')
    return {'version': version,
            'description': 'Purple Gboard candidate. Full large-v3 dictation and explicit AI Translate; phone acceptance pending.',
            'created_at': created, 'download_url': download_url,
            'signature_download_url': None,
            'page_url': f'https://github.com/Sodelin/purple-patches/releases/tag/v{version}'}


def check():
    version = '0.1.0-alpha.1'
    url = f'https://github.com/Sodelin/purple-patches/releases/download/v{version}/patches-{version}.mpp'
    assert bundle_feed(version, '2026-10-27T08:02:03-07:00', url)['created_at'] == '2026-10-27T15:02:03'
    assert bundle_feed(version, '2026-10-27T23:30:00-07:00')['created_at'] == '2026-10-28T06:30:00'
    for bad in [url.replace('Sodelin', 'somebody'), url.replace('.mpp', '-sources.mpp'),
                url.replace('github.com', 'github.com.evil.invalid'), url + '?token=secret',
                url.replace('v0.1.0-alpha.1/', 'v0.0.9/')]:
        try:
            bundle_feed(version, '2026-10-27T15:00:00Z', bad)
        except ValueError:
            pass
        else:
            raise AssertionError('Accepted wrong identity/version or credential-bearing URL')
    try:
        bundle_feed(version, '2026-10-27T15:00:00')
    except ValueError:
        pass
    else:
        raise AssertionError('Accepted ambiguous timestamp')
    assert not bundle_feed(version, '2026-10-27T15:00:00Z')['download_url']
    print('Feed UTC conversion, rollover, source identity/version and unreleased refusal controls passed')


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', required=True)
    parser.parse_args()
    check()
