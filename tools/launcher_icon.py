"""Build the GamersHub launcher icon from the intro emblem.

Two outputs:
  * the legacy square/round PNGs (48..192) the manifest points at
  * adaptive-icon layers (foreground/background/monochrome) for API 26+,
    which is what stops Android from putting the icon in a white squircle
    and cropping it.

The emblem is drawn from the same Geo proportions used in GameSpaceMark.kt so
the launcher, the in-app mark and the film are one shape.
"""
from PIL import Image, ImageDraw
import os, math

OUT = "/home/user/GamersHub/app/src/main/res"

RED      = (0xE0,0x1B,0x39); RED_DEEP = (0xB3,0x12,0x2C)
ORANGE   = (0xF0,0x74,0x2A); AMBER    = (0xFF,0xA2,0x3A)
VIOLET   = (0x7B,0x5C,0xE6); VSOPT    = (0x9A,0x7C,0xF0)
GUN      = (0x3A,0x3F,0x4A); GUN_HI   = (0x5A,0x61,0x70)
GUN_LO   = (0x23,0x28,0x30); TRACE    = (0xE8,0x66,0x3C)
BG       = (0x0A,0x0B,0x11)

# emblem proportions, from GameSpaceMark.kt Geo
L=(0.065,0.145); A=(0.500,0.905); R=(0.935,0.145)
Li=(0.215,0.225); Ai=(0.500,0.700); Ri=(0.785,0.225)
Lw=(0.085,0.185); Aw=(0.500,0.840); Rw=(0.915,0.185)
Liw=(0.205,0.255); Aiw=(0.500,0.665); Riw=(0.795,0.255)
IL=(0.365,0.555); IA=(0.500,0.815); IR=(0.635,0.555)
CORE=[(0.395,0.700),(0.500,0.820),(0.605,0.700),(0.560,0.690),(0.500,0.760),(0.440,0.690)]
TRACES=[([(0.250,0.430),(0.310,0.430),(0.360,0.520)],(0.360,0.520)),
        ([(0.750,0.430),(0.690,0.430),(0.640,0.520)],(0.640,0.520))]
STUDS=[(0.170,0.325),(0.240,0.470),(0.760,0.470),(0.830,0.325)]

def draw_emblem(img, ox, oy, span, ss=4):
    ox, oy, span = float(ox), float(oy), float(span)
    """Draw the emblem into img at (ox,oy) with the given span, supersampled."""
    W = int(span); S = W*ss
    layer = Image.new("RGBA",(S,S),(0,0,0,0))
    d = ImageDraw.Draw(layer,"RGBA")
    def P(t): return (t[0]*S, t[1]*S)
    lwid = max(1,int(S*0.011))

    d.line([P(L),P(A),P(R)], fill=VIOLET+(0xFF,), width=lwid, joint="curve")
    d.polygon([P(L),P(A),P(R),P(Ri),P(Ai),P(Li)], fill=GUN+(0xFF,))
    d.line([P(Li),P(Ai),P(Ri)], fill=GUN_HI+(0xFF,), width=max(1,int(S*0.006)), joint="curve")

    # wings with per-half gradient
    for poly, c0, c1 in [
        ([P(Lw),P(Aw),P(Ai),P(Liw)], RED_DEEP, RED),
        ([P(Aw),P(Rw),P(Riw),P(Ai)], ORANGE, AMBER),
    ]:
        m=Image.new("L",(S,S),0); ImageDraw.Draw(m).polygon(poly, fill=255)
        g=Image.new("RGB",(S,S))
        gd=ImageDraw.Draw(g)
        for y in range(S):
            t=y/max(1,S-1)
            gd.line([(0,y),(S,y)], fill=tuple(int(c0[k]+(c1[k]-c0[k])*t) for k in range(3)))
        layer.paste(g,(0,0),m)
    d = ImageDraw.Draw(layer,"RGBA")

    d.line([P(Lw),P(Aw),P(Rw)], fill=(255,255,255,0x70), width=max(1,int(S*0.005)), joint="curve")
    d.line([P(IL),P(IA),P(IR)], fill=VSOPT+(0xFF,), width=max(1,int(S*0.008)), joint="curve")
    d.polygon([P(c) for c in CORE], fill=GUN_LO+(0xFF,))
    d.line([P(c) for c in CORE]+[P(CORE[0])], fill=GUN_HI+(0xFF,), width=max(1,int(S*0.005)), joint="curve")

    for run,node in TRACES:
        d.line([P(x) for x in run], fill=TRACE+(0xFF,), width=max(1,int(S*0.006)), joint="curve")
        x,y=P(node); r=S*0.018
        d.ellipse([x-r,y-r,x+r,y+r], fill=AMBER+(0xFF,))

    for s in STUDS:
        x,y=P(s); r=S*0.0085
        d.ellipse([x-r,y-r,x+r,y+r], fill=AMBER+(0xFF,))

    layer = layer.resize((W,W), Image.LANCZOS)
    img.alpha_composite(layer,(int(ox),int(oy)))

def square_icon(size, round_mask=False):
    ss=4; S=size*ss
    img=Image.new("RGBA",(S,S),(0,0,0,0))
    d=ImageDraw.Draw(img)
    # background plate: full-bleed charcoal with a violet hairline
    d.rectangle([0,0,S,S], fill=BG+(0xFF,))
    # emblem occupies the middle 78% (Material icon safe area)
    span=S*0.78; ox=(S-span)/2; oy=(S-span)/2
    draw_emblem(img, ox, oy, span, ss=2)
    if round_mask:
        mask=Image.new("L",(S,S),0)
        ImageDraw.Draw(mask).ellipse([0,0,S,S], fill=255)
        img.putalpha(mask)
    return img.resize((size,size), Image.LANCZOS)

def adaptive_fg(size):
    """Foreground: emblem only, inside the 66% safe zone, transparent elsewhere."""
    ss=4; S=size*ss
    img=Image.new("RGBA",(S,S),(0,0,0,0))
    span=S*0.62; ox=(S-span)/2; oy=(S-span)/2
    draw_emblem(img, ox, oy, span, ss=2)
    return img.resize((size,size), Image.LANCZOS)

def adaptive_bg(size):
    S=size
    img=Image.new("RGBA",(S,S),BG+(0xFF,))
    d=ImageDraw.Draw(img,"RGBA")
    # faint violet vignette so the plate is not a dead flat fill
    for i in range(24):
        a=int(6*(1-i/24))
        d.ellipse([-S*0.2+i*2,-S*0.2+i*2,S*1.2-i*2,S*1.2-i*2], outline=VIOLET+(a,), width=2)
    return img

def mono(size):
    """Themed-icon layer: single-colour silhouette of the emblem."""
    ss=4; S=size*ss
    img=Image.new("RGBA",(S,S),(0,0,0,0))
    d=ImageDraw.Draw(img)
    def P(t): return (t[0]*S, t[1]*S)
    span=S*0.62; ox=(S-span)/2; oy=(S-span)/2
    lay=Image.new("RGBA",(span if False else S,S),(0,0,0,0))
    dd=ImageDraw.Draw(lay)
    def Q(t): return (ox+t[0]*span, oy+t[1]*span)
    dd.line([Q(L),Q(A),Q(R)], fill=(255,255,255,255), width=max(2,int(S*0.030)), joint="curve")
    dd.polygon([Q(Lw),Q(Aw),Q(Rw),Q(Riw),Q(Aiw),Q(Liw)], fill=(255,255,255,255))
    img=lay.resize((size,size), Image.LANCZOS)
    return img

DENS = {"mdpi":48,"hdpi":72,"xhdpi":96,"xxhdpi":144,"xxxhdpi":192}
if __name__ == "__main__":
    for dpi, px in DENS.items():
        d=os.path.join(OUT,f"mipmap-{dpi}")
        os.makedirs(d, exist_ok=True)
        square_icon(px).save(os.path.join(d,"ic_launcher.png"))
        square_icon(px, round_mask=True).save(os.path.join(d,"ic_launcher_round.png"))
        adaptive_fg(px*2).save(os.path.join(d,"ic_launcher_foreground.png"))
        print("wrote", dpi, px)
    nd=os.path.join(OUT,"drawable-nodpi"); os.makedirs(nd, exist_ok=True)
    adaptive_fg(432).save(os.path.join(nd,"ic_launcher_foreground.png"))
    adaptive_bg(432).save(os.path.join(nd,"ic_launcher_background.png"))
    mono(432).save(os.path.join(nd,"ic_launcher_mono.png"))
    print("wrote drawable-nodpi layers")
