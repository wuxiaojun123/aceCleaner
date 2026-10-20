#!/usr/bin/env python3
"""Portable TinyPNG compressor. Python 3.8+, standard library only."""

from __future__ import annotations

import argparse
import base64
import fnmatch
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import sys
import tempfile
from datetime import datetime
from http.client import HTTPException
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener

# 开关：False 仅预览；改成 True，或运行时传 --apply，才上传并替换图片。
ENABLED = False
SUPPORTED = {".png", ".jpg", ".jpeg", ".webp"}
IGNORED_DIRS = {".git", ".idea", ".gradle", ".gradle-home", "node_modules", "build", "dist",
                ".venv", "venv", "__pycache__", ".tinypng-backups"}
API = "https://api.tinify.com/shrink"


class CompressionError(Exception):
    def __init__(self, message: str, status: int = 0):
        super().__init__(message)
        self.status = status


class NoRedirect(HTTPRedirectHandler):
    # Do not forward the API credential to a redirect destination.
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def atomic_write(path: Path, data: bytes, mode: int = 0o600) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    name = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".tinypng-", delete=False) as stream:
            name = stream.name
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(name, mode)
        os.replace(name, path)
    finally:
        if name and os.path.exists(name):
            os.unlink(name)


def inspect_image(data: bytes) -> tuple[str, tuple[int, int], bool]:
    """Read format, dimensions and animation flag without third-party dependencies."""
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        offset, dimensions, animated = 8, None, False
        while offset + 12 <= len(data):
            length = int.from_bytes(data[offset:offset + 4], "big")
            kind = data[offset + 4:offset + 8]
            chunk = data[offset + 8:offset + 8 + length]
            if offset + 12 + length > len(data):
                break
            if kind == b"IHDR" and length == 13:
                dimensions = struct.unpack(">II", chunk[:8])
            animated |= kind == b"acTL"
            if kind == b"IEND" and dimensions:
                return "png", dimensions, animated
            offset += length + 12
    elif data.startswith(b"RIFF") and data[8:12] == b"WEBP":
        offset, dimensions, animated = 12, None, False
        while offset + 8 <= len(data):
            kind = data[offset:offset + 4]
            length = int.from_bytes(data[offset + 4:offset + 8], "little")
            chunk = data[offset + 8:offset + 8 + length]
            if len(chunk) != length:
                break
            if kind == b"VP8X" and length >= 10:
                dimensions = (int.from_bytes(chunk[4:7], "little") + 1,
                              int.from_bytes(chunk[7:10], "little") + 1)
                animated |= bool(chunk[0] & 2)
            elif kind == b"VP8 " and length >= 10 and chunk[3:6] == b"\x9d\x01\x2a":
                dimensions = (int.from_bytes(chunk[6:8], "little") & 0x3fff,
                              int.from_bytes(chunk[8:10], "little") & 0x3fff)
            elif kind == b"VP8L" and length >= 5 and chunk[0] == 0x2f:
                bits = int.from_bytes(chunk[1:5], "little")
                dimensions = ((bits & 0x3fff) + 1, ((bits >> 14) & 0x3fff) + 1)
            animated |= kind == b"ANIM"
            offset += 8 + length + (length % 2)
        if dimensions:
            return "webp", dimensions, animated
    elif data.startswith(b"\xff\xd8") and data.endswith(b"\xff\xd9"):
        offset = 2
        while offset + 4 <= len(data):
            if data[offset] != 0xff:
                break
            while offset < len(data) and data[offset] == 0xff:
                offset += 1
            if offset >= len(data):
                break
            marker = data[offset]
            offset += 1
            if marker == 0xda:
                break
            if marker == 0x01 or 0xd0 <= marker <= 0xd9:
                continue
            length = int.from_bytes(data[offset:offset + 2], "big")
            if length < 2 or offset + length > len(data):
                break
            if marker in {0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf} and length >= 7:
                height, width = struct.unpack(">HH", data[offset + 3:offset + 7])
                return "jpeg", (width, height), False
            offset += length
    raise CompressionError("无法识别或图片头不完整，已保留原图")


def find_images(paths: list[Path], excludes: list[str]) -> list[Path]:
    found = set()
    for source in paths:
        if source.is_symlink():
            continue
        if not source.exists():
            raise CompressionError(f"路径不存在：{source}")
        candidates = [source] if source.is_file() else []
        if source.is_dir():
            for folder, dirs, names in os.walk(source, followlinks=False):
                dirs[:] = sorted(d for d in dirs if d not in IGNORED_DIRS and
                                 not (Path(folder) / d).is_symlink())
                candidates.extend(Path(folder) / name for name in names)
        for file in candidates:
            if file.is_symlink() or file.suffix.lower() not in SUPPORTED or file.name.lower().endswith(".9.png"):
                continue
            if any(fnmatch.fnmatch(file.as_posix(), pattern) or fnmatch.fnmatch(file.name, pattern)
                   for pattern in excludes):
                continue
            found.add(file.resolve())
    return sorted(found)


class Tinify:
    def __init__(self, key: str, timeout: float):
        token = base64.b64encode(f"api:{key}".encode()).decode()
        self.headers = {"Authorization": f"Basic {token}", "User-Agent": "portable-tinypng/1.0"}
        self.timeout = timeout
        self.opener = build_opener(NoRedirect())
        self.count = None

    def request(self, url: str, data: bytes | None = None):
        parsed = urlparse(url)
        if (parsed.scheme != "https" or parsed.netloc != "api.tinify.com" or
                not (parsed.path == "/shrink" or parsed.path.startswith("/output/"))):
            raise CompressionError("拒绝非 Tinify API 下载地址")
        req = Request(url, data=data, headers=self.headers)
        if data is not None:
            req.add_header("Content-Type", "application/octet-stream")
        try:
            with self.opener.open(req, timeout=self.timeout) as response:
                self.count = response.headers.get("Compression-Count", self.count)
                return response.read(), response.headers
        except HTTPError as error:
            # Keep keys, remote URLs and arbitrary server response bodies out of logs.
            messages = {401: "API Key 无效", 402: "API 配额不足", 429: "请求过于频繁，请稍后重试"}
            raise CompressionError(messages.get(error.code, f"Tinify 返回 HTTP {error.code}"), error.code) from None
        except (URLError, TimeoutError, OSError, HTTPException):
            raise CompressionError("网络请求失败或超时；重新运行可重试，原图未替换") from None

    def upload(self, data: bytes) -> str:
        body, headers = self.request(API, data)
        try:
            result = json.loads(body)
            url = headers.get("Location") or result["output"]["url"]
            if not isinstance(url, str):
                raise ValueError()
            return url
        except (ValueError, KeyError, TypeError):
            raise CompressionError("Tinify 响应缺少下载地址") from None


def process(file: Path, record: dict, client: Tinify, backup_root: Path, key: str, save) -> tuple[int, bool]:
    original = file.read_bytes()
    source_hash = digest(original)
    kind, dimensions, animated = inspect_image(original)
    if animated:
        raise CompressionError("动画图片跳过")
    if record.get("source_sha256") == source_hash and record.get("pending_url"):
        url = record["pending_url"]
    else:
        url = client.upload(original)
        record.clear()
        record.update(source_sha256=source_hash, pending_url=url)
        save()  # A failed download can be resumed without uploading the original again.
    compressed, _ = client.request(url)
    out_kind, out_dimensions, out_animated = inspect_image(compressed)
    if (kind, dimensions, False) != (out_kind, out_dimensions, out_animated):
        raise CompressionError("压缩结果格式、尺寸或动画状态异常，已保留原图")
    if file.is_symlink() or file.read_bytes() != original:
        raise CompressionError("文件在压缩期间被修改，已取消替换")
    changed = len(compressed) < len(original)
    if changed:
        relative = Path(key) if not Path(key).is_absolute() else Path("external") / digest(str(file.parent).encode())[:12] / file.name
        backup = backup_root / relative
        backup.parent.mkdir(parents=True, exist_ok=True)
        with backup.open("xb") as stream:
            stream.write(original)
        shutil.copystat(file, backup)
        atomic_write(file, compressed, file.stat().st_mode & 0o777)
    record.clear()
    record.update(result_sha256=digest(compressed if changed else original))
    save()
    return len(original) - len(compressed) if changed else 0, changed


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="TinyPNG 图片压缩：默认仅预览，--apply 才实际上传并替换。")
    parser.add_argument("paths", nargs="*", default=["."], help="图片文件或目录，可多个；默认当前目录")
    switches = parser.add_mutually_exclusive_group()
    switches.add_argument("--apply", action="store_true", help="开启上传、压缩、下载和替换")
    switches.add_argument("--dry-run", action="store_true", help="强制只预览，即使 ENABLED=True")
    parser.add_argument("--force", action="store_true", help="重新压缩已处理且没有变化的图片")
    parser.add_argument("--limit", type=int, default=0, help="本次最多处理多少张；0 不限")
    parser.add_argument("--exclude", action="append", default=[], help="排除文件名或完整路径通配符，可重复")
    parser.add_argument("--timeout", type=float, default=60, help="单次请求超时秒数，默认 60")
    args = parser.parse_args(argv)
    if args.limit < 0 or args.timeout <= 0:
        parser.error("--limit 必须 >= 0，--timeout 必须 > 0")
    apply = (ENABLED or args.apply) and not args.dry_run
    cwd = Path.cwd()
    state_path = cwd / ".tinypng-cache.json"
    lock_path = cwd / ".tinypng.lock"
    backup_root = cwd / ".tinypng-backups" / datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    lock_fd = None
    try:
        state = json.loads(state_path.read_text(encoding="utf-8")) if state_path.exists() else {}
        if not isinstance(state, dict):
            raise CompressionError("缓存格式错误，请检查 .tinypng-cache.json")
        files = find_images([Path(p).absolute() for p in args.paths], args.exclude)
        queue = []
        for file in files:
            try:
                key = file.relative_to(cwd).as_posix()
            except ValueError:
                key = str(file)
            data = file.read_bytes()
            record = state.get(key, {})
            if not isinstance(record, dict):
                raise CompressionError("缓存条目格式错误")
            if not args.force and record.get("result_sha256") == digest(data):
                continue
            if inspect_image(data)[2]:
                continue
            queue.append((file, key))
        if args.limit:
            queue = queue[:args.limit]
        print(f"{'执行' if apply else '预览'}：待处理 {len(queue)} 张图片（PNG/JPEG/WebP）。")
        for file, _ in queue:
            print(f"  {file}")
        if not apply:
            print("未上传或修改文件。加 --apply，或设置脚本顶部 ENABLED = True 后开启。")
            return 0
        if not queue:
            return 0
        api_key = os.environ.get("TINYPNG_API_KEY", "").strip()
        if not api_key:
            raise CompressionError("请先设置环境变量 TINYPNG_API_KEY（不要将 Key 写入脚本或 Git）")
        try:
            lock_fd = os.open(lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        except FileExistsError:
            raise CompressionError("发现 .tinypng.lock；请确认没有其他压缩进程，残留锁可手动删除") from None
        os.write(lock_fd, str(os.getpid()).encode())
        client = Tinify(api_key, args.timeout)
        def save():
            atomic_write(state_path, (json.dumps(state, indent=2, ensure_ascii=False) + "\n").encode())
        failures, changed, saved = 0, 0, 0
        for index, (file, key) in enumerate(queue, 1):
            try:
                count, replaced = process(file, state.setdefault(key, {}), client, backup_root, key, save)
                changed += int(replaced)
                saved += count
                print(f"[{index}/{len(queue)}] {key}：" + (f"节省 {count:,} 字节" if replaced else "结果未变小，保留原图"))
            except (CompressionError, OSError) as error:
                failures += 1
                print(f"[{index}/{len(queue)}] {key}：{error}", file=sys.stderr)
                if isinstance(error, CompressionError) and error.status in {401, 402, 429}:
                    print("批次已停止，剩余图片未处理。", file=sys.stderr)
                    break
        print(f"完成：替换 {changed} 张，失败 {failures} 张，节省 {saved:,} 字节。")
        if changed:
            print(f"原图备份：{backup_root}")
        if client.count is not None:
            print(f"API 本月已用次数：{client.count}")
        return 1 if failures else 0
    except (CompressionError, OSError, ValueError) as error:
        print(f"错误：{error}", file=sys.stderr)
        return 1
    finally:
        if lock_fd is not None:
            os.close(lock_fd)
            try:
                lock_path.unlink()
            except FileNotFoundError:
                pass


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n已中断；已完成的文件保留，重新运行可继续。", file=sys.stderr)
        sys.exit(130)
