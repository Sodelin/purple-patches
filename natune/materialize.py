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
SETTINGS_SOURCES = ('RuntimeSettings.java', 'PurpleSettingsPage.java', 'NatuneSettingsNavigation.java')
FOLDER_SOURCES = ('VerifiedStreamCopy.java', 'NatuneCachedSource.java', 'TemporarySpaceBudget.java',
                  'NatuneCoroutineStop.java', 'SharedDocumentDestination.java', 'MediaStoreAudioDestination.java',
                  'NatuneFolderCopy.java', 'FlacIntegrity.java')
FLAC_LIBRARY_SHA256 = '72ee124f75f3be57c18df3a542076f126a3e5c3f6e477bf368ef4fcc6e2e9b8244'
FLAC_LICENSE_SHA256 = '7866ee98760fc1f0156b4fe6bf530257e02be487ab3fd94e2b63799dd32d6b2c'


def materialize(destination, local_flac=False, runtime_settings=False, folder_carrier=False):
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
    # The pinned template's plugin repositories omit Maven Central. The plugin
    # portal can mirror transitive libraries, but that route was unavailable in
    # one hosted run. Add the canonical repository without changing any pins.
    settings = destination / 'settings.gradle.kts'
    settings_text = settings.read_text()
    anchor = '        gradlePluginPortal()\n        google()\n'
    if settings_text.count(anchor) != 1:
        raise RuntimeError('Template plugin repository hook changed')
    settings.write_text(settings_text.replace(anchor, anchor + '        mavenCentral()\n'))
    shutil.rmtree(destination / 'patches/src/main/kotlin/app/template')
    target = destination / 'patches/src/main/kotlin/dev/purple/patches/natune/NatuneMobileDataPatch.kt'
    target.parent.mkdir(parents=True, exist_ok=False)
    source = (ROOT / 'NatuneMobileDataPatch.kt').read_bytes()
    target.write_bytes(source)
    if local_flac:
        (target.parent / 'NatuneLocalFlacPatch.kt').write_bytes((ROOT / 'NatuneLocalFlacPatch.kt').read_bytes())
    if runtime_settings:
        if not local_flac:
            raise RuntimeError('Runtime settings currently bind both selected audio dependencies')
        (target.parent / 'NatuneRuntimeSettingsPatch.kt').write_bytes((ROOT / 'NatuneRuntimeSettingsPatch.kt').read_bytes())
    if folder_carrier:
        (target.parent / 'NatuneFlacFolderCarrierPatch.kt').write_bytes((ROOT / 'NatuneFlacFolderCarrierPatch.kt').read_bytes())
        resource = destination / 'patches/src/main/resources/purple/natune'
        resource.mkdir(parents=True, exist_ok=False)
        for env_name, filename, expected in (
            ('NATUNE_FLAC_LIBRARY', 'libpurple_natune_flac.so', FLAC_LIBRARY_SHA256),
            ('NATUNE_FLAC_LICENSE', 'FLAC-LICENSE.txt', FLAC_LICENSE_SHA256),
        ):
            source_path = Path(os.environ[env_name]).resolve()
            if not source_path.is_relative_to(runner) or not source_path.is_file():
                raise RuntimeError('Expected selected carrier input inside runner temporary storage')
            data = source_path.read_bytes()
            if hashlib.sha256(data).hexdigest() != expected:
                raise RuntimeError('Selected carrier input hash differs')
            (resource / filename).write_bytes(data)
    if runtime_settings or folder_carrier:
        extension = destination / 'extensions/extension'
        shutil.rmtree(extension / 'src/main/java/app/template')
        settings = extension / 'src/main/java/dev/purple/patches/natune'
        settings.mkdir(parents=True, exist_ok=False)
        for name in (SETTINGS_SOURCES if runtime_settings else ()) + (FOLDER_SOURCES if folder_carrier else ()):
            shutil.copyfile(ROOT / 'runtime/dev/purple/patches/natune' / name, settings / name)
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
    version = '0.4.0-dev.1' if folder_carrier else '0.3.0-dev.1' if runtime_settings else '0.2.0-dev.2' if local_flac else '0.1.0-alpha.1'
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
             'runtime_settings_app_binding': runtime_settings,
             'runtime_settings_navigation_binding': runtime_settings,
             'canonical_maven_central_plugin_resolution': True,
             'runtime_settings_patch_sha256': hashlib.sha256((ROOT / 'NatuneRuntimeSettingsPatch.kt').read_bytes()).hexdigest() if runtime_settings else None,
             'runtime_settings_sources': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in
                 (ROOT / 'runtime/dev/purple/patches/natune' / name for name in SETTINGS_SOURCES)} if runtime_settings else {},
             'folder_carrier_included': folder_carrier,
             'folder_carrier_patch_sha256': hashlib.sha256((ROOT / 'NatuneFlacFolderCarrierPatch.kt').read_bytes()).hexdigest() if folder_carrier else None,
             'folder_carrier_sources': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in
                 (ROOT / 'runtime/dev/purple/patches/natune' / name for name in FOLDER_SOURCES)} if folder_carrier else {},
             'flac_native_sha256': FLAC_LIBRARY_SHA256 if folder_carrier else None,
             'flac_license_sha256': FLAC_LICENSE_SHA256 if folder_carrier else None,
             'folder_action_bound': False,
             'strict_flac_implemented': False, 'additional_providers_implemented': False,
             'phone_acceptance': False}
    (destination / 'purple-natune-provenance.json').write_text(json.dumps(proof, indent=2) + '\n')


if __name__ == '__main__':
    materialize(Path(sys.argv[1]), local_flac='--local-flac' in sys.argv[2:],
                runtime_settings='--runtime-settings' in sys.argv[2:], folder_carrier='--folder-carrier' in sys.argv[2:])
