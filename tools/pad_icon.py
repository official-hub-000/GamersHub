"""GamersHub launcher icon — the flat red gamepad from the intro, traced.

This is not a redraw. BODY below was traced from the film still: the silhouette
was segmented, its contour walked, smoothed and resampled to 56 points, then
normalised to the pad's bounding box. The aspect ratio (1.939:1) and the slim
tapered grips are the film's, not a guess.

Control geometry is measured off the same still:

  d-pad   0.159 wide, 0.335 tall, arms 0.053 thick, centre (0.286, 0.317)
  minus   0.079 x 0.029 at x 0.458-0.537, centred vertically on the d-pad
  dots    four circles, r 0.0355, diamond at (0.6475/0.7095/0.7705, 0.1965-0.437)

Colour is sampled from the body: it is a horizontal ramp (#ff4a3f left to
#dc1d13 right) with a slight darkening toward the bottom, and a pale rim
(#ff9591) that catches the light along the lower silhouette.

Outputs, all from one geometry pass:

  adaptive (API 26+)   ic_launcher_pad[_round].xml        follows the system
                       ic_launcher_pad_dark.xml           pinned dark plate
                       ic_launcher_pad_light.xml          pinned light plate
                       ic_launcher_pad_mono.xml           themed silhouette
  foregrounds          drawable-nodpi/ic_pad_fg.png        light-mode art
                       drawable-night-nodpi/ic_pad_fg.png  dark-mode art
                       drawable-nodpi/ic_pad_fg_{light,dark,mono}.png
  legacy (pre-26)      mipmap-*/ic_launcher_pad{,_dark,_light,_mono}{,_round}.png
  in-app               drawable-nodpi/ic_pad_mark.png     the pad alone
                       drawable-*/ic_stat_gh.png           status bar
  settings previews    drawable-nodpi/ic_pick_{auto,dark,light,mono}.png

The pad itself is identical in every one of those. Only the plate changes with
light and dark, which is what keeps the mark exact while still following the
system.
"""
from PIL import Image, ImageDraw, ImageFilter
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")

# ---- palette (sampled from the still) --------------------------------------
TL = (0xFF, 0x50, 0x40)
TR = (0xF9, 0x2A, 0x20)
BL = (0xF0, 0x35, 0x28)
BR = (0xCF, 0x1A, 0x12)
RIM = (0xFF, 0x95, 0x91)
INK = (0x06, 0x04, 0x05)   # the controls are near-black, not pure black
GLOW = (0xFF, 0x3A, 0x28)
PLATE_D = (0x05, 0x06, 0x0A)
PLATE_L = (0xF4, 0xF2, 0xED)

ASPECT = 1.9389            # width / height of the traced silhouette

# ---- silhouette: traced, normalised to the pad's own bounding box ----------
BODY = [
    (0.2066, 0.0367), (0.2382, 0.0064), (0.2909, 0.0026), (0.3438, 0.0029), (0.3967, 0.0005), (0.4496, 0.0033),
    (0.5024, 0.0000), (0.5554, 0.0000), (0.5906, 0.0000), (0.6436, 0.0000), (0.6965, 0.0000), (0.7494, 0.0001),
    (0.7976, 0.0394), (0.8435, 0.0902), (0.8791, 0.1637), (0.8957, 0.2243), (0.9207, 0.3152), (0.9422, 0.4087),
    (0.9592, 0.5063), (0.9753, 0.6044), (0.9916, 0.7025), (0.9980, 0.8029), (0.9847, 0.8665), (0.9633, 0.9608),
    (0.9162, 0.9921), (0.8620, 1.0000), (0.8150, 0.9717), (0.7859, 0.8858), (0.7560, 0.8007), (0.7371, 0.7428),
    (0.7041, 0.6639), (0.6532, 0.6356), (0.6021, 0.6107), (0.5492, 0.6091), (0.4963, 0.6091), (0.4434, 0.6091),
    (0.4081, 0.6092), (0.3565, 0.6293), (0.3054, 0.6553), (0.2672, 0.7214), (0.2393, 0.8090), (0.2099, 0.8945),
    (0.1800, 0.9795), (0.1487, 1.0000), (0.0959, 0.9948), (0.0440, 0.9793), (0.0193, 0.8890), (0.0000, 0.7934),
    (0.0089, 0.6927), (0.0251, 0.5948), (0.0369, 0.5302), (0.0540, 0.4330), (0.0731, 0.3380), (0.0969, 0.2461),
    (0.1234, 0.1570), (0.1607, 0.0878),
]

# ---- controls, normalised to the same box ----------------------------------
DP_CX, DP_CY = 0.2858, 0.3168
DP_SPAN, DP_ARM = 0.1595, 0.0535
MINUS = (0.458, 0.253, 0.537, 0.282)          # x0, y0, x1, y1
DOT_R = 0.0355
DOTS = [(0.6475, 0.3165), (0.7095, 0.1965), (0.7095, 0.4370), (0.7705, 0.3165)]

SS = 4   # supersample


def _to_px(pts, scale, ox, oy):
    return [(ox + x * scale, oy + y * scale) for x, y in pts]


def _ramp(w, h):
    """Bilinear body colour: four corners, blended. Matches the film's lighting."""
    small = Image.new("RGB", (2, 2))
    small.putpixel((0, 0), TL); small.putpixel((1, 0), TR)
    small.putpixel((0, 1), BL); small.putpixel((1, 1), BR)
    return small.resize((w, h), Image.BICUBIC).convert("RGBA")


def render_pad(mode, height, span=0.60, canvas=None):
    """Draw the pad.

    mode:   "dark" | "light" | "mono"
    height: pad height in px; width follows ASPECT
    span:   fraction of the square canvas the pad's width occupies
    canvas: canvas size in px (square); derived from span when omitted
    """
    w_pad = max(1, int(round(height * ASPECT)))
    if canvas is None:
        canvas = max(w_pad, int(round(w_pad / span)))
    S = canvas * SS
    art = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    pw, ph = w_pad * SS, height * SS
    ox, oy = (S - pw) / 2, (S - ph) / 2

    def poly(pts):
        return [(ox + x * pw, oy + y * ph) for x, y in pts]

    body = Image.new("L", (S, S), 0)
    ImageDraw.Draw(body).polygon(poly(BODY), fill=255)

    if mode == "mono":
        white = Image.new("RGBA", (S, S), (255, 255, 255, 255))
        art.paste(white, (0, 0), body)
        cut = Image.new("L", (S, S), 0)
        _draw_controls(ImageDraw.Draw(cut), ox, oy, pw, ph)
        art.paste((0, 0, 0, 0), (0, 0), cut)
        return art.resize((canvas, canvas), Image.LANCZOS)

    # 1. bloom under the body — the film has a red bounce on black, and without
    #    it the flat red reads as a sticker on a dark plate
    if mode == "dark":
        halo = body.filter(ImageFilter.GaussianBlur(10 * SS))
        halo = halo.point(lambda v: int(v * 0.40))
        art.paste(Image.new("RGBA", (S, S), GLOW + (255,)), (0, 0), halo)

    # 2. the body
    art.paste(_ramp(S, S), (0, 0), body)

    # 3. rim: only the lower silhouette catches light, and it is clipped inside
    rim = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    rd = ImageDraw.Draw(rim)
    ring = poly(BODY)
    lw = max(2, int(1.7 * SS))
    for i in range(len(ring)):
        (x1, y1), (x2, y2) = ring[i], ring[(i + 1) % len(ring)]
        if (y1 + y2) / 2 < oy + ph * 0.46:
            continue
        rd.line([(x1, y1), (x2, y2)], fill=RIM + (175 if mode == "dark" else 120,), width=lw)
    art.alpha_composite(Image.composite(rim, Image.new("RGBA", (S, S), (0, 0, 0, 0)), body))

    # 4. controls
    ctl = Image.new("L", (S, S), 0)
    _draw_controls(ImageDraw.Draw(ctl), ox, oy, pw, ph)
    art.paste(Image.new("RGBA", (S, S), INK + (255,)), (0, 0), ctl)

    return art.resize((canvas, canvas), Image.LANCZOS)


def _draw_controls(d, ox, oy, pw, ph):
    """d-pad, minus key and the four round buttons, in one mask."""
    def X(v): return ox + v * pw
    def Y(v): return oy + v * ph

    cx, cy = X(DP_CX), Y(DP_CY)
    arm_x, arm_y = DP_ARM * pw, DP_ARM * ph
    half_x = DP_SPAN / 2 * pw
    half_y = 0.335 / 2 * ph            # the vertical bar is taller than it is wide
    d.rectangle([cx - half_x, cy - arm_y, cx + half_x, cy + arm_y], fill=255)
    d.rectangle([cx - arm_x, cy - half_y, cx + arm_x, cy + half_y], fill=255)

    x0, y0, x1, y1 = MINUS
    d.rectangle([X(x0), Y(y0), X(x1), Y(y1)], fill=255)

    for x, y in DOTS:
        r = DOT_R * pw
        d.ellipse([X(x) - r, Y(y) - r, X(x) + r, Y(y) + r], fill=255)


def render_mark(height=420):
    """The pad alone on transparency, cropped to its own bounding box.

    This is the in-app mark (lobby emblem, launch sheet, bubble, splash fallback).
    No plate, no padding: it is the same art the launcher shows.
    """
    w = int(round(height * ASPECT))
    S = w * SS
    art = Image.new("RGBA", (S, S // 1), (0, 0, 0, 0))  # placeholder, replaced below
    art = Image.new("RGBA", (S, int(round(height * SS))), (0, 0, 0, 0))
    body = Image.new("L", (S, art.size[1]), 0)
    poly = [(x * S, y * art.size[1]) for x, y in BODY]
    ImageDraw.Draw(body).polygon(poly, fill=255)
    art.paste(_ramp(S, art.size[1]), (0, 0), body)
    rim = Image.new("RGBA", art.size, (0, 0, 0, 0))
    rd = ImageDraw.Draw(rim)
    for i in range(len(poly)):
        (x1, y1), (x2, y2) = poly[i], poly[(i + 1) % len(poly)]
        if (y1 + y2) / 2 < art.size[1] * 0.46:
            continue
        rd.line([(x1, y1), (x2, y2)], fill=RIM + (200,), width=max(2, int(3.2 * SS)))
    art.alpha_composite(Image.composite(rim, Image.new("RGBA", art.size, (0, 0, 0, 0)), body))
    ctl = Image.new("L", art.size, 0)
    _draw_controls(ImageDraw.Draw(ctl), 0.0, 0.0, float(S), float(art.size[1]))
    art.paste(Image.new("RGBA", art.size, INK + (255,)), (0, 0), ctl)
    return art.resize((w, height), Image.LANCZOS)


def render_mark_mono(height=420):
    w = int(round(height * ASPECT))
    S = w * SS
    art = Image.new("RGBA", (S, int(round(height * SS))), (0, 0, 0, 0))
    poly = [(x * S, y * art.size[1]) for x, y in BODY]
    body = Image.new("L", art.size, 0)
    ImageDraw.Draw(body).polygon(poly, fill=255)
    art.paste(Image.new("RGBA", art.size, (255, 255, 255, 255)), (0, 0), body)
    cut = Image.new("L", art.size, 0)
    _draw_controls(ImageDraw.Draw(cut), 0.0, 0.0, float(S), float(art.size[1]))
    art.paste((0, 0, 0, 0), (0, 0), cut)
    return art.resize((w, height), Image.LANCZOS)


def render_emblem(height=360):
    """The pad on a soft circular bed — the in-app emblem.

    Used by the lobby hero card, the launch sheet, the floating bubble and the
    intro fallback. Same pad as the launcher; only the bed is added, because a
    wide thin mark has nothing to sit on when the panel behind it is dark.
    """
    S = height
    pad_h = max(1, int(round(height * 0.52)))
    pad_w = max(1, int(round(pad_h * ASPECT)))
    pad = render_pad("dark", height=pad_h, span=0.5, canvas=pad_w + int(pad_w * 0.24))
    bed = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bed)
    bd.ellipse([6, 6, S - 7, S - 7], fill=(0x14, 0x10, 0x16, 255))
    bd.ellipse([6, 6, S - 7, S - 7], outline=(0x3A, 0x1A, 0x1C, 255), width=3)
    glow = bed.split()[3].filter(ImageFilter.GaussianBlur(10))
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(Image.new("RGBA", (S, S), (0x7A, 0x12, 0x14, 255)), (0, 0), glow)
    out.alpha_composite(bed)
    out.alpha_composite(pad, ((S - pad.size[0]) // 2, (S - pad.size[1]) // 2))
    return out


def plate(w, h, bg, art, round_full=False):
    """Compose a legacy (pre-adaptive) icon: plate + pad, centred."""
    S = max(w, h) * SS
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if round_full:
        d.ellipse([0, 0, S - 1, S - 1], fill=bg + (255,))
    else:
        d.rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=bg + (255,))
    scaled = art.resize((S, S), Image.LANCZOS)
    img.alpha_composite(scaled)
    return img.resize((w, h), Image.LANCZOS)


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
    with open(path, "wb" if isinstance(data, bytes) else "w") as fh:
        fh.write(data)


def main():
    # 432 is the adaptive-icon foreground size at xxxhdpi (108dp @ 4x)
    dark = render_pad("dark", height=232, span=0.60, canvas=432)
    light = render_pad("light", height=232, span=0.60, canvas=432)
    mono = render_pad("mono", height=232, span=0.60, canvas=432)

    dark.save(f"{RES}/drawable-night-nodpi/ic_pad_fg.png")
    light.save(f"{RES}/drawable-nodpi/ic_pad_fg.png")
    light.save(f"{RES}/drawable-nodpi/ic_pad_fg_light.png")
    dark.save(f"{RES}/drawable-nodpi/ic_pad_fg_dark.png")
    mono.save(f"{RES}/drawable-nodpi/ic_pad_fg_mono.png")

    anydpi = f"{RES}/mipmap-anydpi-v26"
    for name, bg, fg, why in [
        ("ic_launcher_pad", "@color/ic_pad_bg", "@drawable/ic_pad_fg",
         "Plate and art both have -night variants, so the icon follows the\n     system light/dark setting."),
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

    looks = {
        "ic_launcher_pad":       (PLATE_D, dark),
        "ic_launcher_pad_round": (PLATE_D, dark),
        "ic_launcher_pad_dark":  (PLATE_D, dark),
        "ic_launcher_pad_dark_round": (PLATE_D, dark),
        "ic_launcher_pad_light": (PLATE_L, light),
        "ic_launcher_pad_light_round": (PLATE_L, light),
        "ic_launcher_pad_mono":  (PLATE_D, mono),
        "ic_launcher_pad_mono_round": (PLATE_D, mono),
    }
    for dens, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        for name, (bg, art) in looks.items():
            plate(px, px, bg, art, round_full=name.endswith("_round")).save(f"{RES}/mipmap-{dens}/{name}.png")

    # the package-level fallback icons, same art
    for dens, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        plate(px, px, PLATE_D, dark).save(f"{RES}/mipmap-{dens}/ic_launcher.png")
        plate(px, px, PLATE_D, dark, round_full=True).save(f"{RES}/mipmap-{dens}/ic_launcher_round.png")

    # in-app emblem: the pad on a soft circular bed. A 1.94:1 mark disappears on a
    # dark panel without one, and the lobby card shows it at 112dp.
    render_emblem(360).save(f"{RES}/drawable-nodpi/ic_float_emblem.png")

    # settings previews — composed, because that is what the user will see
    for name, (bg, art) in {
        "ic_pick_auto":  (PLATE_D, dark),
        "ic_pick_dark":  (PLATE_D, dark),
        "ic_pick_light": (PLATE_L, light),
        "ic_pick_mono":  (PLATE_D, mono),
    }.items():
        plate(192, 192, bg, art).save(f"{RES}/drawable-nodpi/{name}.png")

    # status bar: white silhouette, the system tints it
    for dens, px in [("mdpi", 24), ("hdpi", 36), ("xhdpi", 48), ("xxhdpi", 72), ("xxxhdpi", 96)]:
        d = f"{RES}/drawable-{dens}"
        os.makedirs(d, exist_ok=True)
        render_pad("mono", height=int(px / ASPECT) or 1, span=0.96, canvas=px).save(f"{d}/ic_stat_gh.png")

    print("traced pad icon written")


if __name__ == "__main__":
    main()
