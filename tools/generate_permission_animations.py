"""Generate self-contained vector Lotties for the permission gates.

Run from any directory: python tools/generate_permission_animations.py
No network, fonts, or raster assets are needed. App logos use fontTools.
"""
import json
import math
from bisect import bisect_left
from pathlib import Path
import generate_similar_photo_permission as b

b.END = 120
OUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"


def shape(name, content, x=0, y=0):
    return b.layer(name, [{"ty": "gr", "it": content}], x, y)


def rectangle(name, x, y, w, h, radius, color):
    return shape(name, b.rect(0, 0, w, h, radius, color), x, y)


def circle(name, x, y, radius, color):
    return shape(name, [{"ty": "el", "d": 1, "p": b.prop([0, 0]),
                        "s": b.prop([radius * 2, radius * 2])}, b.fill(color)], x, y)


def path(name, vertices, color, width=4, closed=False, solid=False, x=0, y=0):
    content = [{"ty": "sh", "ks": b.prop({"i": [[0, 0]] * len(vertices),
               "o": [[0, 0]] * len(vertices), "v": vertices, "c": closed})}]
    content += [b.fill(color)] if solid else [{"ty": "st", "c": b.prop(b.color(color)),
                "o": b.prop(100), "w": b.prop(width), "lc": 2, "lj": 2}]
    return shape(name, content, x, y)


def motion(item, key, values):
    item["ks"][key] = b.animated(values)
    return item


def float_at(item, x, y, amount=5):
    return motion(item, "p", [(0, [x, y, 0]), (60, [x, y - amount, 0]), (120, [x, y, 0])])


def backdrop():
    circle("Warm backdrop", 160, 160, 119, "FFF1E7")


def save(name):
    data = {"v": "5.7.4", "fr": 30, "ip": 0, "op": 120, "w": 320, "h": 320,
            "nm": name, "ddd": 0, "assets": [], "layers": b.layers}
    (OUT / f"{name}.json").write_text(json.dumps(data, separators=(",", ":")), encoding="utf-8")
    print(f"{name}: {len(b.layers)} layers, 4s")
    b.layers.clear()


def network():
    def linear(samples):
        return {"a": 1, "k": [{"t": frame, "s": value,
                "o": {"x": [0], "y": [0]}, "i": {"x": [1], "y": [1]}}
                for frame, value in samples]}

    def point(t):
        u = 1 - t
        return [u ** 3 * 79 + 3 * u * u * t * 146 + 3 * u * t * t * 185 + t ** 3 * 224,
                u ** 3 * 193 + 3 * u * u * t * 191 + 3 * u * t * t * 159 + t ** 3 * 88]

    # Match the arrowhead to Trim Paths by arc length, rather than curve parameter.
    samples = [point(index / 400) for index in range(401)]
    distances = [0.0]
    for first, second in zip(samples, samples[1:]):
        distances.append(distances[-1] + math.dist(first, second))
    def at_distance(distance):
        distance = max(0, min(distances[-1], distance))
        index = max(1, bisect_left(distances, distance))
        fraction = (distance - distances[index - 1]) / (distances[index] - distances[index - 1])
        t = (index - 1 + fraction) / 400
        x, y = point(t)
        u = 1 - t
        dx = 3 * u * u * 67 + 6 * u * t * 39 + 3 * t * t * 39
        dy = 3 * u * u * -2 + 6 * u * t * -32 + 3 * t * t * -71
        length = math.hypot(dx, dy)
        return [x, y], [-dy / length, dx / length]

    def arrow_outline(progress):
        tip_distance = distances[-1] * progress
        neck_distance = max(0, tip_distance - 22)
        size = min(1, tip_distance / 22)
        left, right = [], []
        for index in range(21):
            center, normal = at_distance(neck_distance * index / 20)
            left.append([center[i] + normal[i] * 4.5 * size for i in (0, 1)])
            right.append([center[i] - normal[i] * 4.5 * size for i in (0, 1)])
        neck, normal = at_distance(neck_distance)
        tip, _ = at_distance(tip_distance)
        # One closed silhouette: the shaft ends at the base of the triangular head.
        vertices = left + [[neck[i] + normal[i] * 13 * size for i in (0, 1)], tip,
                           [neck[i] - normal[i] * 13 * size for i in (0, 1)]] + right[::-1]
        return {"v": vertices, "i": [[0, 0]] * len(vertices),
                "o": [[0, 0]] * len(vertices), "c": True}

    keys = []
    fade = []
    for offset in (0, 60):
        for frame in range(0, 60, 2):
            progress = max(0, min(1, (frame - 3) / 36))
            keys.append({"t": offset + frame, "s": [arrow_outline(progress)],
                         "o": {"x": [0], "y": [0]}, "i": {"x": [1], "y": [1]}})
        fade.extend([(offset, [0]), (offset + 3, [0]), (offset + 7, [100]),
                     (offset + 45, [100]), (offset + 57, [0])])
    keys.append({"t": 120, "s": [arrow_outline(0)]})
    fade.append((120, [0]))
    arrow = shape("Growing arrow silhouette", [
        {"ty": "sh", "ks": {"a": 1, "k": keys}}, b.fill("FFC778"),
    ])
    arrow["ks"]["o"] = linear(fade)
    for index, height in enumerate([66, 106, 160, 123]):
        x = 76 + index * 55
        bar = shape(f"Traffic column {index + 1}", b.rect(0, -height / 2, 36, height, 7, "91C9FA"), x, 244)
        motion(bar, "s", [(0, [100, 92, 100]), (20 + index * 10, [100, 108, 100]),
                          (80 + index * 7, [100, 88, 100]), (120, [100, 92, 100])])
    path("Baseline", [[48, 249], [274, 249]], "D8E6F3", 3)
    circle("Traffic backdrop", 160, 160, 119, "EEF7FF")
    save("network_traffic_permission")


def apps():
    from generate_app_manager_permission import generate
    generate()


def storage():
    # Compose each card before moving it, keeping its decorative lines attached.
    for index, (x, y, angle) in enumerate([(83, 109, -15), (239, 210, 12)]):
        content = []
        for vertices in [b.rect(0, -9, 62, 4, 2, "E8EBEC"), b.rect(-10, 3, 42, 4, 2, "E8EBEC"),
                         b.rect(-18, 15, 26, 4, 2, "FFD7BC"), b.rect(0, 0, 92, 65, 12, "FFFFFF")]:
            content.append({"ty": "gr", "it": vertices})
        card = b.layer(f"Floating file {index + 1}", content, x, y)
        card["ks"]["r"] = b.prop(angle)
        float_at(card, x, y, 8 if index == 0 else -7)
    path("Folder icon", [[-25, -17], [-8, -17], [0, -9], [25, -9], [25, 21], [-25, 21], [-25, -17]], "FFFFFF", 3, x=160, y=161)
    path("Folder divider", [[-25, -3], [25, -3]], "FFFFFF", 3, x=160, y=161)
    tile = rectangle("Storage folder tile", 160, 161, 92, 94, 25, "FF6B2C")
    motion(tile, "s", [(0, [100, 100, 100]), (60, [103, 103, 100]), (120, [100, 100, 100])])
    backdrop()
    save("storage_permission")


def media(video=False):
    if video:
        path("Play", [[-9, -16], [17, 0], [-9, 16]], "FFFFFF", closed=True, solid=True, x=160, y=159)
        circle("Play button", 160, 159, 30, "FF6B2C")
        ring = circle("Play pulse", 160, 159, 32, "FFE0CC")
        motion(ring, "s", [(0, [100, 100, 100]), (120, [160, 160, 100])])
        motion(ring, "o", [(0, [70]), (120, [0])])
    else:
        for x, y, sx, sy in [(83, 82, 1, 1), (237, 82, -1, 1), (83, 230, 1, -1), (237, 230, -1, -1)]:
            path("Capture corner", [[x, y + sy * 18], [x, y], [x + sx * 18, y]], "FF6B2C", 4)
        scan = rectangle("Screenshot scan line", 160, 105, 130, 3, 1.5, "FF9F61")
        motion(scan, "p", [(0, [160, 105, 0]), (60, [160, 211, 0]), (120, [160, 105, 0])])
    circle("Sun", 194, 121, 12, "FFD49B")
    path("Photo landscape", [[91, 208], [133, 153], [164, 186], [186, 161], [229, 208]], "B8D6CC", closed=True, solid=True)
    rectangle("Media preview", 160, 156, 142, 140, 14, "F2F7F6")
    rectangle("Media card", 160, 160, 178, 194, 22, "FFFFFF")
    backdrop()
    save("video_permission" if video else "screenshot_permission")


if __name__ == "__main__":
    network()
    apps()
    storage()
    media()
    media(video=True)
