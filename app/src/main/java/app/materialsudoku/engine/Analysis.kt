package app.materialsudoku.engine

import app.materialsudoku.engine.Geo.CELL_UNITS
import app.materialsudoku.engine.Geo.UNITS

/**
 * How one placement was justified on the board the player actually had (02 §2.2–2.4, effort v1).
 *
 * @property technique the hardest technique the placement needed, or null when no catalogue
 *   technique forces it from that board: a guess, or reasoning the app does not know.
 * @property effort `w(single that finally places it) + Σ w(elimination steps it depended on)`;
 *   0 for a guess.
 * @property easiest the technique behind the cheapest placement available anywhere at that moment.
 * @property detour `effort − effort(easiest)`, never negative. Neutral information (02 §2.3c).
 */
class Rating(val technique: Technique?, val effort: Int, val easiest: Technique?, val detour: Int) {
    override fun toString() = "Rating($technique, effort=$effort, easiest=$easiest, detour=$detour)"
}

/**
 * The per-placement effort model. For a target (cell, digit) on board V:
 *
 * 1. Start from V's basic candidates (values only; notes are never trusted as truth).
 * 2. Repeatedly apply the cheapest **elimination** step anywhere on the board, *without placing any
 *    other cell*, until the target is a full house, hidden single or naked single. Stop at a
 *    step budget, or when nothing applies: then the placement was not forced.
 * 3. Charge only the steps the final single depended on, traced back: the eliminations that
 *    emptied the target cell (naked single) or the other cells of its unit for the digit (hidden
 *    single), then recursively whatever emptied each contributing step's pattern cells.
 *
 * This is SE's "easiest first, take the max" restricted to one target (the per-cell analogue of
 * SE's EP). Weights are HoDoKu 2.2 defaults for patterns and the "no candidates visible" column
 * of 02 §2.4 for singles. Deterministic: fixed scan orders, an operation budget, integers only.
 */
object Analysis {
    const val EFFORT_VERSION = 1
    private const val STEP_BUDGET = 120

    fun weight(t: Technique): Int = when (t) {
        Technique.FULL_HOUSE -> 4
        Technique.HIDDEN_SINGLE_BOX -> 10
        Technique.HIDDEN_SINGLE_LINE -> 14
        Technique.NAKED_SINGLE -> 20
        Technique.POINTING, Technique.CLAIMING -> 50
        Technique.NAKED_PAIR -> 60
        Technique.HIDDEN_PAIR -> 70
        Technique.NAKED_TRIPLE -> 80
        Technique.HIDDEN_TRIPLE -> 100
        Technique.NAKED_QUAD -> 120
        Technique.HIDDEN_QUAD -> 150
        Technique.X_WING -> 140
        Technique.SKYSCRAPER -> 130
        Technique.TWO_STRING_KITE -> 150
        Technique.EMPTY_RECTANGLE -> 150
        Technique.SWORDFISH -> 150
        Technique.JELLYFISH -> 160
        Technique.XY_WING -> 160
        Technique.XYZ_WING -> 180
        Technique.W_WING -> 150
        Technique.UNIQUE_RECTANGLE_1 -> 100
        Technique.BUG_PLUS_1 -> 100
        Technique.X_CHAIN -> 260
        Technique.XY_CHAIN -> 260
        Technique.AIC -> 280
    }

    /**
     * Rates placing [digit] at [cell] on [values] (before the move, 0 = empty). If [solution] is
     * given, wrong values on the board are ignored first, and a wrong [digit] rates as not forced.
     * Without it, [values] must hold only correct digits.
     */
    fun rate(values: IntArray, cell: Int, digit: Int, solution: String? = null): Rating {
        require(values.size == 81 && cell in 0 until 81 && digit in 1..9)
        val clean = if (solution == null) values.copyOf()
        else IntArray(81) { if (values[it] != 0 && values[it] == solution[it] - '0') values[it] else 0 }
        clean[cell] = 0
        val easiest = justifyCheapest(clean)
        val wrong = solution != null && solution[cell] - '0' != digit
        val mine = if (wrong) null else justify(clean, cell, digit)
        val effort = mine?.effort ?: 0
        val detour = if (mine == null || easiest == null) 0 else maxOf(0, effort - easiest.effort)
        return Rating(mine?.technique, effort, easiest?.technique, detour)
    }

    private class Justified(val technique: Technique, val effort: Int)

    /** One applied elimination step and the (cell, digit) pairs it removed. */
    private class Applied(val step: Step, val removed: List<Pair<Int, Int>>)

    private fun justify(values: IntArray, cell: Int, digit: Int): Justified? {
        val b = Board.of(values) ?: return null
        if (b.c[cell] and (1 shl digit) == 0) return null
        val log = ArrayList<Applied>()
        while (true) {
            singleAt(b, cell, digit)?.let { return charge(b, it, cell, digit, log) }
            if (log.size >= STEP_BUDGET || !eliminate(b, log, cell, digit)) return null
            if (b.c[cell] and (1 shl digit) == 0) return null // only possible on an unsound board
        }
    }

    /** The cheapest placement anywhere: the first moment any cell becomes a single, cheapest single wins. */
    private fun justifyCheapest(values: IntArray): Justified? {
        val b = Board.of(values) ?: return null
        if (b.solved()) return null
        val log = ArrayList<Applied>()
        while (true) {
            var best: Justified? = null
            for (w in Grader.singlesWave(b)) {
                val t = Technique.entries[w[2]]
                val j = charge(b, t, w[0], w[1], log)
                if (best == null || j.effort < best.effort) best = j
            }
            if (best != null) return best
            if (log.size >= STEP_BUDGET || !eliminate(b, log, -1, 0)) return null
        }
    }

    /** Applies the cheapest elimination-only step (a placement only if it is the target). */
    private fun eliminate(b: Board, log: MutableList<Applied>, cell: Int, digit: Int): Boolean {
        for (t in Technique.entries) {
            if (t.isSingle) continue
            val s = Logic.find(b, t) ?: continue
            if (s.placements.isNotEmpty()) {
                if (s.placements.any { it.first == cell && it.second == digit }) {
                    // BUG+1 placing the target itself: treat as its own final step
                    log.add(Applied(s, emptyList()))
                    b.c[cell] = 1 shl digit
                    return true
                }
                continue
            }
            val removed = s.eliminations.filter { (i, d) -> b.c[i] and (1 shl d) != 0 }
            if (removed.isEmpty()) continue
            for ((i, d) in removed) b.c[i] = b.c[i] and (1 shl d).inv()
            log.add(Applied(s, removed))
            return true
        }
        return false
    }

    /** The cheapest single that places [digit] at [cell] right now, if any. */
    private fun singleAt(b: Board, cell: Int, digit: Int): Technique? {
        val bit = 1 shl digit
        if (b.c[cell] and bit == 0) return null
        for (u in CELL_UNITS[cell]) {
            var empties = 0
            for (i in UNITS[u]) if (b.v[i] == 0) empties++
            if (empties == 1) return Technique.FULL_HOUSE
        }
        if (b.countIn(CELL_UNITS[cell][2], digit) == 1) return Technique.HIDDEN_SINGLE_BOX
        if (b.countIn(CELL_UNITS[cell][0], digit) == 1 || b.countIn(CELL_UNITS[cell][1], digit) == 1) return Technique.HIDDEN_SINGLE_LINE
        if (b.c[cell] == bit) return Technique.NAKED_SINGLE
        return null
    }

    /**
     * Charges the final single plus the logged steps it depended on, traced back through the log.
     * "Needed" starts as the candidates whose absence makes the single: the cell's other digits
     * (naked) or the digit in the unit's other cells (hidden); a step is charged if it removed a
     * needed candidate, and then the candidates its own pattern cells lost earlier become needed.
     */
    private fun charge(b: Board, single: Technique, cell: Int, digit: Int, log: List<Applied>): Justified {
        val needed = BooleanArray(81 * 10)
        when (single) {
            Technique.FULL_HOUSE -> {}
            Technique.NAKED_SINGLE -> for (d in 1..9) if (d != digit) needed[cell * 10 + d] = true
            Technique.HIDDEN_SINGLE_BOX -> for (i in UNITS[CELL_UNITS[cell][2]]) if (i != cell) needed[i * 10 + digit] = true
            else -> {
                val u = CELL_UNITS[cell].first { it < 18 && b.countIn(it, digit) == 1 }
                for (i in UNITS[u]) if (i != cell) needed[i * 10 + digit] = true
            }
        }
        var effort = weight(single)
        var hardest = single
        for (k in log.indices.reversed()) {
            val a = log[k]
            val hit = a.removed.isEmpty() || a.removed.any { (i, d) -> needed[i * 10 + d] }
            if (!hit) continue
            effort += weight(a.step.technique)
            if (a.step.technique > hardest) hardest = a.step.technique
            for (i in a.step.focusCells) for (d in 1..9) needed[i * 10 + d] = true
        }
        return Justified(hardest, effort)
    }
}
