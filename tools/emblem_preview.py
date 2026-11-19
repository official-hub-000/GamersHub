#!/usr/bin/env python3
"""Render the Game Space emblem geometry outside Android.

Port of the Geo table in GameSpaceMark.kt. The point is to be able to *look* at
the emblem after changing a coordinate, instead of building an APK, installing it
on a phone and squinting at a 40dp icon. A shallow-V mistake and a chevron poking
outside the silhouette were both caught this way.

    python3 -m venv /tmp/pv && /tmp/pv/bin/pip install pillow
    /tmp/pv/bin/python tools/emblem_preview.py   # writes docs/emblem-preview.png

Keep LATEST in sync with Geo when you edit it.
"""
from PIL import Image, ImageDraw
W=H=900
RED=(0xE0,0x1B,0x39); RED_DEEP=(0xB3,0x12,0x2C); ORANGE=(0xF0,0x74,0x2A); AMBER=(0xFF,0xA2,0x3A)
VIOLET=(0x7B,0x5C,0xE6); VSOPT=(0x9A,0x7C,0xF0); GUN=(0x3A,0x3F,0x4A); GUN_HI=(0x5A,0x61,0x70)
GUN_LO=(0x23,0x28,0x30); TRACE=(0xE8,0x66,0x3C)
def P(t): return (W*t[0], H*t[1])
L=(0.065,0.145); A=(0.500,0.905); R=(0.935,0.145)
Li=(0.215,0.225); Ai=(0.500,0.700); Ri=(0.785,0.225)
Lw=(0.085,0.185); Aw=(0.500,0.840); Rw=(0.915,0.185)
Liw=(0.205,0.255); Aiw=(0.500,0.665); Riw=(0.795,0.255)
IL=(0.365,0.555); IA=(0.500,0.815); IR=(0.635,0.555)
core=[(0.395,0.700),(0.500,0.820),(0.605,0.700),(0.560,0.690),(0.500,0.760),(0.440,0.690)]
traces=[([(0.250,0.430),(0.310,0.430),(0.360,0.520)],(0.360,0.520)),
        ([(0.750,0.430),(0.690,0.430),(0.640,0.520)],(0.640,0.520)),
        ([(0.180,0.330),(0.180,0.395)],(0.180,0.395)),
        ([(0.820,0.330),(0.820,0.395)],(0.820,0.395))]
studs=[(0.170,0.325),(0.240,0.470),(0.760,0.470),(0.830,0.325)]

def grad(c0,c1,ang):
    sm=Image.new("RGB",(256,1))
    for i in range(256):
        t=i/255
        sm.putpixel((i,0),tuple(int(c0[k]+(c1[k]-c0[k])*t) for k in range(3)))
    return sm.resize((W,H)).rotate(ang,expand=False)

img=Image.new("RGB",(W,H),(0x05,0x06,0x0A)); d=ImageDraw.Draw(img,"RGBA")
d.line([P(L),P(A),P(R)],fill=VIOLET+(0xE6,),width=int(H*0.011),joint="curve")
d.polygon([P(L),P(A),P(R),P(Ri),P(Ai),P(Li)],fill=GUN+(255,))
d.line([P(Li),P(Ai),P(Ri)],fill=GUN_HI+(0xD9,),width=int(H*0.006),joint="curve")

lw=[P(Lw),P(Aw),P(Ai),P(Liw)]
rw=[P(Aw),P(Rw),P(Riw),P(Ai)]
for poly,c0,c1,ang in [(lw,RED_DEEP,RED,-38),(rw,ORANGE,AMBER,38)]:
    m=Image.new("L",(W,H),0); ImageDraw.Draw(m).polygon(poly,fill=255)
    img.paste(grad(c0,c1,ang),(0,0),m)
d=ImageDraw.Draw(img,"RGBA")
d.line([P(Lw),P(Aw),P(Rw)],fill=(255,255,255,0x66),width=int(H*0.005),joint="curve")
d.line([P(IL),P(IA),P(IR)],fill=VSOPT+(0xD9,),width=int(H*0.008),joint="curve")
d.polygon([P(c) for c in core],fill=GUN_LO+(255,))
d.line([P(c) for c in core]+[P(core[0])],fill=GUN_HI+(0xB3,),width=int(H*0.005),joint="curve")
for run,node in traces:
    d.line([P(p) for p in run],fill=TRACE+(0xBF,),width=int(H*0.005),joint="curve")
    x,y=P(node); r=H*0.020
    d.ellipse([x-r,y-r,x+r,y+r],fill=AMBER+(0x4D,))
    r=H*0.011; d.ellipse([x-r,y-r,x+r,y+r],fill=AMBER)
for s in studs:
    x,y=P(s); r=H*0.0075; d.ellipse([x-r,y-r,x+r,y+r],fill=AMBER+(0xE6,))
import os
os.makedirs("docs", exist_ok=True)
img.save("docs/emblem-preview.png")
print("wrote docs/emblem-preview.png")
