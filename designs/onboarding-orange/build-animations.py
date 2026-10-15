"""Generate portable shape-only Lottie assets; optionally stage in the official Skottie player."""
import json
import math
import sys
from pathlib import Path

OUT = Path(__file__).parent / 'animations'
OUT.mkdir(exist_ok=True)
ORANGE = [1, 107/255, 44/255, 1]
INK = [23/255, 29/255, 39/255, 1]
PALE = [1, 242/255, 234/255, 1]
LINE = [232/255, 233/255, 237/255, 1]
WHITE = [1, 1, 1, 1]

def prop(v): return {'a': 0, 'k': v}
def keys(points, linear=False):
    result = []
    for t, value in points:
        item = {'t': t, 's': value if isinstance(value, list) else [value]}
        item['o'] = {'x': [0.333 if linear else 0.22], 'y': [0.333 if linear else 0]}
        item['i'] = {'x': [0.667 if linear else 0.2], 'y': [0.667 if linear else 1]}
        result.append(item)
    return {'a': 1, 'k': result}
def stroke(c=ORANGE, w=5):
    return {'ty':'st', 'c':prop(c), 'o':prop(100), 'w':prop(w), 'lc':2, 'lj':2}
def fill(c): return {'ty':'fl', 'c':prop(c), 'o':prop(100), 'r':1}
def rect(x,y,w,h,r=0): return {'ty':'rc','d':1,'p':prop([x,y]),'s':prop([w,h]),'r':prop(r)}
def ellipse(x,y,w,h): return {'ty':'el','d':1,'p':prop([x,y]),'s':prop([w,h])}
def path(points, closed=False):
    return {'ty':'sh','ks':prop({'i':[[0,0] for _ in points], 'o':[[0,0] for _ in points], 'v':points,'c':closed})}
def group(shapes):
    return {'ty':'gr', 'it':shapes+[{'ty':'tr','p':prop([0,0]),'a':prop([0,0]),'s':prop([100,100]),'r':prop(0),'o':prop(100),'sk':prop(0),'sa':prop(0)}]}
def layer(name, shapes, position=None, scale=None, rotation=None, opacity=None, anchor=None):
    # Isolate each primitive's paint: later fills must not repaint earlier geometry.
    chunks=[]
    chunk=[]
    painted=False
    for shape in shapes:
        geometry=shape['ty'] in ['rc','el','sh','sr']
        if geometry and painted:
            chunks.append(group(chunk));chunk=[];painted=False
        chunk.append(shape)
        if shape['ty'] in ['fl','st']: painted=True
    if chunk: chunks.append(group(chunk))
    return {'ty':4,'nm':name,'ddd':0,'sr':1,'st':0,'ip':0,'op':90,'ao':0,
            'ks':{'a':prop(anchor or [0,0,0]),'p':position or prop([0,0,0]),'s':scale or prop([100,100,100]),
                  'r':rotation or prop(0),'o':opacity or prop(100)},'shapes':list(reversed(chunks))}
def phone():
    return [layer('Phone outline', [rect(160,160,96,168,17), fill(WHITE), stroke(INK,6)]),
            layer('Camera', [ellipse(160,90,6,6),fill(ORANGE)]),
            layer('Home mark', [path([[148,231],[172,231]]),stroke(LINE,4)])]
def battery():
    return [layer('Battery shell',[rect(160,170,96,148,15),fill(WHITE),stroke(INK,6)]),
            layer('Battery terminal',[rect(160,86,34,10,4),fill(INK)]),
            layer('Battery fill',[rect(160,186,76,94,8),fill(PALE)])]
def scan(kind):
    layers=[layer('Soft focus',[ellipse(160,160,242,242),fill(PALE)])]
    if kind=='device':
        layers += phone()
        for i in range(3): layers.append(layer('Readout '+str(i),[rect(160,130+i*24,52-(i%2)*16,5,2.5),fill(LINE)]))
        layers.append(layer('Scan beam',[rect(160,160,76,5,2.5),fill(ORANGE)],position=keys([(0,[0,-45,0]),(45,[0,45,0]),(90,[0,-45,0])],True)))
    elif kind=='junk':
        for i in range(3):
            y=110+i*48
            layers.append(layer('File '+str(i),[rect(160,y,130,37,9),fill(WHITE),stroke(LINE,2),rect(118,y,13,13,3),fill(ORANGE),rect(166,y,45,4,2),fill(LINE)],position=keys([(0,[0,0,0]),(20+i*6,[0,-3,0]),(50+i*6,[0,0,0]),(90,[0,0,0])])))
        layers.append(layer('Read files',[path([[91,160],[229,160]]),stroke(ORANGE,4)],position=keys([(0,[0,-65,0]),(45,[0,65,0]),(90,[0,-65,0])],True)))
    else:
        layers+=battery()
        layers.append(layer('Charge',[path([[171,122],[144,173],[165,173],[152,211],[181,159],[159,159]],True),fill(ORANGE)],opacity=keys([(0,100),(45,65),(90,100)])))
    return layers
def clean():
    layers=[layer('Soft focus',[ellipse(160,160,242,242),fill(PALE)])]
    for i, (x,y) in enumerate([(99,204),(129,215),(190,209),(216,198)]):
        layers.append(layer('Dust '+str(i),[rect(0,0,9+i*2,9+i*2,3),fill(ORANGE)],
            position=keys([(0,[x,y,0]),(24+i*3,[x,y,0]),(55+i*3,[160,226,0]),(90,[x,y,0])]),
            opacity=keys([(0,70),(24+i*3,70),(55+i*3,0),(78,0),(90,70)])))
    layers.append(layer('Broom',[path([[0,-65],[0,-7]]),stroke(INK,9),rect(0,2,56,14,5),fill(ORANGE),
        path([[-26,12],[26,12],[40,57],[10,57],[5,39],[5,57],[-9,57],[-9,42],[-16,57],[-40,57]],True),fill(ORANGE)],
        anchor=[0,0,0],position=keys([(0,[147,160,0]),(45,[177,160,0]),(90,[147,160,0])]),
        rotation=keys([(0,25),(45,-7),(90,25)])))
    return layers
def check(battery_check=False):
    if battery_check:
        layers=[layer('Soft focus',[ellipse(160,160,242,242),fill(PALE)])]+battery()
        layers.append(layer('Check trace',[path([[138,170],[153,185],[184,151]]),stroke(ORANGE,7),
            {'ty':'tm','s':prop(0),'e':keys([(0,0),(15,0),(48,100),(90,100)]),'o':prop(0),'m':1}]))
    else:
        layers=[layer('Soft focus',[ellipse(160,160,242,242),fill(PALE)]),
                layer('Success disk',[ellipse(160,160,146,146),fill(ORANGE)],anchor=[160,160,0],
                    position=prop([160,160,0]),scale=keys([(0,[88,88,100]),(18,[100,100,100]),(90,[100,100,100])])),
                layer('Check trace',[path([[124,160],[149,184],[197,134]]),stroke(WHITE,9),
                    {'ty':'tm','s':prop(0),'e':keys([(0,0),(12,0),(42,100),(90,100)]),'o':prop(0),'m':1}])]
    return layers

scenes=[('device-scan',scan('device'),True),('junk-scan',scan('junk'),True),('junk-clean',clean(),True),
        ('battery-scan',scan('battery'),True),('battery-check',check(True),False),('complete',check(),False)]
manifest=[]
for i,(name,layers,loop) in enumerate(scenes,1):
    scene={'v':'5.7.4','fr':30,'ip':0,'op':90,'w':320,'h':320,'nm':'AceClean • '+name,'assets':[],
           'layers':list(reversed(layers))}
    for idx,l in enumerate(scene['layers'],1): l['ind']=idx
    data=json.dumps(scene,separators=(',',':'))
    (OUT/(name+'.json')).write_text(data)
    if len(sys.argv)>1:
        target=Path(sys.argv[1])/'public/projects/ace-onboarding'/('scene-'+str(i))
        target.mkdir(parents=True,exist_ok=True)
        (target/'lottie.json').write_text(data)
    manifest.append({'name':name,'file':name+'.json','frames':90,'fps':30,'loop':loop,'transparent':True,'scene':'scene-'+str(i)})
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2))
print('Generated six transparent, vector-only Lottie scenes.')
