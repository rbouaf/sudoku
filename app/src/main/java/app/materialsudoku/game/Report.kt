package app.materialsudoku.game

import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.tanh

/** How one placement was rated against the board the player actually had (02 §2.4). */
data class PlacementRating(
    val technique: String?,     // null = not forced: a guess (E3)
    val techniqueLevel: Int,    // difficulty index of that technique, -1 for a guess
    val effort: Int,
    val easiest: String?,
    val detour: Int,
)

/** Rates a placement; the engine supplies the real one, tests can supply anything. */
fun interface Rater { fun rate(values: IntArray, cell: Int, digit: Int): PlacementRating? }

data class Placement(val t: Long, val cell: Int, val digit: Int, val auto: Boolean, val rating: PlacementRating?)
data class Strike(val t: Long, val cell: Int, val digit: Int, val kind: StrikeKind, val placedSoFar: Int, val singlesOpen: Int, val lookedMs: Long)
enum class StrikeKind { SLIP, GUESS, WRONG }

/**
 * Everything the result screen and Stats show about one game, computed only from its log. Nothing
 * here is stored: when a metric changes, every past game gets the new one.
 */
class Report(val game: Game, rater: Rater?) {
    val placements: List<Placement>
    val strikes: List<Strike>
    /** Board after each placement, for the replay scrub. index 0 = the givens. */
    val boards: List<IntArray>

    init {
        val v = IntArray(81) { game.givens[it] }
        val ps = mutableListOf<Placement>()
        val ss = mutableListOf<Strike>()
        val bs = mutableListOf(v.copyOf())
        var lastPlace = 0L
        for (e in game.events) {
            when (e.kind) {
                Ev.PLACE, Ev.AUTO -> {
                    if (e.digit == game.solution[e.cell] && v[e.cell] == 0) {
                        val r = if (e.kind == Ev.PLACE) rater?.rate(v.copyOf(), e.cell, e.digit) else null
                        v[e.cell] = e.digit
                        ps += Placement(e.t, e.cell, e.digit, e.kind == Ev.AUTO, r)
                        bs += v.copyOf()
                        lastPlace = e.t
                    }
                    // A wrong digit left standing (no-net mode) never enters the replay as a placement.
                }
                Ev.MISTAKE -> {
                    val slip = Geo.peers[e.cell].any { v[it] == e.digit }
                    val cand = candidatesOf(v, e.cell)
                    val kind = when {
                        slip -> StrikeKind.SLIP
                        cand.hasBit(e.digit) && Integer.bitCount(cand) > 1 -> StrikeKind.GUESS
                        else -> StrikeKind.WRONG
                    }
                    val open = (0 until 81).count { v[it] == 0 && Integer.bitCount(candidatesOf(v, it)) == 1 }
                    ss += Strike(e.t, e.cell, e.digit, kind, ps.size, open, e.t - lastPlace)
                }
            }
        }
        placements = ps; strikes = ss; boards = bs
    }

    val won get() = game.status == Status.WON
    val time get() = game.elapsed
    val toFill = game.givens.count { it == 0 }
    val accuracy: Double get() = placements.size.toDouble() / (placements.size + strikes.size).coerceAtLeast(1)
    val guesses get() = placements.count { it.rating != null && it.rating.technique == null }
    val rated get() = placements.any { it.rating != null }
    val effort get() = placements.sumOf { it.rating?.effort ?: 0 }
    val effortPerMin: Int get() = if (time <= 0) 0 else (effort * 60_000L / time).toInt()
    val hardest: Placement? get() = placements.filter { it.rating?.technique != null }.maxByOrNull { it.rating!!.effort }
    val detours get() = placements.count { (it.rating?.detour ?: 0) > 0 }

    /** Seconds spent on each placement: the gap since the previous one. */
    val gaps: List<Long> get() = placements.mapIndexed { k, p -> p.t - (if (k == 0) 0L else placements[k - 1].t) }

    /**
     * Rhythm: Monkeytype's consistency (kogasa) on placements per 15 s bucket. 100 = metronome,
     * low = bursts and stalls.
     */
    val rhythm: Int
        get() {
            if (time < 30_000 || placements.size < 4) return 0
            val n = (time / 15_000).toInt() + 1
            val b = DoubleArray(n)
            placements.forEach { b[(it.t / 15_000).toInt().coerceAtMost(n - 1)] += 1.0 }
            val mean = b.average()
            if (mean == 0.0) return 0
            val sd = sqrt(b.sumOf { (it - mean) * (it - mean) } / n)
            val cov = sd / mean
            return (100 * (1 - tanh(cov + cov * cov * cov / 3 + cov * cov * cov * cov * cov / 5))).toInt().coerceIn(0, 100)
        }

    val hinted get() = game.hinted
    val assisted get() = game.assisted
    /** Every digit placed by you (auto-finish barely counts as help, but these weren't your taps). */
    val byHand get() = placements.none { it.auto }
    val flawless get() = strikes.isEmpty()
}

/**
 * Records (SOLVE-CONDITIONS): never ranked across difficulty × length × net × band, and a hinted run
 * goes to its own assisted table.
 */
data class RecordKey(val difficulty: Int, val length: Int, val net: Int, val band: Int, val assisted: Boolean)

/** 0 Lives (3) · 1 Sudden death (1 life) · 2 No net. */
fun Game.net(): Int = when { !rules.flagMistakes -> 2; rules.lives == 1 -> 1; else -> 0 }
fun Game.recordKey() = RecordKey(deal.difficulty, deal.length, net(), band, assisted)

val NET_NAMES = listOf("Lives", "Sudden death", "No net")
val BAND_NAMES = listOf("Little help", "Some help")

data class Accolade(val key: String, val label: String, val earned: Boolean, val why: String)

/** Accolades, measured off the log (SOLVE-CONDITIONS §5). */
fun accolades(r: Report, prior: List<Game>): List<Accolade> {
    val g = r.game
    if (!r.won) return emptyList()
    val sameKey = prior.filter { it.status == Status.WON && it.recordKey() == g.recordKey() && it.id != g.id }
    val best = sameKey.minOfOrNull { it.elapsed }
    val sameFast = prior.filter { it.status == Status.WON && it.deal.difficulty == g.deal.difficulty && it.deal.length == g.deal.length && it.net() == g.net() && it.id != g.id }
        .takeLast(30).map { it.elapsed }.sorted()
    val median = if (sameFast.size >= 10) sameFast[sameFast.size / 2] else null
    return listOf(
        Accolade("record", "New record", best == null || g.elapsed < best, if (best == null) "first of its kind" else "best was ${clock(best)}"),
        Accolade("unaided", "Unaided", !r.assisted, if (g.usedFillNotes) "filled notes" else "${g.events.count { it.kind == Ev.HINT }} hints"),
        Accolade("byhand", "By hand", r.byHand, "auto-finish"),
        Accolade("fast", "Fast", median != null && g.elapsed < median, median?.let { "usual ${clock(it)}" } ?: "after 10 games"),
        Accolade("pure", "Pure logic", r.rated && r.guesses == 0, "${r.guesses} guess" + if (r.guesses == 1) "" else "es"),
        Accolade("flawless", "Flawless", r.flawless && g.rules.flagMistakes, "${r.strikes.size} strike" + if (r.strikes.size == 1) "" else "s"),
        Accolade("sudden", "Sudden death", g.net() == 1, ""),
        Accolade("nonet", "No net", g.net() == 2, ""),
    ).filter { it.earned || it.key in setOf("unaided", "byhand", "pure", "flawless", "fast") }
}

fun clock(ms: Long): String { val s = ms / 1000; return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }
fun clockMs(ms: Long): Pair<String, String> = clock(ms) to "." + (ms % 1000).toString().padStart(3, '0')
fun signedDelta(ms: Long): String { val a = kotlin.math.abs(ms); return (if (ms < 0) "−" else "+") + clock(a) + "." + (a % 1000).toString().padStart(3, '0') }

/** Game time when half of the empty cells had been filled, from the log; null if it never got there. */
fun Game.halfwayTime(): Long? {
    val need = (givens.count { it == 0 } + 1) / 2
    var n = 0
    for (e in events) if (e.kind == Ev.PLACE || e.kind == Ev.AUTO) { n++; if (n >= need) return e.t }
    return null
}

/**
 * The halfway message (user, 2026-10-27). First match wins and it is never discouraging:
 * record pace · ahead of your usual · no mistakes yet · halfway there. Pace lines need the timer
 * shown; with it hidden, time is never mentioned.
 */
fun halfwayLine(g: Game, prior: List<Game>, timerShown: Boolean): Pair<String, String?> {
    val now = g.elapsed
    if (timerShown) {
        val best = prior.filter { it.status == Status.WON && it.recordKey() == g.recordKey() }.minByOrNull { it.elapsed }?.halfwayTime()
        if (best != null && now < best) return "Halfway" to "Record pace"
        val usual = prior.filter { it.status == Status.WON && it.deal.difficulty == g.deal.difficulty && it.deal.length == g.deal.length && it.net() == g.net() }
            .takeLast(30).mapNotNull { it.halfwayTime() }
        if (usual.size >= 3) {
            val m = usual.median()!!
            if (now < m) return "Halfway" to "${clock(m - now)} ahead of usual"
        }
    }
    if (g.rules.flagMistakes && g.mistakes == 0) return "Halfway" to "No mistakes yet"
    return "Halfway there" to null
}

/** Median helper for stats. */
fun List<Long>.median(): Long? = if (isEmpty()) null else sorted()[size / 2]
fun logOf(x: Double) = ln(x)
