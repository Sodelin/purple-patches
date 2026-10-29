"""Place the authored Natune patch into a clean pinned Morphe template OFF PHONE."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

PIN = 'e721536ab19e30d16ce9d31ae1830ca299aad383'
ROOT = Path(__file__).resolve().parent


def materialize(destination, local_flac=False, runtime_settings=False):
    if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_OS') != 'Linux':
        raise RuntimeError('Off-phone hosted materialization only')
    destination = destination.resolve()
    runner = Path(os.environ['RUNNER_TEMP']).resolve()
    if not destination.is_relative_to(runner) or destination == runner:
        raise RuntimeError('Expected a disposable checkout inside runner temporary storage')
    if subprocess.check_output(['git', '-C', str(destination), 'rev-parse', 'HEAD'], text=True).strip() != PIN:
        raise RuntimeError('Wrong template revision')
    if subprocess.check_output(['git', '-C', str(destination), 'status', '--porcelain']):
        raise RuntimeError('Do not overwrite dirty source')
    shutil.rmtree(destination / 'patches/src/main/kotlin/app/template')
    target = destination / 'patches/src/main/kotlin/dev/purple/patches/natune/NatuneMobileDataPatch.kt'
    target.parent.mkdir(parents=True, exist_ok=False)
    source = (ROOT / 'NatuneMobileDataPatch.kt').read_bytes()
    target.write_bytes(source)
    if local_flac:
        (target.parent / 'NatuneLocalFlacPatch.kt').write_bytes((ROOT / 'NatuneLocalFlacPatch.kt').read_bytes())
    if runtime_settings:
        extension = destination / 'extensions/extension'
        shutil.rmtree(extension / 'src/main/java/app/template')
        shutil.copytree(ROOT / 'runtime/dev', extension / 'src/main/java/dev')
        (extension / 'build.gradle.kts').write_text("""extension {
    name = "extensions/extension.mpe"
}
android {
    namespace = "dev.purple.patches.natune"
}
// This extension is plain Java/Android. Do not inject another Kotlin standard
// library into the app just to host two native settings classes.
configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}
""")
    version = '0.3.0-dev.1' if runtime_settings else '0.2.0-dev.2' if local_flac else '0.1.0-alpha.1'
    build = destination / 'patches/build.gradle.kts'
    text = build.read_text()
    for old, new in {
        'group = "app.template"': 'group = "dev.purple.patches"',
        'name = "UserXYZ Patches"': 'name = "Purple Natune Patches"',
        'description = "Patches for apps I like"': 'description = "Optional Natune audio policy patches; experimental exact-release admission."',
        'source = "git@github.com:UserXYZ/morphe-patches.git"': 'source = "https://github.com/Sodelin/purple-patches"',
        'author = "Awesome dev"': 'author = "Purple Patches contributors"',
    }.items():
        if text.count(old) != 1:
            raise RuntimeError('Template hook changed')
        text = text.replace(old, new)
    build.write_text(text)
    properties = destination / 'gradle.properties'
    text = properties.read_text()
    if text.count('version = 1.0.0') != 1:
        raise RuntimeError('Template version hook changed')
    properties.write_text(text.replace('version = 1.0.0', 'version = ' + version))
    proof = {'template_commit': PIN, 'overlay_commit': os.environ['GITHUB_SHA'],
             'patch_source_sha256': hashlib.sha256(source).hexdigest(),
             'target': json.loads((ROOT / 'target.json').read_text()),
             'feature': 'Allow full-quality audio on mobile data',
             'bundle_version': version,
             'local_decoder_guard_included': local_flac,
             'local_decoder_guard_sha256': hashlib.sha256((ROOT / 'NatuneLocalFlacPatch.kt').read_bytes()).hexdigest() if local_flac else None,
             'runtime_settings_extension_included': runtime_settings,
             'runtime_settings_app_binding': False,
             'runtime_settings_sources': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((ROOT / 'runtime').rglob('*.java'))} if runtime_settings else {},
             'strict_flac_implemented': False, 'additional_providers_implemented': False,
             'phone_acceptance': False}
    (destination / 'purple-natune-provenance.json').write_text(json.dumps(proof, indent=2) + '\n')


if __name__ == '__main__':
    materialize(Path(sys.argv[1]), local_flac='--local-flac' in sys.argv[2:], runtime_settings='--runtime-settings' in sys.argv[2:])
