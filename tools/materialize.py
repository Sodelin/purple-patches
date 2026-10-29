"""Apply the small authored overlay to a clean, pinned upstream checkout OFF PHONE."""
import copy,hashlib,json,os,re,subprocess,sys
from pathlib import Path
from bundle_feed import bundle_feed
PIN='2eb71854accc7e906d3e4f5495b2a54ed1e2af47'
ROOT=Path(__file__).resolve().parents[1]
TOOL_INPUTS=('tools/materialize.py','tools/bundle_feed.py','tools/check_materialization.py',
             'tools/version.txt','.github/workflows/build.yml')

def snapshot_source(root):
    """Select exact committed bytes; refuse changed, ignored or linked overlay files."""
    root=Path(root).resolve()
    def git(*args):
        return subprocess.check_output(['git','--no-replace-objects','-C',str(root),*args])
    commit=git('rev-parse','--verify','HEAD').decode('ascii').strip()
    if not re.fullmatch(r'[0-9a-f]{40}',commit):raise ValueError('Invalid source commit')
    entries=git('ls-tree','-r','-z','--full-tree',commit,'--','gboard').split(b'\0')
    tracked=set()
    for entry in entries:
        if not entry:continue
        header,name=entry.split(b'\t',1)
        mode,kind,_=header.split(b' ',2)
        if mode!=b'100644' or kind!=b'blob':raise ValueError('Unsupported overlay file mode')
        tracked.add(os.fsdecode(name))
    if not tracked:raise ValueError('Missing committed Gboard overlay')
    actual=set()
    overlay=root/'gboard'
    if overlay.is_symlink() or not overlay.is_dir():raise ValueError('Missing or linked overlay directory')
    for path in overlay.rglob('*'):
        if path.is_symlink():raise ValueError('Linked overlay input refused')
        if path.is_dir():continue
        if not path.is_file():raise ValueError('Unsupported overlay input')
        actual.add(path.relative_to(root).as_posix())
    if actual!=tracked:raise ValueError('Overlay files differ from committed membership')
    snapshot={}
    for name in sorted(tracked|set(TOOL_INPUTS)):
        path=root/name
        for parent in path.parents:
            if parent==root:break
            if parent.is_symlink():raise ValueError('Linked source directory refused')
        if path.is_symlink() or not path.is_file():raise ValueError('Missing or linked source input: '+name)
        current=path.read_bytes()
        committed=git('show',commit+':'+name)
        if current!=committed:raise ValueError('Source input differs from commit: '+name)
        snapshot[name]=committed
    created_at=git('show','-s','--format=%cI',commit).decode('ascii').strip()
    epoch=git('show','-s','--format=%ct',commit).decode('ascii').strip()
    if not re.fullmatch(r'-?[0-9]+',epoch):raise ValueError('Invalid source timestamp')
    timestamp=int(epoch)*1000
    if not -(1<<63)<=timestamp<(1<<63):raise ValueError('Source timestamp exceeds manifest Long')
    return commit,created_at,timestamp,snapshot

def manifest_timestamp_override(timestamp):
    if type(timestamp) is not int or not -(1<<63)<=timestamp<(1<<63):
        raise ValueError('Invalid manifest timestamp')
    return ('\n// Keep Morphe bundle metadata tied to the exact source commit.\n'
            'tasks.withType<Jar>().configureEach {\n'
            '    manifest.attributes["Timestamp"] = "'+str(timestamp)+'"\n'
            '}\n')

def replace(path,old,new):
    text=path.read_text()
    if text.count(old)!=1:raise ValueError('Upstream hook no longer unique: '+path.name)
    path.write_text(text.replace(old,new))

def materialize(destination):
    destination=destination.resolve()
    source_commit,created_at,timestamp,snapshot=snapshot_source(ROOT)
    head=subprocess.check_output(['git','-C',str(destination),'rev-parse','HEAD'],text=True).strip()
    if head!=PIN:raise ValueError('Wrong upstream revision')
    dirty=subprocess.check_output(['git','-C',str(destination),'status','--porcelain'])
    if dirty:raise ValueError('Do not overwrite an existing dirty checkout')
    java=destination/'extensions/extension/src/main/java'
    for name,body in snapshot.items():
        if not name.startswith('gboard/src/') or not name.endswith('.java'):continue
        target=java/Path(name).relative_to('gboard/src')
        if target.exists():raise ValueError('Existing overlay source')
        target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(body)
    hooks=java/'dev/jason/gboardpatches/extension/accesspoint/GboardAccessPointContributionsRuntime.java'
    replace(hooks,'        return result;\n    }','        return dev.purple.patches.gboard.PurpleAccessPoints.extend(context, result);\n    }')
    replace(hooks,'            synchronizeControllerOrderCatalog(controller, context);','            synchronizeControllerOrderCatalog(controller, context);\n            dev.purple.patches.gboard.PurpleAccessPoints.register(controller, context);')
    lifecycle=java/'dev/jason/gboardpatches/extension/editingaccesspoints/GboardEditingAccessPointsRuntime.java'
    replace(lifecycle,'                SESSION.begin(editor);','                SESSION.begin(editor);\n                dev.purple.patches.gboard.PurpleAiRuntime.start(receiver);')
    replace(lifecycle,'        SESSION.end();','        dev.purple.patches.gboard.PurpleAiRuntime.end();\n        SESSION.end();')
    patch=destination/'patches/src/main/kotlin/dev/purple/patches/gboard/PurpleGboardPatch.kt'
    patch.parent.mkdir(parents=True,exist_ok=True);patch.write_bytes(snapshot['gboard/PurpleGboardPatch.kt'])
    resource_helper=destination/'patches/src/main/java/dev/purple/patches/gboard/PurpleNetworkResource.java'
    if resource_helper.exists():raise ValueError('Existing patch-time resource helper')
    resource_helper.parent.mkdir(parents=True,exist_ok=True);resource_helper.write_bytes(snapshot['gboard/PurpleNetworkResource.java'])
    registry=destination/'patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/GboardPatchRegistry.kt'
    replace(registry,'    val morpheRegistrations: Set<Patch<*>> = linkedSetOf(',
            '    val morpheRegistrations: Set<Patch<*>> = linkedSetOf(\n        dev.purple.patches.gboard.purpleGboardActionsPatch,')
    version=snapshot['tools/version.txt'].decode('utf-8').strip()
    properties=destination/'gradle.properties'
    replace(properties,'version = 4.0.0','version = '+version)
    # Upstream's checked-in feed points at its original release. Never ship that
    # URL as our customized bundle or silently install the unmodified product.
    (destination/'patches-bundle.json').write_text(json.dumps(bundle_feed(version,created_at),indent=2)+'\n')
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
    build.write_text(text+manifest_timestamp_override(timestamp))
    manifest={'upstream_commit':PIN,'version':version,'overlay_commit':source_commit,
              'manifest_timestamp_ms':timestamp,'manifest_timestamp_basis':'source commit time',
              'overlay':[{'path':name,'sha256':hashlib.sha256(body).hexdigest()} for name,body in snapshot.items() if name.startswith('gboard/')],
              'build_inputs':[{'path':name,'sha256':hashlib.sha256(snapshot[name]).hexdigest()} for name in TOOL_INPUTS]}
    (destination/'purple-provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')

if __name__=='__main__':materialize(Path(sys.argv[1]))
