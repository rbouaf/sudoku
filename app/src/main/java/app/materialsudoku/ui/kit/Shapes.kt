package app.materialsudoku.ui.kit

import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/** The shape vocabulary (board 1 and Home): roles map to MaterialShapes, never hand-drawn paths. */
object Shp {
    val square get() = MaterialShapes.Square
    val circle get() = MaterialShapes.Circle
    val selected get() = MaterialShapes.Clover4Leaf
    val highlighted get() = MaterialShapes.Cookie9Sided
    val ripple get() = MaterialShapes.SoftBurst
    val error get() = MaterialShapes.Burst
    val heart get() = MaterialShapes.Heart

    /** Difficulty sharpens: smooth → scalloped → pointed → spiked. Index = difficulty. */
    val difficulty: List<RoundedPolygon>
        get() = listOf(
            MaterialShapes.Circle, MaterialShapes.Cookie12Sided, MaterialShapes.Sunny,
            MaterialShapes.VerySunny, MaterialShapes.SoftBurst, MaterialShapes.Burst, MaterialShapes.Boom,
        )
}

/** Morphs are built once per pair; graphics-shapes' feature matching is the expensive part. */
object Morphs {
    private val cache = HashMap<Pair<RoundedPolygon, RoundedPolygon>, Morph>()
    fun of(a: RoundedPolygon, b: RoundedPolygon): Morph = cache.getOrPut(a to b) { Morph(a, b) }
}

/** Path of `morph` at `progress` (un-clamped: an overshooting spring extrapolates the geometry). */
fun morphPath(morph: Morph, progress: Float, size: Size, into: Path = Path()): Path {
    into.rewind()
    morph.toPath(progress, into)
    into.transform(Matrix().apply { scale(size.width, size.height) })
    return into
}

fun polygonPath(p: RoundedPolygon, size: Size, into: Path = Path()): Path =
    morphPath(Morphs.of(p, p), 0f, size, into)

/** Draws a polygon (or a morph) scaled to `size`, top-left at `topLeft`. */
fun DrawScope.drawShape(
    morph: Morph,
    progress: Float,
    color: Color,
    size: Size = this.size,
    topLeft: Offset = Offset.Zero,
    path: Path = Path(),
    style: DrawStyle = Fill,
) {
    morphPath(morph, progress, size, path)
    if (topLeft != Offset.Zero) path.translate(topLeft)
    drawPath(path, color, style = style)
}

/** A Compose [Shape] showing a morph at a fixed progress (for clip, background, Surface). */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(morphPath(morph, progress, size))
}

fun RoundedPolygon.asShape(): Shape = MorphShape(Morphs.of(this, this), 0f)
