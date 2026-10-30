"""Synthetic, in-memory controls for a retained Gboard build archive."""
import copy
import io
import sys
import unittest
from unittest import mock
import warnings
import zipfile

import compare_gboard_builds
import recover_gboard_bundle
from check_compare_gboard_builds import COMMIT, EPOCH, OVERLAY, SOURCE, VERSION, artifact
from compare_gboard_builds import PIN, sha
from recover_gboard_bundle import main, proof_inputs, recover


def selection(commit):
    if commit != COMMIT: raise AssertionError('Wrong fixture source selection')
    return EPOCH, OVERLAY, SOURCE.__getitem__


def fixture_proof(first,second):
    with zipfile.ZipFile(io.BytesIO(first)) as archive:
        main=archive.read(f'patches-{VERSION}.mpp')
    return {
        'schema':'purple.hosted-build-verification.v1', 'source_commit':COMMIT, 'version':VERSION,
        'upstream_commit':PIN, 'all_seven_artifact_members_identical':True,
        'workflow_runs':[10,11], 'artifact_ids':[20,21],
        'artifact_zip_bytes':[len(first),len(second)], 'artifact_zip_sha256':[sha(first),sha(second)],
        'main_mpp_sha256':sha(main)
    },main


class RecoveryControls(unittest.TestCase):
    def setUp(self):
        self.first=artifact()
        self.second=artifact(reverse=True)
        self.proof,self.main=fixture_proof(self.first,self.second)

    def test_either_verified_wrapper_recovers_the_same_main_bundle(self):
        for raw in (self.first,self.second):
            with self.subTest(archive_sha256=sha(raw)):
                main,report=recover(raw,self.proof,selection)
                self.assertEqual(main,self.main)
                self.assertEqual(report['main_mpp_sha256'],sha(self.main))
                self.assertFalse(report['device_test_performed'])
                self.assertFalse(report['release_changed'])

    def test_old_archive_uses_its_committed_profile_after_current_profile_changes(self):
        with mock.patch.object(compare_gboard_builds,'PIN','b'*40), \
             mock.patch.object(compare_gboard_builds,'TOOL_INPUTS',('tools/new-input.py',)), \
             mock.patch.object(recover_gboard_bundle,'PIN','b'*40,create=True):
            main,report=recover(self.first,self.proof,selection)
        self.assertEqual(main,self.main)
        self.assertEqual(report['source_commit'],COMMIT)

    def test_changed_or_unselected_archive_is_refused(self):
        with self.assertRaisesRegex(ValueError,'Archive does not match'):
            recover(self.first+b'x',self.proof,selection)
        with self.assertRaisesRegex(ValueError,'Archive does not match'):
            recover(artifact(payload=b'different'),self.proof,selection)

    def test_matching_outer_pin_does_not_hide_bad_member_or_source(self):
        bad=artifact(tamper_after_checks=True)
        proof=copy.deepcopy(self.proof)
        proof['artifact_zip_bytes'][0]=len(bad)
        proof['artifact_zip_sha256'][0]=sha(bad)
        with self.assertRaisesRegex(ValueError,'Artifact checksum mismatch'):
            recover(bad,proof,selection)
        with self.assertRaisesRegex(ValueError,'Committed input hash mismatch'):
            recover(self.first,self.proof,lambda _: (EPOCH,OVERLAY,
                    lambda path:SOURCE[path] if path=='tools/materialize.py' else b'changed'))

    def test_duplicate_zip_member_is_refused_even_with_a_matching_outer_pin(self):
        target=io.BytesIO()
        with zipfile.ZipFile(io.BytesIO(self.first)) as original, \
             zipfile.ZipFile(target,'w') as duplicate, warnings.catch_warnings():
            warnings.simplefilter('ignore',UserWarning)
            for name in original.namelist():duplicate.writestr(name,original.read(name))
            duplicate.writestr('patches-list.json',original.read('patches-list.json'))
        raw=target.getvalue();proof=copy.deepcopy(self.proof)
        proof['artifact_zip_bytes'][0]=len(raw);proof['artifact_zip_sha256'][0]=sha(raw)
        with self.assertRaisesRegex(ValueError,'Artifact member set differs'):
            recover(raw,proof,selection)

    def test_binary_output_is_refused_on_a_terminal_before_reading_a_proof(self):
        class Terminal(io.StringIO):
            def isatty(self):return True
        with mock.patch.object(sys,'argv',['recover','--proof','missing','--extract-main']), \
             mock.patch.object(sys,'stdout',Terminal()), \
             self.assertRaisesRegex(SystemExit,'Refuse to write binary'):
            main()

    def test_incomplete_reproducibility_or_wrong_main_pin_is_refused(self):
        for key,value in [('all_seven_artifact_members_identical',False),
                          ('workflow_runs',[10,10]),('main_mpp_sha256','f'*64)]:
            proof=copy.deepcopy(self.proof);proof[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):
                recover(self.first,proof,selection)
        with self.assertRaises(ValueError):
            proof_inputs({'schema':'not-a-build-proof'})

    def test_dynamic_or_mismatched_historical_profile_is_refused(self):
        proof=copy.deepcopy(self.proof);proof['upstream_commit']='b'*40
        with self.assertRaisesRegex(ValueError,'Selected source and proof upstream differ'):
            recover(self.first,proof,selection)
        invalid=b'PIN=load_pin()\nTOOL_INPUTS=("tools/materialize.py",)\n'
        with self.assertRaisesRegex(ValueError,'Nonliteral committed build profile'):
            recover(self.first,self.proof,lambda _: (EPOCH,OVERLAY,
                    lambda path:invalid if path=='tools/materialize.py' else SOURCE[path]))
        ambiguous=(f'PIN={PIN!r}\nTOOL_INPUTS=("tools/materialize.py",)\n'
                   f'if True:\n    PIN={PIN!r}\n').encode()
        with self.assertRaisesRegex(ValueError,'Ambiguous committed build profile'):
            recover(self.first,self.proof,lambda _: (EPOCH,OVERLAY,
                    lambda path:ambiguous if path=='tools/materialize.py' else SOURCE[path]))
        unsafe=(f'PIN={PIN!r}\nTOOL_INPUTS=("tools/materialize.py","../private")\n').encode()
        with self.assertRaisesRegex(ValueError,'Invalid committed build input path'):
            recover(self.first,self.proof,lambda _: (EPOCH,OVERLAY,
                    lambda path:unsafe if path=='tools/materialize.py' else SOURCE[path]))


if __name__=='__main__':unittest.main(verbosity=2)
