"""Public fixture only: inspect actual Android anonymous-input and pipe transport."""
import ctypes
import fcntl
import json
import os
import subprocess

assert os.uname().machine == 'aarch64', 'This control pins the ARM64 syscall ABI'
libc = ctypes.CDLL(None, use_errno=True)
fd = libc.syscall(279, ctypes.c_char_p(b'purple-gboard-public-control'), 1)
assert fd >= 0, 'memfd_create unavailable'
body = b'purple-gboard-public-memory-fixture\n'
cat = '/data/data/com.termux/files/usr/bin/cat'
try:
    os.write(fd, body)
    os.lseek(fd, 0, 0)
    via_path = subprocess.run([cat, f'/proc/self/fd/{fd}'], pass_fds=(fd,), capture_output=True)
    os.lseek(fd, 0, 0)
    inherited = subprocess.run([cat], stdin=fd, capture_output=True)
    piped = subprocess.run([cat], input=body, capture_output=True)
    assert inherited.returncode == 0 and inherited.stdout == body
    assert piped.returncode == 0 and piped.stdout == body
    assert fcntl.fcntl(fd, fcntl.F_GETFD) & fcntl.FD_CLOEXEC
    print(json.dumps({'platform': 'Android ARM64 Termux', 'public_fixture_bytes': len(body),
        'anonymous_path_exit': via_path.returncode,
        'anonymous_path_payload_matches': via_path.stdout == body,
        'anonymous_path_permission_denied': b'Permission denied' in via_path.stderr,
        'inherited_stdin_payload_matches': True, 'pipe_stdin_payload_matches': True,
        'parent_cloexec_preserved': True, 'microphone': False, 'model_invoked': False,
        'limits': 'Actual OS/public cat controls only. Not Rust companion execution, Whisper decoder, callback, privacy-writer or live speech acceptance.'}, indent=2))
finally:
    os.close(fd)
