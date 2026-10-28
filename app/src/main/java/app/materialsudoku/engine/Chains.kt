package app.materialsudoku.engine

import app.materialsudoku.engine.Geo.CELL_UNITS
import app.materialsudoku.engine.Geo.PEERS
import app.materialsudoku.engine.Geo.sees

/**
 * Alternating inference chains over candidates (cell, digit).
 *
 * A **strong** link A=B means "not A ⇒ B": a bivalue cell's two digits, or a digit's only two
 * places in a unit. A **weak** link A−B means "A ⇒ not B": two digits of one cell, or one digit in
 * two cells that see each other. A chain that starts and ends with a strong link proves that at
 * least one of its two ends is true, which yields:
 *
 * - ends with the same digit in different cells: that digit leaves every cell seeing both ends;
 * - ends in the same cell: every other digit leaves that cell;
 * - ends with different digits in cells that see each other: each end's digit leaves the other cell.
 *
 * The finder returns the **shortest** productive chain (fewest nodes; ties by lowest start, then
 * lowest end node), found by breadth-first search from every start in node order. Paths that
 * revisit a node are discarded. Chains have 4 to [MAX_NODES] nodes; shorter ones are pairs and
 * intersections the cheaper techniques already cover.
 */
internal object Chains {
    enum class Kind { X, XY, AIC }

    private const val MAX_NODES = 16

    private fun node(cell: Int, d: Int) = cell * 9 + d - 1
    private fun cellOf(n: Int) = n / 9
    private fun digitOf(n: Int) = n % 9 + 1

    fun find(b: Board, kind: Kind): Step? {
        val strong = Array(729) { IntArray(0) }
        val weak = Array(729) { IntArray(0) }
        val present = BooleanArray(729)
        for (c in 0 until 81) for (d in Geo.digits(b.c[c])) present[node(c, d)] = true
        for (n in 0 until 729) {
            if (!present[n]) continue
            val c = cellOf(n); val d = digitOf(n)
            val s = ArrayList<Int>(); val w = ArrayList<Int>()
            if (kind != Kind.X && Integer.bitCount(b.c[c]) == 2) {
                s.add(node(c, Integer.numberOfTrailingZeros(b.c[c] and (1 shl d).inv())))
            }
            if (kind != Kind.XY) for (u in CELL_UNITS[c]) {
                val cells = b.cellsWith(u, d)
                if (cells.size == 2) { val o = if (cells[0] == c) cells[1] else cells[0]; val m = node(o, d); if (m !in s) s.add(m) }
            }
            if (kind == Kind.AIC) for (e in Geo.digits(b.c[c])) if (e != d) w.add(node(c, e))
            for (p in PEERS[c]) if (b.c[p] and (1 shl d) != 0) w.add(node(p, d))
            s.sort(); w.sort()
            strong[n] = s.toIntArray(); weak[n] = w.toIntArray()
        }
        if (kind == Kind.XY) {
            // XY-chains run only through bivalue cells
            for (n in 0 until 729) if (present[n] && Integer.bitCount(b.c[cellOf(n)]) != 2) { strong[n] = IntArray(0); weak[n] = IntArray(0) }
            for (n in 0 until 729) weak[n] = weak[n].filter { Integer.bitCount(b.c[cellOf(it)]) == 2 }.toIntArray()
        }

        var best: Step? = null
        var bestLen = Int.MAX_VALUE
        val dist = IntArray(729 * 2)
        val parent = IntArray(729 * 2)
        val queue = IntArray(729 * 2)
        for (start in 0 until 729) {
            if (!present[start] || strong[start].isEmpty()) continue
            dist.fill(-1)
            // state = node * 2 + p, where p = 1 means "arrived by a strong link" (the next link is weak)
            var head = 0; var tail = 0
            val s0 = start * 2 + 0
            dist[s0] = 0; parent[s0] = -1; queue[tail++] = s0
            while (head < tail) {
                val st = queue[head++]
                val n = st / 2; val arrivedStrong = st % 2 == 1
                val len = dist[st] + 1 // nodes in the path so far
                if (len >= minOf(MAX_NODES, bestLen)) continue
                val next = if (arrivedStrong) weak[n] else strong[n]
                val p2 = if (arrivedStrong) 0 else 1
                for (m in next) {
                    val ns = m * 2 + p2
                    if (dist[ns] >= 0 || m == start) continue
                    dist[ns] = dist[st] + 1; parent[ns] = st; queue[tail++] = ns
                    if (p2 == 1 && dist[ns] + 1 >= 4) {
                        val nodes = dist[ns] + 1
                        if (nodes >= bestLen) continue
                        val path = path(ns, parent) ?: continue
                        val elim = conclude(b, start, m, path)
                        if (elim.isNotEmpty()) {
                            bestLen = nodes
                            val tech = when (kind) { Kind.X -> Technique.X_CHAIN; Kind.XY -> Technique.XY_CHAIN; Kind.AIC -> Technique.AIC }
                            best = Step(tech, emptyList(), elim, path.map { cellOf(it) }.distinct(), null,
                                path.map { digitOf(it) }.distinct().sorted())
                        }
                    }
                }
            }
        }
        return best
    }

    /** Nodes from start to the end of state [s], or null if a node repeats. */
    private fun path(s: Int, parent: IntArray): List<Int>? {
        val out = ArrayList<Int>()
        var x = s
        while (x >= 0) { out.add(x / 2); x = parent[x] }
        out.reverse()
        if (out.toSet().size != out.size) return null
        return out
    }

    private fun conclude(b: Board, a: Int, z: Int, chain: List<Int>): List<Pair<Int, Int>> {
        val ca = cellOf(a); val da = digitOf(a); val cz = cellOf(z); val dz = digitOf(z)
        val out = ArrayList<Pair<Int, Int>>()
        when {
            ca == cz -> for (e in Geo.digits(b.c[ca])) if (e != da && e != dz) out.add(ca to e)
            da == dz -> for (j in 0 until 81) {
                if (j == ca || j == cz || b.c[j] and (1 shl da) == 0) continue
                if (sees(j, ca) && sees(j, cz)) out.add(j to da)
            }
            sees(ca, cz) -> {
                if (b.c[ca] and (1 shl dz) != 0) out.add(ca to dz)
                if (b.c[cz] and (1 shl da) != 0) out.add(cz to da)
            }
        }
        return out
    }
}
