package app.materialsudoku.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import app.materialsudoku.game.Game
import app.materialsudoku.game.Geo
import app.materialsudoku.ui.kit.LocalSprings
import app.materialsudoku.ui.kit.Morphs
import app.materialsudoku.ui.kit.Shp
import app.materialsudoku.ui.kit.morphPath

/**
 * Home → Play (user, 2026-10-27). Two different morphs, because the two buttons look different:
 *
 * - **Start**: the big Start shape *is* the board. It flies from Home to the board's place, grows to
 *   its size and morphs from the difficulty's MaterialShape to a Square while its colour drains from
 *   primary to the board's surface; then the 81 tiles pop out of it in a wave from the centre.
 * - **Continue**: the card's obscured board *is* the board. Each of its 81 blocks flies to its own
 *   tile, in a wave spreading from the selected cell, its colour draining from the card's ink role
 *   into the tile's surface; the card's colour opens to the whole screen and fades; the digits fade in
 *   as the tiles land.
 *
 * The overlay is drawn in window coordinates above everything; Play hides its board until the
 * hand-off. The game clock waits for the whole thing plus 100 ms.
 */
sealed interface Intro {
    /** `subdued`: it was the secondaryContainer "New game" shape, not the primary Start. */
    data class Start(val rect: Rect, val difficulty: Int, val subdued: Boolean = false) : Intro
    data class Continue(val card: Rect, val cardRadius: Float, val thumb: GridGeo) : Intro
}

/** A grid's geometry in window pixels: origin of cell (0,0), cell size, gap inside a box, gutter between boxes. */
data class GridGeo(val origin: Offset, val cell: Float, val gap: Float, val gut: Float) {
    fun pos(k: Int) = k * (cell + gap) + (k / 3) * (gut - gap)
    fun tile(i: Int) = Rect(Offset(origin.x + pos(Geo.col(i)), origin.y + pos(Geo.row(i))), Size(cell, cell))
    val bounds: Rect get() = Rect(origin, Size(pos(8) + cell, pos(8) + cell))
}

@Composable
fun IntroOverlay(intro: Intro, board: GridGeo, game: Game, onReveal: () -> Unit, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val springs = LocalSprings.current
    val p = remember(intro) { Animatable(0f) }
    val fade = remember(intro) { Animatable(1f) }
    val path = remember { Path() }
    LaunchedEffect(intro, board) {
        // Hand over to the real board as soon as the shape has all but landed, and start the
        // overlay's fade right then: a spring's last few pixels take as long as the rest, and the
        // digits must not wait under an opaque overlay for them.
        val fadeMs = if (intro is Intro.Start) 170 else 160
        val scope = this
        var revealed = false
        var fading: kotlinx.coroutines.Job? = null
        fun reveal() {
            if (revealed) return
            revealed = true
            onReveal()
            fading = scope.launch { fade.animateTo(0f, tween(fadeMs)) }
        }
        if (springs.reduced) p.snapTo(1f)
        else p.animateTo(1f, spring(0.86f, 240f, 0.002f)) { if (value > 0.93f) reveal() }
        reveal()
        fading?.join()
        onDone()
    }
    Canvas(Modifier.fillMaxSize()) {
        val e = p.value
        val a = fade.value
        when (intro) {
            is Intro.Start -> {
                // One shape, travelling and growing into the board, morphing to a Square as it goes.
                val r = lerp(intro.rect, board.bounds, e)
                val m = Morphs.of(Shp.difficulty[intro.difficulty], MaterialShapes.Square)
                morphPath(m, e.coerceIn(0f, 1.05f), r.size, path)
                path.translate(r.topLeft)
                rotate(-70f * (1f - e).coerceAtLeast(0f), r.center) {
                    val c0 = if (intro.subdued) cs.secondaryContainer else cs.primary
                    drawPath(path, lerp(c0, cs.surfaceContainer, e.coerceIn(0f, 1f)).copy(alpha = a))
                }
            }
            is Intro.Continue -> {
                // The card opens to the whole screen and fades as it goes.
                val full = Rect(Offset.Zero, size)
                val bg = lerp(intro.card, full, e.coerceIn(0f, 1f))
                val rad = intro.cardRadius * (1f - e.coerceIn(0f, 1f))
                drawRoundRect(cs.primaryContainer.copy(alpha = (1f - e).coerceIn(0f, 1f) * a), bg.topLeft, bg.size, CornerRadius(rad))
                // Every block flies to its tile, in a wave from the selected cell.
                val from = game.selected.takeIf { it >= 0 } ?: 40
                for (i in 0 until 81) {
                    val d = Geo.euclid(i, from) / 11.3f
                    val q = ((e * 1.45f) - d * 0.45f).coerceIn(0f, 1f)
                    val qe = 1f - (1f - q) * (1f - q)
                    val src = intro.thumb.tile(i)
                    val dst = board.tile(i)
                    val r = lerp(src, dst, qe)
                    val c0 = when {
                        game.isGiven(i) -> cs.onSurfaceVariant
                        game.values[i] != 0 -> cs.tertiary
                        else -> cs.surfaceContainerHighest
                    }
                    val c1 = if (game.isGiven(i)) cs.surfaceContainerHighest else cs.surfaceContainer
                    val rr = r.width * (0.18f + 0.1f * qe)
                    drawRoundRect(lerp(c0, c1, qe).copy(alpha = a), r.topLeft, r.size, CornerRadius(rr))
                }
            }
        }
    }
}
