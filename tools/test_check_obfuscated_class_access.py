import struct
import unittest

from check_obfuscated_class_access import find_illegal_accesses, read_class


class ObfuscatedClassAccessTest(unittest.TestCase):
    ACTIVITY = "com.nice.aceclean.ui.main.RamStatusActivity"
    ADAPTER = "com.nice.aceclean.ui.main.RunningAppsAdapter"

    def test_detects_original_release_crash(self):
        classes = {self.ACTIVITY: (0x11, {self.ADAPTER}), self.ADAPTER: (0x10, set())}
        mapping = {self.ADAPTER: "db.s"}
        self.assertEqual(find_illegal_accesses(classes, mapping), [(self.ACTIVITY, self.ADAPTER)])

    def test_jvm_public_adapter_can_be_relocated(self):
        classes = {self.ACTIVITY: (0x11, {self.ADAPTER}), self.ADAPTER: (0x11, set())}
        self.assertEqual(find_illegal_accesses(classes, {self.ADAPTER: "db.s"}), [])

    def test_private_nested_class_follows_relocated_owner(self):
        nested = self.ADAPTER + "$Companion"
        classes = {self.ADAPTER: (0x11, {nested}), nested: (0x10, set())}
        self.assertEqual(find_illegal_accesses(classes, {self.ADAPTER: "db.s"}), [])

    def test_independent_classes_relocated_to_same_package_are_legal(self):
        classes = {self.ACTIVITY: (0x11, {self.ADAPTER}), self.ADAPTER: (0x10, set())}
        mapping = {self.ACTIVITY: "db.a", self.ADAPTER: "db.s"}
        self.assertEqual(find_illegal_accesses(classes, mapping), [])

    def test_nonpublic_class_in_unchanged_library_package_is_legal(self):
        classes = {"library.Templates": (0x11, {"library.Basic"}), "library.Basic": (0x10, set())}
        self.assertEqual(find_illegal_accesses(classes, {}), [])

    def test_reads_class_flags_and_array_element_references(self):
        def utf8(value):
            encoded = value.encode("utf8")
            return b"\x01" + struct.pack(">H", len(encoded)) + encoded

        # Minimal valid class: pool entries are UTF8/Class pairs, including an array.
        pool = utf8("example/Caller") + b"\x07\x00\x01"
        pool += utf8("java/lang/Object") + b"\x07\x00\x03"
        pool += utf8("[[Lexample/Helper;") + b"\x07\x00\x05"
        header = struct.pack(">IHHH", 0xCAFEBABE, 0, 61, 7)
        body = struct.pack(">7H", 0x0031, 2, 4, 0, 0, 0, 0)
        name, flags, references = read_class(header + pool + body)
        self.assertEqual(name, "example.Caller")
        self.assertEqual(flags, 0x0031)
        self.assertIn("example.Helper", references)

    def test_rejects_invalid_class_file(self):
        with self.assertRaises(ValueError):
            read_class(b"not a class")


if __name__ == "__main__":
    unittest.main()
