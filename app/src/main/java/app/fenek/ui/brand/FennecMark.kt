package app.fenek.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform

/** Flat palette of the mark; [tint] turns it into a one-colour silhouette. */
private object Palette {
    val fur = Color(0xFFE39A5E)
    val innerEar = Color(0xFFF4CFAA)
    val muzzle = Color(0xFFFFF3E3)
    val dark = Color(0xFF2A1E1A)
}

/**
 * FENEK's mascot: a geometric fennec head made of flat facets, the same drawing as the app
 * icon. [earTilt] (0..1) raises the ears, e.g. with microphone loudness.
 */
@Composable
fun FennecMark(modifier: Modifier = Modifier, earTilt: Float = 0f, tint: Color? = null) {
    Canvas(modifier.aspectRatio(1f)) {
        val unit = size.minDimension / 100f
        withTransform({
            translate((size.width - 100f * unit) / 2f, (size.height - 100f * unit) / 2f)
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            drawMark(earTilt.coerceIn(0f, 1f), tint)
        }
    }
}

private fun polygon(vararg points: Float) = Path().apply {
    moveTo(points[0], points[1])
    var i = 2
    while (i < points.size) {
        lineTo(points[i], points[i + 1])
        i += 2
    }
    close()
}

private fun DrawScope.drawMark(earTilt: Float, tint: Color?) {
    val fur = tint ?: Palette.fur
    // Relaxed ears lean out a little; raised ears stand straight.
    val lean = 7f * (1f - earTilt)
    rotate(-lean, pivot = Offset(36f, 44f)) {
        drawPath(polygon(20f, 50f, 29f, 6f, 47f, 36f), fur)
        if (tint == null) drawPath(polygon(27f, 43f, 31f, 17f, 41f, 35f), Palette.innerEar)
    }
    rotate(lean, pivot = Offset(64f, 44f)) {
        drawPath(polygon(80f, 50f, 71f, 6f, 53f, 36f), fur)
        if (tint == null) drawPath(polygon(73f, 43f, 69f, 17f, 59f, 35f), Palette.innerEar)
    }
    drawPath(polygon(18f, 46f, 50f, 31f, 82f, 46f, 74f, 64f, 50f, 93f, 26f, 64f), fur)
    if (tint == null) {
        drawPath(polygon(33f, 63f, 50f, 55f, 67f, 63f, 50f, 93f), Palette.muzzle)
        drawPath(polygon(34f, 56f, 40f, 52.5f, 45f, 56f, 39.5f, 58.5f), Palette.dark)
        drawPath(polygon(66f, 56f, 60f, 52.5f, 55f, 56f, 60.5f, 58.5f), Palette.dark)
        drawPath(polygon(46.5f, 85f, 53.5f, 85f, 50f, 90.5f), Palette.dark)
    }
}
