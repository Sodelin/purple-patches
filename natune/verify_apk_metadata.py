"""Verify public original signer and package metadata across apksigner formats."""
import json
import os
from pathlib import Path
import re
import sys


def verify(pin, signers, badging):
    # New build-tools label each signature scheme (e.g. V3.0 Signer).
    # Old build-tools use Signer #1. Never accept source-stamp certificates.
    digests = re.findall(r'^(?:Signer #\d+ certificate|V\d+(?:\.\d+)? Signer: certificate) SHA-256 digest: ([0-9a-fA-F]{64})\s*$', signers, re.M)
    if not digests or {x.lower() for x in digests} != {pin['signer_sha256']}:
        raise ValueError('Original signer missing or different')
    match = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    if not match or match.groups() != (pin['package'], str(pin['version_code']), pin['version_name']):
        raise ValueError('Original package/version mismatch')
    return {'original_signature_verified': True, 'package_version_verified': True,
            'signer_sha256': pin['signer_sha256'], 'apk_executed': False}


if __name__ == '__main__':
    pin = json.loads(Path(__file__).with_name('target.json').read_text())
    result = verify(pin, Path(sys.argv[1]).read_text(), Path(sys.argv[2]).read_text())
    print(json.dumps({'source_commit': os.environ.get('GITHUB_SHA'), 'public_input': pin, **result}, indent=2))
