import json
from pathlib import Path
import unittest

from verify_apk_metadata import verify


class SignerAdmissionControls(unittest.TestCase):
    def setUp(self):
        self.pin = json.loads(Path(__file__).with_name('target.json').read_text())
        self.badging = "package: name='moe.rukamori.archivetune' versionCode='6900' versionName='N202610191717'"

    def test_observed_scheme_label_and_legacy_label(self):
        for label in ['V3.0 Signer: certificate', 'Signer #1 certificate']:
            line = label + ' SHA-256 digest: ' + self.pin['signer_sha256']
            self.assertTrue(verify(self.pin, line, self.badging)['original_signature_verified'])

    def test_repeated_same_key_across_signature_schemes(self):
        text = '\n'.join('V' + v + ' Signer: certificate SHA-256 digest: ' + self.pin['signer_sha256'] for v in ['2', '3.0'])
        self.assertTrue(verify(self.pin, text, self.badging)['original_signature_verified'])

    def test_source_stamp_is_not_an_apk_signer(self):
        with self.assertRaises(ValueError):
            verify(self.pin, 'Source Stamp Signer certificate SHA-256 digest: ' + self.pin['signer_sha256'], self.badging)

    def test_wrong_or_extra_signing_key_refuses(self):
        good = 'Signer #1 certificate SHA-256 digest: ' + self.pin['signer_sha256']
        wrong = 'Signer #2 certificate SHA-256 digest: ' + '0' * 64
        for text in [wrong, good + '\n' + wrong, '']:
            with self.assertRaises(ValueError):
                verify(self.pin, text, self.badging)

    def test_different_target_refuses(self):
        good = 'V3.0 Signer: certificate SHA-256 digest: ' + self.pin['signer_sha256']
        for badging in [self.badging.replace('6900', '6901'), self.badging.replace('archivetune', 'different')]:
            with self.assertRaises(ValueError):
                verify(self.pin, good, badging)


if __name__ == '__main__':
    unittest.main()
