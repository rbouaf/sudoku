package app.materialsudoku.ui.results

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.materialsudoku.AppModel
import app.materialsudoku.data.Settings
import app.materialsudoku.game.Accolade
import app.materialsudoku.game.BAND_NAMES
import app.materialsudoku.game.Game
import app.materialsudoku.game.Geo
import app.materialsudoku.game.NET_NAMES
import app.materialsudoku.game.Report
import app.materialsudoku.game.Status
import app.materialsudoku.game.StrikeKind
import app.materialsudoku.game.accolades
import app.materialsudoku.game.clock
import app.materialsudoku.game.clockMs
import app.materialsudoku.game.median
import app.materialsudoku.game.net
import app.materialsudoku.game.recordKey
import app.materialsudoku.game.signedDelta
import app.materialsudoku.ui.kit.Hearts
import app.materialsudoku.ui.kit.LevelChip
import app.materialsudoku.ui.kit.LocalBuzz
import app.materialsudoku.ui.kit.LocalSprings
import app.materialsudoku.ui.kit.Morphs
import app.materialsudoku.ui.kit.Pill
import app.materialsudoku.ui.kit.ShapeGlyph
import app.materialsudoku.ui.kit.Shp
import app.materialsudoku.ui.kit.SquishIcon
import app.materialsudoku.ui.kit.Sym
import app.materialsudoku.ui.kit.WordChip
import app.materialsudoku.ui.kit.drawShape
import app.materialsudoku.ui.kit.ts
import app.materialsudoku.ui.play.DIFFICULTY_NAMES
import kotlinx.coroutines.delay
import androidx.compose.material3.MaterialShapes

/**
 * End of game, won and lost (board 6, locked). The time owns the hero; the timeline underneath
 * explains it step by step; accolades are MaterialShapes; every number is derived from the log.
 */
@Composable
fun ResultsScreen(model: AppModel, settings: Settings) {
    val g = model.finished ?: return
    // Unrated at once (it is instant), then rated in the background (Analysis is ~ms per move).
    val quick = remember(g) { Report(g, null) }
    val rated by androidx.compose.runtime.produceState<Report?>(null, g) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Report(g, model.raterFor(g)) }
    }
    val report = rated ?: quick
    val prior = model.history.filter { it.id != g.id }
    val cs = MaterialTheme.colorScheme
    val clip = LocalClipboardManager.current
    val buzz = LocalBuzz.current
    LaunchedEffect(g) { if (g.status == Status.WON) { delay(200); buzz.won() } }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 104.dp)) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LevelChip(g.deal.difficulty, g.deal.length, DIFFICULTY_NAMES)
                WordChip(null, if (g.net() == 0) "${g.rules.lives} lives" else NET_NAMES[g.net()], leading = {
                    if (g.net() == 2) Icon(Sym["shield"], null, Modifier.size(16.dp)) else ShapeGlyph(Shp.heart, 15.dp, cs.onSurfaceVariant, outline = true)
                })
                WordChip("emoji_events", if (report.assisted) "Assisted" else BAND_NAMES[g.band])
                Spacer(Modifier.weight(1f))
                // The puzzle's identity is its givens (06 §1.1): copy it to share or replay anywhere.
                SquishIcon("content_copy", "Copy puzzle", { clip.setText(AnnotatedString(g.deal.givens)); buzz.toggle() }, size = 40.dp, iconSize = 20.dp)
            }
            if (g.status == Status.WON) Won(g, report, prior) else Lost(g, report)
        }
        Actions(g, model)
    }
}

// ── Won ──────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun Won(g: Game, r: Report, prior: List<Game>) {
    val cs = MaterialTheme.colorScheme
    val key = g.recordKey()
    val sameKey = prior.filter { it.status == Status.WON && it.recordKey() == key }
    val best = sameKey.minByOrNull { it.elapsed }
    val usual = prior.filter { it.status == Status.WON && it.deal.difficulty == g.deal.difficulty && it.deal.length == g.deal.length && it.net() == g.net() }
        .takeLast(30).map { it.elapsed }.median()
    Hero(cs.primaryContainer, cs.onPrimaryContainer, g.deal.difficulty) {
        Text(if (best == null || g.elapsed < best.elapsed) "Solved · new record" else "Solved", style = ts(12f, 600, cs.onPrimaryContainer.copy(alpha = 0.8f)))
        BigTime(g.elapsed, 76f, cs.onPrimaryContainer)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (best != null) Pill(
                "${signedDelta(g.elapsed - best.elapsed)} vs best ${clockMs(best.elapsed).let { it.first + it.second }}",
                if (g.elapsed < best.elapsed) cs.onPrimaryContainer else cs.onPrimaryContainer.copy(alpha = 0.12f),
                if (g.elapsed < best.elapsed) cs.primaryContainer else cs.onPrimaryContainer,
            )
            if (usual != null) Pill("${clock(usual)} usual", cs.onPrimaryContainer.copy(alpha = 0.12f), cs.onPrimaryContainer)
        }
        g.deal.famous?.let {
            Spacer(Modifier.height(10.dp))
            Pill("This was $it", cs.onPrimaryContainer, cs.primaryContainer)
        }
    }
    Accolades(accolades(r, prior))
    StatTiles(r)
    Timeline(r, sameKey.minByOrNull { it.elapsed }?.let { Report(it, null) })
}

@Composable
private fun Hero(bg: Color, fg: Color, difficulty: Int, content: @Composable () -> Unit) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(32.dp), modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
        Box(
            // A huge cropped MaterialShape behind the number (Mosaic's tactic); drawn, never measured.
            Modifier.drawBehind {
                val s = 230.dp.toPx()
                drawShape(Morphs.of(Shp.difficulty[difficulty], Shp.difficulty[difficulty]), 0f, fg.copy(alpha = 0.13f), Size(s, s), Offset(size.width - s + 66.dp.toPx(), -74.dp.toPx()))
            },
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)) { content() }
        }
    }
}

/** m:ss at display size, thousandths frozen and quieter (report 04 §5). */
@Composable
private fun BigTime(ms: Long, size: Float, color: Color) {
    val (mm, frac) = clockMs(ms)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(mm, style = ts(size, 330, color, wdth = 82, tnum = true, lineHeight = size * 0.98f))
        Text(frac, Modifier.padding(start = 2.dp, bottom = (size * 0.1f).dp).graphicsLayer { alpha = 0.78f }, style = ts(size * 0.45f, 420, color, wdth = 82, tnum = true, lineHeight = size * 0.45f))
    }
}

/** Earned: the badge shape in tertiaryContainer, revealed one by one. Missed: a dashed outline and the reason. */
@Composable
private fun Accolades(list: List<Accolade>) {
    val cs = MaterialTheme.colorScheme
    val springs = LocalSprings.current
    val earned = list.filter { it.earned }
    val missed = list.filter { !it.earned }
    Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        earned.take(4).forEachIndexed { k, a ->
            val s = remember { Animatable(0f) }
            LaunchedEffect(a.key) { delay(700L + k * 90); s.animateTo(1f, springs.fast()) }
            Column(Modifier.width(66.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(52.dp).graphicsLayer { scaleX = s.value; scaleY = s.value; rotationZ = -90f * (1 - s.value) }, contentAlignment = Alignment.Center) {
                    ShapeGlyph(badgeShape(a.key), 52.dp, cs.tertiaryContainer)
                    Icon(Sym[badgeIcon(a.key)], null, Modifier.size(24.dp), tint = cs.onTertiaryContainer)
                }
                Text(a.label, style = ts(12f, 600, cs.onSurface), maxLines = 2)
            }
        }
        Column(Modifier.weight(1f).padding(start = 4.dp, top = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            missed.take(3).forEach { a ->
                Surface(color = cs.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShapeGlyph(badgeShape(a.key), 22.dp, cs.outline, outline = true)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(a.label, style = ts(12.5f, 650, cs.onSurface), maxLines = 1)
                            if (a.why.isNotEmpty()) Text(a.why, style = ts(11f, 540, cs.onSurfaceVariant, lineHeight = 13f), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun badgeShape(k: String) = when (k) {
    "unaided" -> MaterialShapes.Cookie9Sided
    "byhand" -> MaterialShapes.Flower
    "fast" -> MaterialShapes.Arrow
    "pure" -> MaterialShapes.Clover4Leaf
    "flawless" -> MaterialShapes.Gem
    "record" -> MaterialShapes.SoftBurst
    "sudden" -> MaterialShapes.Heart
    else -> MaterialShapes.Pentagon
}
private fun badgeIcon(k: String) = when (k) {
    "unaided" -> "lightbulb"; "byhand" -> "edit"; "fast" -> "bolt"; "pure" -> "check"
    "flawless" -> "star"; "record" -> "emoji_events"; "sudden" -> "favorite"; else -> "shield"
}

@Composable
private fun StatTiles(r: Report) {
    Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatTile("Accuracy", "${(r.accuracy * 100).toInt()}", "%", "${r.placements.size} of ${r.placements.size + r.strikes.size}", Modifier.weight(0.92f))
        if (r.rated) {
            StatTile("Effort/min", "${r.effortPerMin}", "", "${r.effort} total", Modifier.weight(1f))
            val h = r.hardest
            StatTile("Hardest step", h?.rating?.technique ?: "—", "", h?.let { "r${Geo.row(it.cell) + 1}c${Geo.col(it.cell) + 1} · ${it.rating!!.effort}" } ?: "", Modifier.weight(1.42f), word = true)
        } else {
            val per = if (r.placements.isEmpty()) 0 else r.time / r.placements.size
            StatTile("Per cell", "%.1f".format(per / 1000.0), "s", "${r.placements.size} cells", Modifier.weight(1f))
            StatTile("Strikes", "${r.strikes.size}", "", r.strikes.groupBy { it.kind }.entries.joinToString(" · ") { "${it.value.size} ${it.key.name.lowercase()}" }.ifEmpty { "none" }, Modifier.weight(1.42f))
        }
        StatTile("Rhythm", "${r.rhythm}", "", when { r.rhythm >= 70 -> "steady"; r.rhythm >= 45 -> "bursty"; else -> "uneven" }, Modifier.weight(0.9f))
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, sub: String, modifier: Modifier, word: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = ts(12f, 600, cs.onSurfaceVariant), maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = if (word) ts(17f, 560, cs.onSurface, wdth = 90, lineHeight = 20f) else ts(25f, 480, cs.onSurface, wdth = 90, tnum = true), maxLines = if (word) 2 else 1)
                if (unit.isNotEmpty()) Text(unit, Modifier.padding(start = 1.dp, bottom = 2.dp), style = ts(15f, 520, cs.onSurfaceVariant))
            }
            Text(sub, style = ts(11.5f, 520, cs.onSurfaceVariant, tnum = true), maxLines = 1)
        }
    }
}

/**
 * The timeline (Monkeytype's grammar: one hero series, everything else recessive). Above: cells
 * filled over time, yours against your best. Below: seconds per cell as tiles. Drag to scrub; the
 * replay row below follows.
 */
@Composable
private fun Timeline(r: Report, best: Report?) {
    val cs = MaterialTheme.colorScheme
    var k by remember(r) { mutableIntStateOf(r.placements.size) }
    val measurer = rememberTextMeasurer()
    val axis = ts(10.5f, 560, cs.onSurfaceVariant, tnum = true)
    Surface(color = cs.surfaceContainerLow, shape = RoundedCornerShape(28.dp), modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp).fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
            Row(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Timeline", Modifier.weight(1f), style = ts(14f, 650, cs.onSurface))
                Legend(cs.primary, "you"); Spacer(Modifier.width(10.dp))
                if (best != null) Legend(cs.outline, "best", dashed = true)
            }
            val n = r.placements.size.coerceAtLeast(1)
            val end = maxOf(r.time, best?.time ?: 0L).coerceAtLeast(1L)
            val gaps = r.gaps
            val maxGap = (gaps.sorted().getOrNull(gaps.size / 2)?.times(3) ?: 1L).coerceAtLeast(1000L)
            Canvas(
                Modifier.fillMaxWidth().height(200.dp)
                    .pointerInput(r) {
                        fun at(x: Float) { val t = (x / size.width) * end; k = r.placements.indexOfLast { it.t <= t } + 1 }
                        detectTapGestures { at(it.x) }
                    }
                    .pointerInput(r) {
                        // Horizontal only, so a vertical swipe still scrolls the page.
                        detectHorizontalDragGestures { ch, _ -> val t = (ch.position.x / size.width) * end; k = (r.placements.indexOfLast { it.t <= t } + 1).coerceIn(0, n) }
                    },
            ) {
                val w = size.width; val top = 18.dp.toPx(); val mid = size.height * 0.56f; val bot = size.height - 18.dp.toPx()
                fun x(t: Long) = w * t / end
                fun y(c: Int) = mid - (mid - top) * c / n
                // Best: dashed.
                if (best != null && best.placements.isNotEmpty()) {
                    val p = Path().apply { moveTo(0f, mid); best.placements.forEachIndexed { i, pl -> lineTo(x(pl.t), y(i + 1)) } }
                    drawPath(p, cs.outline, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                }
                // You: a stepped line with a soft area.
                val you = Path().apply {
                    moveTo(0f, mid)
                    r.placements.forEachIndexed { i, pl -> lineTo(x(pl.t), y(i)); lineTo(x(pl.t), y(i + 1)) }
                    lineTo(x(r.time), y(r.placements.size))
                }
                val area = Path().apply { addPath(you); lineTo(x(r.time), mid); lineTo(0f, mid); close() }
                drawPath(area, Brush.verticalGradient(listOf(cs.primary.copy(alpha = 0.28f), cs.primary.copy(alpha = 0.04f)), top, mid))
                drawPath(you, cs.primary, style = Stroke(2.5.dp.toPx()))
                drawText(measurer, "$n cells", Offset(4.dp.toPx(), top - 14.dp.toPx()), axis)
                // Seconds per cell: one tile per placement, the scrubbed one lit.
                val barW = (w / n * 0.7f).coerceIn(1.5f, 8.dp.toPx())
                r.placements.forEachIndexed { i, pl ->
                    val h = (bot - mid - 22.dp.toPx()) * (gaps[i].toFloat() / maxGap).coerceAtMost(1f)
                    val c = when { i == k - 1 -> cs.primary; pl.rating?.technique == null && pl.rating != null -> cs.tertiary; else -> cs.onSurfaceVariant.copy(alpha = 0.45f) }
                    drawRoundRect(c, Offset(x(pl.t) - barW, bot - h), Size(barW, h), CornerRadius(1.5f))
                }
                r.strikes.forEach { s ->
                    drawCircle(cs.error, 5.dp.toPx(), Offset(x(s.t), mid + 12.dp.toPx()))
                }
                drawText(measurer, "seconds per cell", Offset(4.dp.toPx(), mid + 4.dp.toPx()), axis)
                // Scrub line.
                val tk = if (k == 0) 0L else r.placements[k - 1].t
                drawLine(cs.onSurface, Offset(x(tk), top), Offset(x(tk), bot), 1.5.dp.toPx())
                drawCircle(cs.onSurface, 5.dp.toPx(), Offset(x(tk), y(k)))
                // Minutes axis.
                val mins = (end / 60_000).toInt()
                val step = when { mins > 30 -> 10; mins > 12 -> 5; mins > 5 -> 2; else -> 1 }
                for (m in 0..mins step step) drawText(measurer, "$m", Offset(x(m * 60_000L) + 1f, bot + 3.dp.toPx()), axis)
            }
        }
    }
    Replay(r, k)
}

@Composable
private fun Legend(c: Color, label: String, dashed: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(14.dp, 8.dp)) {
            drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) else null)
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = ts(11.5f, 560, cs.onSurfaceVariant))
    }
}

/** The board after step k, the placed tile lit in the highlight shape, and what that step was. */
@Composable
private fun Replay(r: Report, k: Int) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniBoard(r.game, r.boards[k.coerceIn(0, r.boards.lastIndex)], if (k > 0) r.placements[k - 1].cell else -1, 15.dp)
        Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(22.dp), modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(start = 14.dp, end = 12.dp, top = 11.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val p = r.placements.getOrNull(k - 1)
                Text(if (p == null) "Start" else "Step $k of ${r.placements.size} · ${clock(p.t)}", style = ts(12.5f, 560, cs.onSurfaceVariant, tnum = true))
                if (p != null) {
                    Text(p.rating?.technique ?: if (p.rating != null) "Guess" else if (p.auto) "Auto-finish" else "Placed", style = ts(15.5f, 650, cs.onSurface))
                    Text("r${Geo.row(p.cell) + 1}c${Geo.col(p.cell) + 1} = ${p.digit}", style = ts(12.5f, 560, cs.onSurfaceVariant, tnum = true))
                    val gap = r.gaps[k - 1]
                    Text("%.1f s to find".format(gap / 1000.0), style = ts(12.5f, 560, cs.onSurfaceVariant, tnum = true))
                    p.rating?.let { rt ->
                        if (rt.detour > 0) Pill("Effort ${rt.effort} · +${rt.detour} detour", cs.surfaceContainerHighest, cs.onSurface)
                        else Pill("Effort ${rt.effort}", cs.surfaceContainerHighest, cs.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun MiniBoard(g: Game, values: IntArray, lit: Int, cell: Dp, marks: Set<Int> = emptySet(), bad: Int = -1) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val gap = cell * 0.13f; val gut = cell * 0.4f
    val total = cell * 9 + gap * 6 + gut * 2
    Canvas(Modifier.size(total)) {
        val c = cell.toPx(); val gp = gap.toPx(); val gt = gut.toPx()
        fun p(k: Int) = k * (c + gp) + (k / 3) * (gt - gp)
        val giv = ts(c * 0.62f / density / fontScale, 700, cs.onSurface)
        val pl = ts(c * 0.62f / density / fontScale, 560, cs.tertiary, rond = 100)
        for (i in 0 until 81) {
            val o = Offset(p(Geo.col(i)), p(Geo.row(i)))
            val v = values[i]
            val isLit = i == lit
            if (isLit) {
                drawShape(Morphs.of(Shp.highlighted, Shp.highlighted), 0f, cs.primary, Size(c * 1.3f, c * 1.3f), o - Offset(c * 0.15f, c * 0.15f))
            } else {
                val bg = when { i == bad -> cs.error; g.isGiven(i) -> cs.surfaceContainerHighest; v != 0 -> cs.surfaceContainer; else -> cs.surfaceContainerLow }
                drawRoundRect(bg, o, Size(c, c), CornerRadius(c * 0.22f))
                if (i in marks) drawRoundRect(cs.primary, o, Size(c, c), CornerRadius(c * 0.22f), style = Stroke(1.5.dp.toPx()))
            }
            val d = if (i == bad) 0 else v
            if (d != 0) {
                val style = if (isLit) pl.copy(color = cs.onPrimary) else if (g.isGiven(i)) giv else pl
                val l = measurer.measure(d.toString(), style)
                drawText(l, topLeft = Offset(o.x + (c - l.size.width) / 2, o.y + (c - l.size.height) / 2))
            }
        }
    }
}

// ── Lost ─────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun Lost(g: Game, r: Report) {
    val cs = MaterialTheme.colorScheme
    Hero(cs.surfaceContainerHigh, cs.onSurface, g.deal.difficulty) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hearts(0, g.rules.lives, 24.dp, spent = cs.onSurface)
            Spacer(Modifier.width(10.dp))
            Text("Out of lives", style = ts(26f, 560, cs.onSurface))
        }
        Spacer(Modifier.height(12.dp))
        Text("Time survived", style = ts(12f, 600, cs.onSurfaceVariant))
        BigTime(g.elapsed, 58f, cs.onSurface)
    }
    // How far you got: a strip of tiles, one per cell you had to fill.
    Surface(color = cs.surfaceContainerLow, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp).fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("How far you got", Modifier.weight(1f), style = ts(14f, 650, cs.onSurface))
                Text("${r.placements.size} of ${r.toFill} cells · ${100 * r.placements.size / r.toFill.coerceAtLeast(1)}%", style = ts(12f, 540, cs.onSurfaceVariant, tnum = true))
            }
            Spacer(Modifier.height(9.dp))
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val cols = 29; val gp = 2.5.dp.toPx(); val s = (size.width - gp * (cols - 1)) / cols
                for (i in 0 until r.toFill) {
                    val o = Offset((i % cols) * (s + gp), (i / cols) * (s + gp))
                    if (i == r.placements.size) drawCircle(cs.error, s / 2, o + Offset(s / 2, s / 2))
                    else drawRoundRect(if (i < r.placements.size) cs.tertiary else cs.surfaceContainerHighest, o, Size(s, s), CornerRadius(3.dp.toPx()))
                }
            }
        }
    }
    val last = r.strikes.lastOrNull()
    if (last != null) Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniBoard(g, r.boards.last(), -1, 15.dp, bad = last.cell)
        Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(22.dp), modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What ended it", style = ts(12.5f, 600, cs.onSurfaceVariant))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("r${Geo.row(last.cell) + 1}c${Geo.col(last.cell) + 1}", style = ts(22f, 600, cs.onSurface))
                    DigitTile(last.digit, cs.error, cs.onError)
                    Text("was", style = ts(14f, 560, cs.onSurfaceVariant))
                    DigitTile(g.solution[last.cell], cs.surfaceContainerHighest, cs.tertiary)
                }
                Text(
                    when (last.kind) {
                        StrikeKind.SLIP -> "A slip: ${last.digit} was already in that row, column or box."
                        StrikeKind.GUESS -> "Not forced yet: other digits were still possible there. A coin flip, and it came up wrong."
                        StrikeKind.WRONG -> "${last.digit} could not go there."
                    },
                    style = ts(12.5f, 500, cs.onSurfaceVariant, lineHeight = 17f),
                )
                Text("${last.singlesOpen} cells were open to a single right then. You had looked for %.1f s.".format(last.lookedMs / 1000.0), style = ts(12.5f, 500, cs.onSurfaceVariant, lineHeight = 17f))
            }
        }
    }
    Surface(color = cs.surfaceContainerLow, shape = RoundedCornerShape(22.dp), modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp).fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            r.strikes.forEach { s ->
                Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { Icon(Sym["close"], null, Modifier.size(16.dp), tint = cs.error) }
                    Text(clock(s.t), Modifier.width(44.dp), style = ts(12.5f, 650, cs.onSurface, tnum = true))
                    Text("r${Geo.row(s.cell) + 1}c${Geo.col(s.cell) + 1}", Modifier.width(44.dp), style = ts(12.5f, 540, cs.onSurfaceVariant, tnum = true))
                    Text(
                        when (s.kind) { StrikeKind.SLIP -> "Slip: ${s.digit} was already there"; StrikeKind.GUESS -> "Guess: not forced yet"; StrikeKind.WRONG -> "${s.digit} can't go there" },
                        style = ts(12.5f, 540, cs.onSurfaceVariant), maxLines = 1,
                    )
                }
            }
        }
    }
    Timeline(r, null)
    Text("Losses keep no time record. This one counts once in your stats as a loss.", Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp), style = ts(12f, 500, cs.onSurfaceVariant, lineHeight = 16f))
}

@Composable
private fun DigitTile(d: Int, bg: Color, fg: Color) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(24.dp)) {
        Box(contentAlignment = Alignment.Center) { Text("$d", style = ts(14f, 700, fg)) }
    }
}

/** Home · Same puzzle (Try it again) · New game: a ButtonGroup [M3E-NEW] over a scrim. */
@Composable
private fun Actions(g: Game, model: AppModel) {
    val cs = MaterialTheme.colorScheme
    val buzz = LocalBuzz.current
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(104.dp)) {
            Canvas(Modifier.fillMaxSize()) { drawRect(Brush.verticalGradient(0.38f to Color.Transparent, 0.38f to cs.surface.copy(alpha = 0f), 1f to cs.surface)) }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SquishIcon("home", "Home", { buzz.select(); model.screen = app.materialsudoku.Screen.HOME }, size = 56.dp, container = cs.surfaceContainerHigh, content = cs.onSurface, pressedCorner = 16.dp)
            Surface(onClick = { buzz.press(); model.replay(g) }, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).height(56.dp)) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Sym["replay"], null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (g.status == Status.WON) "Same puzzle" else "Try it again", style = ts(16f, 620), maxLines = 1)
                }
            }
            Surface(onClick = { buzz.start(); model.start(g.deal.difficulty, g.deal.length) }, color = cs.primary, contentColor = cs.onPrimary, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1.2f).height(56.dp)) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("New game", style = ts(16f, 620), maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    ShapeGlyph(Shp.difficulty[g.deal.difficulty], 20.dp, cs.onPrimary.copy(alpha = 0.85f))
                }
            }
        }
    }
}
