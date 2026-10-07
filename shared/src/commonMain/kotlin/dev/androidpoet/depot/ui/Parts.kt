package dev.androidpoet.depot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@Composable
fun Label(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Ui.colors.ink,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        style = style.copy(color = color),
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Ui.colors.line))
}

@Composable
fun VerticalHairline(modifier: Modifier = Modifier) {
    Box(modifier.width(1.dp).background(Ui.colors.line))
}

@Composable
fun AppIcon(url: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    var loaded by remember(url) { mutableStateOf(false) }
    val shape = RoundedCornerShape(size * 0.24f)
    Box(modifier.size(size).clip(shape).background(if (loaded) Color.Transparent else Ui.colors.sunken), contentAlignment = Alignment.Center) {
        if (!loaded) {
            Label(
                text = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "·",
                style = Ui.type.heading.copy(fontSize = (size.value * 0.42f).sp),
                color = Ui.colors.inkSoft,
            )
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun IconButton(glyph: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Ui.colors.inkSoft, touch: Dp = 40.dp) {
    Box(
        modifier.size(touch).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyph, tint)
    }
}

@Composable
fun SearchField(text: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, height: Dp = 40.dp) {
    // The caller's text comes back through flows a frame or more late. Echoing that into the field drops and
    // reorders fast keystrokes, so the field keeps what was typed and only reports it upward.
    var typed by remember { mutableStateOf(text) }
    val change = { next: String ->
        typed = next
        onChange(next)
    }
    Row(
        modifier.height(height).clip(RoundedCornerShape(8.dp)).background(Ui.colors.sunken).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(Glyphs.Search, Ui.colors.inkFaint, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (typed.isEmpty()) Label(placeholder, Ui.type.body, color = Ui.colors.inkFaint, maxLines = 1)
            BasicTextField(
                value = typed,
                onValueChange = change,
                singleLine = true,
                textStyle = Ui.type.body.copy(color = Ui.colors.ink),
                cursorBrush = SolidColor(Ui.colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (typed.isNotEmpty()) {
            IconButton(Glyphs.Close, onClick = { change("") }, tint = Ui.colors.inkFaint, touch = height)
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
fun Tag(text: String, foreground: Color, background: Color, modifier: Modifier = Modifier) {
    Label(
        text = text,
        style = Ui.type.overline,
        color = foreground,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(background).padding(horizontal = 6.dp, vertical = 2.dp),
        maxLines = 1,
    )
}

@Composable
fun FilledAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, glyph: ImageVector? = null) {
    Row(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Ui.colors.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Glyph(glyph, Ui.colors.onAccent, size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Label(label, Ui.type.bodyStrong, color = Ui.colors.onAccent, maxLines = 1)
    }
}

@Composable
fun QuietAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Ui.colors.line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(label, Ui.type.bodyStrong, maxLines = 1)
    }
}

@Composable
fun ProgressLine(fraction: Float?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Ui.colors.sunken)) {
        Box(Modifier.fillMaxWidth(fraction?.coerceIn(0f, 1f) ?: 1f).height(3.dp).background(if (fraction == null) Ui.colors.inkFaint else Ui.colors.accent))
    }
}
