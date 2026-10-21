#!/usr/bin/env python3
"""Check compiled project class references against a fresh ActivityGuard mapping.

Run after :app:activityGuardReleaseClassTask. This checks JVM class visibility
before R8; it does not replace final DEX inspection or release device testing.
"""

import argparse
from pathlib import Path
import re
import struct


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CLASSES = [
    ROOT / "app/build/intermediates/classes/release/transformReleaseClassesWithAsm/dirs",
    ROOT / "library_ad_sdk/build/tmp/kotlin-classes/release",
    ROOT / "library_push/build/tmp/kotlin-classes/release",
]


def read_class(data):
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("Invalid JVM class file")
    offset = 8

    def u2():
        nonlocal offset
        value = struct.unpack_from(">H", data, offset)[0]
        offset += 2
        return value

    pool = [None] * u2()
    index = 1
    while index < len(pool):
        tag = data[offset]
        offset += 1
        if tag == 1:
            size = u2()
            pool[index] = data[offset:offset + size].decode("utf8", errors="replace")
            offset += size
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = (tag, u2())
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            offset += 4
        elif tag in (5, 6):
            offset += 8
            index += 1
        elif tag == 15:
            offset += 3
        else:
            raise ValueError(f"Unsupported constant pool tag: {tag}")
        index += 1

    flags, this_class = u2(), u2()
    name = pool[pool[this_class][1]].replace("/", ".")
    references = set()
    for item in pool:
        if isinstance(item, tuple) and item[0] == 7:
            target = pool[item[1]].replace("/", ".")
            if target.startswith("["):
                # Arrays use the visibility of their element class.
                target = target.lstrip("[")
                if not target.startswith("L"):
                    continue
                target = target[1:-1]
            references.add(target)
    return name, flags, references


def relocated(name, mapping):
    # ActivityGuard maps outer classes; nested and generated classes follow them.
    outer, separator, nested = name.partition("$")
    return mapping.get(outer, outer) + separator + nested


def find_illegal_accesses(classes, mapping):
    issues = set()
    for caller, (_, references) in classes.items():
        for target in references:
            if target not in classes or classes[target][0] & 0x0001:
                continue
            caller_package = relocated(caller, mapping).rpartition(".")[0]
            target_package = relocated(target, mapping).rpartition(".")[0]
            if caller_package != target_package:
                issues.add((caller, target))
    return sorted(issues)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mapping", type=Path, default=ROOT / "app/mapping.txt")
    parser.add_argument("--classes", type=Path, action="append", help="Repeat for each compiled class directory")
    args = parser.parse_args()
    mapping = dict(re.findall(r"^(\S+) -> (\S+)$", args.mapping.read_text(), re.MULTILINE))
    if not mapping:
        parser.error("No ActivityGuard class mappings found")
    classes = {}
    for directory in args.classes or DEFAULT_CLASSES:
        paths = list(directory.rglob("*.class"))
        if not paths:
            parser.error(f"No compiled classes in {directory}; build release classes first")
        for path in paths:
            name, flags, references = read_class(path.read_bytes())
            classes[name] = (flags, references)
    issues = find_illegal_accesses(classes, mapping)
    for caller, target in issues:
        print(f"FAIL: {caller} -> {target} (non-public JVM class)")
        print(f"      after ActivityGuard: {relocated(caller, mapping)} -> {relocated(target, mapping)}")
    nonpublic = sum(not flags & 0x0001 for flags, _ in classes.values())
    print(f"Checked {len(classes)} classes ({nonpublic} non-public); {len(issues)} illegal class references")
    return bool(issues)


if __name__ == "__main__":
    raise SystemExit(main())
