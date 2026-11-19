import os
import re, sys
from PIL import Image, ImageDraw, ImageFont
try:
    _ = ImageFont.load_default()
except Exception:
    pass
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import icons_src as M

S=24; K=14; pad=14; cell=S*K+pad*2

def draw_icon(name, parts, color=(255,255,255,255)):
    img=Image.new("RGBA",(S*K,S*K),(0,0,0,0))
    d=ImageDraw.Draw(img)
    for dp, filled in parts:
        pts=[]
        for cmd,args in re.findall(r'([MLZ])([^MLZ]*)', dp):
            if cmd=='M':
                x,y=args.split(','); pts.append((float(x)*K,float(y)*K))
            elif cmd=='L':
                x,y=args.split(','); pts.append((float(x)*K,float(y)*K))
            else:
                if filled and pts: d.polygon(pts, fill=color)
                elif pts: d.line(pts, fill=color, width=int(1.75*K), joint="curve")
                pts=[]
        if pts:
            if filled: d.polygon(pts, fill=color)
            else: d.line(pts, fill=color, width=int(1.75*K), joint="curve")
    return img

names=sorted(M.ICONS)
cols=11; rows=(len(names)+cols-1)//cols
sheet=Image.new("RGBA",(cols*cell, rows*cell),(0x09,0x0B,0x11,255))
sd=ImageDraw.Draw(sheet)
for i,n in enumerate(names):
    im=draw_icon(n, M.ICONS[n])
    cx=(i%cols)*cell+pad; cy=(i//cols)*cell+pad
    sheet.alpha_composite(im,(cx,cy))
    sd.text((cx, cy+S*K+1), n[:13], fill=(120,130,150,255))

os.makedirs("docs", exist_ok=True)
sheet.save("docs/icon-set.png")
print("icons:",len(names),"sheet:",sheet.size)
