"""In-memory fixture controls for the Gboard build comparison."""
import io
import json
import unittest
import zipfile

from compare_gboard_builds import TOOL_INPUTS, compare_archives, inspect_archive, sha
from materialize import PIN

COMMIT='a'*40
VERSION='0.1.0-alpha.14'
EPOCH=1793235723000
OVERLAY={'gboard/src/Fixture.java'}
SOURCE={name:('committed '+name).encode() for name in OVERLAY|set(TOOL_INPUTS)}


def zip_bytes(files,reverse=False):
    result=io.BytesIO()
    with zipfile.ZipFile(result,'w',compression=zipfile.ZIP_DEFLATED) as archive:
        for name in sorted(files,reverse=reverse):archive.writestr(name,files[name])
    return result.getvalue()


def artifact(payload=b'first',tamper_after_checks=False,extra_overlay=False,reverse=False,
             manifest_epoch=EPOCH,download_url=''):
    manifest=f'Manifest-Version: 1.0\nTimestamp: {manifest_epoch}\n'.encode()
    mpp=zip_bytes({'META-INF/MANIFEST.MF':manifest,'fixture.bin':payload})
    source_mpp=zip_bytes({'META-INF/MANIFEST.MF':manifest,'fixture-source':b'source'})
    doc_mpp=zip_bytes({'META-INF/MANIFEST.MF':manifest,'fixture-doc':b'doc'})
    entries=lambda names:[{'path':name,'sha256':sha(SOURCE[name])} for name in sorted(names)]
    overlay=entries(OVERLAY)
    if extra_overlay:overlay.append({'path':'gboard/extra.java','sha256':'0'*64})
    provenance={'overlay_commit':COMMIT,'version':VERSION,'upstream_commit':PIN,
                'manifest_timestamp_ms':EPOCH,'manifest_timestamp_basis':'source commit time',
                'overlay':overlay,'build_inputs':entries(TOOL_INPUTS)}
    feed={'version':VERSION,'created_at':'2026-10-29T01:02:03','download_url':download_url}
    files={f'patches-{VERSION}.mpp':mpp,f'patches-{VERSION}-sources.mpp':source_mpp,
           f'patches-{VERSION}-javadoc.mpp':doc_mpp,'patches-list.json':b'[]',
           'patches-bundle.json':json.dumps(feed).encode(),
           'purple-provenance.json':json.dumps(provenance).encode()}
    files['SHA256SUMS']=''.join(f'{sha(body)}  {name}\n' for name,body in sorted(files.items())).encode()
    if tamper_after_checks:files['patches-list.json']=b'changed after checksum'
    return zip_bytes(files,reverse)


class ComparisonControls(unittest.TestCase):
    def compare(self,first,second):
        return compare_archives(first,second,COMMIT,VERSION,EPOCH,OVERLAY,SOURCE.__getitem__)

    def test_identical_members_ignore_outer_wrapper_order(self):
        result=self.compare(artifact(),artifact(reverse=True))
        self.assertTrue(result['all_members_identical'])
        self.assertTrue(result['main_mpp_identical'])
        self.assertFalse(result['outer_zip_bytes_identical'])

    def test_detects_two_valid_but_different_patch_bundles(self):
        result=self.compare(artifact(b'first'),artifact(b'second'))
        self.assertFalse(result['main_mpp_identical'])
        self.assertIn(f'patches-{VERSION}.mpp',result['different_members'])

    def test_refuses_bad_checksums_uncommitted_inputs_and_extra_provenance(self):
        with self.assertRaises(ValueError):
            inspect_archive(artifact(tamper_after_checks=True),COMMIT,VERSION,EPOCH,OVERLAY,SOURCE.__getitem__)
        with self.assertRaises(ValueError):
            inspect_archive(artifact(extra_overlay=True),COMMIT,VERSION,EPOCH,OVERLAY,SOURCE.__getitem__)
        with self.assertRaises(ValueError):
            inspect_archive(artifact(),COMMIT,VERSION,EPOCH,OVERLAY,lambda name:b'wrong bytes')
        with self.assertRaises(ValueError):
            inspect_archive(artifact(),COMMIT,VERSION,EPOCH+1000,OVERLAY,SOURCE.__getitem__)
        with self.assertRaises(ValueError):
            inspect_archive(artifact(manifest_epoch=EPOCH+1000),COMMIT,VERSION,EPOCH,OVERLAY,SOURCE.__getitem__)
        with self.assertRaises(ValueError):
            inspect_archive(artifact(download_url='https://example.invalid/other.mpp'),COMMIT,VERSION,EPOCH,OVERLAY,SOURCE.__getitem__)


if __name__=='__main__':unittest.main(verbosity=2)
