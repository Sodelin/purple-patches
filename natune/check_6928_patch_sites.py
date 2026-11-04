"""Compare selected Kotlin/Java patch site guards with a pinned DEX inventory."""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parent
report = json.loads((root / 'current-6928-policy-inspection.json').read_text())
if report['apk_sha256'] != 'b6e6c4fed044416cd8c8b232ebda6bdca8b6c2626637a400ffc19d8800026d60':
    raise SystemExit('wrong APK inventory')
kotlin = (root / 'NatuneMobileDataPatch.kt').read_text()
pattern = r'AudioSite\("([^"]+)",\s*"([^"]+)", listOf\(([^)]*)\), "([^"]+)", (\d+), (\d+), (\d+), (\d+)\)'
rows = re.findall(pattern, kotlin)
actual = {}
for owner, name, params, result, regs, units, offset, register in rows:
    owner = owner.replace('\\$', '$')
    signature = owner + '->' + name + '(' + ''.join(re.findall(r'"([^"]+)"', params)) + ')' + result
    if signature in actual: raise SystemExit('duplicate Kotlin target')
    actual[signature] = tuple(map(int, (regs, units, offset, register)))
expected = {}
for row in report['audio_callers']:
    if row['flags'] != 17 or len(row['calls']) != 1: raise SystemExit('unexpected method shape')
    call = row['calls'][0]
    if call['target'] != 'Lr8/mg6;->u(Landroid/content/Context;)Z' or call['invoke_opcode'] != 113:
        raise SystemExit('unexpected call target/opcode')
    expected[row['signature']] = (row['registers'], row['code_units'], call['code_unit'], call['move_result_register'])
if len(expected) != 6 or actual != expected: raise SystemExit('Kotlin sites differ from exact inventory')
java = (root / 'VerifyNatuneMobilePatch.java').read_text()
verifier = {m: (int(offset), int(register)) for m, offset, register in
            re.findall(r'new Site\("([^"]+)", (\d+), (\d+)\)', java)}
if verifier != {k: (v[2], v[3]) for k, v in expected.items()}:
    raise SystemExit('verification sites differ from exact inventory')
if 'AppTarget(version = "N202611030452"' not in kotlin or 'reference.definingClass == "Lr8/mg6;" && reference.name == "u"' not in kotlin:
    raise SystemExit('version/helper guard differs')
page = (root / 'runtime/dev/purple/patches/natune/PurpleSettingsPage.java').read_text()
if 'Natune N202611030452' not in page or 'N202610191717' in page:
    raise SystemExit('settings page displays the wrong Natune target')
print('PASS: six Kotlin and Java site guards and settings target match the current APK inventory')
