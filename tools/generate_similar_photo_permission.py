"""Build the permission Lottie from a local photo (Pillow required).

Usage: python tools/generate_similar_photo_permission.py path/to/img_girl2.png
The photo is embedded; the generated animation needs no external image files.
Timing follows CleanBoxPro OnboardingAdapter.startCheckSequence().
"""

import base64
import io
import json
from pathlib import Path
import sys

FPS = 100  # Exact 140 ms stagger and 360 ms selection animation.
END = 196  # 5 * 140 + 360 + 900 ms.
layers = []


def prop(value):
    return {"a": 0, "k": value}


def animated(samples):
    return {"a": 1, "k": [
        {"t": t, "s": value, "o": {"x": [0.42], "y": [0]},
         "i": {"x": [0.58], "y": [1]}}
        for t, value in samples
    ]}


def transform(x=0, y=0):
    return {"a": prop([0, 0, 0]), "p": prop([x, y, 0]),
            "s": prop([100, 100, 100]), "r": prop(0), "o": prop(100)}


def color(hex_value):
    return [int(hex_value[i:i + 2], 16) / 255 for i in (0, 2, 4)] + [1]


def fill(hex_value):
    return {"ty": "fl", "c": prop(color(hex_value)), "o": prop(100), "r": 1}


def rect(x, y, w, h, radius, hex_value):
    return [{"ty": "rc", "d": 1, "p": prop([x, y]),
             "s": prop([w, h]), "r": prop(radius)}, fill(hex_value)]


def layer(name, shapes=None, x=0, y=0, **extra):
    item = {"ddd": 0, "ind": len(layers) + 1, "ty": 4, "nm": name,
            "sr": 1, "st": 0, "ip": 0, "op": END, "ks": transform(x, y),
            **extra}
    if shapes is not None:
        item["shapes"] = shapes
    layers.append(item)
    return item


def main():
    from PIL import Image, ImageDraw, ImageOps

    photo = ImageOps.fit(Image.open(sys.argv[1]).convert("RGB"), (182, 156))
    photo = photo.convert("RGBA")
    mask = Image.new("L", photo.size)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 181, 155), radius=16, fill=255)
    photo.putalpha(mask)
    buffer = io.BytesIO()
    photo.save(buffer, format="PNG", optimize=True)
    encoded = base64.b64encode(buffer.getvalue()).decode("ascii")

    # Lottie draws the first layer on top: badges precede photos and phone frame.
    for index in range(6):
        x = 64 + (index % 2) * 101
        y = 116 + (index // 2) * 86
        start = index * 14
        disk = [{"ty": "el", "d": 1, "p": prop([0, 0]), "s": prop([22, 22])}, fill("FF6B2C")]
        check = [
            {"ty": "sh", "ks": prop({"i": [[0, 0]] * 3, "o": [[0, 0]] * 3,
                                      "v": [[-5, 0], [-1, 4], [6, -4]], "c": False})},
            {"ty": "st", "c": prop(color("FFFFFF")), "o": prop(100),
             "w": prop(2.4), "lc": 2, "lj": 2},
        ]
        # Separate groups keep the disk fill and check stroke independent.
        badge = layer(f"Selection {index + 1}", [
            {"ty": "gr", "it": check}, {"ty": "gr", "it": disk},
        ], x + 78, y + 13)
        badge["ks"]["s"] = animated([
            (0, [72, 72, 100]), (start, [72, 72, 100]),
            (start + 18, [118, 118, 100]), (start + 36, [100, 100, 100]),
        ] if start else [(0, [72, 72, 100]), (18, [118, 118, 100]), (36, [100, 100, 100])])
        badge["ks"]["o"] = animated(
            [(0, [45]), (start, [45]), (start + 36, [100])]
            if start else [(0, [45]), (36, [100])])
        image = layer(f"Similar photo {index + 1}", x=x, y=y, ty=2, refId="photo")
        image["ks"]["s"] = prop([50, 50, 100])

    layer("Gallery heading", rect(116, 80, 104, 10, 5, "243A5A"))
    layer("Gallery subheading", rect(103, 99, 78, 5, 2.5, "A8B3C0"))
    layer("Speaker", rect(160, 42, 42, 5, 2.5, "243A5A"))
    layer("Summary icon", rect(89, 401, 26, 26, 8, "FF6B2C"))
    layer("Summary detail", rect(161, 401, 90, 7, 3.5, "A8B3C0"))
    layer("Summary panel", rect(160, 401, 192, 46, 10, "F3F5F8"))
    layer("Screen", rect(160, 240, 212, 440, 24, "FFFFFF"))
    layer("Phone frame", rect(160, 240, 228, 456, 30, "243A5A"))

    result = {"v": "5.7.4", "fr": FPS, "ip": 0, "op": END, "w": 320, "h": 480,
              "nm": "Similar photos - staggered selection", "ddd": 0,
              "assets": [{"id": "photo", "w": 182, "h": 156, "u": "",
                          "p": "data:image/png;base64," + encoded, "e": 1}], "layers": layers}
    output = Path(__file__).resolve().parents[1] / "app/src/main/res/raw/similar_photo_permission.json"
    output.write_text(json.dumps(result, separators=(",", ":")), encoding="utf-8")
    print(f"Generated {output.name}: {len(layers)} layers, {END / FPS}s, {output.stat().st_size} bytes")


if __name__ == "__main__":
    main()
