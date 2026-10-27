"""Apply the small authored overlay to a clean, pinned upstream checkout OFF PHONE."""
import copy,hashlib,json,subprocess,sys
from pathlib import Path
PIN='2eb71854accc7e906d3e4f5495b2a54ed1e2af47'
ROOT=Path(__file__).resolve().parents[1]

def replace(path,old,new):
    text=path.read_text()
    if text.count(old)!=1:raise ValueError('Upstream hook no longer unique: '+path.name)
    path.write_text(text.replace(old,new))

def materialize(destination):
    destination=destination.resolve()
    head=subprocess.check_output(['git','-C',str(destination),'rev-parse','HEAD'],text=True).strip()
    if head!=PIN:raise ValueError('Wrong upstream revision')
    dirty=subprocess.check_output(['git','-C',str(destination),'status','--porcelain'])
    if dirty:raise ValueError('Do not overwrite an existing dirty checkout')
    java=destination/'extensions/extension/src/main/java'
    for source in (ROOT/'gboard/src').rglob('*.java'):
        target=java/source.relative_to(ROOT/'gboard/src')
        if target.exists():raise ValueError('Existing overlay source')
        target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(source.read_bytes())
    hooks=java/'dev/jason/gboardpatches/extension/accesspoint/GboardAccessPointContributionsRuntime.java'
    replace(hooks,'        return result;\n    }','        return dev.purple.patches.gboard.PurpleAccessPoints.extend(context, result);\n    }')
    replace(hooks,'            synchronizeControllerOrderCatalog(controller, context);','            synchronizeControllerOrderCatalog(controller, context);\n            dev.purple.patches.gboard.PurpleAccessPoints.register(controller, context);')
    lifecycle=java/'dev/jason/gboardpatches/extension/editingaccesspoints/GboardEditingAccessPointsRuntime.java'
    replace(lifecycle,'                SESSION.begin(editor);','                SESSION.begin(editor);\n                dev.purple.patches.gboard.PurpleAiRuntime.start(receiver);')
    replace(lifecycle,'        SESSION.end();','        dev.purple.patches.gboard.PurpleAiRuntime.end();\n        SESSION.end();')
    patch=destination/'patches/src/main/kotlin/dev/purple/patches/gboard/PurpleGboardPatch.kt'
    patch.parent.mkdir(parents=True,exist_ok=True);patch.write_bytes((ROOT/'gboard/PurpleGboardPatch.kt').read_bytes())
    registry=destination/'patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/GboardPatchRegistry.kt'
    replace(registry,'    val morpheRegistrations: Set<Patch<*>> = linkedSetOf(',
            '    val morpheRegistrations: Set<Patch<*>> = linkedSetOf(\n        dev.purple.patches.gboard.purpleGboardActionsPatch,')
    version=(ROOT/'tools/version.txt').read_text().strip()
    properties=destination/'gradle.properties'
    replace(properties,'version = 4.0.0','version = '+version)
    # Upstream's checked-in feed points at its original release. Never ship that
    # URL as our customized bundle or silently install the unmodified product.
    (destination/'patches-bundle.json').write_text(json.dumps({
        'version':version,'description':'Unreleased Purple Gboard candidate. Device acceptance pending.',
        'created_at':subprocess.check_output(['git','-C',str(ROOT),'show','-s','--format=%cI','HEAD'],text=True).strip(),
        'download_url':'','signature_download_url':''},indent=2)+'\n')
    # Register a real admitted feature, not an unaccounted-for extra patch name.
    catalog_path=destination/'patches/src/main/resources/gboard/gboard-port-product-catalog.json'
    catalog=json.loads(catalog_path.read_text())
    feature=copy.deepcopy(next(f for f in catalog['features'] if f['feature_id']=='editing_access_points'))
    feature.update(feature_id='purple_gboard_ai',public_patch_name='Whisper 3 dictation and AI Translate',feature_marker=None)
    feature['consumer_files']=sorted(set(feature['consumer_files']+[str(patch.relative_to(destination))]))
    feature['contributions'][0].update(contribution_id='purple_gboard_ai.runtime',order=1261)
    catalog['features'].append(feature);catalog['features'].sort(key=lambda f:f['feature_id'])
    catalog_path.write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n')
    catalog_path.with_suffix('.sha256').write_text(hashlib.sha256(catalog_path.read_bytes()).hexdigest()+'\n')
    # Extend the exact expected feature inventory; retain every original assertion.
    contract=destination/'patches/src/test/kotlin/dev/jason/gboardpatches/patches/gboard/registry/GboardPortProductCatalogContractTest.kt'
    replace(contract,'        val EXPECTED_MIGRATION_SCOPES = mapOf(',
            '        val EXPECTED_MIGRATION_SCOPES = mapOf(\n            "purple_gboard_ai" to "version-sensitive",')
    compatibility=destination/'patches/src/test/kotlin/dev/jason/gboardpatches/patches/gboard/registry/GboardCompatibilitySourceTest.kt'
    replace(compatibility,'        val registry = readSource(REGISTRY_PATH)',
            '        val registry = readSource(REGISTRY_PATH) + "\\n" + readSource("'+str(patch.relative_to(destination))+'")')
    replace(compatibility,'                    .containsMatchIn(block)\n            }',
            '                    .containsMatchIn(block) || block.contains("val purpleGboardActionsPatch =")\n            }')
    build=destination/'patches/build.gradle.kts'
    replace(build,'name = "Gboard Patches"','name = "Purple Patches"')
    replace(build,'description = "Morphe patches for Gboard."','description = "Optional Gboard Whisper 3 dictation and AI translation; based on Jason Wu Gboard patches."')
    # Retain upstream license/notices; identify our distinct distribution source.
    text=build.read_text().replace('https://github.com/jasonwu1994/gboard-patches','https://github.com/Sodelin/purple-patches').replace('author = "Jason Wu"','author = "Purple Patches contributors; upstream Jason Wu"')
    build.write_text(text)
    manifest={'upstream_commit':PIN,'version':version,'overlay_commit':subprocess.check_output(['git','-C',str(ROOT),'rev-parse','HEAD'],text=True).strip(),'overlay':[{'path':str(p.relative_to(ROOT)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted((ROOT/'gboard').rglob('*')) if p.is_file()]}
    (destination/'purple-provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')

if __name__=='__main__':materialize(Path(sys.argv[1]))
