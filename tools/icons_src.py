"""Generate all 67 GamersHub icons as Compose ImageVector builders.

Design language, derived from the intro emblem:
  * 24x24 grid, 2u safe margin
  * strokes 1.75u, miter joins only, no round caps - the emblem has no curves
  * chamfered corners: short 45-degree facets instead of fillets
  * the emblem's downward V is the recurring container/divider motif
  * small solid diamonds read as the emblem's studs / circuit nodes
"""
import math

# ---------------------------------------------------------------- primitives

def P(*pts):
    """Path data from points."""
    return list(pts)

def fmt(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return s if s else "0"

def move(x, y): return f"M{fmt(x)},{fmt(y)}"
def line_to(x, y): return f"L{fmt(x)},{fmt(y)}"
def close(): return "Z"

def poly(pts, closed=True):
    d = move(*pts[0]) + "".join(line_to(*p) for p in pts[1:])
    return d + (close() if closed else "")

def stroke_pts(pts, closed=False):
    return poly(pts, closed=closed)

def chamfer_rect(x0, y0, x1, y1, c=1.6):
    """Rectangle with all four corners cut by c."""
    return poly([
        (x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c),
        (x1 - c, y1), (x0 + c, y1), (x0, y1 - c), (x0, y0 + c),
    ])

def chevron(cx, y, w, h, thick=1.75):
    """The emblem's V, as a stroked open polyline."""
    return stroke_pts([(cx - w, y), (cx, y + h), (cx + w, y)])

def diamond(cx, cy, r):
    return poly([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)])

def diag(x0, y0, x1, y1, thick=1.75):
    return stroke_pts([(x0, y0), (x1, y1)])

def bar(x, y, w, h):
    return chamfer_rect(x, y, x + w, y + h, min(0.9, h / 2.2))

# ---------------------------------------------------------------- registry
# name -> (list of (pathData, filled))
ICONS = {}

def icon(name, *parts):
    ICONS[name] = list(parts)

def stroke(d): return (d, False)
def fill(d): return (d, True)

# ---------------------------------------------------------------- glyphs

# --- navigation / chrome
icon("Close",
     stroke(stroke_pts([(5.5, 5.5), (18.5, 18.5)])),
     stroke(stroke_pts([(18.5, 5.5), (5.5, 18.5)])),
     stroke(stroke_pts([(5.5, 5.5), (4.2, 6.8)])),
     stroke(stroke_pts([(18.5, 5.5), (19.8, 6.8)])),
     stroke(stroke_pts([(18.5, 18.5), (19.8, 17.2)])),
     stroke(stroke_pts([(5.5, 18.5), (4.2, 17.2)])))

icon("ArrowBack",
     stroke(stroke_pts([(19, 12), (5, 12)])),
     stroke(stroke_pts([(11, 5.5), (4.5, 12), (11, 18.5)])))

icon("ChevronRight", stroke(stroke_pts([(9.5, 5), (16.5, 12), (9.5, 19)])))
icon("KeyboardArrowUp", stroke(stroke_pts([(5, 15.5), (12, 8.5), (19, 15.5)])))
icon("KeyboardArrowDown", stroke(stroke_pts([(5, 8.5), (12, 15.5), (19, 8.5)])))

icon("Add", stroke(stroke_pts([(12, 5), (12, 19)])), stroke(stroke_pts([(5, 12), (19, 12)])))
icon("Check", stroke(stroke_pts([(5, 12.5), (10, 18), (19.5, 6)])))
icon("CheckCircle",
     stroke(poly([(12, 2.5), (21.5, 12), (12, 21.5), (2.5, 12)], closed=True)),
     stroke(stroke_pts([(7.5, 12), (10.5, 15.5), (16.5, 8.5)])))

icon("Delete",
     stroke(chamfer_rect(5, 7, 19, 20.5)),
     stroke(stroke_pts([(3.5, 7), (20.5, 7)])),
     stroke(chamfer_rect(9, 3, 15, 6.5, 0.8)),
     stroke(stroke_pts([(10, 11), (10, 17)])),
     stroke(stroke_pts([(14, 11), (14, 17)])))

icon("DeleteSweep",
     stroke(chamfer_rect(7, 9, 18, 20)),
     stroke(stroke_pts([(5.5, 9), (19.5, 9)])),
     stroke(stroke_pts([(10, 3.5), (15, 3.5)])),
     stroke(stroke_pts([(11.5, 12.5), (11.5, 16.5)])),
     stroke(stroke_pts([(14, 12.5), (14, 16.5)])))

icon("Search",
     stroke(poly([(10.5, 3), (16, 5.5), (18.5, 10.5), (16, 15.5), (10.5, 18),
                  (5, 15.5), (2.5, 10.5), (5, 5.5)])),
     stroke(stroke_pts([(15.5, 15.5), (21, 21)])))

icon("Refresh",
     fill(poly([(12, 3.5), (21, 9), (18.5, 12), (12, 6.5), (5.5, 10), (5.5, 14),
                (12, 17.5), (18, 14.5), (20.5, 18), (12, 21), (3, 15), (3, 9)])))
icon("Autorenew",
     fill(poly([(12, 2.5), (20.5, 7), (17.5, 10), (14.5, 7.5), (10, 7.5), (8, 10),
                (8, 14), (10.5, 16.5), (14, 16.5), (17, 14.5), (20.5, 17),
                (15, 20.5), (8.5, 20.5), (3.5, 15.5), (3.5, 8.5), (7, 4.5)])))

icon("Settings",
     stroke(poly([(12, 2.5), (20.5, 7.2), (20.5, 16.8), (12, 21.5), (3.5, 16.8), (3.5, 7.2)])),
     fill(poly([(12, 7.5), (16.5, 12), (12, 16.5), (7.5, 12)])))
icon("Tune",
     stroke(stroke_pts([(3, 6.5), (21, 6.5)])),
     fill(bar(13.5, 4.6, 4, 3.8)),
     stroke(stroke_pts([(3, 12), (21, 12)])),
     fill(bar(6.5, 10.1, 4, 3.8)),
     stroke(stroke_pts([(3, 17.5), (21, 17.5)])),
     fill(bar(14.5, 15.6, 4, 3.8)))
icon("Adjust",
     stroke(poly([(12, 2.5), (21, 10.2), (17.5, 21), (6.5, 21), (3, 10.2)])),
     fill(diamond(12, 12, 3)))
icon("Palette",
     stroke(poly([(12, 2.5), (21, 7), (21, 17), (12, 21.5), (3, 17), (3, 7)])),
     fill(diamond(12, 12, 4.2)))

# --- power / engine
icon("PowerSettingsNew",
     stroke(stroke_pts([(12, 2.5), (12, 12)])),
     stroke(poly([(6.5, 5.5), (3.5, 10), (3.5, 15.5), (8, 20), (16, 20), (20.5, 15.5),
                  (20.5, 10), (17.5, 5.5)], closed=False)))

icon("Security",
     stroke(poly([(12, 2), (21, 5.5), (21, 12), (12, 22), (3, 12), (3, 5.5)])),
     fill(diamond(12, 11, 3.2)))
icon("Shield",
     stroke(poly([(12, 2), (21, 5.5), (21, 12), (12, 22), (3, 12), (3, 5.5)])),
     stroke(stroke_pts([(8, 11.5), (11, 15), (16.5, 8.5)])))
icon("Warning",
     stroke(poly([(12, 2.5), (22, 20), (2, 20)])),
     fill(diamond(12, 13, 2.2)),
     fill(diamond(12, 17.6, 1.5)))

icon("Block",
     stroke(poly([(12, 2.5), (21.5, 7), (21.5, 17), (12, 21.5), (2.5, 17), (2.5, 7)])),
     stroke(stroke_pts([(6.5, 6.5), (17.5, 17.5)])))

icon("AdminPanelSettings",
     stroke(poly([(12, 2), (21, 5.5), (21, 12), (12, 22), (3, 12), (3, 5.5)])),
     stroke(stroke_pts([(12, 7), (12, 12)])),
     fill(diamond(12, 16, 1.8)))

icon("Lock",
     stroke(chamfer_rect(4.5, 10, 19.5, 21)),
     stroke(stroke_pts([(8, 10), (8, 7), (12, 3.5), (16, 7), (16, 10)])),
     fill(diamond(12, 15.5, 1.8)))

icon("Extension",
     fill(poly([(9, 3), (15, 3), (15, 8), (20, 8), (20, 14), (15, 14), (15, 19),
                (9, 19), (9, 14), (4, 14), (4, 8), (9, 8)])))

# --- performance
icon("Bolt",
     fill(poly([(13.5, 2), (4.5, 13.5), (10.5, 13.5), (9, 22), (19.5, 9.5), (13, 9.5)])))
icon("Speed",
     stroke(stroke_pts([(3, 17), (12, 5.5), (21, 17)])),
     stroke(stroke_pts([(12, 17), (16.5, 9.5)])),
     fill(diamond(12, 17, 2)))
icon("FastForward",
     fill(poly([(2.5, 5), (11, 12), (2.5, 19)])),
     fill(poly([(12, 5), (20.5, 12), (12, 19)])))
icon("PlayArrow", fill(poly([(6, 3.5), (21, 12), (6, 20.5)])))
icon("Memory",
     stroke(chamfer_rect(3.5, 6.5, 20.5, 17.5, 1.2)),
     fill(poly([(7, 9.5), (17, 9.5), (17, 14.5), (7, 14.5)])),
     stroke(stroke_pts([(7, 3.5), (7, 6.5)])),
     stroke(stroke_pts([(12, 3.5), (12, 6.5)])),
     stroke(stroke_pts([(17, 3.5), (17, 6.5)])),
     stroke(stroke_pts([(7, 17.5), (7, 20.5)])),
     stroke(stroke_pts([(12, 17.5), (12, 20.5)])),
     stroke(stroke_pts([(17, 17.5), (17, 20.5)])))

icon("Storage",
     stroke(chamfer_rect(3, 4.5, 21, 10.5, 1.2)),
     stroke(chamfer_rect(3, 13.5, 21, 19.5, 1.2)),
     fill(diamond(17.5, 7.5, 1.4)),
     fill(diamond(17.5, 16.5, 1.4)))

icon("GraphicEq",
     fill(bar(2.5, 10, 2.2, 8)), fill(bar(7.5, 4.5, 2.2, 15)),
     fill(bar(12.5, 8, 2.2, 9)), fill(bar(17.5, 6, 2.2, 12)),
     fill(diamond(3.6, 8.6, 1.3)), fill(diamond(18.6, 4.6, 1.3)))
icon("Equal", fill(bar(3.5, 8, 17, 2.6)), fill(bar(3.5, 14, 17, 2.6)))

icon("Thermostat",
     stroke(stroke_pts([(10, 13.5), (10, 4.5), (14, 4.5), (14, 13.5)])),
     fill(diamond(12, 17.5, 3)))

icon("PieChart",
     fill(poly([(12, 2.5), (12, 12), (21.5, 12)])),
     stroke(poly([(12, 2.5), (21.5, 12), (12, 21.5), (2.5, 12)])))

# --- display / capture
icon("BrightnessHigh",
     fill(poly([(12, 2), (14.5, 9), (21.5, 9.5), (16, 14), (18, 21), (12, 17),
                (6, 21), (8, 14), (2.5, 9.5), (9.5, 9)])))
icon("Screenshot",
     stroke(poly([(3, 8), (3, 3), (8, 3)])),
     stroke(poly([(16, 3), (21, 3), (21, 8)])),
     stroke(poly([(21, 16), (21, 21), (16, 21)])),
     stroke(poly([(8, 21), (3, 21), (3, 16)])))
icon("ScreenRotation",
     stroke(poly([(12, 2.5), (21.5, 12), (12, 21.5), (2.5, 12)])),
     fill(diamond(12, 12, 3.4)))
icon("ScreenLockPortrait",
     stroke(chamfer_rect(6, 2.5, 18, 21.5, 1.4)),
     fill(diamond(12, 11, 2)),
     stroke(stroke_pts([(9.5, 15.5), (14.5, 15.5)])))
icon("PhotoCamera",
     stroke(chamfer_rect(2.5, 7, 21.5, 19.5, 1.4)),
     fill(poly([(9, 4), (15, 4), (16.5, 7), (7.5, 7)])),
     fill(diamond(12, 13, 3.4)))
icon("Videocam",
     stroke(chamfer_rect(2.5, 6.5, 15, 17.5, 1.4)),
     fill(poly([(16.5, 10), (21.5, 6.5), (21.5, 17.5), (16.5, 14)])))
icon("CenterFocusStrong",
     stroke(poly([(3, 8), (3, 3), (8, 3)])),
     stroke(poly([(16, 3), (21, 3), (21, 8)])),
     stroke(poly([(21, 16), (21, 21), (16, 21)])),
     stroke(poly([(8, 21), (3, 21), (3, 16)])),
     fill(diamond(12, 12, 3)))
icon("OpenInFull",
     fill(poly([(3, 3), (10, 3), (3, 10)])),
     fill(poly([(21, 21), (14, 21), (21, 14)])))
icon("CloudDownload",
     stroke(poly([(6, 16), (3.5, 12), (5.5, 6.5), (12, 3.5), (18.5, 6.5), (20.5, 12),
                  (18, 16)], closed=False)),
     stroke(stroke_pts([(12, 10), (12, 20)])),
     stroke(stroke_pts([(7.5, 15.5), (12, 20), (16.5, 15.5)])))

# --- input devices
icon("Gamepad",
     fill(poly([(6, 7.5), (18, 7.5), (22.5, 17), (15.5, 17), (13.5, 13.5),
                (10.5, 13.5), (8.5, 17), (1.5, 17)])),
     fill(diamond(7.5, 11, 1.5)),
     fill(diamond(16.5, 11, 1.5)))
icon("VideogameAsset",
     stroke(chamfer_rect(2, 7, 22, 18.5, 1.6)),
     fill(diamond(7.5, 12.5, 1.6)),
     fill(diamond(16, 11.5, 1.3)),
     fill(diamond(18.5, 14, 1.3)))
icon("TouchApp",
     fill(poly([(9, 3), (13, 3), (13, 12), (17, 13.5), (18, 21), (9, 21), (8, 15)])))
icon("Dialpad",
     fill(diamond(6, 6, 1.7)), fill(diamond(12, 6, 1.7)), fill(diamond(18, 6, 1.7)),
     fill(diamond(6, 12, 1.7)), fill(diamond(12, 12, 1.7)), fill(diamond(18, 12, 1.7)),
     fill(diamond(6, 18, 1.7)), fill(diamond(12, 18, 1.7)), fill(diamond(18, 18, 1.7)))
icon("Smartphone",
     stroke(chamfer_rect(6, 2, 18, 22, 1.6)),
     stroke(stroke_pts([(9.5, 5), (14.5, 5)])),
     fill(diamond(12, 18.5, 1.4)))
icon("Usb",
     stroke(stroke_pts([(12, 21), (12, 4)])),
     fill(poly([(12, 2), (15, 6), (9, 6)])),
     fill(poly([(8, 12), (8, 15), (5, 13.5)])),
     fill(diamond(16, 10, 1.6)))
icon("Keyboard",
     stroke(chamfer_rect(2, 7, 22, 17, 1.4)),
     fill(bar(5, 10, 2, 1.6)), fill(bar(8.5, 10, 2, 1.6)), fill(bar(12, 10, 2, 1.6)),
     fill(bar(15.5, 10, 2, 1.6)), fill(bar(19, 10, 1.5, 1.6)),
     fill(bar(6, 13.5, 12, 1.6)))
icon("Mouse",
     stroke(chamfer_rect(7, 2.5, 17, 21.5, 2.2)),
     stroke(stroke_pts([(12, 6.5), (12, 10)])))

# --- connectivity
icon("Wifi",
     stroke(stroke_pts([(3.5, 9), (12, 3), (20.5, 9)])),
     stroke(stroke_pts([(6.5, 13), (12, 8.8), (17.5, 13)])),
     stroke(stroke_pts([(9.5, 17), (12, 15), (14.5, 17)])),
     fill(diamond(12, 20.5, 1.4)))
icon("Dns",
     stroke(chamfer_rect(3, 3.5, 21, 10, 1.2)),
     stroke(chamfer_rect(3, 14, 21, 20.5, 1.2)),
     fill(diamond(17.5, 6.8, 1.3)),
     fill(diamond(17.5, 17.3, 1.3)))
icon("Language",
     stroke(poly([(12, 2.5), (21.5, 12), (12, 21.5), (2.5, 12)])),
     stroke(stroke_pts([(2.5, 12), (21.5, 12)])),
     stroke(stroke_pts([(12, 2.5), (7, 12), (12, 21.5)])),
     stroke(stroke_pts([(12, 2.5), (17, 12), (12, 21.5)])))
icon("GpsFixed",
     stroke(poly([(12, 3), (20, 7), (20, 15), (12, 21), (4, 15), (4, 7)])),
     fill(diamond(12, 12, 2.6)))
icon("MyLocation",
     stroke(poly([(12, 2.5), (21.5, 12), (12, 21.5), (2.5, 12)])),
     fill(diamond(12, 12, 2.4)),
     stroke(stroke_pts([(12, 5), (12, 7.5)])),
     stroke(stroke_pts([(12, 16.5), (12, 19)])),
     stroke(stroke_pts([(5, 12), (7.5, 12)])),
     stroke(stroke_pts([(16.5, 12), (19, 12)])))

# --- content / media
icon("Apps",
     fill(chamfer_rect(3, 3, 10.5, 10.5, 1.2)),
     fill(chamfer_rect(13.5, 3, 21, 10.5, 1.2)),
     fill(chamfer_rect(3, 13.5, 10.5, 21, 1.2)),
     fill(chamfer_rect(13.5, 13.5, 21, 21, 1.2)))
icon("Widgets",
     fill(poly([(12, 2.5), (17.5, 8), (12, 13.5), (6.5, 8)])),
     fill(chamfer_rect(3, 15, 10, 21.5, 1.2)),
     fill(chamfer_rect(14, 15, 21, 21.5, 1.2)))
icon("MusicNote",
     fill(poly([(9, 18.5), (9, 4), (19, 2), (19, 6.5), (11.5, 8), (11.5, 15)])),
     fill(diamond(6.5, 18.5, 3.2)))
icon("Podcasts",
     stroke(stroke_pts([(12, 8.5), (12, 15.5)])),
     fill(diamond(12, 5, 2)),
     stroke(stroke_pts([(7, 7.5), (7, 16)])),
     stroke(stroke_pts([(17, 7.5), (17, 16)])),
     stroke(stroke_pts([(3.5, 10), (3.5, 14)])),
     stroke(stroke_pts([(20.5, 10), (20.5, 14)])))
icon("Campaign",
     fill(poly([(3, 9), (11, 9), (19, 3.5), (19, 20.5), (11, 15), (3, 15)])))
icon("Share",
     stroke(stroke_pts([(6, 18), (18, 6)])),
     fill(diamond(5.5, 18.5, 2)),
     fill(diamond(18.5, 5.5, 2)))

# --- identity / misc
icon("Person",
     fill(poly([(12, 2.5), (17.5, 7), (17.5, 12), (12, 16), (6.5, 12), (6.5, 7)])),
     fill(poly([(4, 21.5), (6, 17.5), (18, 17.5), (20, 21.5)])))
icon("Home",
     fill(poly([(12, 2.5), (21.5, 10), (19, 10), (19, 21.5), (5, 21.5), (5, 10), (2.5, 10)])))
icon("Info",
     stroke(poly([(12, 2.5), (21.5, 12), (12, 21.5), (2.5, 12)])),
     fill(diamond(12, 7.5, 1.4)),
     fill(bar(11, 11, 2, 6.5)))
icon("Notifications",
     stroke(poly([(6, 15), (6, 8.5), (9, 4.5), (15, 4.5), (18, 8.5), (18, 15), (20.5, 18),
                  (3.5, 18)], closed=False)),
     fill(diamond(12, 21, 1.6)))
icon("NotificationsOff",
     stroke(poly([(6, 15), (6, 8.5), (9, 4.5), (15, 4.5), (18, 8.5), (18, 15)], closed=False)),
     stroke(stroke_pts([(3, 3), (21, 21)])))
icon("AccessTime",
     stroke(poly([(12, 2.5), (21.5, 12), (12, 21.5), (2.5, 12)])),
     stroke(stroke_pts([(12, 7), (12, 12), (16, 14.5)])))
icon("Chat",
     stroke(poly([(3, 4.5), (21, 4.5), (21, 15), (10, 15), (6, 20), (6, 15), (3, 15)])))
icon("AutoAwesome",
     fill(poly([(12, 2), (13.8, 9), (21, 11), (13.8, 13), (12, 20.5), (10.2, 13),
                (3, 11), (10.2, 9)])))
icon("Rocket",
     fill(poly([(12, 2), (16.5, 8), (16.5, 15), (12, 19), (7.5, 15), (7.5, 8)])),
     fill(poly([(7.5, 14), (4, 20.5), (7.5, 19)])),
     fill(poly([(16.5, 14), (20, 20.5), (16.5, 19)])),
     fill(diamond(12, 10, 1.8)))
icon("Science",
     stroke(poly([(9, 2.5), (9, 9), (3.5, 19), (20.5, 19), (15, 9), (15, 2.5)])),
     fill(diamond(12, 15.5, 2)))
icon("Android",
     fill(poly([(3.5, 20.5), (5.5, 9), (18.5, 9), (20.5, 20.5)])),
     fill(poly([(6, 9), (8, 3), (10, 3), (8.5, 9)])),
     fill(poly([(14, 9), (16, 3), (18, 3), (16, 9)])),
     fill(diamond(8.5, 13.5, 1.4)),
     fill(diamond(15.5, 13.5, 1.4)))
icon("CleaningServices",
     stroke(chamfer_rect(8, 2.5, 16, 8, 1)),
     stroke(chamfer_rect(3.5, 10, 20.5, 17, 1.2)),
     stroke(stroke_pts([(8, 17), (8, 21)])),
     stroke(stroke_pts([(16, 17), (16, 21)])))
icon("TrailLengthMedium",
     fill(poly([(3, 8), (14, 8), (16.5, 10.4), (14, 12.8), (3, 12.8)])),
     fill(poly([(3, 15), (19, 15), (21.5, 17.4), (19, 19.8), (3, 19.8)])))
icon("Sync",
     stroke(stroke_pts([(4, 12), (4, 8), (8, 4), (16, 4), (20, 8)])),
     stroke(stroke_pts([(20, 12), (20, 16), (16, 20), (8, 20), (4, 16)])))

# ---------------------------------------------------------------- output

def kotlin_for(name, parts):
    body = []
    for d, filled in parts:
        if filled:
            body.append(f'            path(fill = SolidColor(Color.Black)) {{ {pathcalls(d)} }}')
        else:
            body.append(f'            path(\n                fill = null,\n                stroke = SolidColor(Color.Black),\n                strokeLineWidth = 1.75f,\n                strokeLineCap = StrokeCap.Butt,\n                strokeLineJoin = StrokeJoin.Miter\n            ) {{ {pathcalls(d)} }}')
    return body

def pathcalls(d):
    """Turn 'M12,2L...' into builder calls."""
    import re
    out = []
    for cmd, args in re.findall(r'([MLZ])([^MLZ]*)', d):
        if cmd == 'M':
            x, y = args.split(",")
            out.append(f"moveTo({x}f, {y}f)")
        elif cmd == 'L':
            x, y = args.split(",")
            out.append(f"lineTo({x}f, {y}f)")
        else:
            out.append("close()")
    return "; ".join(out)

print("generated", len(ICONS), "icons")
