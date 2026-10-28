package app.materialsudoku.engine

/**
 * Equivalence under the 9×9 group: band/stack permutations, row/column permutations inside them,
 * transposition and digit relabelling (≈ 1.2 × 10¹² variants, 06 §2.1).
 *
 * [classId] is the **solution-anchored minlex** form (Deverin / Mathimagics "minlex CP", 06 §2.3):
 * minlex the solution grid, then take the smallest image of the puzzle (0 sorts first) among every
 * transformation that reaches that minimal grid. Two puzzles are "the same puzzle in disguise"
 * exactly when their class IDs match. Ported from `reference/bench/Determinism.java`, which
 * reproduced both published worked examples and measured 0.53 ms median on the Pixel 10.
 */
object Canonical {

    /** The class representative of [givens] (whose unique solution is [solution]), as 81 characters. */
    fun classId(givens: String, solution: String): String = Geo.format(canonical(Geo.parse(givens), Geo.parse(solution)))

    /** Same, solving first. Null if the puzzle is not unique. */
    fun classId(givens: String): String? = Solver.solve(givens)?.let { classId(givens, it) }

    /** A seeded member of the group, applied to a puzzle and its solution together. */
    class Transform internal constructor(private val cell: IntArray, private val digit: IntArray) {
        fun apply(grid: String): String {
            val g = Geo.parse(grid)
            return Geo.format(IntArray(81) { digit[g[cell[it]]] })
        }

        companion object {
            fun random(rng: SplitMix64): Transform {
                val rows = lineOrder(rng); val cols = lineOrder(rng)
                val t = rng.nextInt(2) == 1
                val cell = IntArray(81)
                for (i in 0 until 9) for (j in 0 until 9) cell[i * 9 + j] = if (t) cols[j] * 9 + rows[i] else rows[i] * 9 + cols[j]
                val dg = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9); rng.shuffle(dg)
                val dm = IntArray(10); for (d in 1..9) dm[d] = dg[d - 1]
                return Transform(cell, dm)
            }

            private fun lineOrder(r: SplitMix64): IntArray {
                val bands = intArrayOf(0, 1, 2); r.shuffle(bands)
                val o = IntArray(9)
                for (b in 0 until 3) { val w = intArrayOf(0, 1, 2); r.shuffle(w); for (k in 0 until 3) o[b * 3 + k] = bands[b] * 3 + w[k] }
                return o
            }
        }
    }

    // ------------------------------------------------------------------------------- minlex

    private val P3 = arrayOf(intArrayOf(0, 1, 2), intArrayOf(0, 2, 1), intArrayOf(1, 0, 2), intArrayOf(1, 2, 0), intArrayOf(2, 0, 1), intArrayOf(2, 1, 0))
    private val POW10 = intArrayOf(100000000, 10000000, 1000000, 100000, 10000, 1000, 100, 10, 1)

    /** 1296 column orders: stack order × three within-stack orders. */
    private val COLPERMS: Array<IntArray> = run {
        val out = Array(1296) { IntArray(9) }
        var k = 0
        for (s in P3) for (a in P3) for (b in P3) for (c in P3) {
            val w = arrayOf(a, b, c)
            for (S in 0 until 3) for (j in 0 until 3) out[k][S * 3 + j] = s[S] * 3 + w[S][j]
            k++
        }
        out
    }

    internal fun canonical(puzzle: IntArray, sol: IntArray): IntArray {
        val best = IntArray(9) { Int.MAX_VALUE }
        val ties = ArrayList<IntArray>() // {t, r0..r8, colPermIndex}
        val map = IntArray(10); val code = IntArray(9); val cand = IntArray(9)
        val g = Array(9) { IntArray(9) }
        for (t in 0 until 2) {
            for (r in 0 until 9) for (c in 0 until 9) g[r][c] = if (t == 0) sol[r * 9 + c] else sol[c * 9 + r]
            for (r0 in 0 until 9) {
                val b0 = r0 / 3
                var oa = -1; var ob = -1
                for (r in b0 * 3 until b0 * 3 + 3) if (r != r0) { if (oa < 0) oa = r else ob = r }
                for (pi in 0 until 1296) {
                    val cp = COLPERMS[pi]
                    for (j in 0 until 9) map[g[r0][cp[j]]] = j + 1
                    var ca = 0; var cb = 0
                    for (j in 0 until 9) { ca += map[g[oa][cp[j]]] * POW10[j]; cb += map[g[ob][cp[j]]] * POW10[j] }
                    val r1 = minOf(ca, cb); val r2 = maxOf(ca, cb)
                    if (r1 > best[1]) continue
                    var less = r1 < best[1]
                    if (!less) { if (r2 > best[2]) continue; less = r2 < best[2] }
                    for (r in 0 until 9) {
                        if (r / 3 == b0) continue
                        var x = 0; for (j in 0 until 9) x += map[g[r][cp[j]]] * POW10[j]; code[r] = x
                    }
                    val bands = IntArray(2); var nb = 0
                    for (b in 0 until 3) if (b != b0) bands[nb++] = b
                    val br = Array(2) { IntArray(3) }; val bi = Array(2) { IntArray(3) }
                    for (q in 0 until 2) {
                        val idx = intArrayOf(bands[q] * 3, bands[q] * 3 + 1, bands[q] * 3 + 2)
                        // sort three row indices by their codes (codes of distinct rows are distinct)
                        for (x in 0 until 3) for (y in x + 1 until 3) if (code[idx[y]] < code[idx[x]]) { val tmp = idx[x]; idx[x] = idx[y]; idx[y] = tmp }
                        for (k in 0 until 3) { bi[q][k] = idx[k]; br[q][k] = code[idx[k]] }
                    }
                    val f = if (br[0][0] < br[1][0]) 0 else 1
                    cand[0] = 123456789; cand[1] = r1; cand[2] = r2
                    for (k in 0 until 3) { cand[3 + k] = br[f][k]; cand[6 + k] = br[1 - f][k] }
                    var cmp = if (less) -1 else 0
                    var k = 3
                    while (k < 9 && cmp == 0) { cmp = cand[k].compareTo(best[k]); k++ }
                    if (cmp > 0) continue
                    if (cmp < 0) { System.arraycopy(cand, 0, best, 0, 9); ties.clear() }
                    val tie = IntArray(11)
                    tie[0] = t; tie[1] = r0; tie[2] = if (ca <= cb) oa else ob; tie[3] = if (ca <= cb) ob else oa
                    for (q in 0 until 3) { tie[4 + q] = bi[f][q]; tie[7 + q] = bi[1 - f][q] }
                    tie[10] = pi
                    ties.add(tie)
                }
            }
        }
        var bestP: IntArray? = null
        for (tie in ties) {
            val t = tie[0]; val cp = COLPERMS[tie[10]]
            val mp = IntArray(10)
            for (j in 0 until 9) { val c = cp[j]; val r = tie[1]; mp[if (t == 0) sol[r * 9 + c] else sol[c * 9 + r]] = j + 1 }
            val img = IntArray(81)
            for (i in 0 until 9) for (j in 0 until 9) {
                val r = tie[1 + i]; val c = cp[j]
                img[i * 9 + j] = mp[if (t == 0) puzzle[r * 9 + c] else puzzle[c * 9 + r]]
            }
            if (bestP == null || lexLess(img, bestP)) bestP = img
        }
        return bestP!!
    }

    private fun lexLess(a: IntArray, b: IntArray): Boolean {
        for (i in a.indices) if (a[i] != b[i]) return a[i] < b[i]
        return false
    }
}
