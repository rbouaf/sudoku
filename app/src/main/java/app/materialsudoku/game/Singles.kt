package app.materialsudoku.game

/**
 * A tiny singles-only solver for the UI's own needs (auto-complete availability, the stand-in hint).
 * The real grader lives in `engine/`; this exists so the game layer never waits on it.
 */
object Singles {
    /** The next forced placement (full house / hidden single / naked single), or null. */
    fun next(values: IntArray): Pair<Int, Int>? {
        // Hidden singles by unit first: the cheapest thing for a human to see.
        for (u in 0 until 27) {
            val cells = Geo.units[u]
            for (d in 1..9) {
                if (cells.any { values[it] == d }) continue
                var where = -1; var count = 0
                for (c in cells) if (values[c] == 0 && candidatesOf(values, c).hasBit(d)) { where = c; count++ }
                if (count == 1) return where to d
            }
        }
        for (i in 0 until 81) {
            if (values[i] != 0) continue
            val m = candidatesOf(values, i)
            if (Integer.bitCount(m) == 1) return i to Integer.numberOfTrailingZeros(m)
        }
        return null
    }

    /** Solves by singles alone, or null if it stalls or contradicts. */
    fun solve(values: IntArray): IntArray? {
        val v = values.copyOf()
        while (true) {
            if (v.none { it == 0 }) return v
            for (i in 0 until 81) if (v[i] == 0 && candidatesOf(v, i) == 0) return null
            val (i, d) = next(v) ?: return null
            v[i] = d
        }
    }

    /** The unit a hidden single lives in, for a stage-1 hint. */
    fun unitOf(values: IntArray, cell: Int, digit: Int): Int? {
        for (u in Geo.unitsOf(cell)) {
            val n = Geo.units[u].count { values[it] == 0 && candidatesOf(values, it).hasBit(digit) }
            if (n == 1) return u
        }
        return null
    }
}
