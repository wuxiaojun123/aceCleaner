"""Orange shape-only robot cleaner. 480px, 30fps, seamless 4-second loop.
Reference: user-provided laji_qingli_zhong.gif. No raster frames are embedded.
Run from the repository root to regenerate the portable Lottie asset.
"""
import json, math
from pathlib import Path

OUT=Path('designs/onboarding-orange/animations/robot-cleaning-orange.json')
END=120
ACCENT='#FF6B2C'
WHITE='#FFF9F2'
SHADOW='#E7B28B'
DARK='#3B2B24'
L=[]

def color(h):
    h=h.lstrip('#');return [int(h[i:i+2],16)/255 for i in (0,2,4)]+[1]
def prop(v):return {'a':0,'k':v}
def animated(points,curve=(.42,0,.58,1)):
    keys=[]
    for t,value in points:
        keys.append({'t':t,'s':value if isinstance(value,list) else [value],
                     'o':{'x':[curve[0]],'y':[curve[1]]},'i':{'x':[curve[2]],'y':[curve[3]]}})
    return {'a':1,'k':keys}
def xform(pos=None,anchor=None,rotation=None,scale=None,opacity=None):
    return {'p':pos or prop([0,0,0]),'a':prop(anchor or [0,0,0]),'r':rotation or prop(0),
            's':scale or prop([100,100,100]),'o':opacity or prop(100)}
def layer(name,shapes,parent=None,ks=None,ip=0,op=END):
    n=len(L)+1
    obj={'ddd':0,'ind':n,'ty':4,'nm':name,'sr':1,'ks':ks or xform(),
         'ao':0,'shapes':shapes,'ip':ip,'op':op,'st':0,'bm':0}
    if parent:obj['parent']=parent
    L.append(obj);return n

def null(name,ks,parent=None):
    n=len(L)+1
    obj={'ddd':0,'ind':n,'ty':3,'nm':name,'sr':1,'ks':ks,'ip':0,'op':END,'st':0}
    if parent:obj['parent']=parent
    L.append(obj);return n

def transform():
    return {'ty':'tr','p':prop([0,0]),'a':prop([0,0]),'s':prop([100,100]),'r':prop(0),'o':prop(100),'sk':prop(0),'sa':prop(0)}
def shape(kind,fill=None,stroke=None,width=1,opacity=100):
    items=[kind]
    if fill:items.append({'ty':'fl','c':prop(color(fill)),'o':prop(opacity),'r':1,'bm':0})
    if stroke:items.append({'ty':'st','c':prop(color(stroke)),'o':prop(opacity),'w':prop(width),'lc':2,'lj':2,'ml':4,'bm':0})
    items.append(transform())
    return [{'ty':'gr','nm':'Vector geometry','it':items,'np':len(items),'cix':2,'bm':0,'ix':1}]
def ellipse(name,cx,cy,w,h,fill,**kw):
    return layer(name,shape({'ty':'el','p':prop([cx,cy]),'s':prop([w,h]),'d':1},fill),**kw)
def rect(name,cx,cy,w,h,r,fill,stroke=None,width=1,**kw):
    return layer(name,shape({'ty':'rc','p':prop([cx,cy]),'s':prop([w,h]),'r':prop(r),'d':1},fill,stroke,width),**kw)
def path(name,vertices,fill=None,stroke=None,width=1,closed=False,ins=None,outs=None,opacity=100,**kw):
    n=len(vertices)
    geo={'ty':'sh','ks':prop({'i':ins or [[0,0] for _ in vertices],'o':outs or [[0,0] for _ in vertices],'v':vertices,'c':closed}),'ind':0}
    return layer(name,shape(geo,fill,stroke,width,opacity),**kw)
def line(name,points,stroke,width,parent=None,**kw):return path(name,points,stroke=stroke,width=width,parent=parent,**kw)

# Soft circular stage, transparent corners: it can sit on the warm onboarding page.
ellipse('Outer peach halo',240,240,414,414,'#FFF0E4')
ellipse('Orange stage',240,240,354,354,'#FFAF73')
ellipse('Warm inset disc',240,244,332,332,'#FFA366')
path('Upper disc highlight',[[106,166],[152,112],[213,91]],stroke='#FFD1A6',width=4,
     ins=[[0,0],[-20,12],[-23,1]],outs=[[10,-20],[20,-14],[0,0]],opacity=75)
ellipse('Ground shadow',266,365,155,19,'#E37B3C',ks=xform(scale=animated([(0,[100,100,100]),(20,[95,85,100]),(40,[100,100,100]),(60,[95,85,100]),(80,[100,100,100]),(100,[95,85,100]),(120,[100,100,100])]),anchor=[266,365,0],pos=prop([266,365,0])))
line('Ground plane',[[113,365],[350,365]],'#F38C4D',2,opacity=70)

robot=null('Robot - subtle weight shift',xform(pos=animated([(0,[0,0,0]),(20,[0,-3,0]),(40,[0,0,0]),(60,[0,-3,0]),(80,[0,0,0]),(100,[0,-3,0]),(120,[0,0,0])])))
# Back arm and two compact boots.
arm=null('Relaxed right arm',xform(anchor=[325,270,0],pos=prop([325,270,0]),rotation=animated([(0,-3),(20,4),(40,-3),(60,4),(80,-3),(100,4),(120,-3)])),parent=robot)
line('Right arm edge',[[321,266],[336,285],[331,305]],SHADOW,19,parent=arm)
line('Right arm white sleeve',[[321,263],[333,282],[329,298]],WHITE,15,parent=arm)
ellipse('Right glove',329,302,20,21,WHITE,parent=arm)
rect('Left leg',256,331,20,40,9,WHITE,parent=robot)
rect('Right leg',298,331,20,40,9,WHITE,parent=robot)
rect('Left sole',251,353,35,12,6,'#E98B51',parent=robot)
rect('Right sole',302,353,35,12,6,'#E98B51',parent=robot)
rect('Left boot',251,348,35,15,7,WHITE,parent=robot)
rect('Right boot',302,348,35,15,7,WHITE,parent=robot)
rect('Left boot highlight',245,346,14,4,2,'#FFFFFF',parent=robot)
rect('Right boot highlight',297,346,14,4,2,'#FFFFFF',parent=robot)

# Rounded pressure suit with a warm enamel chest panel.
rect('Torso edge',281,287,104,79,30,SHADOW,parent=robot)
rect('Torso shell',278,282,101,76,29,WHITE,parent=robot)
rect('Collar',278,251,66,16,8,'#EAC4A4',parent=robot)
rect('Collar light',276,248,65,11,5,WHITE,parent=robot)
rect('Chest panel',279,284,65,37,14,'#FFE4CC',parent=robot)
rect('Chest lower seam',279,300,49,4,2,'#EDC4A2',parent=robot)
ellipse('Chest indicator orange',267,280,7,7,ACCENT,parent=robot)
ellipse('Chest indicator peach',281,280,5,5,'#EDAE7C',parent=robot)

head=null('Helmet - gentle nod',xform(anchor=[278,207,0],pos=prop([278,207,0]),rotation=animated([(0,-1.2),(20,1),(40,-1.2),(60,1),(80,-1.2),(100,1),(120,-1.2)])),parent=robot)
# Small antennae and side buttons echo the supplied helmet silhouette.
rect('Antenna left',265,148,6,19,3,WHITE,parent=head)
rect('Antenna right',285,144,6,22,3,WHITE,parent=head)
ellipse('Antenna tip left',265,138,7,7,'#FFD3AE',parent=head)
ellipse('Antenna tip right',285,132,8,8,ACCENT,parent=head)
rect('Helmet left ear',211,210,14,31,6,'#F5CEAF',parent=head)
rect('Helmet right ear',345,210,14,31,6,'#EFA064',parent=head)
rect('Helmet depth',281,208,138,112,48,SHADOW,parent=head)
rect('Helmet white shell',277,202,136,108,47,'#FFFFFF',parent=head)
rect('Helmet warm rim',277,205,117,84,36,'#FFC699',parent=head)
rect('Visor orange seal',277,205,108,76,32,ACCENT,parent=head)
rect('Visor dark glass',277,205,99,65,28,DARK,parent=head)
rect('Visor lower reflected light',279,227,57,3,1.5,'#665044',parent=head)
line('Visor diagonal reflection',[[240,200],[249,187],[271,181]],'#9E7D68',5,parent=head,opacity=45,
     ins=[[0,0],[-5,5],[-9,0]],outs=[[2,-6],[6,-5],[0,0]])
# A single short blink per loop is the secondary charm gesture.
for x in [262,291]:
    ks=xform(anchor=[x,204,0],pos=prop([x,204,0]),scale=animated([(0,[100,100,100]),(82,[100,100,100]),(85,[100,15,100]),(88,[100,100,100]),(120,[100,100,100])],(.2,.75,.34,.94)))
    rect('Warm visor eye',x,204,7,15,3.5,'#FFF1D5',parent=head,ks=ks)
line('Friendly visor smile',[[267,219],[277,222],[287,219]],'#F6B477',2.5,parent=head,
     ins=[[0,0],[-5,0],[-3,2]],outs=[[3,2],[5,0],[0,0]])
line('Helmet shell glint',[[231,177],[242,167],[259,162]],'#FFFFFF',4,parent=head,
     ins=[[0,0],[-4,3],[-6,0]],outs=[[2,-4],[5,-3],[0,0]])

# Robot holds a compact stick vacuum; the wide dark inlet faces the incoming debris.
line('Left arm shadow',[[242,267],[233,279],[225,284]],SHADOW,19,parent=robot)
line('Left arm sleeve',[[241,264],[231,275],[225,281]],WHITE,16,parent=robot)
line('Vacuum metal wand edge',[[225,281],[166,340]],'#AC643E',11)
line('Vacuum metal wand',[[225,279],[166,338]],'#FFF2DF',7)
rect('Vacuum orange motor',210,301,31,43,11,ACCENT,
     ks=xform(anchor=[210,301,0],pos=prop([210,301,0]),rotation=prop(43)))
rect('Vacuum transparent dust chamber',193,318,23,28,8,'#FFD9A0',stroke='#FFF3DF',width=3,
     ks=xform(anchor=[193,318,0],pos=prop([193,318,0]),rotation=prop(43)))
ellipse('Vacuum power light',212,295,5,5,'#FFFFFF')
for j in range(3):
    line('Motor ventilation',[[212+j*4,306-j*4],[218+j*4,312-j*4]],'#C8461D',2)
# The inlet stays at ground level so the suction trajectories meet its opening exactly.
line('Vacuum nozzle swivel',[[166,338],[160,349]],'#FFF3E3',13)
path('Vacuum nozzle orange shell',[[133,337],[170,337],[187,353],[181,362],[132,360],[125,351]],
     fill=ACCENT,closed=True)
line('Nozzle upper highlight',[[137,341],[166,341],[177,351]],'#FFB16D',4)
rect('Nozzle lower bumper',155,359,55,8,4,'#C44D26')
ellipse('Vacuum open inlet',133,351,16,19,DARK)
ellipse('Inlet inner depth',132,351,8,12,'#211B1A')
ellipse('Nozzle right wheel',179,357,8,10,'#775043')

ellipse('Holding glove edge',225,281,23,21,'#E9BE9A',parent=robot)
ellipse('Holding glove',224,278,22,20,WHITE,parent=robot)
line('Glove seam',[[219,281],[224,283],[229,282]],'#E5C3A5',1.8,parent=robot)

# Staggered colorful scraps accelerate along curved paths into the dark inlet.
# At recycle the scrap is invisible; every phase repeats exactly after 120 frames.
for j,(x,y,size,tint) in enumerate([
    (78,298,12,'#51BCE0'),(97,380,11,'#8B78E4'),
    (185,391,12,'#62C69B'),(85,342,10,'#FFD454'),
    (116,285,10,'#F1789D'),(217,375,9,'#56AFDB'),
    (143,401,10,'#B18AE7'),(73,365,8,'#FFD454')]):
    points=[];rot=[];opacity=[];scales=[]
    for t in range(END+1):
        u=((t+j*7)%60)/60
        # Increasing speed makes the last part visibly pulled into the opening.
        v=u**1.65
        tx,ty=132,351
        bend=(-22 if x<132 else -30)
        px=x+(tx-x)*v
        py=y+(ty-y)*v+bend*math.sin(math.pi*v)
        points.append((t,[px,py,0]))
        rot.append((t,j*29+v*210))
        opacity.append((t,100*max(0,min(1,u/.12,(1-u)/.06))))
        z=100*(1-.93*v**3)
        scales.append((t,[z,z,100]))
    ks=xform(anchor=[x,y,0],pos=animated(points,(0,0,1,1)),
        rotation=animated(rot,(0,0,1,1)),opacity=animated(opacity,(0,0,1,1)),
        scale=animated(scales,(0,0,1,1)))
    if j%3==0:
        rect('Sucked colored paper %d'%j,x,y,size,size*.7,2,tint,ks=ks)
    elif j%3==1:
        path('Sucked folded scrap %d'%j,[[x-size/2,y+size/3],[x,y-size/2],[x+size/2,y+size/3]],fill=tint,closed=True,ks=ks)
    else:
        line('Sucked curled scrap %d'%j,[[x-4,y-4],[x+4,y-3],[x-3,y+4]],tint,3,ks=ks)

# Short air trails contract toward the inlet rather than sweeping away from it.
for j,(x,y) in enumerate([(97,324),(88,354),(112,379)]):
    positions=[];alphas=[];scales=[]
    for t in range(END+1):
        u=((t+j*13)%40)/40
        v=u*u
        positions.append((t,[x+(128-x)*v,y+(351-y)*v,0]))
        alphas.append((t,45*math.sin(math.pi*u)**2))
        scales.append((t,[100*(1-v),100*(1-v),100]))
    line('Inward suction air %d'%j,[[x-7,y+2],[x+7,y-2]],'#FFF9EB',2,
        ks=xform(anchor=[x,y,0],pos=animated(positions,(0,0,1,1)),
                 opacity=animated(alphas,(0,0,1,1)),scale=animated(scales,(0,0,1,1))))

# Tiny steady accents support the circular stage without adding extra visual noise.
ellipse('Halo accent top right',365,151,7,7,'#FFCF8E')
ellipse('Halo accent left',77,236,5,5,'#FFD4B0')

scene={'v':'5.9.0','fr':30,'ip':0,'op':END,'w':480,'h':480,
       'nm':'Orange robot vacuum - colorful suction - 4s seamless loop','ddd':0,'assets':[],
       'markers':[{'tm':0,'cm':'suction starts','dr':0},{'tm':20,'cm':'colorful debris intake','dr':0},{'tm':40,'cm':'continuous suction','dr':0}],
       'layers':list(reversed(L))}
OUT.parent.mkdir(parents=True,exist_ok=True)
OUT.write_text(json.dumps(scene,separators=(',',':'))+'\n')
print(str(OUT),len(scene['layers']),'layers',OUT.stat().st_size,'bytes')
