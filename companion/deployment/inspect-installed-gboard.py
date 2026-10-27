#!/usr/bin/env python3
"""Fixed read-only target audit; existing route/identity, no APK copies or ADB setup.

Raw package, path and verifier output is sealed before any persistence. This
helper proves installed-input identity, not beta patch compatibility or custody.
"""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import socket
import stat
import subprocess
import sys

HOME = Path.home()
HELPER = HOME / 'Documents/Codex/2026-10-12-okay-so-i-was-thinking-so/linux-levels/bin/android_device.py'
HELPER_SHA = '14a613b109e29b5b97e1c3b55b70960c06d844c218fc0f700ea7f0ba9d7ce588'
SEALER = HOME / '.local/bin/featurectl'
GOOGLE_SIGNERS = {
    '7ce83c1b71f3d572fed04c8d40c5cb10ff75e6d87d9df6fbd53f0468c2905053',
    'f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83',
}


def retain(value):
    output = subprocess.run([str(SEALER), 'seal-stdin'],
        input=json.dumps(value).encode(), capture_output=True, timeout=30, check=True)
    receipt = json.loads(output.stdout)
    if receipt.get('state') != 'ENCRYPTED_AND_READ_BACK':
        raise RuntimeError('Encrypted readback required')
    path = Path(receipt['record'])
    if path.parent != HOME / '.local/state/featurectl/records' or path.suffix != '.age':
        raise RuntimeError('Unexpected record destination')
    return str(path)


def audit():
    result = {'action': 'purple-gboard/installed-input-audit-v1',
              'scope': 'read-only fixed packages; no device input/install/grants/copies',
              'packages': [], 'state': 'STARTED'}
    try:
        if hashlib.sha256(HELPER.read_bytes()).hexdigest() != HELPER_SHA:
            raise RuntimeError('Existing route helper changed; review required')
        # adb itself must never start a missing server as a side effect of this audit.
        with socket.create_connection(('127.0.0.1', 5037), timeout=1):
            pass
        spec = importlib.util.spec_from_file_location('purple_gboard_existing_device', HELPER)
        device = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = device
        spec.loader.exec_module(device)
        endpoint = device.target()
        device.verify(endpoint)
        environment = dict(os.environ)
        environment['_JAVA_OPTIONS'] = '-XX:-UsePerfData -XX:ErrorFile=/dev/null -XX:+DisableAttachMechanism'
        for package in ['com.google.android.inputmethod.latin', 'app.morphe.manager']:
            metadata = device.adb(endpoint, 'shell', 'dumpsys', 'package', package)
            paths = device.adb(endpoint, 'shell', 'pm', 'path', package)
            entry = {'package': package, 'package_metadata': metadata,
                     'version_names': re.findall(r'^\s*versionName=(\S+)', metadata, re.M),
                     'parts': []}
            result['packages'].append(entry)
            for line in paths.splitlines():
                if not line.startswith('package:'):
                    raise RuntimeError('Unexpected package path response')
                path = Path(line[8:].strip())
                if not re.fullmatch(r'/[A-Za-z0-9_./=+@~-]+\.apk', str(path)):
                    raise RuntimeError('Unexpected APK path')
                fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
                with os.fdopen(fd, 'rb') as source:
                    before = os.fstat(source.fileno())
                    if not stat.S_ISREG(before.st_mode):
                        raise RuntimeError('APK must be a regular file')
                    digest = hashlib.sha256()
                    for chunk in iter(lambda: source.read(1024 * 1024), b''):
                        digest.update(chunk)
                    after = os.fstat(source.fileno())
                    if (before.st_ino, before.st_size, before.st_mtime_ns) != (after.st_ino, after.st_size, after.st_mtime_ns):
                        raise RuntimeError('APK changed during hashing')
                part = {'path': str(path), 'bytes': before.st_size, 'sha256': digest.hexdigest()}
                entry['parts'].append(part)
                if package == 'com.google.android.inputmethod.latin':
                    check = subprocess.run(['apksigner', 'verify', '--print-certs', str(path)],
                        capture_output=True, env=environment, timeout=45)
                    output = check.stdout.decode('utf-8', 'replace')
                    part.update(verifier_exit=check.returncode, verifier_stdout=output,
                                verifier_stderr=check.stderr.decode('utf-8', 'replace'))
                    # Source-stamp certificates are not APK signing identities.
                    signers = re.findall(r'^(?:V[0-9.]+ )?Signer[^\n]*certificate SHA-256 digest: ([0-9a-f]{64})$', output, re.M)
                    if check.returncode or not signers or not set(signers).issubset(GOOGLE_SIGNERS):
                        raise RuntimeError('Google APK signature contract not proven')
                    part['apk_signers_sha256'] = signers
            if paths != device.adb(endpoint, 'shell', 'pm', 'path', package):
                raise RuntimeError('Package paths changed during observation')
        device.verify(endpoint)
        result['state'] = 'VERIFIED_INPUT_IDENTITY_ONLY'
    except Exception as error:
        result.update(state='REFUSED_NO_CHANGE', error_type=type(error).__name__)
    record = retain(result)
    print(json.dumps({'state': result['state'], 'record': record}))
    return 0 if result['state'] == 'VERIFIED_INPUT_IDENTITY_ONLY' else 2


if __name__ == '__main__':
    try:
        raise SystemExit(audit())
    except Exception:
        # No raw diagnostic or fallback file if encryption itself is unavailable.
        print('{"state":"REFUSED_ENCRYPTED_RECORD_UNAVAILABLE"}')
        raise SystemExit(2)
