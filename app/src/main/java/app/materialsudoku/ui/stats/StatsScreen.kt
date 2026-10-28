package app.materialsudoku.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.materialsudoku.AppModel
import app.materialsudoku.Screen
import app.materialsudoku.data.Settings
import app.materialsudoku.game.Ev
import app.materialsudoku.game.Game
import app.materialsudoku.game.NET_NAMES
import app.materialsudoku.game.BAND_NAMES
import app.materialsudoku.game.Status
import app.materialsudoku.game.clock
import app.materialsudoku.game.clockMs
import app.materialsudoku.game.net
import app.materialsudoku.ui.kit.LocalBuzz
import app.materialsudoku.ui.kit.Morphs
import app.materialsudoku.ui.kit.SectionHead
import app.materialsudoku.ui.kit.ShapeGlyph
import app.materialsudoku.ui.kit.Shp
import app.materialsudoku.ui.kit.SquishIcon
import app.materialsudoku.ui.kit.Sym
import app.materialsudoku.ui.kit.drawShape
import app.materialsudoku.ui.kit.ts
import app.materialsudoku.ui.play.DIFFICULTY_NAMES
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.ln
import kotlin.math.max

/**
 * Stats (board 6, locked): Monkeytype's account page rebuilt from tiles and MaterialShapes. One
 * filter row scopes everything below it. Every number is recomputed from the game logs.
 */
@Composable
fun StatsScreen(model: AppModel, settings: Settings) {
    val cs = MaterialTheme.colorScheme
    val buzz = LocalBuzz.current
    val all = model.history
    var diff by remember { mutableIntStateOf(-1) }   // -1 = all
    var netF by remember { mutableIntStateOf(-1) }
    var aidF by remember { mutableIntStateOf(-1) }
    val games = all.filter { g ->
        (diff < 0 || g.deal.difficulty == diff) && (netF < 0 || g.net() == netF) && (aidF < 0 || (if (aidF == 2) g.assisted else !g.assisted && g.band == aidF))
    }
    val won = games.filter { it.status == Status.WON }
    val played = all.map { it.deal.difficulty }.distinct().sorted()

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SquishIcon("arrow_back", "Back", { model.screen = Screen.HOME })
            Text("Stats", Modifier.weight(1f).padding(start = 4.dp), style = ts(24f, 560, cs.onSurface))
        }
        // Filters: one row above everything they scope.
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 8.dp)) {
            Row(Modifier.height(40.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val opts = listOf(-1) + played
                opts.forEachIndexed { k, d ->
                    val on = d == diff
                    val first = k == 0; val last = k == opts.lastIndex
                    Surface(
                        color = if (on) cs.secondaryContainer else cs.surfaceContainerHigh, contentColor = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant,
                        shape = RoundedCornerShape(if (on || first) 20.dp else 8.dp, if (on || last) 20.dp else 8.dp, if (on || last) 20.dp else 8.dp, if (on || first) 20.dp else 8.dp),
                        modifier = Modifier.weight(if (on) 1.35f else 1f).height(40.dp).semantics { role = Role.Tab; selected = on }.clickable { buzz.toggle(); diff = d },
                    ) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            if (d < 0) Text("All", style = ts(14f, 620))
                            else { ShapeGlyph(Shp.difficulty[d], 16.dp, if (on) cs.onSecondaryContainer else cs.onSurfaceVariant); if (on) { Spacer(Modifier.width(6.dp)); Text(DIFFICULTY_NAMES[d], style = ts(14f, 620), maxLines = 1) } }
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(if (netF < 0) "Any net" else NET_NAMES[netF], netF >= 0) { buzz.toggle(); netF = if (netF >= 2) -1 else netF + 1 }
                FilterChip(if (aidF < 0) "Any help" else if (aidF == 2) "Assisted" else BAND_NAMES[aidF], aidF >= 0) { buzz.toggle(); aidF = if (aidF >= 2) -1 else aidF + 1 }
                Spacer(Modifier.weight(1f))
                Text("${games.size} games · ${won.size} won", style = ts(12f, 540, cs.onSurfaceVariant, tnum = true))
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
            if (all.isEmpty()) {
                Text("Finish a game and your stats start here.", Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                return@Column
            }
            Activity(all)
            Spacer(Modifier.height(14.dp))
            SectionHead("Personal bests", "per difficulty, length and net", Modifier.padding(horizontal = 14.dp))
            Bests(won)
            Spacer(Modifier.height(14.dp))
            SectionHead("History", "${won.size} solves · shape = difficulty", Modifier.padding(horizontal = 14.dp))
            History(won)
            Spacer(Modifier.height(14.dp))
            SectionHead("When you play", "${won.size} solves by hour", Modifier.padding(horizontal = 14.dp))
            WhenYouPlay(won)
            Spacer(Modifier.height(14.dp))
            SectionHead("Totals", games.minOfOrNull { it.startedAt }?.let { "since " + date(it) }, Modifier.padding(horizontal = 14.dp))
            Totals(games, model.current)
        }
    }
}

@Composable
private fun FilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, color = if (on) cs.secondaryContainer else Color.Transparent, contentColor = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp), border = if (on) null else BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Row(Modifier.height(32.dp).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = ts(13f, 600)); Icon(Sym["expand_more"], null, Modifier.size(18.dp))
        }
    }
}

private val zone: ZoneId get() = ZoneId.systemDefault()
private fun day(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
private fun date(ms: Long): String { val d = day(ms); return "${d.dayOfMonth} ${d.month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH)}" }
private fun hm(ms: Long): String { val m = ms / 60_000; return if (m >= 60) "${m / 60} h ${m % 60} m" else "$m m" }

/** Streak + 26 weeks of day tiles; streak days are round. */
@Composable
private fun Activity(all: List<Game>) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val byDay = all.groupBy { day(it.startedAt) }.mapValues { (_, v) -> v.sumOf { it.elapsed } }
    val today = LocalDate.now(zone)
    var streak = 0
    var d = if (byDay.containsKey(today)) today else today.minusDays(1)
    while (byDay.containsKey(d)) { streak++; d = d.minusDays(1) }
    val days = byDay.keys.sorted()
    var longest = 0; var run = 0; var prev: LocalDate? = null
    for (x in days) { run = if (prev != null && prev.plusDays(1) == x) run + 1 else 1; longest = max(longest, run); prev = x }
    val week = all.filter { day(it.startedAt) > today.minusDays(7) }.sumOf { it.elapsed }
    val streakDays = (0 until streak).map { (if (byDay.containsKey(today)) today else today.minusDays(1)).minusDays(it.toLong()) }.toSet()
    val maxMin = (byDay.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                    ShapeGlyph(MaterialShapes.Cookie9Sided, 60.dp, cs.tertiaryContainer)
                    Icon(Sym["local_fire_department"], null, Modifier.size(28.dp), tint = cs.onTertiaryContainer)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$streak", style = ts(46f, 420, cs.onSurface, wdth = 88, tnum = true, lineHeight = 44f))
                        Text(if (streak == 1) "day" else "days", Modifier.padding(start = 4.dp, bottom = 4.dp), style = ts(16f, 600, cs.onSurface))
                    }
                    Text("streak · longest $longest", style = ts(12.5f, 540, cs.onSurfaceVariant))
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(hm(week), style = ts(20f, 520, cs.onSurface, tnum = true))
                    Text("last 7 days", style = ts(12.5f, 540, cs.onSurfaceVariant))
                }
            }
            Spacer(Modifier.height(14.dp))
            val axis = ts(10f, 560, cs.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val weeks = 26
                val left = 16.dp.toPx(); val top = 14.dp.toPx()
                val s = ((size.width - left) / weeks) * 0.82f
                val gp = ((size.width - left) / weeks) - s
                // The last column is the current week, Monday first.
                val start = today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks((weeks - 1).toLong())
                listOf("M" to 0, "W" to 2, "F" to 4).forEach { (l, r) -> drawText(measurer, l, Offset(0f, top + r * (s + gp)), axis) }
                var lastMonth = -1; var lastLabelX = -100f
                for (w in 0 until weeks) for (dd in 0 until 7) {
                    val date = start.plusDays((w * 7 + dd).toLong())
                    if (date.isAfter(today)) continue
                    val lx = left + w * (s + gp)
                    if (dd == 0 && date.monthValue != lastMonth) {
                        lastMonth = date.monthValue
                        if (lx - lastLabelX > 28.dp.toPx()) { drawText(measurer, date.month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH), Offset(lx, 0f), axis); lastLabelX = lx }
                    }
                    val o = Offset(left + w * (s + gp), top + dd * (s + gp))
                    val ms = byDay[date]
                    val c = if (ms == null) cs.surfaceContainerHigh else lerp(cs.primaryContainer, cs.primary, (ms.toFloat() / maxMin).coerceIn(0.15f, 1f))
                    if (date in streakDays) drawCircle(c, s / 2, o + Offset(s / 2, s / 2)) else drawRoundRect(c, o, Size(s, s), CornerRadius(s * 0.22f))
                }
            }
            Text("minutes per day · round = streak", style = ts(11f, 540, cs.onSurfaceVariant))
        }
    }
}

/** One card per (difficulty × length × net) actually played: best, its date, the last ten. */
@Composable
private fun Bests(won: List<Game>) {
    val cs = MaterialTheme.colorScheme
    val groups = won.groupBy { Triple(it.deal.difficulty, it.deal.length, it.net()) }.entries.sortedWith(compareBy({ it.key.first }, { it.key.second }, { it.key.third }))
    if (groups.isEmpty()) { Text("No wins yet.", Modifier.padding(horizontal = 20.dp), color = cs.onSurfaceVariant); return }
    val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        groups.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { (k, gs) ->
                    val best = gs.minBy { it.elapsed }
                    val recent = best.startedAt > weekAgo
                    val last10 = gs.sortedBy { it.startedAt }.takeLast(10)
                    val fg = if (recent) cs.onTertiaryContainer else cs.onSurface
                    val sub = if (recent) cs.onTertiaryContainer else cs.onSurfaceVariant
                    Surface(color = if (recent) cs.tertiaryContainer else cs.surfaceContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ShapeGlyph(Shp.difficulty[k.first], 18.dp, if (recent) cs.onTertiaryContainer else cs.secondary)
                                Spacer(Modifier.width(6.dp))
                                Text("${DIFFICULTY_NAMES[k.first]} · ${listOf("Quick", "Normal", "Long")[k.second]}" + if (k.third == 0) "" else " · ${NET_NAMES[k.third]}", Modifier.weight(1f), style = ts(12f, 620, sub), maxLines = 1)
                                Spark(last10.map { it.elapsed }, best.elapsed, if (recent) cs.onTertiaryContainer else cs.tertiary, sub.copy(alpha = 0.35f))
                            }
                            val (mm, frac) = clockMs(best.elapsed)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(mm, style = ts(25f, 470, fg, wdth = 90, tnum = true))
                                Text(frac, Modifier.padding(bottom = 2.dp), style = ts(14f, 500, sub, wdth = 90, tnum = true))
                            }
                            Row {
                                Text(date(best.startedAt), Modifier.weight(1f), style = ts(11.5f, 540, sub))
                                Text("last 10 " + clock(last10.map { it.elapsed }.sorted()[last10.size / 2]), style = ts(11.5f, 640, fg, tnum = true))
                            }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Spark(xs: List<Long>, best: Long, on: Color, off: Color) {
    val mx = (xs.maxOrNull() ?: 1L).coerceAtLeast(1L)
    Canvas(Modifier.size((xs.size * 3.5f).dp, 18.dp)) {
        xs.forEachIndexed { i, x ->
            val h = size.height * (0.25f + 0.75f * x / mx)
            drawRoundRect(if (x == best) on else off, Offset(i * 3.5.dp.toPx(), size.height - h), Size(2.2.dp.toPx(), h), CornerRadius(1.1.dp.toPx()))
        }
    }
}

/** Every solve as its difficulty's shape on a log-time axis; a trailing mean of 10; a stepped best. */
@Composable
private fun History(won: List<Game>) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val axis = ts(10.5f, 560, cs.onSurfaceVariant, tnum = true)
    val xs = won.sortedBy { it.startedAt }
    if (xs.isEmpty()) return
    val path = remember { Path() }
    Surface(color = cs.surfaceContainerLow, shape = RoundedCornerShape(28.dp), modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Canvas(Modifier.fillMaxWidth().height(180.dp)) {
                val left = 22.dp.toPx()
                val tMin = xs.minOf { it.elapsed }.coerceAtLeast(10_000L); val tMax = xs.maxOf { it.elapsed }.coerceAtLeast(tMin + 60_000L)
                fun y(t: Long) = size.height - 14.dp.toPx() - (size.height - 28.dp.toPx()) * ((ln(t.toDouble()) - ln(tMin * 0.9)) / (ln(tMax * 1.1) - ln(tMin * 0.9))).toFloat()
                fun x(i: Int) = left + (size.width - left - 8.dp.toPx()) * (if (xs.size == 1) 0.5f else i.toFloat() / (xs.size - 1))
                listOf(1, 2, 3, 5, 8, 13, 21, 34).forEach { m ->
                    val t = m * 60_000L
                    if (t in (tMin * 0.9).toLong()..(tMax * 1.1).toLong()) {
                        drawLine(cs.surfaceContainerHigh, Offset(left, y(t)), Offset(size.width, y(t)), 1f)
                        drawText(measurer, "$m", Offset(0f, y(t) - 7.dp.toPx()), axis)
                    }
                }
                // Stepped best per difficulty.
                xs.map { it.deal.difficulty }.distinct().forEach { d ->
                    var best = Long.MAX_VALUE
                    val p = Path(); var started = false
                    xs.forEachIndexed { i, g -> if (g.deal.difficulty == d && g.elapsed < best) {
                        if (started) p.lineTo(x(i), y(best)) else { p.moveTo(x(i), y(g.elapsed)); started = true }
                        best = g.elapsed; p.lineTo(x(i), y(best))
                    } }
                    if (started) { p.lineTo(size.width, y(best)); drawPath(p, cs.tertiary, style = Stroke(1.5.dp.toPx())) }
                }
                // Trailing mean of 10 (geometric).
                if (xs.size >= 3) {
                    val p = Path()
                    xs.indices.forEach { i ->
                        val w = xs.subList(max(0, i - 9), i + 1)
                        val gm = kotlin.math.exp(w.map { ln(it.elapsed.toDouble()) }.average()).toLong()
                        if (i == 0) p.moveTo(x(i), y(gm)) else p.lineTo(x(i), y(gm))
                    }
                    drawPath(p, cs.primary, style = Stroke(2.dp.toPx(), pathEffect = null))
                }
                xs.forEachIndexed { i, g ->
                    val s = 9.dp.toPx()
                    drawShape(Morphs.of(Shp.difficulty[g.deal.difficulty], Shp.difficulty[g.deal.difficulty]), 0f, cs.onSurfaceVariant.copy(alpha = 0.55f), Size(s, s), Offset(x(i) - s / 2, y(g.elapsed) - s / 2), path)
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendLine(cs.primary, "avg of 10"); Spacer(Modifier.width(12.dp)); LegendLine(cs.tertiary, "best")
            }
        }
    }
}

@Composable
private fun LegendLine(c: Color, label: String) {
    val cs = MaterialTheme.colorScheme
    Canvas(Modifier.size(14.dp, 8.dp)) { drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx()) }
    Spacer(Modifier.width(4.dp))
    Text(label, style = ts(12f, 540, cs.onSurfaceVariant))
}

/** By hour: a radial of 24 wedges (length = games). By weekday: bars. */
@Composable
private fun WhenYouPlay(won: List<Game>) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val hours = IntArray(24); val dows = IntArray(7)
    won.forEach { val z = Instant.ofEpochMilli(it.startedAt).atZone(zone); hours[z.hour]++; dows[z.dayOfWeek.value - 1]++ }
    val bestHour = hours.indices.maxBy { hours[it] }
    Surface(color = cs.surfaceContainerLow, shape = RoundedCornerShape(28.dp), modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val label = ts(19f, 640, cs.onSurface)
            val small = ts(10f, 560, cs.onSurfaceVariant)
            Canvas(Modifier.size(132.dp)) {
                val c = center; val r0 = size.minDimension * 0.24f; val r1 = size.minDimension * 0.48f
                val mx = (hours.maxOrNull() ?: 1).coerceAtLeast(1)
                for (h in 0 until 24) {
                    val len = r0 + (r1 - r0) * hours[h] / mx
                    drawArc(
                        if (h == bestHour) cs.primary else cs.primary.copy(alpha = 0.45f), h * 15f - 90f - 6f, 12f, true,
                        topLeft = c - Offset(len, len), size = Size(len * 2, len * 2),
                    )
                }
                drawCircle(cs.surfaceContainerLow, r0 * 0.95f)
                val t = measurer.measure("${bestHour}h", label)
                drawText(t, topLeft = c - Offset(t.size.width / 2f, t.size.height / 2f + 5.dp.toPx()))
                val s = measurer.measure("best hour", small)
                drawText(s, topLeft = c + Offset(-s.size.width / 2f, 6.dp.toPx()))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val mx = (dows.maxOrNull() ?: 1).coerceAtLeast(1)
                DayOfWeek.entries.forEachIndexed { k, dw ->
                    Row(Modifier.height(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(dw.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH), Modifier.width(32.dp), style = ts(11.5f, 580, cs.onSurfaceVariant))
                        Box(Modifier.weight(1f)) {
                            Canvas(Modifier.fillMaxWidth().height(11.dp)) {
                                drawRoundRect(cs.primary, Offset.Zero, Size(size.width * dows[k] / mx, size.height), CornerRadius(4.dp.toPx()))
                            }
                        }
                        Text("${dows[k]}", Modifier.width(30.dp).padding(start = 6.dp), style = ts(11.5f, 580, cs.onSurface, tnum = true))
                    }
                }
            }
        }
    }
}

@Composable
private fun Totals(games: List<Game>, current: Game?) {
    val won = games.count { it.status == Status.WON }
    val lost = games.count { it.status == Status.LOST }
    val abandoned = games.count { it.status == Status.PLAYING && it.id != current?.id }
    val cells = games.sumOf { g -> g.events.count { it.kind == Ev.PLACE || it.kind == Ev.AUTO } }
    val strikes = games.sumOf { it.mistakes }
    val hints = games.sumOf { g -> g.events.count { it.kind == Ev.HINT } }
    val n = games.size.coerceAtLeast(1)
    val tiles = listOf(
        "${games.size}" to "started", "$won" to "won · ${100 * won / n}%", "$lost" to "lost",
        hm(games.sumOf { it.elapsed }) to "solving", "$cells" to "cells placed", "%.2f".format(abandoned.toDouble() / won.coerceAtLeast(1)) to "abandons / win",
        "%.2f".format(strikes.toDouble() / n) to "strikes / game", "$hints" to "hints", "${games.count { it.deal.famous != null }}" to "famous puzzles",
    )
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (v, l) ->
                    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(start = 11.dp, end = 11.dp, top = 9.dp, bottom = 8.dp)) {
                            Text(v, style = ts(19f, 520, cs.onSurface, wdth = 92, tnum = true), maxLines = 1)
                            Text(l, style = ts(11.5f, 560, cs.onSurfaceVariant), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
