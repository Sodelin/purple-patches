#!/usr/bin/env bash
# Finish a public unsigned hosted test APK; do not touch a phone or sign it.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_OS:-}" == Linux ]] || { echo 'Hosted runner only' >&2; exit 1; }
[[ $# == 4 ]] || { echo 'Expected input APK, selected library, zipalign, output APK' >&2; exit 1; }
work_root="$(realpath "$RUNNER_TEMP")"
input_apk="$(realpath "$1")"
source_library="$(realpath "$2")"
aligner="$(realpath "$3")"
output_apk="$(realpath -m "$4")"
for path in "$input_apk" "$source_library" "$output_apk"; do
  [[ "$path" == "$work_root/"* ]] || { echo 'Expected runner temporary paths' >&2; exit 1; }
done
[[ -f "$input_apk" && -f "$source_library" && -x "$aligner" && ! -e "$output_apk" && ! -L "$output_apk" ]] || exit 1
library_entry='lib/arm64-v8a/libpurple_natune_flac.so'
python3 - "$input_apk" "$source_library" "$library_entry" <<'PY'
import hashlib, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as apk:
    entries = [e for e in apk.infolist() if e.filename == sys.argv[3]]
    if len(entries) != 1:
        raise SystemExit('Expected exactly one original carrier library')
    with open(sys.argv[2], 'rb') as source:
        expected = hashlib.sha256(source.read()).digest()
    if hashlib.sha256(apk.read(entries[0])).digest() != expected:
        raise SystemExit('Carrier library does not match selected input')
PY
work_dir="$(mktemp -d "$work_root/natune-finalize.XXXXXXXX")"
trap 'rm -rf -- "$work_dir"' EXIT
cp -- "$input_apk" "$work_dir/unaligned.apk"
zip -q -d "$work_dir/unaligned.apk" "$library_entry"
mkdir -p "$work_dir/input/lib/arm64-v8a"
cp -- "$source_library" "$work_dir/input/$library_entry"
(cd "$work_dir/input" && zip -q -0 -D "$work_dir/unaligned.apk" "$library_entry")
"$aligner" -P 16 4 "$work_dir/unaligned.apk" "$output_apk"
printf '%s\n' 'CARRIER_FINALIZED stored_library=true signing_performed=false device_execution=false'
