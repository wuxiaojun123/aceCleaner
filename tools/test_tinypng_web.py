"""Offline checks for importing ZIPs downloaded from the TinyPNG website."""
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
import zipfile

import tinypng_web
from test_tinypng import png


class WebImportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.previous = Path.cwd()
        os.chdir(self.root)
        self.addCleanup(os.chdir, self.previous)
        self.images = self.root / "images"
        self.images.mkdir()
        self.first = self.images / "one.png"
        self.second = self.images / "two.png"
        self.original = png(300)
        self.first.write_bytes(self.original)
        self.second.write_bytes(self.original)
        self.output = io.StringIO()

    def prepare(self):
        with contextlib.redirect_stdout(self.output):
            tinypng_web.prepare(self.root, ["images"], True)
        manifest = json.loads((self.root / '.tinypng-web/manifest.json').read_text())
        self.assertEqual(len(manifest['groups']), 1)
        self.assertEqual(len(manifest['groups'][0]['paths']), 2)
        return manifest['groups'][0]['name']

    def archive(self, name, data):
        path = self.root / "download.zip"
        with zipfile.ZipFile(path, "w") as zip_file:
            zip_file.writestr("tinified/" + name, data)
        return path

    def test_prepare_preview_does_not_create_work_directory(self):
        with contextlib.redirect_stdout(self.output):
            tinypng_web.prepare(self.root, ["images"], False)
        self.assertFalse((self.root / '.tinypng-web').exists())

    def test_downloaded_zip_replaces_duplicates_and_backs_up_originals(self):
        name = self.prepare()
        zip_path = self.archive(name, png())
        with contextlib.redirect_stdout(self.output):
            tinypng_web.import_zips(self.root, [zip_path], True)
        self.assertEqual(self.first.read_bytes(), png())
        self.assertEqual(self.second.read_bytes(), png())
        backups = list((self.root / '.tinypng-backups').rglob('*.png'))
        self.assertEqual(len(backups), 2)
        self.assertTrue(all(p.read_bytes() == self.original for p in backups))
        with contextlib.redirect_stdout(self.output):
            tinypng_web.import_zips(self.root, [zip_path], True)
        self.assertEqual(len(list((self.root / '.tinypng-backups').rglob('*.png'))), 2)

    def test_import_preview_leaves_originals_unchanged(self):
        zip_path = self.archive(self.prepare(), png())
        with contextlib.redirect_stdout(self.output):
            tinypng_web.import_zips(self.root, [zip_path], False)
        self.assertEqual(self.first.read_bytes(), self.original)
        self.assertFalse((self.root / '.tinypng-backups').exists())

    def test_modified_source_aborts_before_any_replacement(self):
        zip_path = self.archive(self.prepare(), png())
        self.second.write_bytes(png(350))
        with self.assertRaises(tinypng_web.CompressionError):
            tinypng_web.import_zips(self.root, [zip_path], True)
        self.assertEqual(self.first.read_bytes(), self.original)

    def test_unknown_archive_member_is_not_extracted(self):
        self.prepare()
        zip_path = self.archive('../../other.png', png())
        with self.assertRaises(tinypng_web.CompressionError):
            tinypng_web.import_zips(self.root, [zip_path], True)
        self.assertEqual(self.first.read_bytes(), self.original)


if __name__ == '__main__':
    unittest.main()
