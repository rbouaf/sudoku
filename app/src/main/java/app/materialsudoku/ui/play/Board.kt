package app.materialsudoku.ui.play

import androidx.compose.animation.VectorConverter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import app.materialsudoku.data.Settings
import app.materialsudoku.game.Fx
import app.materialsudoku.game.Game
import app.materialsudoku.game.Geo
import app.materialsudoku.game.Status
import app.materialsudoku.game.hasBit
import app.materialsudoku.ui.kit.LocalBuzz
import app.materialsudoku.ui.kit.LocalSprings
import app.materialsudoku.ui.kit.Morphs
import app.materialsudoku.ui.kit.Shp
import app.materialsudoku.ui.kit.morphPath
import app.materialsudoku.ui.theme.flex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.hypot

/**
 * The Tiles board (board 1, locked): 81 separate tiles, small gaps inside a box, wider gutters
 * between boxes, no lines anywhere. Selection and highlight morph a tile's silhouette; a finished
 * unit ripples; a mistake bursts and knocks the neighbours away.
 *
 * One Canvas draws all 81 tiles. Every animated value is read in the draw phase, so a running
 * animation redraws without recomposing.
 */
@Composable
fun Board(
    game: Game,
    settings: Settings,
    width: Dp,
    onFlash: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (Int) -> Unit = {},
    revealed: Boolean = true,
    /** 0: tiles pop out of the Start shape from the centre; 1: blocks already landed, digits fade in. */
    revealKind: Int = 0,
    onGeo: (app.materialsudoku.ui.play.GridGeo) -> Unit = {},
    /** No input until the clock may start. */
    locked: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val springs = LocalSprings.current
    val buzz = LocalBuzz.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 64)

    // 9c + 6·gap + 2·gutter, gap = 0.075c and gutter = 0.225c: 40 / 3 / 9 dp at a 396 dp board.
    val cellDp = width / 9.9f
    val px = with(density) { cellDp.toPx() }
    val gap = px * 0.075f
    val gut = px * 0.225f
    fun pos(c: Int) = c * (px + gap) + (c / 3) * (gut - gap)
    fun centre(i: Int) = Offset(pos(Geo.col(i)) + px / 2, pos(Geo.row(i)) + px / 2)
    val k = cellDp.value / 40f

    val tiles = remember { Array(81) { TileAnim() } }
    // The deal-in: per-tile 0..1, 1 = fully present. Starts at 0 when the board opens hidden.
    val deal = remember { Array(81) { Animatable(if (revealed) 1f else 0f) } }
    LaunchedEffect(revealed) {
        if (!revealed || deal.all { it.value >= 1f }) return@LaunchedEffect
        val from = if (revealKind == 1) (game.selected.takeIf { it >= 0 } ?: 40) else 40
        for (i in 0 until 81) scope.launch {
            if (!springs.reduced) delay((Geo.euclid(i, from) * (if (revealKind == 1) 12 else 34)).toLong())
            deal[i].animateTo(1f, if (revealKind == 1) springs.hitIn() else springs.fast())
        }
    }
    val shake = remember { Animatable(0f) }
    var origin by remember { mutableIntStateOf(-1) }

    // ── Glyphs, measured once per style ─────────────────────────────────────────────────────────
    val numerals = if (settings.arabicNumerals) "٠١٢٣٤٥٦٧٨٩" else "0123456789"
    val givenStyle = TextStyle(fontFamily = flex(23f * k, 700, rond = 0), fontSize = (23f * k).sp, letterSpacing = 0.sp)
    val placedStyle = TextStyle(fontFamily = flex(23f * k, 520, rond = 100), fontSize = (23f * k).sp, letterSpacing = 0.sp)
    val noteStyle = TextStyle(fontFamily = flex(10.5f * k, 640), fontSize = (10.5f * k).sp, letterSpacing = 0.sp)
    val glyphs = remember(k, numerals) {
        Glyphs(
            given = Array(10) { measurer.measure(numerals[it].toString(), givenStyle) },
            placed = Array(10) { measurer.measure(numerals[it].toString(), placedStyle) },
            note = Array(10) { measurer.measure(numerals[it].toString(), noteStyle) },
        )
    }

    // ── Colour roles (board 1: neutral = the puzzle, tertiary = your hand, primary = attention) ──
    val dark = cs.surface.luminance() < 0.5f
    val roles = Roles(
        well = cs.surfaceContainer, given = cs.surfaceContainerHighest,
        peerOf = { lerp(it, cs.primary, 0.13f) }, blocked = cs.surfaceContainerLowest,
        hl = cs.primary, onHl = cs.onPrimary, sel = cs.tertiaryContainer, onSel = cs.onTertiaryContainer,
        inkGiven = cs.onSurface, inkPlaced = cs.tertiary, inkNote = cs.onSurfaceVariant,
        hintUnit = lerp(cs.surfaceContainer, cs.secondaryContainer, 0.85f),
        shadow = Color.Black.copy(alpha = if (dark) 0.32f else 0.14f),
        error = cs.error, onError = cs.onError,
    )

    // ── Retarget every tile when the position, selection, highlight or hint changes ─────────────
    val values = game.values
    val notes = game.notes
    val sel = game.selected
    val hlDigit = if (settings.highlight >= 1) game.highlight else 0
    val hint = game.hint
    LaunchedEffect(values, sel, hlDigit, hint, settings.highlight, roles) {
        val from = origin.takeIf { it >= 0 } ?: sel
        for (i in 0 until 81) {
            val t = tiles[i]
            val v = values[i]
            val isSel = i == sel
            val isHl = hlDigit != 0 && v == hlDigit
            val base = if (game.isGiven(i)) roles.given else roles.well
            val peer = settings.highlight >= 2 && sel >= 0 && !isSel && i in Geo.peers[sel]
            val blocked = settings.highlight >= 4 && hlDigit != 0 && v == 0 &&
                Geo.peers[i].any { values[it] == hlDigit }
            val inHintUnit = hint != null && hint.stage == 1 && hint.unit != null && i in Geo.units[hint.unit]
            val shape = when { isSel -> Shp.selected; isHl -> Shp.highlighted; else -> Shp.square }
            val fill = when {
                isSel && isHl -> roles.hl
                isSel -> roles.sel
                isHl -> roles.hl
                inHintUnit -> roles.hintUnit
                blocked -> roles.blocked
                peer -> roles.peerOf(base)
                else -> base
            }
            val ink = when {
                isHl -> roles.onHl
                isSel -> roles.onSel
                game.isGiven(i) -> roles.inkGiven
                else -> roles.inkPlaced
            }
            val key = Target(shape, fill, ink, if (isSel) 1f else 0f)
            if (key == t.target) continue
            val first = t.target == null
            t.target = key
            t.job?.cancel()
            t.job = scope.launch {
                if (first) {
                    t.from = shape; t.to = shape; t.morph.snapTo(1f); t.fill.snapTo(fill); t.ink.snapTo(ink); t.lift.snapTo(key.lift)
                    return@launch
                }
                // A tap still travels as a ripple, but a tight one: 6 ms per cell of distance.
                if (from >= 0 && i != from && !springs.reduced) delay((Geo.euclid(i, from) * 6).toLong())
                // Lighting up attacks at once; going back to rest fades out slower (user, 2026-10-27).
                val lighting = fill != base
                launch { t.fill.animateTo(fill, if (lighting) springs.glowIn() else springs.glowOut()) }
                launch { t.ink.animateTo(ink, if (lighting) springs.glowIn() else springs.glowOut()) }
                launch { t.lift.animateTo(key.lift, if (key.lift > 0f) springs.hitIn() else springs.hitOut()) }
                val toRest = shape == Shp.square
                if (shape != t.to) {
                    t.from = t.to; t.to = shape
                    t.morph.snapTo(0f)
                    t.morph.animateTo(1f, if (toRest) springs.hitOut() else springs.hitIn())
                } else if (t.morph.value < 1f) {
                    // A cancelled retarget can leave a morph half-way; finish it toward the same shape.
                    t.morph.animateTo(1f, springs.hitIn())
                }
            }
        }
        origin = -1
    }

    // ── One-shot effects ─────────────────────────────────────────────────────────────────────────
    val onFlashNow by rememberUpdatedState(onFlash)
    LaunchedEffect(game) {
        game.fx.collect { fx ->
            when (fx) {
                is Fx.Placed -> {
                    buzz.key()
                    val t = tiles[fx.cell]
                    scope.launch { t.pop.snapTo(0.35f); t.pop.animateTo(1f, springs.hitIn()) }
                    if (fx.completedUnits.isNotEmpty()) {
                        // The celebration: a wave runs along each finished line, box or diagonal,
                        // starting at the digit you just placed. Draw-only; it never blocks a tap.
                        val step = 45L
                        val wave = HashMap<Int, Int>()
                        for (u in fx.completedUnits) for (j in Geo.cellsOf(u)) {
                            val k = when {
                                u < 9 -> kotlin.math.abs(Geo.col(j) - Geo.col(fx.cell))
                                u < 18 -> kotlin.math.abs(Geo.row(j) - Geo.row(fx.cell))
                                u < 27 -> Geo.dist(j, fx.cell)
                                else -> kotlin.math.abs(Geo.row(j) - Geo.row(fx.cell))
                            }
                            wave[j] = minOf(wave[j] ?: Int.MAX_VALUE, k)
                        }
                        buzz.unitDone(fx.completedUnits.size, (wave.values.maxOrNull() ?: 8) + 1, step.toInt())
                        for ((j, k) in wave) scope.launch {
                            if (!springs.reduced) delay(k * step)
                            tiles[j].rip.animateTo(1f, springs.hitIn())
                            tiles[j].rip.animateTo(0f, springs.loading())
                        }
                    }
                }
                is Fx.Mistake -> {
                    buzz.mistake()
                    scope.launch { delay(180); buzz.heartCrack() }
                    onFlashNow()
                    val t = tiles[fx.cell]
                    t.errDigit = fx.digit
                    scope.launch {
                        t.err.snapTo(0f); t.err.animateTo(1f, springs.hitIn())
                        delay(470)
                        t.err.animateTo(0f, springs.hitOut())
                        t.errDigit = 0
                    }
                    if (!springs.reduced) {
                        val c = centre(fx.cell)
                        val unit = with(density) { 1.dp.toPx() }
                        for (j in 0 until 81) if (j != fx.cell) scope.launch {
                            val d = centre(j) - c
                            val r = hypot(d.x, d.y)
                            val kick = 9f * unit * exp(-r / (70f * unit))
                            val tj = tiles[j]
                            tj.dx.snapTo(d.x / r * kick); tj.dy.snapTo(d.y / r * kick)
                            tj.rot.snapTo(((j * 37) % 11 - 5) * exp(-r / (90f * unit)))
                            launch { tj.dx.animateTo(0f, springs.fast()) }
                            launch { tj.dy.animateTo(0f, springs.fast()) }
                            tj.rot.animateTo(0f, springs.fast())
                        }
                        scope.launch {
                            // The board kicks sideways and rings out: −11, 9, −6, 3, −1 dp (board 1 keyframes).
                            for ((x, ms) in listOf(-11f to 42, 9f to 59, -6f to 59, 3f to 67, -1f to 76, 0f to 117)) {
                                shake.animateTo(x * unit, tween(ms))
                            }
                        }
                    }
                }
                is Fx.NoteToggled -> if (fx.on) buzz.noteOn() else buzz.noteOff()
                is Fx.Erased, Fx.Undone -> buzz.undo()
                is Fx.HintShown -> {
                    buzz.hint()
                    val c = fx.cell
                    if (c != null && fx.stage >= 3) scope.launch {
                        repeat(2) {
                            tiles[c].breathe.animateTo(1f, tween(600))
                            tiles[c].breathe.animateTo(0f, tween(600))
                        }
                    }
                }
                Fx.Won -> buzz.won()
                Fx.Lost -> { delay(620); buzz.lost() }
                Fx.FilledWrong -> buzz.mistake()
                Fx.Halfway -> {}
            }
        }
    }

    // ── Draw ─────────────────────────────────────────────────────────────────────────────────────
    val path = remember { Path() }
    val shadowPath = remember { Path() }
    val paused = game.paused
    val dead = game.status == Status.LOST
    val hlNotes = settings.highlight >= 3
    val unitDp = with(density) { 1.dp.toPx() }

    Box(
        modifier
            .size(width)
            .graphicsLayer { translationX = shake.value }
            .then(if (paused) Modifier.blur(9.dp) else if (dead) Modifier.blur(5.dp) else Modifier)
            .graphicsLayer { alpha = if (dead) 0.55f else 1f }
            .onGloballyPositioned { c ->
                val o = c.boundsInRoot().topLeft
                onGeo(app.materialsudoku.ui.play.GridGeo(o, px, gap, gut))
            }
            .pointerInput(px, gap, gut, locked) {
                if (locked) return@pointerInput
                fun cellAt(o: Offset): Int {
                    fun idx(x: Float): Int {
                        for (c in 0 until 9) { val p = pos(c); if (x < p + px + gut / 2) return c }
                        return 8
                    }
                    return idx(o.y) * 9 + idx(o.x)
                }
                detectTapGestures(
                    onTap = { o -> val i = cellAt(o); origin = i; game.select(i); buzz.select() },
                    onLongPress = { o -> val i = cellAt(o); origin = i; onLongPress(i) },
                )
            }
            .drawBehind {
                val cell = Size(px, px)
                // Pass 1: every tile but the lifted ones; pass 2: selected and erroring tiles on top.
                val easy = game.easyCell
                if (easy >= 0 && values[easy] == 0) {
                    val o = Offset(pos(Geo.col(easy)) + px / 2, pos(Geo.row(easy)) + px / 2)
                    drawCircle(roles.inkPlaced.copy(alpha = 0.9f), px * 0.56f, o, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
                for (pass in 0..1) for (i in 0 until 81) {
                    val t = tiles[i]
                    val top = t.lift.value > 0.02f || t.err.value > 0.02f
                    if ((pass == 1) != top) continue
                    if (!revealed) continue
                    val dv = deal[i].value
                    drawTile(
                        t, i, pos(Geo.col(i)), pos(Geo.row(i)), cell, path, shadowPath, roles, glyphs,
                        given = game.isGiven(i), value = values[i], notes = notes[i],
                        hlDigit = if (hlNotes) hlDigit else 0, hideInk = paused, unit = unitDp,
                        shapeIn = if (revealKind == 1) 1f else dv, inkIn = dv,
                    )
                }
            },
    )
}

private class Glyphs(val given: Array<TextLayoutResult>, val placed: Array<TextLayoutResult>, val note: Array<TextLayoutResult>)

private class Roles(
    val well: Color, val given: Color, val peerOf: (Color) -> Color, val blocked: Color,
    val hl: Color, val onHl: Color, val sel: Color, val onSel: Color,
    val inkGiven: Color, val inkPlaced: Color, val inkNote: Color, val hintUnit: Color, val shadow: Color,
    val error: Color, val onError: Color,
) {
    override fun equals(other: Any?) = other is Roles && other.well == well && other.hl == hl && other.sel == sel && other.inkPlaced == inkPlaced
    override fun hashCode() = well.hashCode() * 31 + hl.hashCode()
}

private data class Target(val shape: RoundedPolygon, val fill: Color, val ink: Color, val lift: Float)

@Stable
private class TileAnim {
    var target: Target? = null
    var job: Job? = null
    var from: RoundedPolygon = Shp.square
    var to: RoundedPolygon = Shp.square
    val morph = Animatable(1f)
    val lift = Animatable(0f)
    val fill = Animatable(Color.Transparent, Color.VectorConverter(ColorSpaces.Oklab))
    val ink = Animatable(Color.Transparent, Color.VectorConverter(ColorSpaces.Oklab))
    val pop = Animatable(1f)
    val rip = Animatable(0f)
    val err = Animatable(0f)
    var errDigit = 0
    val breathe = Animatable(0f)
    val dx = Animatable(0f)
    val dy = Animatable(0f)
    val rot = Animatable(0f)
}

private fun DrawScope.drawTile(
    t: TileAnim, i: Int, x: Float, y: Float, cell: Size, path: Path, shadowPath: Path,
    roles: Roles, glyphs: Glyphs, given: Boolean, value: Int, notes: Int, hlDigit: Int, hideInk: Boolean, unit: Float,
    shapeIn: Float = 1f, inkIn: Float = 1f,
) {
    if (shapeIn <= 0.001f) return
    val lift = t.lift.value
    val rip = t.rip.value
    val err = t.err.value
    val br = t.breathe.value
    val sc = (1f + 0.18f * lift) * (1f + 0.22f * rip) * (1f + 0.32f * err) * (0.35f + 0.65f * shapeIn)
    val rot = t.rot.value - 8f * err + 14f * rip
    val cx = x + cell.width / 2
    val cy = y + cell.height / 2

    translate(t.dx.value, t.dy.value - 3f * unit * lift) {
        rotate(rot, Offset(cx, cy)) {
            scale(sc, Offset(cx, cy)) {
                // Silhouette: the role morph, then any transient (error, ripple, breath) on top of it.
                val morph = when {
                    err > 0.01f -> Morphs.of(t.to, Shp.error)
                    rip > 0.01f -> Morphs.of(t.to, Shp.ripple)
                    br > 0.01f -> Morphs.of(t.to, Shp.difficulty[2])
                    else -> Morphs.of(t.from, t.to)
                }
                val progress = when {
                    err > 0.01f -> err
                    rip > 0.01f -> rip
                    br > 0.01f -> br
                    else -> t.morph.value
                }
                morphPath(morph, progress, cell, path)
                path.translate(Offset(x, y))
                // Givens are raised (1.5 dp contact shadow); a lifted tile casts a deeper one.
                if (given || lift > 0.02f) {
                    shadowPath.rewind(); shadowPath.addPath(path, Offset(0f, (1.5f + 2.5f * lift) * unit))
                    drawPath(shadowPath, roles.shadow.copy(alpha = roles.shadow.alpha * (if (given) 1f else lift) * 1.1f))
                }
                drawPath(path, t.fill.value.let { it.copy(alpha = it.alpha * shapeIn.coerceIn(0f, 1f)) })
                if (rip > 0.01f) drawPath(path, roles.hl.copy(alpha = (rip.coerceIn(0f, 1f)) * 0.92f))
                if (br > 0.01f) drawPath(path, roles.hintUnit.copy(alpha = br * 0.9f))
                if (err > 0.01f) drawPath(path, roles.error.copy(alpha = err.coerceIn(0f, 1f)))
            }
        }
        if (hideInk || inkIn <= 0.01f) return@translate
        // Ink: digits keep upright inside a twisting tile, which reads as weight rather than wobble.
        val ink = when {
            err > 0.01f -> lerp(t.ink.value, roles.onError, err.coerceIn(0f, 1f))
            rip > 0.01f -> lerp(t.ink.value, roles.onHl, rip.coerceIn(0f, 1f))
            else -> t.ink.value
        }.let { it.copy(alpha = it.alpha * inkIn.coerceIn(0f, 1f)) }
        val digit = if (err > 0.01f && t.errDigit != 0) t.errDigit else value
        if (digit != 0) {
            val g = if (given) glyphs.given[digit] else glyphs.placed[digit]
            val s = t.pop.value * (1f + 0.18f * lift) * (1f + 0.32f * err) * (1f + 0.22f * rip)
            scale(s, Offset(cx, cy)) {
                drawText(g, color = ink, topLeft = Offset(cx - g.size.width / 2f, cy - g.size.height / 2f))
            }
        } else if (notes != 0) {
            val n = cell.width * 0.9f / 3f
            val ox = x + cell.width * 0.05f
            val oy = y + cell.height * 0.05f
            for (d in 1..9) {
                if (!notes.hasBit(d)) continue
                val nx = ox + ((d - 1) % 3) * n + n / 2
                val ny = oy + ((d - 1) / 3) * n + n / 2
                val g = glyphs.note[d]
                val lit = d == hlDigit
                if (lit) drawCircle(roles.hl, radius = n * 0.48f, center = Offset(nx, ny))
                val c = when { lit -> roles.onHl; lift > 0.5f -> roles.onSel; else -> roles.inkNote }
                drawText(g, color = c, topLeft = Offset(nx - g.size.width / 2f, ny - g.size.height / 2f))
            }
        }
    }
}

