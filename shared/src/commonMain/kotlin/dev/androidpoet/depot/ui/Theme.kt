package dev.androidpoet.depot.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.androidpoet.depot.resources.Res
import dev.androidpoet.depot.resources.plex_mono_medium
import dev.androidpoet.depot.resources.plex_mono_regular
import dev.androidpoet.depot.resources.plex_sans_medium
import dev.androidpoet.depot.resources.plex_sans_regular
import dev.androidpoet.depot.resources.plex_sans_semibold
import org.jetbrains.compose.resources.Font

@Immutable
data class DepotColors(
    val canvas: Color,
    val surface: Color,
    val sunken: Color,
    val line: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val accent: Color,
    val onAccent: Color,
    val accentTint: Color,
    val positive: Color,
    val caution: Color,
    val cautionTint: Color,
)

private val LightColors = DepotColors(
    canvas = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    sunken = Color(0xFFE9EBEF),
    line = Color(0xFFDDE0E6),
    ink = Color(0xFF14171C),
    inkSoft = Color(0xFF545C69),
    inkFaint = Color(0xFF8A919E),
    accent = Color(0xFF2447D6),
    onAccent = Color(0xFFFFFFFF),
    accentTint = Color(0xFFE5EAFD),
    positive = Color(0xFF1B7A4E),
    caution = Color(0xFF8A5A00),
    cautionTint = Color(0xFFFBF1DC),
)

private val DarkColors = DepotColors(
    canvas = Color(0xFF0E1013),
    surface = Color(0xFF16191E),
    sunken = Color(0xFF1F242B),
    line = Color(0xFF272C35),
    ink = Color(0xFFECEEF2),
    inkSoft = Color(0xFFA3ABB8),
    inkFaint = Color(0xFF6D7583),
    accent = Color(0xFF8EA6FF),
    onAccent = Color(0xFF0B1230),
    accentTint = Color(0xFF1B2547),
    positive = Color(0xFF5FC795),
    caution = Color(0xFFE2B45E),
    cautionTint = Color(0xFF2E2612),
)

@Immutable
data class DepotType(
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val small: TextStyle,
    val mono: TextStyle,
    val monoSmall: TextStyle,
    val overline: TextStyle,
)

private val LocalColors = staticCompositionLocalOf { LightColors }
private val LocalType = staticCompositionLocalOf<DepotType> { error("DepotTheme is missing") }

object Ui {
    val colors: DepotColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: DepotType
        @Composable @ReadOnlyComposable get() = LocalType.current
}

@Composable
fun DepotTheme(dark: Boolean? = null, content: @Composable () -> Unit) {
    val colors = if (dark ?: isSystemInDarkTheme()) DarkColors else LightColors
    val sans = FontFamily(
        Font(Res.font.plex_sans_regular, FontWeight.Normal),
        Font(Res.font.plex_sans_medium, FontWeight.Medium),
        Font(Res.font.plex_sans_semibold, FontWeight.SemiBold),
    )
    val mono = FontFamily(
        Font(Res.font.plex_mono_regular, FontWeight.Normal),
        Font(Res.font.plex_mono_medium, FontWeight.Medium),
    )
    val type = DepotType(
        title = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em),
        heading = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
        body = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
        bodyStrong = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        small = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
        mono = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
        monoSmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        overline = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.08.em),
    )
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalType provides type,
        LocalIndication provides ripple(),
        content = content,
    )
}
