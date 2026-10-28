package app.materialsudoku.engine

import app.materialsudoku.engine.Geo.UNITS

/**
 * Grades a puzzle the established way: by the hardest technique its solve requires (02 §1.1),
 * with techniques tried in [Technique] order so the label is the lowest difficulty whose
 * techniques finish the grid.
 *
 * The solve is a **wave solve**: each round places *every* scanning single available at once (a
 * set, not a sequence, so scan order cannot matter); only when none is open does it place the
 * naked singles, and only when no single exists does it take one step of the cheapest other
 * technique. That gives the order-free availability profile behind [Length]:
 *
 *     load = 40 × empty cells + Σ over single rounds of 2000 / (singles available that round)
 *            + Σ over harder steps of Technique.weight
 *
 * Empty cells are the typing; 2000 / width is the search for a move when few are open (Pelánek's
 * availability, 02 §1.4); the weights cost the deductions. All invented, all integers, versioned.
 */
object Grader {
    /**
     * 2: length cut points recalibrated for generator v2, availability and squeezes added.
     * 3: the ladder shifted one step easier (naked single is Medium; subsets Hard; …), cuts recalibrated.
     */
    const val VERSION = 3

    /**
     * Grades [givens]. A unique puzzle is graded on its class representative ([Canonical]), so the
     * whole grade, length included, is identical for every disguise of the puzzle (06 §5.3; the
     * difficulty alone is invariant on the raw form too, measured in `InvarianceTest`).
     */
    fun grade(givens: String): Grade {
        val sol = Solver.solve(givens) ?: return grade(Geo.parse(givens))
        return grade(Geo.parse(Canonical.classId(givens, sol)))
    }

    /** Grades the grid exactly as drawn: same difficulty as [grade], but load and length may differ. */
    internal fun grade(p: IntArray, measure: Boolean = true): Grade {
        val board = Board.of(p) ?: return stalled(p, emptyMap(), 0, 0, 0, 0, null)
        val counts = IntArray(Technique.entries.size)
        var rounds = 0; var bottlenecks = 0; var hardSteps = 0; var load = 40 * board.empties()
        var hardest: Technique? = null
        var guard = 0
        while (!board.solved()) {
            if (++guard > 2000 || board.broken()) return stalled(p, toMap(counts), rounds, bottlenecks, hardSteps, load, hardest)
            // Scanning singles first; naked singles (Medium since v3) only when no scanning single is
            // open, so a puzzle that scanning alone finishes is never labelled Medium.
            val all = singlesWave(board)
            val scan = all.filter { Technique.entries[it[2]].isScanning }
            val wave = scan.ifEmpty { all }
            if (wave.isNotEmpty()) {
                rounds++
                if (wave.size == 1) bottlenecks++
                load += 2000 / wave.size
                for (w in wave) {
                    counts[w[2]]++
                    val t = Technique.entries[w[2]]
                    if (hardest == null || t > hardest) hardest = t
                }
                for (w in wave) if (board.v[w[0]] == 0 && board.c[w[0]] and (1 shl w[1]) != 0) board.place(w[0], w[1])
                continue
            }
            val step = Logic.cheapest(board) ?: return stalled(p, toMap(counts), rounds, bottlenecks, hardSteps, load, hardest)
            counts[step.technique.ordinal]++
            hardSteps++
            load += step.technique.weight
            if (hardest == null || step.technique > hardest) hardest = step.technique
            board.apply(step)
        }
        val difficulty = hardest?.difficulty ?: Difficulty.EASY
        val avail = if (measure) availability(p) else intArrayOf(0, 0)
        return Grade(
            availability = avail[0], squeezes = avail[1],
            difficulty = difficulty,
            length = lengthOf(difficulty, load),
            techniques = toMap(counts),
            hardest = hardest,
            complete = true,
            clues = p.count { it != 0 },
            rounds = rounds, bottlenecks = bottlenecks, hardSteps = hardSteps, load = load,
            graderVersion = VERSION,
        )
    }

    /**
     * [Grade.availability] and [Grade.squeezes]: place one single at a time, lowest cell first,
     * counting how many singles were open each time; harder steps are taken only when none is.
     */
    /** Squeezes are not counted once this few cells remain: the last cells are always one at a time. */
    private const val ENDGAME = 9

    internal fun availability(p: IntArray): IntArray {
        val b = Board.of(p) ?: return intArrayOf(0, 0)
        var sum = 0L; var steps = 0; var squeezes = 0; var guard = 0
        while (!b.solved() && guard++ < 2000) {
            val wave = singlesWave(b)
            if (wave.isEmpty()) { b.apply(Logic.cheapest(b) ?: break); continue }
            sum += wave.size; steps++
            if (wave.size == 1 && b.empties() > ENDGAME) squeezes++
            b.place(wave[0][0], wave[0][1])
        }
        return intArrayOf(if (steps == 0) 0 else (sum * 100 / steps).toInt(), squeezes)
    }

    /**
     * Every single available now, as (cell, digit, technique ordinal), each cell once and costed at
     * the cheapest single that finds it. Ascending by cell.
     */
    internal fun singlesWave(b: Board): List<IntArray> {
        val best = IntArray(81) { -1 }
        val digit = IntArray(81)
        fun offer(i: Int, d: Int, t: Technique) {
            if (best[i] < 0 || t.ordinal < best[i]) { best[i] = t.ordinal; digit[i] = d }
        }
        for (u in 0 until 27) {
            val placed = b.placedIn(u)
            var empty = -1; var n = 0
            for (i in UNITS[u]) if (b.v[i] == 0) { empty = i; n++ }
            if (n == 1) {
                val m = Geo.ALL and placed.inv()
                if (Integer.bitCount(m) == 1 && b.c[empty] and m != 0) offer(empty, Integer.numberOfTrailingZeros(m), Technique.FULL_HOUSE)
            }
            for (d in 1..9) {
                if (placed and (1 shl d) != 0) continue
                val cells = b.cellsWith(u, d)
                if (cells.size == 1) offer(cells[0], d, if (u >= 18) Technique.HIDDEN_SINGLE_BOX else Technique.HIDDEN_SINGLE_LINE)
            }
        }
        for (i in 0 until 81) if (b.v[i] == 0 && Integer.bitCount(b.c[i]) == 1) offer(i, Integer.numberOfTrailingZeros(b.c[i]), Technique.NAKED_SINGLE)
        val out = ArrayList<IntArray>()
        for (i in 0 until 81) if (best[i] >= 0) out.add(intArrayOf(i, digit[i], best[i]))
        return out
    }

    /**
     * Length cut points (load at the 1st and 2nd tertile) per difficulty. Measured by
     * `CalibrationTest` on generator v3: 300 puzzles from each of the three length recipes, cut at
     * the tertiles of the combined 900 (seed 2026, 2026-10-27). Frozen with [VERSION].
     */
    internal val CUTS: Map<Difficulty, IntArray> = linkedMapOf(
        Difficulty.EASY to intArrayOf(2460, 3186),
        Difficulty.MEDIUM to intArrayOf(8931, 12325),
        Difficulty.HARD to intArrayOf(14023, 18475),
        Difficulty.EXPERT to intArrayOf(13936, 19609),
        Difficulty.MASTER to intArrayOf(17965, 25264),
        Difficulty.EXTREME to intArrayOf(27117, 36503),
    )

    internal fun lengthOf(d: Difficulty, load: Int): Length {
        var k = d
        while (CUTS[k] == null && k.ordinal > 0) k = Difficulty.entries[k.ordinal - 1]
        val cut = CUTS[k] ?: return Length.NORMAL
        return when {
            load < cut[0] -> Length.QUICK
            load < cut[1] -> Length.NORMAL
            else -> Length.LONG
        }
    }

    /** How far [load] lies outside [length]'s band for [d] (0 inside). */
    internal fun missFrom(d: Difficulty, length: Length, load: Int): Int {
        var k = d
        while (CUTS[k] == null && k.ordinal > 0) k = Difficulty.entries[k.ordinal - 1]
        val cut = CUTS[k] ?: return 0
        return when (length) {
            Length.QUICK -> maxOf(0, load - cut[0] + 1)
            Length.NORMAL -> if (load < cut[0]) cut[0] - load else maxOf(0, load - cut[1] + 1)
            Length.LONG -> maxOf(0, cut[1] - load)
        }
    }

    private fun toMap(counts: IntArray): Map<Technique, Int> {
        val m = LinkedHashMap<Technique, Int>()
        for (t in Technique.entries) if (counts[t.ordinal] > 0) m[t] = counts[t.ordinal]
        return m
    }

    private fun stalled(p: IntArray, t: Map<Technique, Int>, rounds: Int, bn: Int, hs: Int, load: Int, hardest: Technique?) = Grade(
        difficulty = Difficulty.IMPOSSIBLE, length = Length.LONG, techniques = t, hardest = hardest, complete = false,
        clues = p.count { it != 0 }, rounds = rounds, bottlenecks = bn, hardSteps = hs, load = load, graderVersion = VERSION,
    )
}
