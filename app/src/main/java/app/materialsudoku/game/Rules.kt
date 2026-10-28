package app.materialsudoku.game

/** Grid geometry shared by the game and the UI. Cells are 0..80, row-major. */
object Geo {
    fun row(i: Int) = i / 9
    fun col(i: Int) = i % 9
    fun box(i: Int) = (i / 27) * 3 + (i % 9) / 3

    /** Units 0..8 rows, 9..17 columns, 18..26 boxes. */
    val units: Array<IntArray> = Array(27) { u ->
        when {
            u < 9 -> IntArray(9) { u * 9 + it }
            u < 18 -> IntArray(9) { it * 9 + (u - 9) }
            else -> { val b = u - 18; IntArray(9) { (b / 3) * 27 + (b % 3) * 3 + (it / 3) * 9 + it % 3 } }
        }
    }
    fun unitsOf(i: Int) = intArrayOf(row(i), 9 + col(i), 18 + box(i))

    /** The two long diagonals, 27 and 28. Not a constraint in classic Sudoku: only celebrated when full. */
    val diagonals: Array<IntArray> = arrayOf(IntArray(9) { it * 10 }, IntArray(9) { (it + 1) * 8 })
    fun cellsOf(u: Int): IntArray = if (u < 27) units[u] else diagonals[u - 27]
    /** Every line, box and diagonal through a cell. */
    fun celebratedUnitsOf(i: Int): List<Int> = unitsOf(i).toList() + listOf(27, 28).filter { i in diagonals[it - 27] }
    val peers: Array<IntArray> = Array(81) { i ->
        unitsOf(i).flatMap { units[it].toList() }.filter { it != i }.distinct().sorted().toIntArray()
    }
    /** Chebyshev distance in cells, the ripple's unit. */
    fun dist(a: Int, b: Int) = maxOf(kotlin.math.abs(row(a) - row(b)), kotlin.math.abs(col(a) - col(b)))
    fun euclid(a: Int, b: Int): Float {
        val dr = (row(a) - row(b)).toFloat(); val dc = (col(a) - col(b)).toFloat()
        return kotlin.math.sqrt(dr * dr + dc * dc)
    }
}

/** Candidates of an empty cell given the placed values (bit d set = d possible, d in 1..9). */
fun candidatesOf(values: IntArray, i: Int): Int {
    if (values[i] != 0) return 0
    var m = 0x3FE
    for (p in Geo.peers[i]) { val v = values[p]; if (v != 0) m = m and (1 shl v).inv() }
    return m
}

fun Int.hasBit(d: Int) = (this shr d) and 1 == 1
