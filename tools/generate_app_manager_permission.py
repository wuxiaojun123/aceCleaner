"""Build the nine-app reveal and opening-lock Lottie using local Simple Icons SVGs.

Run: python tools/generate_app_manager_permission.py
Requires fontTools for SVG path parsing. No network is used by this generator.
"""
import json
from pathlib import Path
import xml.etree.ElementTree as ET

from fontTools.pens.basePen import BasePen
from fontTools.svgLib.path import parse_path
import generate_similar_photo_permission as b

ROOT = Path(__file__).resolve().parents[1]
END = 210
BRANDS = [
    ("facebook", "1877F2", "FFFFFF"), ("youtube", "FFFFFF", "FF0000"),
    ("whatsapp", "25D366", "FFFFFF"), ("x", "141414", "FFFFFF"),
    ("tiktok", "141414", "FFFFFF"), ("instagram", "C13584", "FFFFFF"),
    ("telegram", "26A5E4", "FFFFFF"), ("snapchat", "FFFC00", "141414"),
    ("spotify", "1DB954", "141414"),
]


class LottiePen(BasePen):
    def __init__(self):
        super().__init__(None)
        self.contours = []

    def _moveTo(self, point):
        self.vertices = [list(point)]
        self.incoming = [[0, 0]]
        self.outgoing = [[0, 0]]

    def _lineTo(self, point):
        self.vertices.append(list(point))
        self.incoming.append([0, 0])
        self.outgoing.append([0, 0])

    def _curveToOne(self, first, second, end):
        previous = self.vertices[-1]
        self.outgoing[-1] = [first[i] - previous[i] for i in (0, 1)]
        self.vertices.append(list(end))
        self.incoming.append([second[i] - end[i] for i in (0, 1)])
        self.outgoing.append([0, 0])

    def _qCurveToOne(self, control, end):
        previous = self.vertices[-1]
        self._curveToOne(
            [previous[i] + (control[i] - previous[i]) * 2 / 3 for i in (0, 1)],
            [end[i] + (control[i] - end[i]) * 2 / 3 for i in (0, 1)], end)

    def finish(self, closed):
        if closed and self.vertices[-1] == self.vertices[0] and len(self.vertices) > 1:
            self.incoming[0] = self.incoming.pop()
            self.vertices.pop()
            self.outgoing.pop()
        self.contours.append({"v": self.vertices, "i": self.incoming, "o": self.outgoing, "c": closed})

    def _closePath(self):
        self.finish(True)

    def _endPath(self):
        self.finish(False)


def logo(slug, color):
    pen = LottiePen()
    svg = ET.parse(Path(__file__).parent / "permission_brand_icons" / f"{slug}.svg")
    for element in svg.iter():
        if element.tag.endswith("}path"):
            parse_path(element.attrib["d"], pen)
    for contour in pen.contours:
        contour["v"] = [[(x - 12) * 1.25, (y - 12) * 1.25] for x, y in contour["v"]]
        for key in ("i", "o"):
            contour[key] = [[x * 1.25, y * 1.25] for x, y in contour[key]]
    return [{"ty": "sh", "ks": b.prop(c)} for c in pen.contours] + [b.fill(color)]


def opacity(start, peak):
    return b.animated([(0, [0]), (start, [0]), (peak, [100]), (180, [100]), (198, [0]), (210, [0])])


def tint_when_lock_appears(shapes):
    """Fade all icon fills to pale brand-orange tones, preserving logo contrast."""
    for item in shapes:
        if item["ty"] == "gr":
            tint_when_lock_appears(item["it"])
        elif item["ty"] == "fl":
            original = item["c"]["k"]
            luminance = sum(value * weight for value, weight in zip(original, (0.2126, 0.7152, 0.0722)))
            # Existing app palette: color_FFFFAD7B and color_FFFFF4EC.
            warm = b.color("FFAD7B")
            pale = b.color("FFF4EC")
            tinted = [warm[i] + (pale[i] - warm[i]) * luminance for i in range(3)] + [original[3]]
            item["c"] = b.animated([(0, original), (78, original), (90, tinted), (210, tinted)])


def generate():
    b.END = END
    b.layers.clear()
    # Foreground lock precomposition, with its own independent opening shackle.
    b.layer("Keyhole", [{"ty": "el", "p": b.prop([0, 0]), "s": b.prop([8, 8])}, b.fill("FFFFFF")], 160, 262)
    b.layer("Keyhole stem", b.rect(0, 0, 4, 10, 2, "FFFFFF"), 160, 267)
    b.layer("Lock body", b.rect(0, 0, 72, 44, 12, "347DE1"), 160, 265)
    curve = {"v": [[0, 0], [0, -12], [16, -28], [32, -12], [32, 0]],
             "i": [[0, 0], [0, 0], [-8.837, 0], [0, -8.837], [0, 0]],
             "o": [[0, 0], [0, -8.837], [8.837, 0], [0, 0], [0, 0]], "c": False}
    shackle = b.layer("Opening shackle", [{"ty": "sh", "ks": b.prop(curve)},
        {"ty": "st", "c": b.prop(b.color("94ADC9")), "o": b.prop(100), "w": b.prop(8), "lc": 2, "lj": 2}], 144, 248)
    shackle["ks"]["r"] = b.animated([(0, [0]), (102, [0]), (120, [-30]), (210, [-30])])
    shackle["ks"]["p"] = b.animated([(0, [144, 248, 0]), (102, [144, 248, 0]),
                                                (120, [144, 242, 0]), (210, [144, 242, 0])])
    lock_layers = b.layers[:]
    b.layers.clear()
    lock = b.layer("Lock appears then opens", ty=0, refId="lock", w=320, h=320)
    lock["ks"]["a"] = b.prop([160, 265, 0])
    # Keep the lock as the first (foreground) layer, centered over the icon grid.
    lock["ks"]["p"] = b.prop([160, 182, 0])
    lock["ks"]["o"] = opacity(78, 90)
    lock["ks"]["s"] = b.animated([(0, [55, 55, 100]), (78, [55, 55, 100]),
        (86, [158, 158, 100]), (94, [145, 145, 100]), (210, [145, 145, 100])])
    for index, (slug, background, foreground) in enumerate(BRANDS):
        x, y = 94 + index % 3 * 66, 70 + index // 3 * 90
        content = [{"ty": "gr", "it": logo(slug, foreground)},
                   {"ty": "gr", "it": b.rect(0, 0, 46, 46, 12, background)}]
        # YouTube's play cutout stays white within its red silhouette.
        if slug == "tiktok":
            for offset, color in [([1.1, 0.8], "FE2C55"), ([-1.1, -0.8], "25F4EE")]:
                group = logo(slug, color)
                group.append({"ty": "tr", "p": b.prop(offset), "a": b.prop([0, 0]),
                              "s": b.prop([100, 100]), "r": b.prop(0), "o": b.prop(100)})
                content.insert(-1, {"ty": "gr", "it": group})
        tint_when_lock_appears(content)
        tile = b.layer(f"App {index + 1}: {slug}", content, x, y)
        start = 3 + index * 6
        tile["ks"]["o"] = opacity(start, start + 8)
        tile["ks"]["s"] = b.animated([(0, [50, 50, 100]), (start, [50, 50, 100]),
             (start + 8, [110, 110, 100]), (start + 12, [100, 100, 100]),
             (180, [100, 100, 100]), (198, [94, 94, 100]), (210, [50, 50, 100])])
    b.layer("Camera", b.rect(0, 0, 4, 4, 2, "A5B6CC"), 160, 37)
    b.layer("Phone screen", b.rect(0, 0, 200, 270, 27, "FFFFFF"), 160, 160)
    b.layer("Phone frame", b.rect(0, 0, 216, 286, 33, "B9C8DA"), 160, 160)
    b.layer("Backdrop", [{"ty": "el", "p": b.prop([160, 160]), "s": b.prop([260, 260])}, b.fill("EEF7FF")])
    result = {"v": "5.7.4", "fr": 30, "ip": 0, "op": END, "w": 320, "h": 320, "ddd": 0,
              "nm": "Nine apps appear, then lock opens", "assets": [{"id": "lock", "layers": lock_layers}], "layers": b.layers}
    output = ROOT / "app/src/main/res/raw/app_manager_permission.json"
    output.write_text(json.dumps(result, separators=(",", ":")), encoding="utf-8")
    print(f"Generated {output.name}: nine vector icons, opening lock, 7s, {output.stat().st_size} bytes")
    b.layers.clear()
    b.END = 120


if __name__ == "__main__":
    generate()
