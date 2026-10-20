"""Offline regression checks; no requests to TinyPNG and no API quota used."""
import contextlib
import io
import os
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zlib

import tinypng


def png(padding=0, animated=False, width=1):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    result = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, 1, 8, 6, 0, 0, 0))
    if animated:
        result += chunk(b"acTL", struct.pack(">II", 1, 0))
    if padding:
        result += chunk(b"tEXt", b"comment\0" + b"x" * padding)
    return result + chunk(b"IDAT", zlib.compress(b"\0" + b"\xff\0\0\xff" * width)) + chunk(b"IEND", b"")


class FakeClient:
    def __init__(self, output):
        self.output = output
        self.uploads = 0
        self.fail_download = False
        self.count = "1"

    def upload(self, data):
        self.uploads += 1
        return "https://api.tinify.com/output/test"

    def request(self, url):
        if self.fail_download:
            raise tinypng.CompressionError("download failed")
        return self.output, {}


class TinyPngTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.file = self.root / "image.png"
        self.original = png(500)
        self.file.write_bytes(self.original)
        self.backups = self.root / "backups"

    def process(self, client, record=None):
        record = {} if record is None else record
        return tinypng.process(self.file, record, client, self.backups, "image.png", lambda: None)

    def test_smaller_result_replaces_and_backs_up_exact_original(self):
        output = png()
        self.file.chmod(0o644)
        saved, changed = self.process(FakeClient(output))
        self.assertTrue(changed)
        self.assertEqual(saved, len(self.original) - len(output))
        self.assertEqual(self.file.read_bytes(), output)
        self.assertEqual((self.backups / "image.png").read_bytes(), self.original)
        self.assertEqual(self.file.stat().st_mode & 0o777, 0o644)

    def test_larger_output_keeps_original(self):
        record = {}
        self.assertEqual(self.process(FakeClient(png(1000)), record), (0, False))
        self.assertEqual(self.file.read_bytes(), self.original)
        self.assertFalse(self.backups.exists())
        self.assertEqual(record["result_sha256"], tinypng.digest(self.original))

    def test_download_failure_resumes_without_another_upload(self):
        record, client = {}, FakeClient(png())
        client.fail_download = True
        with self.assertRaises(tinypng.CompressionError):
            self.process(client, record)
        self.assertEqual(self.file.read_bytes(), self.original)
        self.assertIn("pending_url", record)
        client.fail_download = False
        self.process(client, record)
        self.assertEqual(client.uploads, 1)

    def test_dimensions_cannot_change(self):
        with self.assertRaises(tinypng.CompressionError):
            self.process(FakeClient(png(width=2)))
        self.assertEqual(self.file.read_bytes(), self.original)

    def test_html_response_cannot_replace_image(self):
        with self.assertRaises(tinypng.CompressionError):
            self.process(FakeClient(b"<html>error</html>"))
        self.assertEqual(self.file.read_bytes(), self.original)

    def test_local_edits_during_upload_are_preserved(self):
        client = FakeClient(png())
        def download(url):
            self.file.write_bytes(b"new local edit")
            return png(), {}
        client.request = download
        with self.assertRaises(tinypng.CompressionError):
            self.process(client)
        self.assertEqual(self.file.read_bytes(), b"new local edit")

    def test_default_preview_has_no_writes_or_network(self):
        previous = Path.cwd()
        try:
            os.chdir(self.root)
            with patch.object(tinypng, "Tinify", side_effect=AssertionError("network")), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(tinypng.main([".", "--dry-run"]), 0)
            self.assertEqual(sorted(p.name for p in self.root.iterdir()), ["image.png"])
        finally:
            os.chdir(previous)

    def test_incremental_second_run_does_not_upload(self):
        previous = Path.cwd()
        client = FakeClient(png())
        try:
            os.chdir(self.root)
            with patch.dict(os.environ, {"TINYPNG_API_KEY": "offline-test"}), patch.object(tinypng, "Tinify", return_value=client), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(tinypng.main([".", "--apply"]), 0)
                self.assertEqual(tinypng.main([".", "--apply"]), 0)
            self.assertEqual(client.uploads, 1)
            self.assertFalse((self.root / ".tinypng.lock").exists())
        finally:
            os.chdir(previous)

    def test_nine_patch_build_files_and_symlinks_are_excluded(self):
        (self.root / "button.9.png").write_bytes(png())
        (self.root / "build").mkdir()
        (self.root / "build" / "generated.png").write_bytes(png())
        (self.root / "linked.png").symlink_to(self.file)
        self.assertEqual(tinypng.find_images([self.root], []), [self.file])

    def test_animated_png_detected(self):
        self.assertTrue(tinypng.inspect_image(png(animated=True))[2])

    def test_jpeg_dimensions(self):
        data = b"\xff\xd8\xff\xc0" + struct.pack(">H", 17) + b"\x08" + struct.pack(">HH", 20, 30) + b"\x03" + b"\x01\x11\x00" * 3 + b"\xff\xd9"
        self.assertEqual(tinypng.inspect_image(data), ("jpeg", (30, 20), False))

    def test_webp_lossless_and_animation_detection(self):
        payload = b"\x2f" + ((20 - 1) | ((30 - 1) << 14)).to_bytes(4, "little")
        body = b"WEBPVP8L" + len(payload).to_bytes(4, "little") + payload + b"\0"
        self.assertEqual(tinypng.inspect_image(b"RIFF" + len(body).to_bytes(4, "little") + body),
                         ("webp", (20, 30), False))
        payload = b"\x02\0\0\0" + (19).to_bytes(3, "little") + (29).to_bytes(3, "little")
        body = b"WEBPVP8X" + len(payload).to_bytes(4, "little") + payload
        self.assertTrue(tinypng.inspect_image(b"RIFF" + len(body).to_bytes(4, "little") + body)[2])

    def test_quota_failure_stops_batch_and_removes_lock(self):
        (self.root / "second.png").write_bytes(png(200))
        previous = Path.cwd()
        client = FakeClient(png())
        client.upload = unittest.mock.Mock(side_effect=tinypng.CompressionError("quota", 402))
        try:
            os.chdir(self.root)
            with patch.dict(os.environ, {"TINYPNG_API_KEY": "offline-test"}), patch.object(tinypng, "Tinify", return_value=client), contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(tinypng.main([".", "--apply"]), 1)
            self.assertEqual(client.upload.call_count, 1)
            self.assertFalse((self.root / ".tinypng.lock").exists())
            self.assertEqual(self.file.read_bytes(), self.original)
        finally:
            os.chdir(previous)

    def test_api_credential_never_sent_to_other_host(self):
        client = tinypng.Tinify("offline-test", 1)
        with patch.object(client.opener, "open", side_effect=AssertionError("network")):
            for url in ["https://example.com/output/a", "http://api.tinify.com/output/a", "https://api.tinify.com.evil/output/a"]:
                with self.assertRaises(tinypng.CompressionError):
                    client.request(url)


if __name__ == "__main__":
    unittest.main()
