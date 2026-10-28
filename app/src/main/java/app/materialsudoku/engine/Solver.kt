package app.materialsudoku.engine

import app.materialsudoku.engine.Geo.ALL
import app.materialsudoku.engine.Geo.PEERS
import app.materialsudoku.engine.Geo.UNITS

/**
 * Brute-force solver: bitset candidates, naked- and hidden-single propagation, MRV branching.
 * It proves uniqueness and finds solutions; it is never used to *grade* (that is [Grader]).
 *
 * Budgets count search nodes, never time, so a budget hit is itself deterministic.
 */
object Solver {

    /** Number of solutions, capped at [limit]. Returns 0 for a contradictory grid. */
    fun countSolutions(givens: String, limit: Int = 2): Int = count(Geo.parse(givens), limit)

    /** The solution if there is exactly one, else null. */
    fun solve(givens: String): String? {
        val s = Search(Long.MAX_VALUE)
        val p = Geo.parse(givens)
        val r = s.run(p, 2, null)
        return if (r == 1) Geo.format(s.first!!) else null
    }

    internal fun count(p: IntArray, limit: Int, nodeBudget: Long = Long.MAX_VALUE): Int = Search(nodeBudget).run(p, limit, null)

    /** A uniformly-ish random complete grid, from randomised branching. */
    internal fun randomSolution(rng: SplitMix64): IntArray {
        val s = Search(Long.MAX_VALUE)
        s.run(IntArray(81), 1, rng)
        return s.first!!
    }

    /** Places [d] at [i] and removes it from every empty peer. False on contradiction. */
    internal fun assign(v: IntArray, c: IntArray, i: Int, d: Int): Boolean {
        v[i] = d; c[i] = 0
        val bit = 1 shl d
        for (p in PEERS[i]) {
            if (v[p] == 0) {
                if (c[p] and bit != 0) { c[p] = c[p] and bit.inv(); if (c[p] == 0) return false }
            } else if (v[p] == d) return false
        }
        return true
    }

    /** Values + basic candidates from a grid; null if two givens clash. */
    internal fun init(p: IntArray, v: IntArray, c: IntArray): Boolean {
        v.fill(0); c.fill(ALL)
        for (i in 0 until 81) if (p[i] != 0 && !assign(v, c, i, p[i])) return false
        return true
    }

    private class Search(val budget: Long) {
        var nodes = 0L
        var first: IntArray? = null

        fun run(p: IntArray, limit: Int, rng: SplitMix64?): Int {
            nodes = 0; first = null
            val v = IntArray(81); val c = IntArray(81)
            if (!init(p, v, c)) return 0
            return count(v, c, limit, rng)
        }

        private fun propagate(v: IntArray, c: IntArray): Boolean {
            var changed = true
            while (changed) {
                changed = false
                for (i in 0 until 81) if (v[i] == 0) {
                    val m = c[i]
                    if (m == 0) return false
                    if (m and (m - 1) == 0) { if (!assign(v, c, i, Integer.numberOfTrailingZeros(m))) return false; changed = true }
                }
                for (u in UNITS) {
                    var once = 0; var twice = 0; var placed = 0
                    for (i in u) {
                        if (v[i] != 0) { placed = placed or (1 shl v[i]); continue }
                        twice = twice or (once and c[i]); once = once or c[i]
                    }
                    if ((once or placed) != ALL) return false
                    var hs = once and twice.inv() and placed.inv()
                    while (hs != 0) {
                        val bit = hs and -hs; hs = hs and (hs - 1)
                        for (i in u) if (v[i] == 0 && c[i] and bit != 0) {
                            if (!assign(v, c, i, Integer.numberOfTrailingZeros(bit))) return false
                            changed = true; break
                        }
                    }
                }
            }
            return true
        }

        fun count(v: IntArray, c: IntArray, limit: Int, rng: SplitMix64?): Int {
            if (++nodes > budget) return -1
            if (!propagate(v, c)) return 0
            var best = -1; var bc = 99
            for (i in 0 until 81) if (v[i] == 0) {
                val pc = Integer.bitCount(c[i])
                if (pc < bc) { bc = pc; best = i; if (pc == 2) break }
            }
            if (best < 0) { if (first == null) first = v.copyOf(); return 1 }
            val order = Geo.digits(c[best])
            rng?.shuffle(order)
            var total = 0
            for (d in order) {
                val v2 = v.copyOf(); val c2 = c.copyOf()
                if (!assign(v2, c2, best, d)) continue
                val r = count(v2, c2, limit - total, rng)
                if (r < 0) return -1
                total += r
                if (total >= limit) return total
            }
            return total
        }
    }
}
