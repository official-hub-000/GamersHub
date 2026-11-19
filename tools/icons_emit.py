import os
import re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import icons_src as M

HEADER = '''package com.gamershub.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The GamersHub icon set.
 *
 * These replace the Material icons across the app. The brief was "according to the
 * intro", so the language is taken from the emblem rather than invented:
 *
 *  - **No curves.** The emblem is cut from straight facets and the film never
 *    eases a corner. Every glyph here uses miter joins and butt caps, and corners
 *    are chamfered with 45-degree facets instead of rounded.
 *  - **The V.** The emblem's downward chevron is the recurring motif — it closes
 *    shields, forms gauges, and cuts the ends of bars.
 *  - **Diamonds.** The emblem's studs and circuit nodes become the way this set
 *    marks a point of interest, where Material would use a filled dot.
 *  - **One weight.** 1.75u strokes on a 24u grid, so icons sit together in a row
 *    without the thickness drift Material has between filled and outline styles.
 *
 * Generated from `tools/icons_src.py`; edit that and re-run rather than editing
 * coordinates here by hand. `tools/icons_preview.py` renders the same geometry to
 * a PNG sheet so the set can be checked without building an APK.
 */
object GhIcons {

'''

FILTERS = '''
}

/** Filled variants — kept so `GhIcons.Filled.X` reads like the Material call it replaces. */
object GhFilled {
    private fun alias(v: ImageVector) = v
'''

def build_paths(parts):
    out = []
    for d, filled in parts:
        calls = []
        for cmd, args in re.findall(r'([MLZ])([^MLZ]*)', d):
            if cmd == 'M':
                x, y = args.split(",")
                calls.append(f"moveTo({x}f, {y}f)")
            elif cmd == 'L':
                x, y = args.split(",")
                calls.append(f"lineTo({x}f, {y}f)")
            else:
                calls.append("close()")
        body = "; ".join(calls)
        if filled:
            out.append(f"            path(fill = SolidColor(Color.Black)) {{ {body} }}")
        else:
            out.append(
                "            path(\n"
                "                fill = null,\n"
                "                stroke = SolidColor(Color.Black),\n"
                "                strokeLineWidth = 1.75f,\n"
                "                strokeLineCap = StrokeCap.Butt,\n"
                "                strokeLineJoin = StrokeJoin.Miter\n"
                f"            ) {{ {body} }}"
            )
    return "\n".join(out)

lines = [HEADER]
for name in sorted(M.ICONS):
    parts = M.ICONS[name]
    lines.append(
        f"    val {name}: ImageVector by lazy {{\n"
        f"        ImageVector.Builder(\n"
        f"            name = \"Gh.{name}\",\n"
        f"            defaultWidth = 24.dp,\n"
        f"            defaultHeight = 24.dp,\n"
        f"            viewportWidth = 24f,\n"
        f"            viewportHeight = 24f\n"
        f"        ).apply {{\n"
        f"{build_paths(parts)}\n"
        f"        }}.build()\n"
        f"    }}\n"
    )

# alias object so both naming styles resolve
lines.append(FILTERS)
for name in sorted(M.ICONS):
    lines.append(f"    val {name}: ImageVector get() = GhIcons.{name}\n")
lines.append("}\n")

out = "\n".join(lines)
import pathlib
p = pathlib.Path("/home/user/GamersHub/app/src/main/java/com/gamershub/ui/icons/GhIcons.kt")
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(out)
print("wrote", p, len(out.splitlines()), "lines,", len(M.ICONS), "icons")
