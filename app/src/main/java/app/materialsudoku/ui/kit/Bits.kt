package app.materialsudoku.ui.kit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/**
 * IconButton(shapes = IconButtonDefaults.shapes()) [M3E-RESTYLED], drawn by hand so its press
 * squish (0.92) and round → 14 dp corner can match the boards exactly.
 */
@Composable
fun SquishIcon(
    icon: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    container: Color = Color.Transparent,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    pressedCorner: Dp = 14.dp,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val springs = LocalSprings.current
    val corner by animateDpAsState(if (pressed) pressedCorner else size / 2, springs.fast(), label = "c")
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, springs.fast(), label = "s")
    Surface(
        color = container, contentColor = content, shape = RoundedCornerShape(corner),
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { contentDescription = description; role = Role.Button }
            .clickable(src, null, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(Sym[icon], null, Modifier.size(iconSize)) }
    }
}

/** A MaterialShape drawn as an icon-sized glyph. */
@Composable
fun ShapeGlyph(shape: RoundedPolygon, size: Dp, color: Color, modifier: Modifier = Modifier, outline: Boolean = false) {
    Canvas(modifier.size(size)) {
        drawShape(Morphs.of(shape, shape), 0f, color, style = if (outline) Stroke(width = this.size.width * 0.09f) else androidx.compose.ui.graphics.drawscope.Fill)
    }
}

/** Lives as MaterialShapes.Heart [M3E-NEW]: filled while held, an outline once spent. */
@Composable
fun Hearts(left: Int, of: Int, size: Dp, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.error, spent: Color = LocalContentColor.current.copy(alpha = 0.55f)) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(size * 0.15f)) {
        repeat(of) { k ->
            if (k < left) ShapeGlyph(Shp.heart, size, color) else ShapeGlyph(Shp.heart, size, spent, outline = true)
        }
    }
}
