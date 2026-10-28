package app.materialsudoku.ui.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.materialsudoku.ui.theme.flex

/** A text style in Google Sans Flex with opsz = size. `tnum` on by default for numbers. */
fun ts(size: Float, weight: Int, color: Color = Color.Unspecified, wdth: Int = 100, tnum: Boolean = false, lineHeight: Float = size * 1.2f, rond: Int = 0): TextStyle =
    TextStyle(
        fontFamily = flex(size, weight, wdth, rond), fontSize = size.sp, lineHeight = lineHeight.sp, color = color,
        fontFeatureSettings = if (tnum) "tnum" else null, letterSpacing = 0.sp,
    )

/** Difficulty shape + name + three length bars: the chip used on Play and Results. */
@Composable
fun LevelChip(difficulty: Int, length: Int, names: List<String>) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, contentColor = cs.onSurface, shape = RoundedCornerShape(50)) {
        Row(Modifier.height(40.dp).padding(start = 6.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeGlyph(Shp.difficulty[difficulty], 28.dp, cs.secondary)
            Spacer(Modifier.width(8.dp))
            Text(names[difficulty], style = ts(15f, 650))
            Spacer(Modifier.width(8.dp))
            LengthBars(length, cs.tertiary, cs.outlineVariant)
        }
    }
}

@Composable
fun LengthBars(length: Int, on: Color, off: Color) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (k in 0..2) Box(
            Modifier.size(5.dp, (4 + k * 3).dp).drawBehind { drawRoundRect(if (k <= length) on else off, cornerRadius = CornerRadius(1.5.dp.toPx())) },
        )
    }
}

/** AssistChip-like word chip [M3-BASELINE], non-interactive. */
@Composable
fun WordChip(icon: String?, text: String, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, contentColor = cs.onSurfaceVariant, shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Row(Modifier.height(32.dp).padding(start = 8.dp, end = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (leading != null) leading() else if (icon != null) Icon(Sym[icon], null, Modifier.size(16.dp))
            Text(text, style = ts(13f, 620, cs.onSurface))
        }
    }
}

/** A pill (28 dp) for small facts on a coloured hero. */
@Composable
fun Pill(text: String, bg: Color, fg: Color) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 11.dp, vertical = 5.dp), style = ts(13f, 620, tnum = true))
    }
}

@Composable
fun SectionHead(title: String, aside: String? = null, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, Modifier.weight(1f), style = ts(16f, 650, cs.onSurface))
        if (aside != null) Text(aside, style = ts(12f, 540, cs.onSurfaceVariant, tnum = true))
    }
}

