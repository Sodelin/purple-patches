"""Compare two hosted Gboard builds without publishing or writing artifacts locally."""
import argparse
from datetime import datetime, timezone
import hashlib
import io
import json
import re
from pathlib import Path
import subprocess
import zipfile

from materialize import PIN, TOOL_INPUTS

ROOT=Path(__file__).resolve().parents[1]
REPO='Sodelin/purple-patches'
WORKFLOW='Build Gboard patch bundle off phone'


def require(condition,message):
    if not condition:raise ValueError(message)


def sha(value):return hashlib.sha256(value).hexdigest()


def inspect_archive(raw,commit,version,epoch_ms,overlay_names,source_bytes):
    """Check all selected bytes and provenance before comparing two public artifacts."""
    require(re.fullmatch(r'[0-9a-f]{40}',commit) is not None,'Invalid source commit')
    require(re.fullmatch(r'\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?',version) is not None,
            'Invalid candidate version')
    require(type(epoch_ms) is int and epoch_ms%1000==0,'Invalid source timestamp')
    names={'SHA256SUMS',f'patches-{version}.mpp',f'patches-{version}-sources.mpp',
           f'patches-{version}-javadoc.mpp','patches-list.json','patches-bundle.json','purple-provenance.json'}
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        listed=archive.namelist()
        require(len(listed)==len(set(listed)) and set(listed)==names,'Artifact member set differs')
        members={name:archive.read(name) for name in names}
    checks={}
    for line in members['SHA256SUMS'].decode('utf-8').splitlines():
        digest,separator,name=line.partition('  ')
        require(separator and name in names-{'SHA256SUMS'} and name not in checks
                and re.fullmatch(r'[0-9a-f]{64}',digest) is not None,'Invalid checksum list')
        checks[name]=digest
    require(set(checks)==names-{'SHA256SUMS'},'Incomplete checksum list')
    for name,digest in checks.items():require(sha(members[name])==digest,'Artifact checksum mismatch: '+name)
    provenance=json.loads(members['purple-provenance.json'])
    require(provenance.get('overlay_commit')==commit and provenance.get('version')==version
            and provenance.get('upstream_commit')==PIN,'Provenance identity mismatch')
    require(type(provenance.get('manifest_timestamp_ms')) is int and provenance.get('manifest_timestamp_ms')==epoch_ms
            and provenance.get('manifest_timestamp_basis')=='source commit time','Timestamp provenance mismatch')
    for field,expected in (('overlay',set(overlay_names)),('build_inputs',set(TOOL_INPUTS))):
        entries=provenance.get(field)
        require(isinstance(entries,list),'Missing provenance input list')
        paths=[]
        for item in entries:
            require(isinstance(item,dict) and set(item)=={'path','sha256'},'Invalid provenance entry')
            path=item['path'];digest=item['sha256']
            require(isinstance(path,str) and path in expected and path not in paths
                    and isinstance(digest,str) and re.fullmatch(r'[0-9a-f]{64}',digest) is not None,
                    'Unexpected or duplicate provenance input')
            require(sha(source_bytes(path))==digest,'Committed input hash mismatch: '+path)
            paths.append(path)
        require(set(paths)==expected,'Incomplete provenance input set')
    feed=json.loads(members['patches-bundle.json'])
    created=datetime.fromtimestamp(epoch_ms//1000,timezone.utc).replace(tzinfo=None).isoformat(timespec='seconds')
    require(feed.get('version')==version and feed.get('created_at')==created
            and feed.get('download_url')=='','Unreleased feed metadata mismatch')
    for suffix in ('','-sources','-javadoc'):
        name=f'patches-{version}{suffix}.mpp'
        with zipfile.ZipFile(io.BytesIO(members[name])) as mpp:
            listed=mpp.namelist()
            require(len(listed)==len(set(listed)) and 'META-INF/MANIFEST.MF' in listed,
                    'Invalid MPP manifest membership')
            manifest=mpp.read('META-INF/MANIFEST.MF').decode('utf-8')
        timestamps=[line for line in manifest.splitlines() if line.startswith('Timestamp: ')]
        require(timestamps==['Timestamp: '+str(epoch_ms)],'MPP timestamp mismatch')
    return members


def compare_archives(first,second,commit,version,epoch_ms,overlay_names,source_bytes):
    a=inspect_archive(first,commit,version,epoch_ms,overlay_names,source_bytes)
    b=inspect_archive(second,commit,version,epoch_ms,overlay_names,source_bytes)
    changed=[name for name in sorted(a) if a[name]!=b[name]]
    main=f'patches-{version}.mpp'
    return {'source_commit':commit,'version':version,'manifest_timestamp_ms':epoch_ms,
            'outer_zip_sha256':[sha(first),sha(second)],'outer_zip_bytes':[len(first),len(second)],
            'outer_zip_bytes_identical':first==second,
            'members_sha256':{name:sha(a[name]) for name in sorted(a)},
            'different_members':changed,'all_members_identical':not changed,
            'main_mpp_identical':a[main]==b[main],
            'limits':'Two hosted builds only; runner, SDK, dependencies, phone behavior and release acceptance are separate.'}


def gh(*args):return subprocess.check_output(['gh',*map(str,args)],cwd=ROOT)


def artifact_for(run,commit):
    meta=json.loads(gh('run','view',run,'--repo',REPO,'--json','status,conclusion,headSha,workflowName'))
    require(meta.get('status')=='completed' and meta.get('conclusion')=='success'
            and meta.get('headSha')==commit and meta.get('workflowName')==WORKFLOW,
            'Run is not a successful build of the selected source')
    selected=[];page=1
    while True:
        data=json.loads(gh('api',f'repos/{REPO}/actions/runs/{run}/artifacts?per_page=100&page={page}'))
        items=data['artifacts'];selected.extend(x for x in items if x['name']=='purple-gboard-candidate' and not x['expired'])
        if len(items)<100:break
        page+=1
    require(len(selected)==1,'Expected one unexpired candidate artifact for the run')
    item=selected[0]
    return item['id'],gh('api',f"repos/{REPO}/actions/artifacts/{item['id']}/zip")


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--first-run',type=int,required=True)
    parser.add_argument('--second-run',type=int,required=True)
    parser.add_argument('--source-commit',required=True)
    parser.add_argument('--version',required=True)
    args=parser.parse_args()
    require(args.first_run>0 and args.second_run>0 and args.first_run!=args.second_run,'Select two different run IDs')
    require(re.fullmatch(r'[0-9a-f]{40}',args.source_commit) is not None,'Invalid source commit')
    def git(*values):return subprocess.check_output(['git','--no-replace-objects','-C',str(ROOT),*values])
    tree=git('ls-tree','-r','-z','--full-tree',args.source_commit,'--','gboard').split(b'\0')
    overlay=[]
    for entry in tree:
        if not entry:continue
        header,name=entry.split(b'\t',1);mode,kind,_=header.split(b' ',2)
        require(mode==b'100644' and kind==b'blob','Unsupported committed overlay mode')
        overlay.append(name.decode('utf-8'))
    require(overlay,'Committed overlay is empty')
    epoch=int(git('show','-s','--format=%ct',args.source_commit))*1000
    cache={}
    def source_bytes(path):
        if path not in cache:cache[path]=git('show',args.source_commit+':'+path)
        return cache[path]
    first_id,first=artifact_for(args.first_run,args.source_commit)
    second_id,second=artifact_for(args.second_run,args.source_commit)
    result=compare_archives(first,second,args.source_commit,args.version,epoch,overlay,source_bytes)
    result.update({'workflow_runs':[args.first_run,args.second_run],'artifact_ids':[first_id,second_id],
                   'overlay_files_verified':len(overlay),'build_inputs_verified':len(TOOL_INPUTS),
                   'phone_or_model_action':False,'release_published':False})
    print(json.dumps(result,indent=2))
    return 0 if result['all_members_identical'] else 2


if __name__=='__main__':raise SystemExit(main())
