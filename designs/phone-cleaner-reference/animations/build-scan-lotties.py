"""Build the blue cleaner scan family from this project's original orb animations.

The source animations provide the glossy sphere, fine tick ring and white outer
ring. These variants replace the central symbol while keeping that motion grammar.
Each JSON has a sibling WebP for browser/Skottie use; each .lottie bundles both
for Android LottieAnimationView.
"""
import copy
import json
import zipfile
from pathlib import Path

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[2]
RAW = ROOT / 'app/src/main/res/raw'
WHITE = [1, 1, 1, 1]


def prop(value):
    return {'a': 0, 'k': value}


def xform():
    return {'a': prop([0, 0]), 'p': prop([0, 0]), 's': prop([100, 100]),
            'r': prop(0), 'o': prop(100), 'sk': prop(0), 'sa': prop(0)}


def shape_path(points, closed=False):
    return {'ty': 'sh', 'ks': prop({'i': [[0, 0] for _ in points],
                                  'o': [[0, 0] for _ in points],
                                  'v': points, 'c': closed})}


def shape_rect(x, y, w, h, r=0):
    return {'ty': 'rc', 'd': 1, 'p': prop([x, y]),
            's': prop([w, h]), 'r': prop(r)}


def shape_circle(x, y, diameter):
    return {'ty': 'el', 'd': 1, 'p': prop([x, y]),
            's': prop([diameter, diameter])}


def group(geometry, fill=False, width=None, opacity=100):
    items = [geometry]
    if fill:
        items.append({'ty': 'fl', 'c': prop(WHITE), 'o': prop(opacity), 'r': 1})
    if width:
        items.append({'ty': 'st', 'c': prop(WHITE), 'o': prop(opacity),
                      'w': prop(width), 'lc': 2, 'lj': 2})
    items.append({'ty': 'tr', **xform()})
    return {'ty': 'gr', 'it': items}


def icon_geometry(kind):
    if kind == 'cleaning':
        return [
            group(shape_path([[12, -39], [-9, 5]]), width=6),
            group(shape_path([[-12, 0], [0, 6], [-3, 13], [-16, 7]], True), fill=True),
            group(shape_path([[-17, 8], [1, 17], [-6, 34], [-12, 27],
                              [-18, 31], [-22, 24], [-29, 24]], True), fill=True),
        ]
    if kind == 'photos':
        return [
            group(shape_rect(-7, -6, 62, 54, 6), width=4.5, opacity=65),
            group(shape_rect(5, 5, 62, 54, 6), width=4.5),
            group(shape_circle(20, -6, 7), fill=True),
            group(shape_path([[-20, 20], [-4, 2], [5, 12],
                              [13, 4], [29, 20]]), width=4.5),
        ]
    if kind == 'screenshots':
        return [
            group(shape_path([[-31, -15], [-31, -30], [-17, -30]]), width=5),
            group(shape_path([[17, -30], [31, -30], [31, -15]]), width=5),
            group(shape_path([[-31, 17], [-31, 30], [-17, 30]]), width=5),
            group(shape_path([[17, 30], [31, 30], [31, 17]]), width=5),
            group(shape_rect(0, 0, 45, 36, 4), width=3.5),
            group(shape_path([[-14, 10], [-3, -2], [5, 5], [12, -4], [17, 10]]), width=3),
        ]
    return [
        group(shape_path([[-31, -24], [-8, -24], [-2, -18],
                          [28, -18], [31, -13], [31, 23],
                          [27, 27], [-27, 27], [-31, 23]], True), width=4.5),
        group(shape_rect(0, 7, 45, 27, 4), width=4),
        group(shape_path([[-13, 7], [13, 7]]), width=4),
    ]


def make_icon(kind, fade):
    if kind == 'cleaning':
        fade = prop(100)
        rotation = {'a': 1, 'k': [
            {'t': 0, 's': [-9], 'e': [9],
             'o': {'x': [0.58], 'y': [0]}, 'i': {'x': [0.42], 'y': [1]}},
            {'t': 45, 's': [9], 'e': [-9],
             'o': {'x': [0.58], 'y': [0]}, 'i': {'x': [0.42], 'y': [1]}},
            {'t': 90, 's': [-9]}]}
    else:
        rotation = prop(0)
    return {'ty': 4, 'nm': 'Ace Cleaner • %s symbol' % kind,
            'ddd': 0, 'sr': 1, 'st': 0, 'ip': 0, 'op': 90, 'ao': 0, 'bm': 0,
            'ks': {'o': fade, 'r': rotation, 'p': prop([187.5, 187, 0]),
                   'a': prop([0, 0, 0]), 's': prop([100, 100, 100])},
            'shapes': icon_geometry(kind)}


def make_glint(x, y, start, index):
    opacity = {'a': 1, 'k': [
        {'t': 0, 's': [0], 'e': [0]},
        {'t': start, 's': [0], 'e': [85]},
        {'t': start + 9, 's': [85], 'e': [0]},
        {'t': start + 22, 's': [0], 'e': [0]},
        {'t': 90, 's': [0]}]}
    return {'ty': 4, 'nm': 'Cleaning glint %s' % index,
            'ddd': 0, 'sr': 1, 'st': 0, 'ip': 0, 'op': 90, 'ao': 0, 'bm': 0,
            'ks': {'o': opacity, 'r': prop(0), 'p': prop([187.5 + x, 187 + y, 0]),
                   'a': prop([0, 0, 0]), 's': prop([100, 100, 100])},
            'shapes': [group(shape_path([[0, -5], [2, -1], [6, 0],
                                          [2, 1], [0, 5], [-2, 1],
                                          [-6, 0], [-2, -1]], True), fill=True)]}


def build(kind, source, image_name):
    with zipfile.ZipFile(source) as archive:
        data = json.loads(archive.read('animations/data.json'))
        original_image = data['assets'][0]['p']
        (OUT / image_name).write_bytes(
            archive.read('images/' + Path(original_image).name))
    if kind == 'big-files':
        base_layers = data['layers'][3:]
        with zipfile.ZipFile(RAW / 'picture_clean_scan_anim.lottie') as archive:
            fade = copy.deepcopy(json.loads(archive.read('animations/data.json'))
                                 ['layers'][0]['ks']['o'])
    else:
        base_layers = data['layers'][1:]
        fade = copy.deepcopy(data['layers'][0]['ks']['o'])
    data['nm'] = 'Ace Cleaner • %s scan' % kind
    data['layers'] = [make_icon(kind, fade)] + base_layers
    if kind == 'cleaning':
        data['layers'] = [make_glint(-31, -20, 10, 1),
                          make_glint(29, 18, 55, 2)] + data['layers']
    data['assets'][0]['p'] = image_name
    data['assets'][0]['u'] = ''
    for index, layer in enumerate(data['layers'], 1):
        layer['ind'] = index
    json_path = OUT / ('scan-%s.json' % kind)
    json_path.write_text(json.dumps(data, ensure_ascii=False,
                                    separators=(',', ':')), encoding='utf-8')
    packed = copy.deepcopy(data)
    packed['assets'][0]['u'] = 'images/'
    manifest = {'version': '1', 'generator': 'Ace Cleaner design prototype',
                'animations': [{'id': 'data'}]}
    with zipfile.ZipFile(OUT / ('scan-%s.lottie' % kind), 'w',
                         compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('manifest.json', json.dumps(manifest))
        archive.writestr('animations/data.json', json.dumps(packed,
                                                         ensure_ascii=False))
        archive.write(OUT / image_name, 'images/' + image_name)
    print('Wrote %s JSON + .lottie' % kind)


build('photos', RAW / 'picture_clean_scan_anim.lottie', 'orb-blue.webp')
build('screenshots', RAW / 'picture_clean_scan_anim.lottie', 'orb-blue.webp')
build('big-files', RAW / 'big_file_scan_anim.lottie', 'orb-amber.webp')
build('cleaning', RAW / 'picture_clean_scan_anim.lottie', 'orb-blue.webp')
