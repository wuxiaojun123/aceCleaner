#!/usr/bin/env python3
"""TinyPNG 网页版离线助手：准备上传批次 / 导入网页下载 ZIP；不需要 API Key。"""
from __future__ import annotations

import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import shutil
import sys
import zipfile

from tinypng import CompressionError, IGNORED_DIRS, atomic_write, digest, find_images, inspect_image

# 默认只预览；--apply 或改为 True 才准备文件 / 替换原图。
ENABLED = False
WORK_DIR = ".tinypng-web"
MAX_SIZE = 5_000_000
BATCH_SIZE = 20


def write_json(path, value):
    atomic_write(path, (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))


def prepare(root, paths, apply):
    IGNORED_DIRS.add(WORK_DIR)
    cache_file = root / WORK_DIR / "cache.json"
    cache = json.loads(cache_file.read_text(encoding="utf-8")) if cache_file.exists() else {}
    groups, skipped = {}, 0
    for file in find_images([Path(p).absolute() for p in paths], []):
        try:
            relative = file.relative_to(root).as_posix()
        except ValueError:
            raise CompressionError("请在项目根目录执行，图片路径必须位于当前项目内")
        data = file.read_bytes()
        sha = digest(data)
        kind, dimensions, animated = inspect_image(data)
        if animated or len(data) > MAX_SIZE or cache.get(relative) == sha:
            skipped += 1
            continue
        if sha not in groups:
            groups[sha] = {"sha256": sha, "name": sha[:16] + "__" + file.name,
                           "kind": kind, "dimensions": list(dimensions), "size": len(data), "paths": []}
        groups[sha]["paths"].append(relative)
    print(f"待处理 {sum(len(g['paths']) for g in groups.values())} 张；去重后 {len(groups)} 张，跳过 {skipped} 张。")
    if not apply:
        print("预览完成；加 --apply 才生成网页上传批次。")
        return
    work = root / WORK_DIR
    session = datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    upload_root = work / "uploads" / session
    for i, group in enumerate(groups.values()):
        batch = upload_root / f"batch-{i // BATCH_SIZE + 1:02d}"
        batch.mkdir(parents=True, exist_ok=True)
        source = root / group["paths"][0]
        if digest(source.read_bytes()) != group["sha256"]:
            raise CompressionError(f"准备期间文件发生变化：{source}")
        shutil.copyfile(source, batch / group["name"])
    write_json(work / "manifest.json", {"root": str(root), "session": session, "groups": list(groups.values())})
    print(f"上传目录：{upload_root}")
    print("在 https://tinypng.com/ 手动上传每个 batch 目录的图片；保持格式转换关闭。")
    print("每批完成后点击 Download all，保存 ZIP，再用 --import-zip 导入。")


def import_zips(root, zips, apply):
    work = root / WORK_DIR
    manifest = json.loads((work / "manifest.json").read_text(encoding="utf-8"))
    if manifest["root"] != str(root):
        raise CompressionError("工作目录与准备批次时不一致，请在原项目根目录执行")
    groups = {group["name"]: group for group in manifest["groups"]}
    cache_file = work / "cache.json"
    cache = json.loads(cache_file.read_text(encoding="utf-8")) if cache_file.exists() else {}
    backup_root = root / ".tinypng-backups" / ("web-" + datetime.now().strftime("%Y%m%d-%H%M%S-%f"))
    outputs = {}
    # Validate every ZIP before replacing any file. Never extract arbitrary archive paths.
    for path in zips:
        with zipfile.ZipFile(path) as archive:
            for info in archive.infolist():
                if info.is_dir() or info.filename.startswith("__MACOSX/"):
                    continue
                name = Path(info.filename).name
                if name not in groups:
                    raise CompressionError(f"ZIP 中出现不属于本次批次的文件：{name}")
                if info.file_size > MAX_SIZE:
                    raise CompressionError(f"下载文件异常大：{name}")
                data = archive.read(info)
                group = groups[name]
                kind, dims, animated = inspect_image(data)
                if kind != group["kind"] or list(dims) != group["dimensions"] or animated:
                    raise CompressionError(f"下载结果格式或尺寸改变：{name}")
                if name in outputs and outputs[name] != data:
                    raise CompressionError(f"多个 ZIP 中包含不同结果：{name}")
                outputs[name] = data
    operations = []
    unchanged = 0
    for name, output in outputs.items():
        group = groups[name]
        for relative in group["paths"]:
            file = root / relative
            try:
                file.resolve().relative_to(root)
            except ValueError:
                raise CompressionError(f"文件已移出项目：{relative}")
            original = file.read_bytes()
            original_sha = digest(original)
            if cache.get(relative) == original_sha:
                unchanged += 1
                continue
            if file.is_symlink() or original_sha != group["sha256"]:
                raise CompressionError(f"原图在上传后已被修改，取消导入：{relative}")
            operations.append((file, relative, original, output))
    print(f"ZIP 结果 {len(outputs)} 张，匹配 {len(operations)} 个原图，已处理 {unchanged} 个。")
    replaced = saved = 0
    for file, relative, original, output in operations:
        smaller = len(output) < len(original)
        print(f"{'替换' if smaller else '保留'} {relative}：{len(original):,} → {len(output):,} 字节")
        if not apply:
            continue
        if file.is_symlink() or file.read_bytes() != original:
            raise CompressionError(f"导入期间原图被修改：{relative}")
        if smaller:
            backup = backup_root / relative
            backup.parent.mkdir(parents=True, exist_ok=True)
            with backup.open("xb") as stream:
                stream.write(original)
            shutil.copystat(file, backup)
            atomic_write(file, output, file.stat().st_mode & 0o777)
            replaced += 1
            saved += len(original) - len(output)
        cache[relative] = digest(output if smaller else original)
        write_json(cache_file, cache)
    if apply:
        print(f"导入完成：替换 {replaced} 张，节省 {saved:,} 字节。")
        if replaced:
            print(f"原图备份：{backup_root}")
    else:
        print("当前仅预览，没有替换原图；加 --apply 才实际导入。")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*", default=["."], help="准备上传的图片目录，默认当前目录")
    parser.add_argument("--import-zip", action="append", type=Path, help="导入网页 Download all 下载的 ZIP，可重复")
    switches = parser.add_mutually_exclusive_group()
    switches.add_argument("--apply", action="store_true", help="开启准备批次 / 导入替换")
    switches.add_argument("--dry-run", action="store_true", help="强制仅预览")
    args = parser.parse_args(argv)
    root = Path.cwd().resolve()
    apply = (ENABLED or args.apply) and not args.dry_run
    lock = root / ".tinypng.lock"
    descriptor = None
    try:
        if apply:
            try:
                descriptor = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
            except FileExistsError:
                raise CompressionError("另一个压缩任务正在运行；确认结束后可手动删除 .tinypng.lock")
        if args.import_zip:
            import_zips(root, args.import_zip, apply)
        else:
            prepare(root, args.paths, apply)
        return 0
    except (CompressionError, OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print(f"错误：{error}", file=sys.stderr)
        return 1
    finally:
        if descriptor is not None:
            os.close(descriptor)
            try:
                lock.unlink()
            except FileNotFoundError:
                pass


if __name__ == "__main__":
    sys.exit(main())
