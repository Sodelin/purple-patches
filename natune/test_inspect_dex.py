import hashlib
from pathlib import Path
import struct
import tempfile
import unittest
import zlib

from inspect_dex import Dex, InvalidDex, inspect_apk, instructions, uleb


def units(*values):
    return struct.pack('<' + 'H' * len(values), *values)


class DexReaderControls(unittest.TestCase):
    def test_uleb_spec_value_and_malformed_values(self):
        self.assertEqual(uleb(bytes.fromhex('e58e26'), 0), (624485, 3))
        for bad in (b'\x80', b'\x80\x80\x80\x80\x10', b'\xff' * 5):
            with self.assertRaises(InvalidDex):
                uleb(bad, 0)

    def test_operand_is_not_interpreted_as_invoke(self):
        decoded = list(instructions(units(0x0014, 0x006e, 0x0001, 0x000e)))
        self.assertEqual([x[1] for x in decoded], [0x14, 0x0e])
        self.assertTrue(all(x[3] is None for x in decoded))

    def test_switch_payload_is_not_interpreted_as_code(self):
        decoded = list(instructions(units(0x0100, 1, 0, 0, 0x006e, 0, 0x000e)))
        self.assertEqual([(x[0], x[2], x[3]) for x in decoded], [(0, 6, None), (6, 1, None)])

    def test_invalid_opcode_truncation_and_payload_alignment_refuse(self):
        for bad in (b'\x00', units(0x003e), units(0x006e, 0),
                    units(0, 0x0100, 0, 0, 0), units(0x0300, 3, 1, 0, 0)):
            with self.assertRaises(InvalidDex):
                list(instructions(bad))

    def test_standard_jumbo_and_modern_invoke_reference_widths(self):
        decoded = list(instructions(units(0x001b, 1, 1, 0x00fa, 37, 0, 0, 0x000e)))
        self.assertEqual(decoded[0], (0, 0x1b, 3, ('string', 65537)))
        self.assertEqual(decoded[1], (3, 0xfa, 4, ('method', 37)))

    def test_header_integrity_and_table_bounds(self):
        data = bytearray(112)
        data[:8] = b'dex\n039\0'
        struct.pack_into('<III', data, 32, 112, 112, 0x12345678)
        def seal():
            data[12:32] = hashlib.sha1(data[32:]).digest()
            struct.pack_into('<I', data, 8, zlib.adler32(data[12:]))
        seal()
        self.assertEqual(list(Dex(bytes(data)).definitions()), [])
        data[-1] = 1
        with self.assertRaises(InvalidDex):
            Dex(bytes(data))
        struct.pack_into('<II', data, 56, 1, 112)
        seal()
        with self.assertRaises(InvalidDex):
            Dex(bytes(data))

    def test_unpinned_apk_refused_before_zip_parse(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'wrong.apk'
            path.write_bytes(b'not an APK')
            with self.assertRaises(InvalidDex):
                inspect_apk(path)


if __name__ == '__main__':
    unittest.main()
