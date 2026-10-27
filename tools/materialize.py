"""Apply the small authored overlay to a clean, pinned upstream checkout OFF PHONE."""
import hashlib,json,subprocess,sys
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
    build=destination/'patches/build.gradle.kts'
    replace(build,'name = "Gboard Patches"','name = "Purple Patches"')
    replace(build,'description = "Morphe patches for Gboard."','description = "Optional Gboard Whisper 3 dictation and AI translation; based on Jason Wu Gboard patches."')
    # Retain upstream license/notices; identify our distinct distribution source.
    text=build.read_text().replace('https://github.com/jasonwu1994/gboard-patches','https://github.com/Sodelin/purple-patches').replace('author = "Jason Wu"','author = "Purple Patches contributors; upstream Jason Wu"')
    build.write_text(text)
    manifest={'upstream_commit':PIN,'overlay':[{'path':str(p.relative_to(ROOT)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted((ROOT/'gboard').rglob('*')) if p.is_file()]}
    (destination/'purple-provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')

if __name__=='__main__':materialize(Path(sys.argv[1]))
