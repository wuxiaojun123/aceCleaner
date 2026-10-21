#!/usr/bin/env python3
"""Check centralized Android resources and common literal UI text in XML/Kotlin/Java.

Run from any directory: python3 tools/check_string_resources.py
Only project source files are scanned, never generated or third-party resources.
"""

import argparse
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
MODULES = ("app", "library_push", "library_ad_sdk")
ANDROID = "http://schemas.android.com/apk/res/android"
AUTO = "http://schemas.android.com/apk/res-auto"
TEXT_ATTRIBUTES = {
    "text", "hint", "contentDescription", "title", "subtitle", "summary",
    "summaryOn", "summaryOff", "dialogTitle", "dialogMessage", "label",
    "textOn", "textOff", "tooltipText", "prompt", "suffixText", "prefixText",
    "helperText", "errorContentDescription", "navigationContentDescription",
    "collapseContentDescription", "closeIconContentDescription",
    "actionViewContentDescription", "stateDescription", "paneTitle",
    "description", "accessibilityPaneTitle", "accessibilityStateDescription",
    "positiveButtonText", "negativeButtonText", "entries",
}
STRING_TAGS = {"string", "plurals", "string-array"}
TOKENS = re.compile(r'//[^\n]*|/\*.*?\*/|""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|[\w$]+|[^\s]', re.S)
UI_CALLS = {
    "setText": 0, "setHint": 0, "setTitle": 0, "setSubtitle": 0,
    "setMessage": 0, "setPositiveButton": 0, "setNegativeButton": 0,
    "setNeutralButton": 0, "setContentDescription": 0, "setTooltipText": 0,
    "setContentTitle": 0, "setContentText": 0, "setSubText": 0,
    "setTextViewText": 1, "drawText": 0,
}


def source_problems(path, root):
    """Catch direct literal UI arguments, including dialogs, Toasts and notifications.

    Lint supplies semantic TextView concatenation/type/format checks. This small
    lexical check deliberately does not infer arbitrary data flow or treat log,
    preference, URI and protocol strings as UI copy.
    """
    source = path.read_text(encoding="utf-8")
    tokens = [(m.group(), m.start()) for m in TOKENS.finditer(source)
              if not m.group().startswith(("//", "/*"))]
    errors = set()

    def report(token):
        value, offset = token
        if value.startswith('"') and value not in ('""', '""""""'):
            line = source.count("\n", 0, offset) + 1
            errors.add(f"{path.relative_to(root)}:{line}: hardcoded UI text {value!r}: use getString(R.string.string_…)")

    for i, (value, _) in enumerate(tokens):
        if (value in {"text", "hint", "contentDescription", "tooltipText", "title", "subtitle"}
                and i > 0 and tokens[i - 1][0] == "." and i + 2 < len(tokens)
                and tokens[i + 1][0] == "="):
            report(tokens[i + 2])
        argument = UI_CALLS.get(value)
        if value in {"makeText", "make"} and i >= 2:
            if tokens[i - 2][0] in {"Toast", "Snackbar"}:
                argument = 1
        if argument is None or i + 1 >= len(tokens) or tokens[i + 1][0] != "(":
            continue
        depth = 0
        current_argument = 0
        for token in tokens[i + 2:]:
            text = token[0]
            if text in {"(", "[", "{"}:
                depth += 1
            elif text in {")", "]", "}"}:
                if depth == 0:
                    break
                depth -= 1
            elif text == "," and depth == 0:
                current_argument += 1
            if current_argument == argument:
                report(token)
    return sorted(errors)


def is_literal(value):
    return bool(value.strip()) and not value.strip().startswith(("@", "?"))


def check(root):
    problems = []
    count = 0
    for module in MODULES:
        src = root / module / "src"
        if not src.is_dir():
            problems.append(f"{module}/src: missing source directory")
            continue
        definitions = {}
        for extension in ("*.kt", "*.java"):
            for path in sorted(src.rglob(extension)):
                # Test fixture strings are not shipped UI.
                if not path.relative_to(src).parts[0].startswith(("test", "androidTest")):
                    problems.extend(source_problems(path, root))
        for path in sorted(src.rglob("*.xml")):
            # Only Android manifests and res XML, not arbitrary assets/XML data.
            if "res" not in path.relative_to(src).parts and path.name != "AndroidManifest.xml":
                continue
            relative = path.relative_to(root)
            try:
                tree = ET.parse(path)
            except ET.ParseError as error:
                problems.append(f"{relative}: invalid XML: {error}")
                continue
            resource_file = path.parent.name.startswith("values") and tree.getroot().tag == "resources"
            for node in tree.iter():
                is_string = node.tag in STRING_TAGS or (node.tag == "item" and node.get("type") == "string")
                if resource_file and is_string:
                    count += 1
                    name = node.get("name", "")
                    if path.name != "strings.xml":
                        problems.append(f"{relative}: {name}: move string resources into this directory's strings.xml")
                    resource_type = "array" if node.tag == "string-array" else "string" if node.tag == "item" else node.tag
                    key = (path.parent, resource_type, name)
                    if key in definitions:
                        problems.append(f"{relative}: duplicate {name} (also in {definitions[key]})")
                    definitions[key] = relative
                for attribute, value in node.attrib.items():
                    if not attribute.startswith((f"{{{ANDROID}}}", f"{{{AUTO}}}")):
                        continue
                    local = attribute.split("}", 1)[1]
                    if local in TEXT_ATTRIBUTES and is_literal(value):
                        problems.append(f"{relative}: {local}={value!r}: use @string/string_…")
                if resource_file and node.tag == "item":
                    name = node.get("name", "")
                    if name.split(":")[-1] in TEXT_ATTRIBUTES and is_literal(node.text or ""):
                        problems.append(f"{relative}: style item {name}={node.text!r}: use a string resource")
    return problems, count


def translation_coverage(root):
    coverage = {}
    for module in MODULES:
        res = root / module / "src/main/res"
        base = res / "values/strings.xml"
        if not base.is_file():
            continue
        required = {node.get("name") for node in ET.parse(base).getroot()
                    if node.tag in STRING_TAGS and node.get("translatable") != "false"}
        languages = {}
        for path in sorted(res.glob("values-*/strings.xml")):
            translated = {node.get("name") for node in ET.parse(path).getroot() if node.tag in STRING_TAGS}
            languages[path.parent.name] = sorted(required - translated)
        coverage[module] = {"translatable_count": len(required), "missing_by_configuration": languages}
    return coverage


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT, help="Project root (for isolated verification)")
    parser.add_argument("--report", type=Path, help="Write a JSON report, including missing translation keys")
    args = parser.parse_args()
    problems, count = check(args.root)
    if args.report:
        # Invalid XML is already reported above; do not let coverage obscure it.
        coverage = {} if problems else translation_coverage(args.root)
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps({"errors": problems, "translation_coverage": coverage}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if problems:
        print("String resource check failed:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1
    print(f"String resource check passed ({count} definitions across {len(MODULES)} modules).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
