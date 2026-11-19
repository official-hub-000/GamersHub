package com.gamershub.ui.icons

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


    val AccessTime: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.AccessTime",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2.5f); lineTo(21.5f, 12f); lineTo(12f, 21.5f); lineTo(2.5f, 12f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 7f); lineTo(12f, 12f); lineTo(16f, 14.5f) }
        }.build()
    }

    val Add: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Add",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 5f); lineTo(12f, 19f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(5f, 12f); lineTo(19f, 12f) }
        }.build()
    }

    val Adjust: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Adjust",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2.5f); lineTo(21f, 10.2f); lineTo(17.5f, 21f); lineTo(6.5f, 21f); lineTo(3f, 10.2f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 9f); lineTo(15f, 12f); lineTo(12f, 15f); lineTo(9f, 12f); close() }
        }.build()
    }

    val AdminPanelSettings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.AdminPanelSettings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2f); lineTo(21f, 5.5f); lineTo(21f, 12f); lineTo(12f, 22f); lineTo(3f, 12f); lineTo(3f, 5.5f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 7f); lineTo(12f, 12f) }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 14.2f); lineTo(13.8f, 16f); lineTo(12f, 17.8f); lineTo(10.2f, 16f); close() }
        }.build()
    }

    val Android: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Android",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(3.5f, 20.5f); lineTo(5.5f, 9f); lineTo(18.5f, 9f); lineTo(20.5f, 20.5f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(6f, 9f); lineTo(8f, 3f); lineTo(10f, 3f); lineTo(8.5f, 9f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(14f, 9f); lineTo(16f, 3f); lineTo(18f, 3f); lineTo(16f, 9f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(8.5f, 12.1f); lineTo(9.9f, 13.5f); lineTo(8.5f, 14.9f); lineTo(7.1f, 13.5f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(15.5f, 12.1f); lineTo(16.9f, 13.5f); lineTo(15.5f, 14.9f); lineTo(14.1f, 13.5f); close() }
        }.build()
    }

    val Apps: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Apps",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(4.2f, 3f); lineTo(9.3f, 3f); lineTo(10.5f, 4.2f); lineTo(10.5f, 9.3f); lineTo(9.3f, 10.5f); lineTo(4.2f, 10.5f); lineTo(3f, 9.3f); lineTo(3f, 4.2f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(14.7f, 3f); lineTo(19.8f, 3f); lineTo(21f, 4.2f); lineTo(21f, 9.3f); lineTo(19.8f, 10.5f); lineTo(14.7f, 10.5f); lineTo(13.5f, 9.3f); lineTo(13.5f, 4.2f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(4.2f, 13.5f); lineTo(9.3f, 13.5f); lineTo(10.5f, 14.7f); lineTo(10.5f, 19.8f); lineTo(9.3f, 21f); lineTo(4.2f, 21f); lineTo(3f, 19.8f); lineTo(3f, 14.7f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(14.7f, 13.5f); lineTo(19.8f, 13.5f); lineTo(21f, 14.7f); lineTo(21f, 19.8f); lineTo(19.8f, 21f); lineTo(14.7f, 21f); lineTo(13.5f, 19.8f); lineTo(13.5f, 14.7f); close() }
        }.build()
    }

    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.ArrowBack",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(19f, 12f); lineTo(5f, 12f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(11f, 5.5f); lineTo(4.5f, 12f); lineTo(11f, 18.5f) }
        }.build()
    }

    val AutoAwesome: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.AutoAwesome",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 2f); lineTo(13.8f, 9f); lineTo(21f, 11f); lineTo(13.8f, 13f); lineTo(12f, 20.5f); lineTo(10.2f, 13f); lineTo(3f, 11f); lineTo(10.2f, 9f); close() }
        }.build()
    }

    val Autorenew: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Autorenew",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 2.5f); lineTo(20.5f, 7f); lineTo(17.5f, 10f); lineTo(14.5f, 7.5f); lineTo(10f, 7.5f); lineTo(8f, 10f); lineTo(8f, 14f); lineTo(10.5f, 16.5f); lineTo(14f, 16.5f); lineTo(17f, 14.5f); lineTo(20.5f, 17f); lineTo(15f, 20.5f); lineTo(8.5f, 20.5f); lineTo(3.5f, 15.5f); lineTo(3.5f, 8.5f); lineTo(7f, 4.5f); close() }
        }.build()
    }

    val Block: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Block",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2.5f); lineTo(21.5f, 7f); lineTo(21.5f, 17f); lineTo(12f, 21.5f); lineTo(2.5f, 17f); lineTo(2.5f, 7f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f) }
        }.build()
    }

    val Bolt: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Bolt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(13.5f, 2f); lineTo(4.5f, 13.5f); lineTo(10.5f, 13.5f); lineTo(9f, 22f); lineTo(19.5f, 9.5f); lineTo(13f, 9.5f); close() }
        }.build()
    }

    val BrightnessHigh: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.BrightnessHigh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 2f); lineTo(14.5f, 9f); lineTo(21.5f, 9.5f); lineTo(16f, 14f); lineTo(18f, 21f); lineTo(12f, 17f); lineTo(6f, 21f); lineTo(8f, 14f); lineTo(2.5f, 9.5f); lineTo(9.5f, 9f); close() }
        }.build()
    }

    val Campaign: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Campaign",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(3f, 9f); lineTo(11f, 9f); lineTo(19f, 3.5f); lineTo(19f, 20.5f); lineTo(11f, 15f); lineTo(3f, 15f); close() }
        }.build()
    }

    val CenterFocusStrong: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.CenterFocusStrong",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(3f, 8f); lineTo(3f, 3f); lineTo(8f, 3f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(16f, 3f); lineTo(21f, 3f); lineTo(21f, 8f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(21f, 16f); lineTo(21f, 21f); lineTo(16f, 21f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(8f, 21f); lineTo(3f, 21f); lineTo(3f, 16f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 9f); lineTo(15f, 12f); lineTo(12f, 15f); lineTo(9f, 12f); close() }
        }.build()
    }

    val Chat: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Chat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(3f, 4.5f); lineTo(21f, 4.5f); lineTo(21f, 15f); lineTo(10f, 15f); lineTo(6f, 20f); lineTo(6f, 15f); lineTo(3f, 15f); close() }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(5f, 12.5f); lineTo(10f, 18f); lineTo(19.5f, 6f) }
        }.build()
    }

    val CheckCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.CheckCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2.5f); lineTo(21.5f, 12f); lineTo(12f, 21.5f); lineTo(2.5f, 12f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(7.5f, 12f); lineTo(10.5f, 15.5f); lineTo(16.5f, 8.5f) }
        }.build()
    }

    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.ChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(9.5f, 5f); lineTo(16.5f, 12f); lineTo(9.5f, 19f) }
        }.build()
    }

    val CleaningServices: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.CleaningServices",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(9f, 2.5f); lineTo(15f, 2.5f); lineTo(16f, 3.5f); lineTo(16f, 7f); lineTo(15f, 8f); lineTo(9f, 8f); lineTo(8f, 7f); lineTo(8f, 3.5f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(4.7f, 10f); lineTo(19.3f, 10f); lineTo(20.5f, 11.2f); lineTo(20.5f, 15.8f); lineTo(19.3f, 17f); lineTo(4.7f, 17f); lineTo(3.5f, 15.8f); lineTo(3.5f, 11.2f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(8f, 17f); lineTo(8f, 21f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(16f, 17f); lineTo(16f, 21f) }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Close",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(5.5f, 5.5f); lineTo(18.5f, 18.5f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(18.5f, 5.5f); lineTo(5.5f, 18.5f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(5.5f, 5.5f); lineTo(4.2f, 6.8f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(18.5f, 5.5f); lineTo(19.8f, 6.8f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(18.5f, 18.5f); lineTo(19.8f, 17.2f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(5.5f, 18.5f); lineTo(4.2f, 17.2f) }
        }.build()
    }

    val CloudDownload: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.CloudDownload",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(6f, 16f); lineTo(3.5f, 12f); lineTo(5.5f, 6.5f); lineTo(12f, 3.5f); lineTo(18.5f, 6.5f); lineTo(20.5f, 12f); lineTo(18f, 16f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 10f); lineTo(12f, 20f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(7.5f, 15.5f); lineTo(12f, 20f); lineTo(16.5f, 15.5f) }
        }.build()
    }

    val Delete: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Delete",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(6.6f, 7f); lineTo(17.4f, 7f); lineTo(19f, 8.6f); lineTo(19f, 18.9f); lineTo(17.4f, 20.5f); lineTo(6.6f, 20.5f); lineTo(5f, 18.9f); lineTo(5f, 8.6f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(3.5f, 7f); lineTo(20.5f, 7f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(9.8f, 3f); lineTo(14.2f, 3f); lineTo(15f, 3.8f); lineTo(15f, 5.7f); lineTo(14.2f, 6.5f); lineTo(9.8f, 6.5f); lineTo(9f, 5.7f); lineTo(9f, 3.8f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(10f, 11f); lineTo(10f, 17f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(14f, 11f); lineTo(14f, 17f) }
        }.build()
    }

    val DeleteSweep: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.DeleteSweep",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(8.6f, 9f); lineTo(16.4f, 9f); lineTo(18f, 10.6f); lineTo(18f, 18.4f); lineTo(16.4f, 20f); lineTo(8.6f, 20f); lineTo(7f, 18.4f); lineTo(7f, 10.6f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(5.5f, 9f); lineTo(19.5f, 9f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(10f, 3.5f); lineTo(15f, 3.5f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(11.5f, 12.5f); lineTo(11.5f, 16.5f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(14f, 12.5f); lineTo(14f, 16.5f) }
        }.build()
    }

    val Dialpad: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Dialpad",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(6f, 4.3f); lineTo(7.7f, 6f); lineTo(6f, 7.7f); lineTo(4.3f, 6f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 4.3f); lineTo(13.7f, 6f); lineTo(12f, 7.7f); lineTo(10.3f, 6f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(18f, 4.3f); lineTo(19.7f, 6f); lineTo(18f, 7.7f); lineTo(16.3f, 6f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(6f, 10.3f); lineTo(7.7f, 12f); lineTo(6f, 13.7f); lineTo(4.3f, 12f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 10.3f); lineTo(13.7f, 12f); lineTo(12f, 13.7f); lineTo(10.3f, 12f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(18f, 10.3f); lineTo(19.7f, 12f); lineTo(18f, 13.7f); lineTo(16.3f, 12f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(6f, 16.3f); lineTo(7.7f, 18f); lineTo(6f, 19.7f); lineTo(4.3f, 18f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 16.3f); lineTo(13.7f, 18f); lineTo(12f, 19.7f); lineTo(10.3f, 18f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(18f, 16.3f); lineTo(19.7f, 18f); lineTo(18f, 19.7f); lineTo(16.3f, 18f); close() }
        }.build()
    }

    val Dns: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Dns",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(4.2f, 3.5f); lineTo(19.8f, 3.5f); lineTo(21f, 4.7f); lineTo(21f, 8.8f); lineTo(19.8f, 10f); lineTo(4.2f, 10f); lineTo(3f, 8.8f); lineTo(3f, 4.7f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(4.2f, 14f); lineTo(19.8f, 14f); lineTo(21f, 15.2f); lineTo(21f, 19.3f); lineTo(19.8f, 20.5f); lineTo(4.2f, 20.5f); lineTo(3f, 19.3f); lineTo(3f, 15.2f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(17.5f, 5.5f); lineTo(18.8f, 6.8f); lineTo(17.5f, 8.1f); lineTo(16.2f, 6.8f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(17.5f, 16f); lineTo(18.8f, 17.3f); lineTo(17.5f, 18.6f); lineTo(16.2f, 17.3f); close() }
        }.build()
    }

    val Equal: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Equal",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(4.4f, 8f); lineTo(19.6f, 8f); lineTo(20.5f, 8.9f); lineTo(20.5f, 9.7f); lineTo(19.6f, 10.6f); lineTo(4.4f, 10.6f); lineTo(3.5f, 9.7f); lineTo(3.5f, 8.9f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(4.4f, 14f); lineTo(19.6f, 14f); lineTo(20.5f, 14.9f); lineTo(20.5f, 15.7f); lineTo(19.6f, 16.6f); lineTo(4.4f, 16.6f); lineTo(3.5f, 15.7f); lineTo(3.5f, 14.9f); close() }
        }.build()
    }

    val Extension: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Extension",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(9f, 3f); lineTo(15f, 3f); lineTo(15f, 8f); lineTo(20f, 8f); lineTo(20f, 14f); lineTo(15f, 14f); lineTo(15f, 19f); lineTo(9f, 19f); lineTo(9f, 14f); lineTo(4f, 14f); lineTo(4f, 8f); lineTo(9f, 8f); close() }
        }.build()
    }

    val FastForward: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.FastForward",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(2.5f, 5f); lineTo(11f, 12f); lineTo(2.5f, 19f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 5f); lineTo(20.5f, 12f); lineTo(12f, 19f); close() }
        }.build()
    }

    val Gamepad: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Gamepad",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(6f, 7.5f); lineTo(18f, 7.5f); lineTo(22.5f, 17f); lineTo(15.5f, 17f); lineTo(13.5f, 13.5f); lineTo(10.5f, 13.5f); lineTo(8.5f, 17f); lineTo(1.5f, 17f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(7.5f, 9.5f); lineTo(9f, 11f); lineTo(7.5f, 12.5f); lineTo(6f, 11f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(16.5f, 9.5f); lineTo(18f, 11f); lineTo(16.5f, 12.5f); lineTo(15f, 11f); close() }
        }.build()
    }

    val GpsFixed: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.GpsFixed",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 3f); lineTo(20f, 7f); lineTo(20f, 15f); lineTo(12f, 21f); lineTo(4f, 15f); lineTo(4f, 7f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 9.4f); lineTo(14.6f, 12f); lineTo(12f, 14.6f); lineTo(9.4f, 12f); close() }
        }.build()
    }

    val GraphicEq: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.GraphicEq",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(3.4f, 10f); lineTo(3.8f, 10f); lineTo(4.7f, 10.9f); lineTo(4.7f, 17.1f); lineTo(3.8f, 18f); lineTo(3.4f, 18f); lineTo(2.5f, 17.1f); lineTo(2.5f, 10.9f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(8.4f, 4.5f); lineTo(8.8f, 4.5f); lineTo(9.7f, 5.4f); lineTo(9.7f, 18.6f); lineTo(8.8f, 19.5f); lineTo(8.4f, 19.5f); lineTo(7.5f, 18.6f); lineTo(7.5f, 5.4f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(13.4f, 8f); lineTo(13.8f, 8f); lineTo(14.7f, 8.9f); lineTo(14.7f, 16.1f); lineTo(13.8f, 17f); lineTo(13.4f, 17f); lineTo(12.5f, 16.1f); lineTo(12.5f, 8.9f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(18.4f, 6f); lineTo(18.8f, 6f); lineTo(19.7f, 6.9f); lineTo(19.7f, 17.1f); lineTo(18.8f, 18f); lineTo(18.4f, 18f); lineTo(17.5f, 17.1f); lineTo(17.5f, 6.9f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(3.6f, 7.3f); lineTo(4.9f, 8.6f); lineTo(3.6f, 9.9f); lineTo(2.3f, 8.6f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(18.6f, 3.3f); lineTo(19.9f, 4.6f); lineTo(18.6f, 5.9f); lineTo(17.3f, 4.6f); close() }
        }.build()
    }

    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Home",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 2.5f); lineTo(21.5f, 10f); lineTo(19f, 10f); lineTo(19f, 21.5f); lineTo(5f, 21.5f); lineTo(5f, 10f); lineTo(2.5f, 10f); close() }
        }.build()
    }

    val Info: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Info",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2.5f); lineTo(21.5f, 12f); lineTo(12f, 21.5f); lineTo(2.5f, 12f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 6.1f); lineTo(13.4f, 7.5f); lineTo(12f, 8.9f); lineTo(10.6f, 7.5f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(11.9f, 11f); lineTo(12.1f, 11f); lineTo(13f, 11.9f); lineTo(13f, 16.6f); lineTo(12.1f, 17.5f); lineTo(11.9f, 17.5f); lineTo(11f, 16.6f); lineTo(11f, 11.9f); close() }
        }.build()
    }

    val Keyboard: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Keyboard",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(3.4f, 7f); lineTo(20.6f, 7f); lineTo(22f, 8.4f); lineTo(22f, 15.6f); lineTo(20.6f, 17f); lineTo(3.4f, 17f); lineTo(2f, 15.6f); lineTo(2f, 8.4f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(5.73f, 10f); lineTo(6.27f, 10f); lineTo(7f, 10.73f); lineTo(7f, 10.87f); lineTo(6.27f, 11.6f); lineTo(5.73f, 11.6f); lineTo(5f, 10.87f); lineTo(5f, 10.73f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(9.23f, 10f); lineTo(9.77f, 10f); lineTo(10.5f, 10.73f); lineTo(10.5f, 10.87f); lineTo(9.77f, 11.6f); lineTo(9.23f, 11.6f); lineTo(8.5f, 10.87f); lineTo(8.5f, 10.73f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12.73f, 10f); lineTo(13.27f, 10f); lineTo(14f, 10.73f); lineTo(14f, 10.87f); lineTo(13.27f, 11.6f); lineTo(12.73f, 11.6f); lineTo(12f, 10.87f); lineTo(12f, 10.73f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(16.23f, 10f); lineTo(16.77f, 10f); lineTo(17.5f, 10.73f); lineTo(17.5f, 10.87f); lineTo(16.77f, 11.6f); lineTo(16.23f, 11.6f); lineTo(15.5f, 10.87f); lineTo(15.5f, 10.73f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(19.73f, 10f); lineTo(19.77f, 10f); lineTo(20.5f, 10.73f); lineTo(20.5f, 10.87f); lineTo(19.77f, 11.6f); lineTo(19.73f, 11.6f); lineTo(19f, 10.87f); lineTo(19f, 10.73f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(6.73f, 13.5f); lineTo(17.27f, 13.5f); lineTo(18f, 14.23f); lineTo(18f, 14.37f); lineTo(17.27f, 15.1f); lineTo(6.73f, 15.1f); lineTo(6f, 14.37f); lineTo(6f, 14.23f); close() }
        }.build()
    }

    val KeyboardArrowDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.KeyboardArrowDown",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(5f, 8.5f); lineTo(12f, 15.5f); lineTo(19f, 8.5f) }
        }.build()
    }

    val KeyboardArrowUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.KeyboardArrowUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(5f, 15.5f); lineTo(12f, 8.5f); lineTo(19f, 15.5f) }
        }.build()
    }

    val Language: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Language",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2.5f); lineTo(21.5f, 12f); lineTo(12f, 21.5f); lineTo(2.5f, 12f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(2.5f, 12f); lineTo(21.5f, 12f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2.5f); lineTo(7f, 12f); lineTo(12f, 21.5f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2.5f); lineTo(17f, 12f); lineTo(12f, 21.5f) }
        }.build()
    }

    val Lock: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Lock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(6.1f, 10f); lineTo(17.9f, 10f); lineTo(19.5f, 11.6f); lineTo(19.5f, 19.4f); lineTo(17.9f, 21f); lineTo(6.1f, 21f); lineTo(4.5f, 19.4f); lineTo(4.5f, 11.6f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(8f, 10f); lineTo(8f, 7f); lineTo(12f, 3.5f); lineTo(16f, 7f); lineTo(16f, 10f) }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 13.7f); lineTo(13.8f, 15.5f); lineTo(12f, 17.3f); lineTo(10.2f, 15.5f); close() }
        }.build()
    }

    val Memory: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Memory",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(4.7f, 6.5f); lineTo(19.3f, 6.5f); lineTo(20.5f, 7.7f); lineTo(20.5f, 16.3f); lineTo(19.3f, 17.5f); lineTo(4.7f, 17.5f); lineTo(3.5f, 16.3f); lineTo(3.5f, 7.7f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(7f, 9.5f); lineTo(17f, 9.5f); lineTo(17f, 14.5f); lineTo(7f, 14.5f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(7f, 3.5f); lineTo(7f, 6.5f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 3.5f); lineTo(12f, 6.5f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(17f, 3.5f); lineTo(17f, 6.5f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(7f, 17.5f); lineTo(7f, 20.5f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 17.5f); lineTo(12f, 20.5f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(17f, 17.5f); lineTo(17f, 20.5f) }
        }.build()
    }

    val Mouse: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Mouse",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(9.2f, 2.5f); lineTo(14.8f, 2.5f); lineTo(17f, 4.7f); lineTo(17f, 19.3f); lineTo(14.8f, 21.5f); lineTo(9.2f, 21.5f); lineTo(7f, 19.3f); lineTo(7f, 4.7f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 6.5f); lineTo(12f, 10f) }
        }.build()
    }

    val MusicNote: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.MusicNote",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(9f, 18.5f); lineTo(9f, 4f); lineTo(19f, 2f); lineTo(19f, 6.5f); lineTo(11.5f, 8f); lineTo(11.5f, 15f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(6.5f, 15.3f); lineTo(9.7f, 18.5f); lineTo(6.5f, 21.7f); lineTo(3.3f, 18.5f); close() }
        }.build()
    }

    val MyLocation: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.MyLocation",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2.5f); lineTo(21.5f, 12f); lineTo(12f, 21.5f); lineTo(2.5f, 12f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 9.6f); lineTo(14.4f, 12f); lineTo(12f, 14.4f); lineTo(9.6f, 12f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 5f); lineTo(12f, 7.5f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 16.5f); lineTo(12f, 19f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(5f, 12f); lineTo(7.5f, 12f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(16.5f, 12f); lineTo(19f, 12f) }
        }.build()
    }

    val Notifications: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Notifications",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(6f, 15f); lineTo(6f, 8.5f); lineTo(9f, 4.5f); lineTo(15f, 4.5f); lineTo(18f, 8.5f); lineTo(18f, 15f); lineTo(20.5f, 18f); lineTo(3.5f, 18f) }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 19.4f); lineTo(13.6f, 21f); lineTo(12f, 22.6f); lineTo(10.4f, 21f); close() }
        }.build()
    }

    val NotificationsOff: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.NotificationsOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(6f, 15f); lineTo(6f, 8.5f); lineTo(9f, 4.5f); lineTo(15f, 4.5f); lineTo(18f, 8.5f); lineTo(18f, 15f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(3f, 3f); lineTo(21f, 21f) }
        }.build()
    }

    val OpenInFull: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.OpenInFull",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(3f, 3f); lineTo(10f, 3f); lineTo(3f, 10f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(21f, 21f); lineTo(14f, 21f); lineTo(21f, 14f); close() }
        }.build()
    }

    val Palette: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Palette",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2.5f); lineTo(21f, 7f); lineTo(21f, 17f); lineTo(12f, 21.5f); lineTo(3f, 17f); lineTo(3f, 7f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 7.8f); lineTo(16.2f, 12f); lineTo(12f, 16.2f); lineTo(7.8f, 12f); close() }
        }.build()
    }

    val Person: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Person",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 2.5f); lineTo(17.5f, 7f); lineTo(17.5f, 12f); lineTo(12f, 16f); lineTo(6.5f, 12f); lineTo(6.5f, 7f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(4f, 21.5f); lineTo(6f, 17.5f); lineTo(18f, 17.5f); lineTo(20f, 21.5f); close() }
        }.build()
    }

    val PhotoCamera: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.PhotoCamera",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(3.9f, 7f); lineTo(20.1f, 7f); lineTo(21.5f, 8.4f); lineTo(21.5f, 18.1f); lineTo(20.1f, 19.5f); lineTo(3.9f, 19.5f); lineTo(2.5f, 18.1f); lineTo(2.5f, 8.4f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(9f, 4f); lineTo(15f, 4f); lineTo(16.5f, 7f); lineTo(7.5f, 7f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 9.6f); lineTo(15.4f, 13f); lineTo(12f, 16.4f); lineTo(8.6f, 13f); close() }
        }.build()
    }

    val PieChart: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.PieChart",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 2.5f); lineTo(12f, 12f); lineTo(21.5f, 12f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2.5f); lineTo(21.5f, 12f); lineTo(12f, 21.5f); lineTo(2.5f, 12f); close() }
        }.build()
    }

    val PlayArrow: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.PlayArrow",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(6f, 3.5f); lineTo(21f, 12f); lineTo(6f, 20.5f); close() }
        }.build()
    }

    val Podcasts: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Podcasts",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 8.5f); lineTo(12f, 15.5f) }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 3f); lineTo(14f, 5f); lineTo(12f, 7f); lineTo(10f, 5f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(7f, 7.5f); lineTo(7f, 16f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(17f, 7.5f); lineTo(17f, 16f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(3.5f, 10f); lineTo(3.5f, 14f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(20.5f, 10f); lineTo(20.5f, 14f) }
        }.build()
    }

    val PowerSettingsNew: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.PowerSettingsNew",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2.5f); lineTo(12f, 12f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(6.5f, 5.5f); lineTo(3.5f, 10f); lineTo(3.5f, 15.5f); lineTo(8f, 20f); lineTo(16f, 20f); lineTo(20.5f, 15.5f); lineTo(20.5f, 10f); lineTo(17.5f, 5.5f) }
        }.build()
    }

    val Refresh: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Refresh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 3.5f); lineTo(21f, 9f); lineTo(18.5f, 12f); lineTo(12f, 6.5f); lineTo(5.5f, 10f); lineTo(5.5f, 14f); lineTo(12f, 17.5f); lineTo(18f, 14.5f); lineTo(20.5f, 18f); lineTo(12f, 21f); lineTo(3f, 15f); lineTo(3f, 9f); close() }
        }.build()
    }

    val Rocket: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Rocket",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 2f); lineTo(16.5f, 8f); lineTo(16.5f, 15f); lineTo(12f, 19f); lineTo(7.5f, 15f); lineTo(7.5f, 8f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(7.5f, 14f); lineTo(4f, 20.5f); lineTo(7.5f, 19f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(16.5f, 14f); lineTo(20f, 20.5f); lineTo(16.5f, 19f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 8.2f); lineTo(13.8f, 10f); lineTo(12f, 11.8f); lineTo(10.2f, 10f); close() }
        }.build()
    }

    val Science: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Science",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(9f, 2.5f); lineTo(9f, 9f); lineTo(3.5f, 19f); lineTo(20.5f, 19f); lineTo(15f, 9f); lineTo(15f, 2.5f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 13.5f); lineTo(14f, 15.5f); lineTo(12f, 17.5f); lineTo(10f, 15.5f); close() }
        }.build()
    }

    val ScreenLockPortrait: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.ScreenLockPortrait",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(7.4f, 2.5f); lineTo(16.6f, 2.5f); lineTo(18f, 3.9f); lineTo(18f, 20.1f); lineTo(16.6f, 21.5f); lineTo(7.4f, 21.5f); lineTo(6f, 20.1f); lineTo(6f, 3.9f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 9f); lineTo(14f, 11f); lineTo(12f, 13f); lineTo(10f, 11f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(9.5f, 15.5f); lineTo(14.5f, 15.5f) }
        }.build()
    }

    val ScreenRotation: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.ScreenRotation",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2.5f); lineTo(21.5f, 12f); lineTo(12f, 21.5f); lineTo(2.5f, 12f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 8.6f); lineTo(15.4f, 12f); lineTo(12f, 15.4f); lineTo(8.6f, 12f); close() }
        }.build()
    }

    val Screenshot: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Screenshot",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(3f, 8f); lineTo(3f, 3f); lineTo(8f, 3f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(16f, 3f); lineTo(21f, 3f); lineTo(21f, 8f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(21f, 16f); lineTo(21f, 21f); lineTo(16f, 21f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(8f, 21f); lineTo(3f, 21f); lineTo(3f, 16f); close() }
        }.build()
    }

    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Search",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(10.5f, 3f); lineTo(16f, 5.5f); lineTo(18.5f, 10.5f); lineTo(16f, 15.5f); lineTo(10.5f, 18f); lineTo(5f, 15.5f); lineTo(2.5f, 10.5f); lineTo(5f, 5.5f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(15.5f, 15.5f); lineTo(21f, 21f) }
        }.build()
    }

    val Security: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Security",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2f); lineTo(21f, 5.5f); lineTo(21f, 12f); lineTo(12f, 22f); lineTo(3f, 12f); lineTo(3f, 5.5f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 7.8f); lineTo(15.2f, 11f); lineTo(12f, 14.2f); lineTo(8.8f, 11f); close() }
        }.build()
    }

    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2.5f); lineTo(20.5f, 7.2f); lineTo(20.5f, 16.8f); lineTo(12f, 21.5f); lineTo(3.5f, 16.8f); lineTo(3.5f, 7.2f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 7.5f); lineTo(16.5f, 12f); lineTo(12f, 16.5f); lineTo(7.5f, 12f); close() }
        }.build()
    }

    val Share: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Share",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(6f, 18f); lineTo(18f, 6f) }
            path(fill = SolidColor(Color.Black)) { moveTo(5.5f, 16.5f); lineTo(7.5f, 18.5f); lineTo(5.5f, 20.5f); lineTo(3.5f, 18.5f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(18.5f, 3.5f); lineTo(20.5f, 5.5f); lineTo(18.5f, 7.5f); lineTo(16.5f, 5.5f); close() }
        }.build()
    }

    val Shield: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Shield",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2f); lineTo(21f, 5.5f); lineTo(21f, 12f); lineTo(12f, 22f); lineTo(3f, 12f); lineTo(3f, 5.5f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(8f, 11.5f); lineTo(11f, 15f); lineTo(16.5f, 8.5f) }
        }.build()
    }

    val Smartphone: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Smartphone",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(7.6f, 2f); lineTo(16.4f, 2f); lineTo(18f, 3.6f); lineTo(18f, 20.4f); lineTo(16.4f, 22f); lineTo(7.6f, 22f); lineTo(6f, 20.4f); lineTo(6f, 3.6f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(9.5f, 5f); lineTo(14.5f, 5f) }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 17.1f); lineTo(13.4f, 18.5f); lineTo(12f, 19.9f); lineTo(10.6f, 18.5f); close() }
        }.build()
    }

    val Speed: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Speed",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(3f, 17f); lineTo(12f, 5.5f); lineTo(21f, 17f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 17f); lineTo(16.5f, 9.5f) }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 15f); lineTo(14f, 17f); lineTo(12f, 19f); lineTo(10f, 17f); close() }
        }.build()
    }

    val Storage: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Storage",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(4.2f, 4.5f); lineTo(19.8f, 4.5f); lineTo(21f, 5.7f); lineTo(21f, 9.3f); lineTo(19.8f, 10.5f); lineTo(4.2f, 10.5f); lineTo(3f, 9.3f); lineTo(3f, 5.7f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(4.2f, 13.5f); lineTo(19.8f, 13.5f); lineTo(21f, 14.7f); lineTo(21f, 18.3f); lineTo(19.8f, 19.5f); lineTo(4.2f, 19.5f); lineTo(3f, 18.3f); lineTo(3f, 14.7f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(17.5f, 6.1f); lineTo(18.9f, 7.5f); lineTo(17.5f, 8.9f); lineTo(16.1f, 7.5f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(17.5f, 15.1f); lineTo(18.9f, 16.5f); lineTo(17.5f, 17.9f); lineTo(16.1f, 16.5f); close() }
        }.build()
    }

    val Sync: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Sync",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(4f, 12f); lineTo(4f, 8f); lineTo(8f, 4f); lineTo(16f, 4f); lineTo(20f, 8f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(20f, 12f); lineTo(20f, 16f); lineTo(16f, 20f); lineTo(8f, 20f); lineTo(4f, 16f) }
        }.build()
    }

    val Thermostat: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Thermostat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(10f, 13.5f); lineTo(10f, 4.5f); lineTo(14f, 4.5f); lineTo(14f, 13.5f) }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 14.5f); lineTo(15f, 17.5f); lineTo(12f, 20.5f); lineTo(9f, 17.5f); close() }
        }.build()
    }

    val TouchApp: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.TouchApp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(9f, 3f); lineTo(13f, 3f); lineTo(13f, 12f); lineTo(17f, 13.5f); lineTo(18f, 21f); lineTo(9f, 21f); lineTo(8f, 15f); close() }
        }.build()
    }

    val TrailLengthMedium: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.TrailLengthMedium",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(3f, 8f); lineTo(14f, 8f); lineTo(16.5f, 10.4f); lineTo(14f, 12.8f); lineTo(3f, 12.8f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(3f, 15f); lineTo(19f, 15f); lineTo(21.5f, 17.4f); lineTo(19f, 19.8f); lineTo(3f, 19.8f); close() }
        }.build()
    }

    val Tune: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Tune",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(3f, 6.5f); lineTo(21f, 6.5f) }
            path(fill = SolidColor(Color.Black)) { moveTo(14.4f, 4.6f); lineTo(16.6f, 4.6f); lineTo(17.5f, 5.5f); lineTo(17.5f, 7.5f); lineTo(16.6f, 8.4f); lineTo(14.4f, 8.4f); lineTo(13.5f, 7.5f); lineTo(13.5f, 5.5f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(3f, 12f); lineTo(21f, 12f) }
            path(fill = SolidColor(Color.Black)) { moveTo(7.4f, 10.1f); lineTo(9.6f, 10.1f); lineTo(10.5f, 11f); lineTo(10.5f, 13f); lineTo(9.6f, 13.9f); lineTo(7.4f, 13.9f); lineTo(6.5f, 13f); lineTo(6.5f, 11f); close() }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(3f, 17.5f); lineTo(21f, 17.5f) }
            path(fill = SolidColor(Color.Black)) { moveTo(15.4f, 15.6f); lineTo(17.6f, 15.6f); lineTo(18.5f, 16.5f); lineTo(18.5f, 18.5f); lineTo(17.6f, 19.4f); lineTo(15.4f, 19.4f); lineTo(14.5f, 18.5f); lineTo(14.5f, 16.5f); close() }
        }.build()
    }

    val Usb: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Usb",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 21f); lineTo(12f, 4f) }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 2f); lineTo(15f, 6f); lineTo(9f, 6f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(8f, 12f); lineTo(8f, 15f); lineTo(5f, 13.5f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(16f, 8.4f); lineTo(17.6f, 10f); lineTo(16f, 11.6f); lineTo(14.4f, 10f); close() }
        }.build()
    }

    val Videocam: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Videocam",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(3.9f, 6.5f); lineTo(13.6f, 6.5f); lineTo(15f, 7.9f); lineTo(15f, 16.1f); lineTo(13.6f, 17.5f); lineTo(3.9f, 17.5f); lineTo(2.5f, 16.1f); lineTo(2.5f, 7.9f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(16.5f, 10f); lineTo(21.5f, 6.5f); lineTo(21.5f, 17.5f); lineTo(16.5f, 14f); close() }
        }.build()
    }

    val VideogameAsset: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.VideogameAsset",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(3.6f, 7f); lineTo(20.4f, 7f); lineTo(22f, 8.6f); lineTo(22f, 16.9f); lineTo(20.4f, 18.5f); lineTo(3.6f, 18.5f); lineTo(2f, 16.9f); lineTo(2f, 8.6f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(7.5f, 10.9f); lineTo(9.1f, 12.5f); lineTo(7.5f, 14.1f); lineTo(5.9f, 12.5f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(16f, 10.2f); lineTo(17.3f, 11.5f); lineTo(16f, 12.8f); lineTo(14.7f, 11.5f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(18.5f, 12.7f); lineTo(19.8f, 14f); lineTo(18.5f, 15.3f); lineTo(17.2f, 14f); close() }
        }.build()
    }

    val Warning: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Warning",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(12f, 2.5f); lineTo(22f, 20f); lineTo(2f, 20f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 10.8f); lineTo(14.2f, 13f); lineTo(12f, 15.2f); lineTo(9.8f, 13f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 16.1f); lineTo(13.5f, 17.6f); lineTo(12f, 19.1f); lineTo(10.5f, 17.6f); close() }
        }.build()
    }

    val Widgets: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Widgets",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 2.5f); lineTo(17.5f, 8f); lineTo(12f, 13.5f); lineTo(6.5f, 8f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(4.2f, 15f); lineTo(8.8f, 15f); lineTo(10f, 16.2f); lineTo(10f, 20.3f); lineTo(8.8f, 21.5f); lineTo(4.2f, 21.5f); lineTo(3f, 20.3f); lineTo(3f, 16.2f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(15.2f, 15f); lineTo(19.8f, 15f); lineTo(21f, 16.2f); lineTo(21f, 20.3f); lineTo(19.8f, 21.5f); lineTo(15.2f, 21.5f); lineTo(14f, 20.3f); lineTo(14f, 16.2f); close() }
        }.build()
    }

    val Wifi: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gh.Wifi",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(3.5f, 9f); lineTo(12f, 3f); lineTo(20.5f, 9f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(6.5f, 13f); lineTo(12f, 8.8f); lineTo(17.5f, 13f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter
            ) { moveTo(9.5f, 17f); lineTo(12f, 15f); lineTo(14.5f, 17f) }
            path(fill = SolidColor(Color.Black)) { moveTo(12f, 19.1f); lineTo(13.4f, 20.5f); lineTo(12f, 21.9f); lineTo(10.6f, 20.5f); close() }
        }.build()
    }


}

/** Filled variants — kept so `GhIcons.Filled.X` reads like the Material call it replaces. */
object GhFilled {
    private fun alias(v: ImageVector) = v

    val AccessTime: ImageVector get() = GhIcons.AccessTime

    val Add: ImageVector get() = GhIcons.Add

    val Adjust: ImageVector get() = GhIcons.Adjust

    val AdminPanelSettings: ImageVector get() = GhIcons.AdminPanelSettings

    val Android: ImageVector get() = GhIcons.Android

    val Apps: ImageVector get() = GhIcons.Apps

    val ArrowBack: ImageVector get() = GhIcons.ArrowBack

    val AutoAwesome: ImageVector get() = GhIcons.AutoAwesome

    val Autorenew: ImageVector get() = GhIcons.Autorenew

    val Block: ImageVector get() = GhIcons.Block

    val Bolt: ImageVector get() = GhIcons.Bolt

    val BrightnessHigh: ImageVector get() = GhIcons.BrightnessHigh

    val Campaign: ImageVector get() = GhIcons.Campaign

    val CenterFocusStrong: ImageVector get() = GhIcons.CenterFocusStrong

    val Chat: ImageVector get() = GhIcons.Chat

    val Check: ImageVector get() = GhIcons.Check

    val CheckCircle: ImageVector get() = GhIcons.CheckCircle

    val ChevronRight: ImageVector get() = GhIcons.ChevronRight

    val CleaningServices: ImageVector get() = GhIcons.CleaningServices

    val Close: ImageVector get() = GhIcons.Close

    val CloudDownload: ImageVector get() = GhIcons.CloudDownload

    val Delete: ImageVector get() = GhIcons.Delete

    val DeleteSweep: ImageVector get() = GhIcons.DeleteSweep

    val Dialpad: ImageVector get() = GhIcons.Dialpad

    val Dns: ImageVector get() = GhIcons.Dns

    val Equal: ImageVector get() = GhIcons.Equal

    val Extension: ImageVector get() = GhIcons.Extension

    val FastForward: ImageVector get() = GhIcons.FastForward

    val Gamepad: ImageVector get() = GhIcons.Gamepad

    val GpsFixed: ImageVector get() = GhIcons.GpsFixed

    val GraphicEq: ImageVector get() = GhIcons.GraphicEq

    val Home: ImageVector get() = GhIcons.Home

    val Info: ImageVector get() = GhIcons.Info

    val Keyboard: ImageVector get() = GhIcons.Keyboard

    val KeyboardArrowDown: ImageVector get() = GhIcons.KeyboardArrowDown

    val KeyboardArrowUp: ImageVector get() = GhIcons.KeyboardArrowUp

    val Language: ImageVector get() = GhIcons.Language

    val Lock: ImageVector get() = GhIcons.Lock

    val Memory: ImageVector get() = GhIcons.Memory

    val Mouse: ImageVector get() = GhIcons.Mouse

    val MusicNote: ImageVector get() = GhIcons.MusicNote

    val MyLocation: ImageVector get() = GhIcons.MyLocation

    val Notifications: ImageVector get() = GhIcons.Notifications

    val NotificationsOff: ImageVector get() = GhIcons.NotificationsOff

    val OpenInFull: ImageVector get() = GhIcons.OpenInFull

    val Palette: ImageVector get() = GhIcons.Palette

    val Person: ImageVector get() = GhIcons.Person

    val PhotoCamera: ImageVector get() = GhIcons.PhotoCamera

    val PieChart: ImageVector get() = GhIcons.PieChart

    val PlayArrow: ImageVector get() = GhIcons.PlayArrow

    val Podcasts: ImageVector get() = GhIcons.Podcasts

    val PowerSettingsNew: ImageVector get() = GhIcons.PowerSettingsNew

    val Refresh: ImageVector get() = GhIcons.Refresh

    val Rocket: ImageVector get() = GhIcons.Rocket

    val Science: ImageVector get() = GhIcons.Science

    val ScreenLockPortrait: ImageVector get() = GhIcons.ScreenLockPortrait

    val ScreenRotation: ImageVector get() = GhIcons.ScreenRotation

    val Screenshot: ImageVector get() = GhIcons.Screenshot

    val Search: ImageVector get() = GhIcons.Search

    val Security: ImageVector get() = GhIcons.Security

    val Settings: ImageVector get() = GhIcons.Settings

    val Share: ImageVector get() = GhIcons.Share

    val Shield: ImageVector get() = GhIcons.Shield

    val Smartphone: ImageVector get() = GhIcons.Smartphone

    val Speed: ImageVector get() = GhIcons.Speed

    val Storage: ImageVector get() = GhIcons.Storage

    val Sync: ImageVector get() = GhIcons.Sync

    val Thermostat: ImageVector get() = GhIcons.Thermostat

    val TouchApp: ImageVector get() = GhIcons.TouchApp

    val TrailLengthMedium: ImageVector get() = GhIcons.TrailLengthMedium

    val Tune: ImageVector get() = GhIcons.Tune

    val Usb: ImageVector get() = GhIcons.Usb

    val Videocam: ImageVector get() = GhIcons.Videocam

    val VideogameAsset: ImageVector get() = GhIcons.VideogameAsset

    val Warning: ImageVector get() = GhIcons.Warning

    val Widgets: ImageVector get() = GhIcons.Widgets

    val Wifi: ImageVector get() = GhIcons.Wifi

}
