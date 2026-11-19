"""GamersHub launcher icon — the gamepad from the intro film.

The intro ends on a flat red controller, and the launcher should be the same
object. Everything is chamfered (no curves) so it stays in the icon language we
already use: 45°/90° edges only, one weight, flat fills.

Outputs, all derived from one geometry pass:

  adaptive (API 26+)   ic_launcher_pad[_round].xml        follows the system
                       ic_launcher_pad_dark.xml           forced dark plate
                       ic_launcher_pad_light.xml          forced light plate
                       ic_launcher_pad_mono.xml           themed-icon silhouette
  foregrounds          drawable-nodpi/ic_pad_fg.png        light-mode art
                       drawable-night-nodpi/ic_pad_fg.png  dark-mode art
                       drawable-nodpi/ic_pad_fg_{light,dark,mono}.png  fixed copies
  legacy (pre-26)      mipmap-*/ic_launcher_pad{,_dark,_light,_mono}{,_round}.png
  settings previews    drawable-nodpi/ic_pick_{auto,dark,light,mono}.png

The dark/light split happens two ways on purpose:
  * the "auto" look reads @color/ic_pad_bg and @drawable/ic_pad_fg, both of
    which have a -night variant, so the launcher swaps the whole plate when the
    phone flips to light mode;
  * the forced looks point at the same art under fixed names, so a user who
    pins dark mode keeps it even in daylight.
"""
from PIL import Image, ImageDraw, ImageFilter
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")

# ---- palette ---------------------------------------------------------------
RED_HI   = (0xFF, 0x53, 0x3B)
RED_LO   = (0xB4, 0x0C, 0x1A)
DARK_HI  = (0xFF, 0x46, 0x2E)
DARK_LO  = (0x9C, 0x08, 0x14)
LIGHT_HI = (0xE8, 0x24, 0x30)
LIGHT_LO = (0x9E, 0x0A, 0x18)
EMBER    = (0xFF, 0x7A, 0x18)
RIM      = (0xFF, 0x8A, 0x5A)
SHOULDER = (0x7A, 0x08, 0x12)   # darker slab under the top corners
MAROON   = (0x5E, 0x06, 0x0E)   # touchpad strip
INK      = (0x14, 0x0A, 0x0C)
PLATE_D  = (0x0A, 0x0B, 0x11)   # dark plate
PLATE_L  = (0xF2, 0xF0, 0xEB)   # light plate

# ---- geometry --------------------------------------------------------------
# Local space is 1000x1000 with the pad's bounding box filling it. Clockwise
# from the top-left chamfer. Every corner is a hard 45 degree cut.
BODY = [
    (352,  74), (648,  74),          # top edge
    (700, 104),                      # right shoulder crease
    (818, 150), (948, 236),          # right wing
    (996, 404),                      # right flank
    (952, 590), (886, 806),          # right grip
    (776, 902), (664, 842),          # right grip tip + inner
    (600, 706), (580, 664),          # notch right
    (420, 664), (400, 706),          # notch left
    (336, 842), (224, 902),          # left grip inner + tip
    (114, 806), ( 48, 590),          # left grip
    (  4, 404),                      # left flank
    ( 52, 236), (182, 150),          # left wing
    (300, 104),                      # left shoulder crease
]
# shoulder buttons: darker chamfered slabs tucked under the top corners
SHOULDERS = [
    [(300, 104), (352, 74), (352, 132), (312, 158)],
    [(700, 104), (648, 74), (648, 132), (688, 158)],
]
# d-pad: a plus sign inside the left half of the face
DP_CX, DP_CY, DP_ARM, DP_HALF = 268, 404, 54, 116
DPAD = [
    (DP_CX - DP_ARM, DP_CY - DP_HALF), (DP_CX + DP_ARM, DP_CY - DP_HALF),
    (DP_CX + DP_ARM, DP_CY - DP_ARM),  (DP_CX + DP_HALF, DP_CY - DP_ARM),
    (DP_CX + DP_HALF, DP_CY + DP_ARM), (DP_CX + DP_ARM, DP_CY + DP_ARM),
    (DP_CX + DP_ARM, DP_CY + DP_HALF), (DP_CX - DP_ARM, DP_CY + DP_HALF),
    (DP_CX - DP_ARM, DP_CY + DP_ARM),  (DP_CX - DP_HALF, DP_CY + DP_ARM),
    (DP_CX - DP_HALF, DP_CY - DP_ARM), (DP_CX - DP_ARM, DP_CY - DP_ARM),
]
# four face buttons in a diamond, plus two small shoulder pills
BTN_CX, BTN_CY, BTN_R, BTN_OFF = 742, 404, 36, 84
# No touchpad strip: at 48dp it smudges into a mouth. The d-pad and the button
# diamond carry the read on their own.
BTNS = [
    (BTN_CX, BTN_CY - BTN_OFF), (BTN_CX, BTN_CY + BTN_OFF),
    (BTN_CX - BTN_OFF, BTN_CY), (BTN_CX + BTN_OFF, BTN_CY),
]

SS = 4  # supersample factor


def _poly(draw, pts, scale, offset, fill):
    draw.polygon([(offset[0] + x * scale, offset[1] + y * scale) for x, y in pts], fill=fill)


def _disc(draw, cx, cy, r, scale, offset, fill, sides=4):
    """A chamfered "circle": a square rotated 45 deg for nodes, per the icon rules."""
    import math
    pts = []
    for i in range(sides):
        a = math.pi / 2 * i + math.pi / 4
        pts.append((cx + r * math.cos(a) * 1.22, cy + r * math.sin(a) * 1.22))
    _poly(draw, pts, scale, offset, fill)


def _gradient(size, top, bottom):
    img = Image.new("RGB", (1, size), top)
    px = img.load()
    for y in range(size):
        t = y / max(1, size - 1)
        px[0, y] = (
            int(top[0] + (bottom[0] - top[0]) * t),
            int(top[1] + (bottom[1] - top[1]) * t),
            int(top[2] + (bottom[2] - top[2]) * t),
        )
    return img.resize((size, size), Image.BILINEAR)


def render_art(mode, size=432, span=0.635, offset=(0, 0)):
    """Draw the pad on a transparent canvas.

    mode: "dark" | "light" | "mono"
    span: fraction of the canvas the pad's bounding box occupies — 0.635 keeps
          it inside the 66% adaptive-icon safe zone, so no mask can crop it.
    """
    S = size * SS
    art = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    scale = S * span / 1000.0
    ox = (S - 1000 * scale) / 2 + offset[0] * SS
    oy = (S - 1000 * scale) / 2 + offset[1] * SS
    off = (ox, oy)

    body_mask = Image.new("L", (S, S), 0)
    _poly(ImageDraw.Draw(body_mask), BODY, scale, off, 255)

    if mode == "mono":
        white = Image.new("RGBA", (S, S), (255, 255, 255, 255))
        art.paste(white, (0, 0), body_mask)
        # knock the d-pad and buttons out so the silhouette still reads as a pad
        cut = Image.new("L", (S, S), 0)
        cd = ImageDraw.Draw(cut)
        _poly(cd, DPAD, scale, off, 255)
        for bx, by in BTNS:
            _disc(cd, bx, by, BTN_R, scale, off, 255)
        art.paste((0, 0, 0, 0), (0, 0), cut)
        return art.resize((size, size), Image.LANCZOS)

    hi, lo = {"dark": (DARK_HI, DARK_LO), "light": (LIGHT_HI, LIGHT_LO)}[mode]

    # 1. bloom, painted under the body so the silhouette stays crisp
    if mode == "dark":
        glow = body_mask.filter(ImageFilter.GaussianBlur(11 * SS * span))
        glow = glow.point(lambda v: int(v * 0.55))
        art.paste(Image.new("RGBA", (S, S), EMBER + (255,)), (0, 0), glow)

    # 2. the body itself
    face = _gradient(S, hi, lo).convert("RGBA")
    art.paste(face, (0, 0), body_mask)

    d = ImageDraw.Draw(art)
    rim_col = (EMBER if mode == "dark" else RIM) + (255,)

    # shoulder slabs: darker red on the body
    slab_mask = Image.new("L", (S, S), 0)
    sd = ImageDraw.Draw(slab_mask)
    for slab in SHOULDERS:
        _poly(sd, slab, scale, off, 255)
    slab_mask = Image.composite(slab_mask, Image.new("L", (S, S), 0), body_mask)
    art.paste(Image.new("RGBA", (S, S), SHOULDER + (255,)), (0, 0), slab_mask)


    # 3. shoulder slabs in darker red, so they sit on the body rather than being
    #    punched out of it
    ctl = Image.new("L", (S, S), 0)
    cd = ImageDraw.Draw(ctl)
    _poly(cd, DPAD, scale, off, 255)
    for bx, by in BTNS:
        _disc(cd, bx, by, BTN_R, scale, off, 255)

    if mode == "dark":
        art.paste(Image.new("RGBA", (S, S), EMBER + (90,)), (0, 0), Image.composite(
            Image.new("L", (S, S), 0), ctl, ctl.point(lambda v: 255 - v)
        ).filter(ImageFilter.GaussianBlur(2.2 * SS)))
    art.paste(Image.new("RGBA", (S, S), INK + (255,)), (0, 0), ctl)

    # 4. edge: a bright lip on the lower half in dark mode, a hard ink line in light
    ring = [(off[0] + x * scale, off[1] + y * scale) for x, y in BODY + [BODY[0]]]
    lw = max(2, int(10 * SS * span / 0.635))
    rim = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    rd = ImageDraw.Draw(rim)
    for i in range(len(ring) - 1):
        (x1, y1), (x2, y2) = ring[i], ring[i + 1]
        if (y1 + y2) / 2 < S * 0.52:
            continue  # top edges stay unlit: the light comes from above
        rd.line([(x1, y1), (x2, y2)], fill=rim_col, width=lw)
    # clip to the silhouette so the lip is an inner edge, never an outer halo
    art.alpha_composite(Image.composite(rim, Image.new("RGBA", (S, S), (0, 0, 0, 0)), body_mask))
    if mode == "light":
        edge = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        ImageDraw.Draw(edge).line(ring, fill=INK + (86,), width=max(2, int(4 * SS)))
        art.alpha_composite(Image.composite(edge, Image.new("RGBA", (S, S), (0, 0, 0, 0)), body_mask))

    return art.resize((size, size), Image.LANCZOS)


def plate(size, bg, art, radius=0.22, round_full=False):
    """Compose a legacy (pre-adaptive) icon: plate + art."""
    S = size * SS
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if round_full:
        d.ellipse([0, 0, S - 1, S - 1], fill=bg + (255,))
    else:
        r = int(S * radius)
        d.rounded_rectangle([0, 0, S - 1, S - 1], radius=r, fill=bg + (255,))
    art_big = art.resize((S, S), Image.LANCZOS)
    img.alpha_composite(art_big)
    return img.resize((size, size), Image.LANCZOS)


ADAPTIVE = """<?xml version="1.0" encoding="utf-8"?>
<!-- Gamepad launcher icon, API 26+. {why} -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="{bg}" />
    <foreground android:drawable="{fg}" />
    <monochrome android:drawable="@drawable/ic_pad_fg_mono" />
</adaptive-icon>
"""


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    mode = "wb" if isinstance(data, bytes) else "w"
    with open(path, mode) as fh:
        fh.write(data)


def main():
    dark = render_art("dark")
    light = render_art("light")
    mono = render_art("mono")

    # foregrounds: qualifier-resolved pair for "auto", fixed copies for the pins
    write(f"{RES}/drawable-night-nodpi/ic_pad_fg.png", b"")
    dark.save(f"{RES}/drawable-night-nodpi/ic_pad_fg.png")
    light.save(f"{RES}/drawable-nodpi/ic_pad_fg.png")
    light.save(f"{RES}/drawable-nodpi/ic_pad_fg_light.png")
    dark.save(f"{RES}/drawable-nodpi/ic_pad_fg_dark.png")
    mono.save(f"{RES}/drawable-nodpi/ic_pad_fg_mono.png")

    # adaptive descriptors
    anydpi = f"{RES}/mipmap-anydpi-v26"
    for name, bg, fg, why in [
        ("ic_launcher_pad", "@color/ic_pad_bg", "@drawable/ic_pad_fg",
         "Background and art both have -night variants, so the plate follows the\n     system light/dark setting."),
        ("ic_launcher_pad_dark", "@color/ic_pad_bg_dark", "@drawable/ic_pad_fg_dark",
         "Pinned to the dark plate whatever the system says."),
        ("ic_launcher_pad_light", "@color/ic_pad_bg_light", "@drawable/ic_pad_fg_light",
         "Pinned to the light plate whatever the system says."),
        ("ic_launcher_pad_mono", "@color/ic_pad_bg_dark", "@drawable/ic_pad_fg_mono",
         "Silhouette only, for launchers that theme icons."),
    ]:
        xml = ADAPTIVE.format(bg=bg, fg=fg, why=why)
        write(f"{anydpi}/{name}.xml", xml)
        write(f"{anydpi}/{name}_round.xml", xml)

    # legacy bitmaps, one set per look
    looks = {
        "ic_launcher_pad":       (PLATE_D, dark),
        "ic_launcher_pad_round": (PLATE_D, dark),
        "ic_launcher_pad_dark":  (PLATE_D, dark),
        "ic_launcher_pad_light": (PLATE_L, light),
        "ic_launcher_pad_mono":  (PLATE_D, mono),
    }
    for dens, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        for name, (bg, art) in looks.items():
            plate(px, bg, art, round_full=name.endswith("_round")).save(f"{RES}/mipmap-{dens}/{name}.png")

    # Settings previews — the composed icon, which is what the user sees
    prev = {
        "ic_pick_auto":  (PLATE_D, dark),
        "ic_pick_dark":  (PLATE_D, dark),
        "ic_pick_light": (PLATE_L, light),
        "ic_pick_mono":  (PLATE_D, mono),
    }
    for name, (bg, art) in prev.items():
        plate(192, bg, art).save(f"{RES}/drawable-nodpi/{name}.png")

    print("pad icon written:", ", ".join(sorted(prev)))


if __name__ == "__main__":
    main()
