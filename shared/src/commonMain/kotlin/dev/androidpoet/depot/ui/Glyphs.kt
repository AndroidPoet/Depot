package dev.androidpoet.depot.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun glyph(name: String, draw: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw,
        )
    }.build()

object Glyphs {
    val Search = glyph("search") {
        moveTo(17f, 10.5f)
        arcTo(6.5f, 6.5f, 0f, true, true, 4f, 10.5f)
        arcTo(6.5f, 6.5f, 0f, true, true, 17f, 10.5f)
        moveTo(15.2f, 15.2f)
        lineTo(20f, 20f)
    }
    val Close = glyph("close") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }
    val Back = glyph("back") {
        moveTo(20f, 12f); lineTo(4f, 12f)
        moveTo(10f, 6f); lineTo(4f, 12f); lineTo(10f, 18f)
    }
    val Refresh = glyph("refresh") {
        moveTo(19.5f, 12f)
        arcTo(7.5f, 7.5f, 0f, true, true, 16.2f, 5.8f)
        moveTo(16.5f, 2.5f); lineTo(16.5f, 6.2f); lineTo(12.8f, 6.2f)
    }
    val Check = glyph("check") {
        moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f)
    }
    val Download = glyph("download") {
        moveTo(12f, 4f); lineTo(12f, 15f)
        moveTo(7f, 10.5f); lineTo(12f, 15.5f); lineTo(17f, 10.5f)
        moveTo(5f, 20f); lineTo(19f, 20f)
    }
    val Phone = glyph("phone") {
        moveTo(8.5f, 3f); lineTo(15.5f, 3f)
        arcTo(2f, 2f, 0f, false, true, 17.5f, 5f)
        lineTo(17.5f, 19f)
        arcTo(2f, 2f, 0f, false, true, 15.5f, 21f)
        lineTo(8.5f, 21f)
        arcTo(2f, 2f, 0f, false, true, 6.5f, 19f)
        lineTo(6.5f, 5f)
        arcTo(2f, 2f, 0f, false, true, 8.5f, 3f)
        close()
        moveTo(11f, 17.8f); lineTo(13f, 17.8f)
    }
    val Crate = glyph("crate") {
        moveTo(12f, 3f); lineTo(20f, 7.5f); lineTo(20f, 16.5f); lineTo(12f, 21f); lineTo(4f, 16.5f); lineTo(4f, 7.5f); close()
        moveTo(4f, 7.5f); lineTo(12f, 12f); lineTo(20f, 7.5f)
        moveTo(12f, 12f); lineTo(12f, 21f)
    }
    val Caution = glyph("caution") {
        moveTo(12f, 4f); lineTo(21f, 19.5f); lineTo(3f, 19.5f); close()
        moveTo(12f, 10f); lineTo(12f, 14.5f)
        moveTo(12f, 17f); lineTo(12f, 17.1f)
    }
    val Outward = glyph("outward") {
        moveTo(8f, 16f); lineTo(17f, 7f)
        moveTo(10f, 7f); lineTo(17f, 7f); lineTo(17f, 14f)
    }
    val Chevron = glyph("chevron") {
        moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
    }
}

@Composable
fun Glyph(vector: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Image(
        imageVector = vector,
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}
