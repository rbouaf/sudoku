package app.materialsudoku.engine

/**
 * 9×9 geometry, computed once. Every list is ordered by cell index, so nothing that decides a
 * puzzle ever iterates a hash structure (06-DETERMINISM §3.3).
 *
 * Units: 0–8 rows, 9–17 columns, 18–26 boxes. Candidate masks use **bit d for digit d** (1–9),
 * so the full mask is [ALL] = 0b11_1111_1110.
 */
internal object Geo {
    const val ALL = 0x3FE

    /** UNITS[u] = the 9 cells of unit u, in reading order. */
    val UNITS: Array<IntArray> = Array(27) { IntArray(9) }

    /** CELL_UNITS[i] = (row, 9 + col, 18 + box) of cell i. */
    val CELL_UNITS: Array<IntArray> = Array(81) { IntArray(3) }

    /** PEERS[i] = the 20 cells sharing a unit with i, ascending. */
    val PEERS: Array<IntArray> = Array(81) { IntArray(20) }

    /** IS_PEER[i * 81 + j] for O(1) "sees" tests. */
    val IS_PEER = BooleanArray(81 * 81)

    init {
        for (r in 0 until 9) for (c in 0 until 9) {
            val i = r * 9 + c
            val b = (r / 3) * 3 + c / 3
            val k = (r % 3) * 3 + c % 3
            UNITS[r][c] = i; UNITS[9 + c][r] = i; UNITS[18 + b][k] = i
            CELL_UNITS[i][0] = r; CELL_UNITS[i][1] = 9 + c; CELL_UNITS[i][2] = 18 + b
        }
        for (i in 0 until 81) {
            val s = BooleanArray(81)
            for (u in CELL_UNITS[i]) for (j in UNITS[u]) if (j != i) s[j] = true
            var k = 0
            for (j in 0 until 81) if (s[j]) { PEERS[i][k++] = j; IS_PEER[i * 81 + j] = true }
        }
    }

    fun row(i: Int) = i / 9
    fun col(i: Int) = i % 9
    fun box(i: Int) = (i / 27) * 3 + (i % 9) / 3
    fun sees(a: Int, b: Int) = IS_PEER[a * 81 + b]

    fun bitCount(m: Int) = Integer.bitCount(m)
    fun firstDigit(m: Int) = Integer.numberOfTrailingZeros(m)

    /** Digits of a mask, ascending. */
    fun digits(m: Int): IntArray {
        val out = IntArray(Integer.bitCount(m)); var x = m; var k = 0
        while (x != 0) { out[k++] = Integer.numberOfTrailingZeros(x); x = x and (x - 1) }
        return out
    }

    fun parse(givens: String): IntArray {
        require(givens.length == 81) { "a 9×9 puzzle is 81 characters, got ${givens.length}" }
        return IntArray(81) { val ch = givens[it]; if (ch in '1'..'9') ch - '0' else 0 }
    }

    fun format(values: IntArray): String {
        val sb = StringBuilder(81)
        for (v in values) sb.append(('0'.code + v).toChar())
        return sb.toString()
    }
}
