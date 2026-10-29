"""Offline controls for release and feed gates, including optimized Python."""
import contextlib
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import zipfile

import export_patch_bundle as export
import publish_private_candidates as publish
import stage_candidate_release as stage
import verify_candidate_source as verify


def draft(assets=(), *, draft_state=True):
    return {
        'id': 11, 'tag_name': stage.TAG, 'draft': draft_state, 'prerelease': True,
        'assets': list(assets),
        'upload_url': f'https://uploads.github.com/repos/{stage.REPO}/releases/11/assets{{?name}}',
    }


class ReleaseControls(unittest.TestCase):
    def test_public_repository_refused_before_draft_lookup_or_mutation(self):
        with mock.patch.object(stage, 'api', return_value={'private': False}) as api:
            with self.assertRaisesRegex(RuntimeError, 'Refuse public repository'):
                stage.stage({})
        api.assert_called_once_with(f'repos/{stage.REPO}')

    def test_ambiguous_or_published_drafts_refused(self):
        for release_list, message in (([draft(), draft()], 'Ambiguous release'),
                                      ([draft(draft_state=False)], 'Refuse changing published release')):
            with self.subTest(message=message):
                with mock.patch.object(stage, 'api', side_effect=[{'private': True}, release_list]) as api:
                    with self.assertRaisesRegex(RuntimeError, message):
                        stage.stage({})
                self.assertEqual(api.call_count, 2)

    def test_existing_asset_bytes_are_checked_before_upload(self):
        release = draft([{'id': 22, 'name': 'candidate'}])
        with mock.patch.object(stage, 'api', side_effect=[{'private': True}, [release]]) as api, \
             mock.patch.object(stage, 'gh', return_value=b'wrong') as gh, \
             mock.patch.object(stage.urllib.request, 'build_opener') as opener:
            with self.assertRaisesRegex(RuntimeError, 'Existing draft asset differs'):
                stage.stage({'candidate': b'correct'})
        self.assertEqual(api.call_count, 2)
        gh.assert_called_once()
        opener.assert_not_called()

    def test_matching_existing_draft_gets_independent_readback(self):
        release = draft([{'id': 22, 'name': 'candidate'}])
        with mock.patch.object(stage, 'api', side_effect=[{'private': True}, [release], release]), \
             mock.patch.object(stage, 'gh', return_value=b'correct') as gh, \
             contextlib.redirect_stdout(io.StringIO()) as output:
            stage.stage({'candidate': b'correct'})
        self.assertEqual(gh.call_count, 2)
        self.assertEqual(json.loads(output.getvalue())['assets_verified'], 1)

    def test_artifact_member_pin_is_required(self):
        archive_bytes = io.BytesIO()
        with zipfile.ZipFile(archive_bytes, 'w') as archive:
            archive.writestr('member', b'synthetic')
        raw = archive_bytes.getvalue()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'docs').mkdir()
            record = {'artifact_id': 10, 'zip_bytes': len(raw), 'source_commit': 'fixture',
                      'files': {'member': {'bytes': 9, 'sha256': hashlib.sha256(b'synthetic').hexdigest()}}}
            record_path = root / 'docs' / 'fixture.json'
            record_path.write_text(json.dumps(record))
            with mock.patch.object(stage, 'ROOT', root), mock.patch.object(stage, 'gh', return_value=raw):
                members, source = stage.verified_members('fixture.json', ['member'])
                self.assertEqual((members, source), ({'member': b'synthetic'}, 'fixture'))
                record['files']['member']['sha256'] = '0' * 64
                record_path.write_text(json.dumps(record))
                with self.assertRaisesRegex(RuntimeError, 'Member SHA256 changed'):
                    stage.verified_members('fixture.json', ['member'])

    def test_publish_rechecks_visibility_before_mutation(self):
        selected = {'tag': 'candidate'}
        with mock.patch.object(publish, 'api', return_value={'private': False}) as api, \
             mock.patch.object(publish, 'gh') as gh:
            with self.assertRaisesRegex(RuntimeError, 'Repository visibility changed'):
                publish.publish([(selected, draft())])
        api.assert_called_once()
        gh.assert_not_called()

    def test_private_candidate_selection_requires_pinned_asset_bytes(self):
        body = b'expected'
        pin = {'bytes': len(body), 'sha256': hashlib.sha256(body).hexdigest()}
        candidate = {'tag': 'candidate', 'release_id': 11, 'assets': {'asset': pin}}
        release = draft([{'id': 22, 'name': 'asset'}])
        release['tag_name'] = candidate['tag']
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'docs').mkdir()
            (root / 'docs/selected-release-assets.json').write_text(
                json.dumps({'candidates': [candidate]}))
            for actual, should_pass in ((body, True), (b'changed!', False)):
                with self.subTest(actual=actual), mock.patch.object(publish, 'ROOT', root), \
                     mock.patch.object(publish, 'api', side_effect=[{'private': True}, release]), \
                     mock.patch.object(publish, 'gh', return_value=actual):
                    if should_pass:
                        self.assertEqual(publish.verify_selected(), [(candidate, release)])
                    else:
                        with self.assertRaisesRegex(RuntimeError, 'Changed asset bytes'):
                            publish.verify_selected()


class Response(io.BytesIO):
    def __init__(self, body, status=200):
        super().__init__(body)
        self.status = status


class FeedControls(unittest.TestCase):
    def check_feed(self, actual_asset):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'channels').mkdir()
            (root / 'docs').mkdir()
            version = '1.0.0-test'
            name = f'patches-{version}.mpp'
            tag = 'patches-v' + version
            url = f'https://github.com/{verify.REPO}/releases/download/{tag}/{name}'
            feed = {'version': version, 'created_at': '2026-10-29T12:34:56', 'download_url': url}
            feed_bytes = json.dumps(feed).encode()
            (root / 'channels/candidate.json').write_bytes(feed_bytes)
            pin = {'bytes': 8, 'sha256': hashlib.sha256(b'expected').hexdigest()}
            candidate = {'tag': tag, 'release_id': 15, 'assets': {name: pin}}
            (root / 'docs/selected-release-assets.json').write_text(json.dumps({'candidates': [candidate]}))
            release = {'id': 15, 'draft': False, 'prerelease': True, 'assets': [{'id': 16, 'name': name}]}

            def urlopen(request, timeout):
                if request == f'https://api.github.com/repos/{verify.REPO}':
                    return Response(json.dumps({'private': False}).encode())
                if request == f'https://api.github.com/repos/{verify.REPO}/releases/tags/{tag}':
                    return Response(json.dumps(release).encode())
                if request == url:
                    return Response(actual_asset)
                self.fail(f'Unexpected synthetic URL: {request}')

            opener = mock.Mock()
            opener.open.return_value = Response(feed_bytes)
            with mock.patch.object(verify, 'ROOT', root), \
                 mock.patch.object(verify.urllib.request, 'urlopen', side_effect=urlopen), \
                 mock.patch.object(verify.urllib.request, 'build_opener', return_value=opener):
                return verify.verify('public', 'gboard')

    def test_feed_accepts_exact_asset(self):
        result = self.check_feed(b'expected')
        self.assertTrue(result['selected_feed_matches_versioned_source'])
        self.assertEqual(result['main_mpp_full_bytes_verified']['bytes'], 8)

    def test_feed_refuses_changed_asset(self):
        with self.assertRaisesRegex(RuntimeError, 'Asset bytes differ'):
            self.check_feed(b'changed!')

    def test_invalid_mode_or_component_refused_before_network(self):
        with mock.patch.object(verify.urllib.request, 'urlopen') as network:
            with self.assertRaisesRegex(RuntimeError, 'Unknown patch component'):
                verify.verify('public', 'invalid')
            with self.assertRaisesRegex(RuntimeError, 'Unknown repository visibility'):
                verify.verify('invalid', 'gboard')
        network.assert_not_called()


class ExportControls(unittest.TestCase):
    def test_invalid_download_does_not_create_destination(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory) / 'selected-output'
            release = json.dumps({'id': 3, 'assets': [{'id': 4, 'name': export.NAME}]}).encode()
            with mock.patch.object(export.subprocess, 'check_output', side_effect=[release, b'not-the-bundle']):
                with self.assertRaisesRegex(RuntimeError, 'Selected bundle bytes differ'):
                    export.export(folder)
            self.assertFalse(folder.exists())

    def test_existing_different_file_is_preserved(self):
        # The historical selected artifact has a fixed length; these bytes are
        # synthetic and never fetched from GitHub or written as a new artifact.
        body = b'x' * 8205100
        pin = hashlib.sha256(body).hexdigest()
        release = json.dumps({'id': 3, 'assets': [{'id': 4, 'name': export.NAME}]}).encode()
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory) / 'selected-output'
            folder.mkdir()
            existing = folder / export.NAME
            existing.write_bytes(b'keep this existing file')
            with mock.patch.object(export, 'PIN', pin), \
                 mock.patch.object(export.subprocess, 'check_output', side_effect=[release, body]):
                with self.assertRaisesRegex(RuntimeError, 'Refuse differing existing deliverable'):
                    export.export(folder)
            self.assertEqual(existing.read_bytes(), b'keep this existing file')
            self.assertFalse((folder / 'SHA256SUMS').exists())


if __name__ == '__main__':
    unittest.main()
