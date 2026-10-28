package app.materialsudoku.engine

/**
 * Hints reason on the player's actual position (02 §5), never on the solver's path.
 *
 * Wrong digits are ignored (treated as empty), so a hint is always sound; call [mistakes] first to
 * tell the player about them. The player's notes are used per cell when they are a sound superset
 * (they still contain the right digit), intersected with the basic candidates; otherwise that cell
 * falls back to its basic candidates. Eliminations the player already made therefore count.
 */
object Hints {

    fun next(values: IntArray, candidates: IntArray?, solution: String): Step? {
        val board = position(values, candidates, solution) ?: return null
        if (board.solved()) return null
        return Logic.cheapest(board)
    }

    /** Cells whose placed value disagrees with the solution, ascending. */
    fun mistakes(values: IntArray, solution: String): List<Int> =
        (0 until 81).filter { values[it] != 0 && values[it] != solution[it] - '0' }

    internal fun position(values: IntArray, candidates: IntArray?, solution: String): Board? {
        require(values.size == 81 && solution.length == 81)
        val clean = IntArray(81) { if (values[it] != 0 && values[it] == solution[it] - '0') values[it] else 0 }
        val b = Board.of(clean) ?: return null
        if (candidates != null) {
            for (i in 0 until 81) {
                if (b.v[i] != 0) continue
                val mine = candidates[i] and Geo.ALL
                val right = 1 shl (solution[i] - '0')
                if (mine != 0 && mine and right != 0) b.c[i] = b.c[i] and mine
            }
        }
        return b
    }
}
