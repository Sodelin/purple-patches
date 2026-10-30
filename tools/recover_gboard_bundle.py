"""Verify a retained hosted build ZIP and optionally stream its main Morphe bundle.

The selected proof must be a trusted, previously recorded build verification.
This tool neither downloads nor installs anything and does not update a feed.
"""
import argparse
import json
import re
import sys
from pathlib import Path

from compare_gboard_builds import committed_profile, committed_selection, inspect_archive, require, sha


def proof_inputs(proof):
    require(isinstance(proof,dict) and proof.get('schema')=='purple.hosted-build-verification.v1',
            'Unsupported build verification record')
    commit=proof.get('source_commit'); version=proof.get('version')
    require(isinstance(commit,str) and re.fullmatch(r'[0-9a-f]{40}',commit) is not None,
            'Invalid selected source commit')
    require(isinstance(version,str) and re.fullmatch(r'\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?',version) is not None,
            'Invalid selected bundle version')
    upstream=proof.get('upstream_commit')
    require(isinstance(upstream,str) and re.fullmatch(r'[0-9a-f]{40}',upstream) is not None
            and proof.get('all_seven_artifact_members_identical') is True,
            'Missing matching-source reproducibility evidence')
    runs=proof.get('workflow_runs'); ids=proof.get('artifact_ids')
    sizes=proof.get('artifact_zip_bytes'); hashes=proof.get('artifact_zip_sha256')
    require(all(isinstance(x,list) for x in (runs,ids,sizes,hashes)), 'Incomplete artifact pins')
    require(len(runs)>=2 and len(ids)==len(sizes)==len(hashes)==len(runs), 'Incomplete reproducible builds')
    require(all(type(x) is int and x>0 for x in runs+ids+sizes), 'Invalid artifact identifiers or sizes')
    require(len(set(runs))==len(runs) and len(set(ids))==len(ids), 'Duplicate build or artifact identity')
    require(all(isinstance(x,str) and re.fullmatch(r'[0-9a-f]{64}',x) is not None for x in hashes),
            'Invalid artifact digest')
    main_hash=proof.get('main_mpp_sha256')
    require(isinstance(main_hash,str) and re.fullmatch(r'[0-9a-f]{64}',main_hash) is not None,
            'Invalid main bundle digest')
    return commit,version,list(zip(sizes,hashes)),main_hash,upstream


def recover(raw,proof,selection=committed_selection):
    commit,version,pins,main_hash,proof_upstream=proof_inputs(proof)
    require((len(raw),sha(raw)) in pins, 'Archive does not match a selected build artifact')
    epoch,overlay,source_bytes=selection(commit)
    upstream_pin,tool_inputs=committed_profile(source_bytes)
    require(upstream_pin==proof_upstream,'Selected source and proof upstream differ')
    members=inspect_archive(raw,commit,version,epoch,overlay,source_bytes,upstream_pin,tool_inputs)
    name=f'patches-{version}.mpp'; main=members[name]
    require(sha(main)==main_hash, 'Main bundle does not match the selected digest')
    return main,{
        'source_commit':commit,'version':version,'archive_bytes':len(raw),'archive_sha256':sha(raw),
        'main_mpp_name':name,'main_mpp_bytes':len(main),'main_mpp_sha256':main_hash,
        'device_test_performed':False,'release_changed':False,
        'limits':'Selected archived bytes and committed source were checked; device and release acceptance are separate.'
    }


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--proof',required=True,help='Previously reviewed build-verification JSON')
    parser.add_argument('--extract-main',action='store_true',help='Write only the verified MPP to non-terminal stdout')
    args=parser.parse_args()
    if args.extract_main and sys.stdout.isatty():
        raise SystemExit('Refuse to write binary patch bytes to a terminal')
    proof=json.loads(Path(args.proof).read_text(encoding='utf-8'))
    _,_,pins,_,_=proof_inputs(proof)
    raw=sys.stdin.buffer.read(max(size for size,_ in pins)+1)
    main,report=recover(raw,proof)
    if args.extract_main:
        sys.stdout.buffer.write(main)
        sys.stdout.buffer.flush()
    else:
        print(json.dumps(report,indent=2))


if __name__=='__main__':main()
