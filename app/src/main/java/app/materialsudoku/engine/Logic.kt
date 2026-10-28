package app.materialsudoku.engine

import app.materialsudoku.engine.Geo.ALL
import app.materialsudoku.engine.Geo.CELL_UNITS
import app.materialsudoku.engine.Geo.PEERS
import app.materialsudoku.engine.Geo.UNITS
import app.materialsudoku.engine.Geo.box
import app.materialsudoku.engine.Geo.col
import app.materialsudoku.engine.Geo.row
import app.materialsudoku.engine.Geo.sees

/** A position being reasoned about: values (0 = empty) and candidate masks (0 for filled cells). */
internal class Board(val v: IntArray, val c: IntArray) {

    fun copy() = Board(v.copyOf(), c.copyOf())

    fun solved(): Boolean { for (x in v) if (x == 0) return false; return true }

    fun empties(): Int { var n = 0; for (x in v) if (x == 0) n++; return n }

    /** Places a digit and removes it from the peers' candidates. */
    fun place(i: Int, d: Int) { Solver.assign(v, c, i, d) }

    fun apply(s: Step) {
        for ((i, d) in s.eliminations) c[i] = c[i] and (1 shl d).inv()
        for ((i, d) in s.placements) if (v[i] == 0) place(i, d)
    }

    /** True when some empty cell has no candidates or a unit cannot hold some digit. */
    fun broken(): Boolean {
        for (i in 0 until 81) if (v[i] == 0 && c[i] == 0) return true
        for (u in UNITS) {
            var have = 0
            for (i in u) have = have or (if (v[i] != 0) 1 shl v[i] else c[i])
            if (have != ALL) return true
        }
        return false
    }

    /** Empty cells of unit [u] holding candidate [d], as a list (reading order). */
    fun cellsWith(u: Int, d: Int): IntArray {
        val bit = 1 shl d
        var n = 0
        for (i in UNITS[u]) if (c[i] and bit != 0) n++
        val out = IntArray(n); var k = 0
        for (i in UNITS[u]) if (c[i] and bit != 0) out[k++] = i
        return out
    }

    fun countIn(u: Int, d: Int): Int {
        val bit = 1 shl d; var n = 0
        for (i in UNITS[u]) if (c[i] and bit != 0) n++
        return n
    }

    fun placedIn(u: Int): Int { var m = 0; for (i in UNITS[u]) if (v[i] != 0) m = m or (1 shl v[i]); return m }

    companion object {
        fun of(givens: IntArray): Board? {
            val v = IntArray(81); val c = IntArray(81)
            return if (Solver.init(givens, v, c)) Board(v, c) else null
        }
    }
}

/**
 * The step finders. Each one scans in a fixed order and returns the first instance that changes
 * something, so a step is a pure function of the position. Every rule here is sound on any
 * candidate set that still contains the solution (a "sound superset"), which is what lets [Hints]
 * reason on the player's own notes.
 */
internal object Logic {

    fun find(b: Board, t: Technique): Step? = when (t) {
        Technique.FULL_HOUSE -> fullHouse(b)
        Technique.HIDDEN_SINGLE_BOX -> hiddenSingle(b, 18, 27, Technique.HIDDEN_SINGLE_BOX)
        Technique.HIDDEN_SINGLE_LINE -> hiddenSingle(b, 0, 18, Technique.HIDDEN_SINGLE_LINE)
        Technique.NAKED_SINGLE -> nakedSingle(b)
        Technique.POINTING -> pointing(b)
        Technique.CLAIMING -> claiming(b)
        Technique.NAKED_PAIR -> nakedSubset(b, 2, t)
        Technique.HIDDEN_PAIR -> hiddenSubset(b, 2, t)
        Technique.NAKED_TRIPLE -> nakedSubset(b, 3, t)
        Technique.HIDDEN_TRIPLE -> hiddenSubset(b, 3, t)
        Technique.NAKED_QUAD -> nakedSubset(b, 4, t)
        Technique.HIDDEN_QUAD -> hiddenSubset(b, 4, t)
        Technique.X_WING -> fish(b, 2, t)
        Technique.SKYSCRAPER -> skyscraper(b)
        Technique.TWO_STRING_KITE -> twoStringKite(b)
        Technique.EMPTY_RECTANGLE -> emptyRectangle(b)
        Technique.SWORDFISH -> fish(b, 3, t)
        Technique.JELLYFISH -> fish(b, 4, t)
        Technique.XY_WING -> xyWing(b)
        Technique.XYZ_WING -> xyzWing(b)
        Technique.W_WING -> wWing(b)
        Technique.UNIQUE_RECTANGLE_1 -> uniqueRectangle1(b)
        Technique.BUG_PLUS_1 -> bugPlus1(b)
        Technique.X_CHAIN -> Chains.find(b, Chains.Kind.X)
        Technique.XY_CHAIN -> Chains.find(b, Chains.Kind.XY)
        Technique.AIC -> Chains.find(b, Chains.Kind.AIC)
    }

    /** The cheapest step, trying techniques in [Technique] order, up to and including [max]. */
    fun cheapest(b: Board, max: Difficulty = Difficulty.IMPOSSIBLE): Step? {
        for (t in Technique.entries) {
            if (t.difficulty > max) break
            find(b, t)?.let { return it }
        }
        return null
    }

    // ------------------------------------------------------------------------------- singles

    private fun fullHouse(b: Board): Step? {
        for (u in 0 until 27) {
            var e = -1; var n = 0
            for (i in UNITS[u]) if (b.v[i] == 0) { e = i; n++ }
            if (n != 1) continue
            val missing = ALL and b.placedIn(u).inv()
            if (Integer.bitCount(missing) != 1 || b.c[e] and missing == 0) continue
            val d = Integer.numberOfTrailingZeros(missing)
            return Step(Technique.FULL_HOUSE, listOf(e to d), emptyList(), listOf(e), u, listOf(d))
        }
        return null
    }

    private fun hiddenSingle(b: Board, from: Int, to: Int, t: Technique): Step? {
        for (u in from until to) {
            val placed = b.placedIn(u)
            for (d in 1..9) {
                if (placed and (1 shl d) != 0) continue
                val cells = b.cellsWith(u, d)
                if (cells.size == 1) return Step(t, listOf(cells[0] to d), emptyList(), listOf(cells[0]), u, listOf(d))
            }
        }
        return null
    }

    private fun nakedSingle(b: Board): Step? {
        for (i in 0 until 81) {
            if (b.v[i] == 0 && Integer.bitCount(b.c[i]) == 1) {
                val d = Integer.numberOfTrailingZeros(b.c[i])
                return Step(Technique.NAKED_SINGLE, listOf(i to d), emptyList(), listOf(i), null, listOf(d))
            }
        }
        return null
    }

    // ------------------------------------------------------------------------------- intersections

    /** A box's candidates for d all lie on one line: d leaves the rest of that line. */
    private fun pointing(b: Board): Step? {
        for (u in 18 until 27) for (d in 1..9) {
            val cells = b.cellsWith(u, d)
            if (cells.size < 2) continue
            for (lineOf in 0..1) {
                val line = if (lineOf == 0) row(cells[0]) else 9 + col(cells[0])
                if (cells.any { (if (lineOf == 0) row(it) else 9 + col(it)) != line }) continue
                val elim = ArrayList<Pair<Int, Int>>()
                for (j in UNITS[line]) if (box(j) != u - 18 && b.c[j] and (1 shl d) != 0) elim.add(j to d)
                if (elim.isNotEmpty()) return Step(Technique.POINTING, emptyList(), elim, cells.toList(), u, listOf(d))
            }
        }
        return null
    }

    /** A line's candidates for d all lie in one box: d leaves the rest of that box. */
    private fun claiming(b: Board): Step? {
        for (u in 0 until 18) for (d in 1..9) {
            val cells = b.cellsWith(u, d)
            if (cells.size < 2) continue
            val bx = box(cells[0])
            if (cells.any { box(it) != bx }) continue
            val elim = ArrayList<Pair<Int, Int>>()
            for (j in UNITS[18 + bx]) if (b.c[j] and (1 shl d) != 0 && j !in cells) elim.add(j to d)
            if (elim.isNotEmpty()) return Step(Technique.CLAIMING, emptyList(), elim, cells.toList(), u, listOf(d))
        }
        return null
    }

    // ------------------------------------------------------------------------------- subsets

    /** n cells of a unit whose candidates together are exactly n digits: those digits leave the unit's other cells. */
    private fun nakedSubset(b: Board, n: Int, t: Technique): Step? {
        for (u in 0 until 27) {
            val e = UNITS[u].filter { b.v[it] == 0 }
            if (e.size <= n) continue
            val idx = IntArray(n)
            val found = combos(e.size, n, idx) {
                var union = 0
                for (k in idx) union = union or b.c[e[k]]
                if (Integer.bitCount(union) != n) return@combos null
                val chosen = idx.map { e[it] }
                val elim = ArrayList<Pair<Int, Int>>()
                for (j in e) if (j !in chosen) for (d in Geo.digits(b.c[j] and union)) elim.add(j to d)
                if (elim.isEmpty()) null else Step(t, emptyList(), elim, chosen, u, Geo.digits(union).toList())
            }
            if (found != null) return found
        }
        return null
    }

    /** n digits of a unit confined to the same n cells: every other digit leaves those cells. */
    private fun hiddenSubset(b: Board, n: Int, t: Technique): Step? {
        for (u in 0 until 27) {
            val placed = b.placedIn(u)
            val free = (1..9).filter { placed and (1 shl it) == 0 }
            if (free.size <= n) continue
            val idx = IntArray(n)
            val found = combos(free.size, n, idx) {
                var digitMask = 0
                for (k in idx) digitMask = digitMask or (1 shl free[k])
                val cells = UNITS[u].filter { b.c[it] and digitMask != 0 }
                if (cells.size != n) return@combos null
                // every chosen digit must actually appear, or the "subset" is a contradiction, not a pattern
                for (k in idx) if (cells.none { b.c[it] and (1 shl free[k]) != 0 }) return@combos null
                val elim = ArrayList<Pair<Int, Int>>()
                for (j in cells) for (d in Geo.digits(b.c[j] and digitMask.inv())) elim.add(j to d)
                if (elim.isEmpty()) null else Step(t, emptyList(), elim, cells, u, Geo.digits(digitMask).toList())
            }
            if (found != null) return found
        }
        return null
    }

    // ------------------------------------------------------------------------------- one-digit patterns

    /**
     * Basic fish of size n (X-Wing 2, Swordfish 3, Jellyfish 4): n base lines whose candidates for d
     * fall in exactly n cover lines; d leaves the cover lines outside the base. Rows as base first.
     */
    private fun fish(b: Board, n: Int, t: Technique): Step? {
        for (d in 1..9) for (orient in 0..1) {
            // pos[line] = bitmask of positions (0–8) along the line holding d
            val lines = ArrayList<Int>(); val pos = ArrayList<Int>()
            for (l in 0 until 9) {
                val u = orient * 9 + l
                var m = 0
                for (k in 0 until 9) if (b.c[UNITS[u][k]] and (1 shl d) != 0) m = m or (1 shl k)
                val pc = Integer.bitCount(m)
                if (pc in 2..n) { lines.add(l); pos.add(m) }
            }
            if (lines.size < n) continue
            val idx = IntArray(n)
            val found = combos(lines.size, n, idx) {
                var cover = 0
                for (k in idx) cover = cover or pos[k]
                if (Integer.bitCount(cover) != n) return@combos null
                val base = idx.map { lines[it] }
                val elim = ArrayList<Pair<Int, Int>>()
                val focus = ArrayList<Int>()
                for (cl in 0 until 9) if (cover and (1 shl cl) != 0) {
                    val cu = (1 - orient) * 9 + cl
                    for (k in 0 until 9) {
                        val j = UNITS[cu][k]
                        if (b.c[j] and (1 shl d) == 0) continue
                        if (k in base) focus.add(j) else elim.add(j to d)
                    }
                }
                if (elim.isEmpty()) null else Step(t, emptyList(), elim.sortedBy { it.first }, focus.sorted(), null, listOf(d))
            }
            if (found != null) return found
        }
        return null
    }

    /** Cells holding d that see both [a] and [z] (excluding the given cells). */
    private fun seeBoth(b: Board, d: Int, a: Int, z: Int, exclude: IntArray): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (j in 0 until 81) {
            if (b.c[j] and (1 shl d) == 0 || j in exclude) continue
            if (sees(j, a) && sees(j, z)) out.add(j to d)
        }
        return out
    }

    /**
     * Two lines of the same orientation each holding d exactly twice, sharing one cross line (the
     * base) and not the other (the tops): one top is d, so d leaves every cell seeing both tops.
     */
    private fun skyscraper(b: Board): Step? {
        for (d in 1..9) for (orient in 0..1) {
            val pairs = ArrayList<IntArray>()
            for (l in 0 until 9) { val cs = b.cellsWith(orient * 9 + l, d); if (cs.size == 2) pairs.add(cs) }
            for (x in 0 until pairs.size) for (y in x + 1 until pairs.size) {
                val p = pairs[x]; val q = pairs[y]
                val cross = { i: Int -> if (orient == 0) col(i) else row(i) }
                for (pi in 0..1) for (qi in 0..1) {
                    if (cross(p[pi]) != cross(q[qi])) continue           // bases share a cross line
                    val t1 = p[1 - pi]; val t2 = q[1 - qi]
                    if (cross(t1) == cross(t2)) continue                   // that would be an X-Wing
                    val elim = seeBoth(b, d, t1, t2, intArrayOf(p[0], p[1], q[0], q[1]))
                    if (elim.isNotEmpty()) return Step(Technique.SKYSCRAPER, emptyList(), elim, listOf(p[0], p[1], q[0], q[1]).sorted(), null, listOf(d))
                }
            }
        }
        return null
    }

    /**
     * A row and a column each holding d exactly twice, with one end of each in the same box: one
     * of the far ends is d, so d leaves every cell seeing both.
     */
    private fun twoStringKite(b: Board): Step? {
        for (d in 1..9) for (r in 0 until 9) {
            val rp = b.cellsWith(r, d)
            if (rp.size != 2 || box(rp[0]) == box(rp[1])) continue
            for (c in 0 until 9) {
                val cp = b.cellsWith(9 + c, d)
                if (cp.size != 2 || box(cp[0]) == box(cp[1])) continue
                if (rp.any { it in cp }) continue
                for (ri in 0..1) for (ci in 0..1) {
                    if (box(rp[ri]) != box(cp[ci])) continue
                    val far1 = rp[1 - ri]; val far2 = cp[1 - ci]
                    val elim = seeBoth(b, d, far1, far2, intArrayOf(rp[0], rp[1], cp[0], cp[1]))
                    if (elim.isNotEmpty()) return Step(Technique.TWO_STRING_KITE, emptyList(), elim, listOf(rp[0], rp[1], cp[0], cp[1]).sorted(), 18 + box(rp[ri]), listOf(d))
                }
            }
        }
        return null
    }

    /**
     * A box whose candidates for d lie on one row R and one column C of it (with some off the
     * crossing on each), plus a conjugate pair on d outside the box with one end on R (or C):
     * d leaves the cell where the pair's other end meets C (or R).
     */
    private fun emptyRectangle(b: Board): Step? {
        for (d in 1..9) for (bx in 0 until 9) {
            val cells = b.cellsWith(18 + bx, d)
            if (cells.size < 2) continue
            val band = bx / 3; val stack = bx % 3
            for (R in band * 3 until band * 3 + 3) for (C in stack * 3 until stack * 3 + 3) {
                if (cells.any { row(it) != R && col(it) != C }) continue
                if (cells.none { row(it) == R && col(it) != C }) continue
                if (cells.none { col(it) == C && row(it) != R }) continue
                // (a) conjugate pair in a column X outside the stack, one end on row R
                for (X in 0 until 9) {
                    if (X / 3 == stack) continue
                    val cp = b.cellsWith(9 + X, d)
                    if (cp.size != 2) continue
                    for (k in 0..1) {
                        if (row(cp[k]) != R) continue
                        val other = cp[1 - k]
                        if (row(other) / 3 == band) continue
                        val target = row(other) * 9 + C
                        if (b.c[target] and (1 shl d) != 0) {
                            return Step(Technique.EMPTY_RECTANGLE, emptyList(), listOf(target to d), (cells.toList() + cp.toList()).sorted(), 18 + bx, listOf(d))
                        }
                    }
                }
                // (b) conjugate pair in a row Y outside the band, one end on column C
                for (Y in 0 until 9) {
                    if (Y / 3 == band) continue
                    val rp = b.cellsWith(Y, d)
                    if (rp.size != 2) continue
                    for (k in 0..1) {
                        if (col(rp[k]) != C) continue
                        val other = rp[1 - k]
                        if (col(other) / 3 == stack) continue
                        val target = R * 9 + col(other)
                        if (b.c[target] and (1 shl d) != 0) {
                            return Step(Technique.EMPTY_RECTANGLE, emptyList(), listOf(target to d), (cells.toList() + rp.toList()).sorted(), 18 + bx, listOf(d))
                        }
                    }
                }
            }
        }
        return null
    }

    // ------------------------------------------------------------------------------- multi-digit patterns

    /** Pivot {x,y} seeing pincers {x,z} and {y,z}: z leaves every cell seeing both pincers. */
    private fun xyWing(b: Board): Step? {
        for (p in 0 until 81) {
            val pm = b.c[p]
            if (Integer.bitCount(pm) != 2) continue
            val (x, y) = Geo.digits(pm).let { it[0] to it[1] }
            for (a in PEERS[p]) {
                val am = b.c[a]
                if (Integer.bitCount(am) != 2 || am and (1 shl x) == 0 || am == pm) continue
                val z = Integer.numberOfTrailingZeros(am and (1 shl x).inv())
                val want = (1 shl y) or (1 shl z)
                for (c in PEERS[p]) {
                    if (c == a || b.c[c] != want) continue
                    val elim = seeBoth(b, z, a, c, intArrayOf(p, a, c))
                    if (elim.isNotEmpty()) return Step(Technique.XY_WING, emptyList(), elim, listOf(p, a, c), null, listOf(x, y, z).sorted())
                }
            }
        }
        return null
    }

    /** Pivot {x,y,z} seeing pincers {x,z} and {y,z}: z leaves every cell seeing all three. */
    private fun xyzWing(b: Board): Step? {
        for (p in 0 until 81) {
            val pm = b.c[p]
            if (Integer.bitCount(pm) != 3) continue
            for (a in PEERS[p]) {
                val am = b.c[a]
                if (Integer.bitCount(am) != 2 || am and pm != am) continue
                for (c in PEERS[p]) {
                    if (c <= a) continue
                    val cm = b.c[c]
                    if (Integer.bitCount(cm) != 2 || cm and pm != cm || cm == am) continue
                    val zm = am and cm
                    if (Integer.bitCount(zm) != 1) continue
                    val z = Integer.numberOfTrailingZeros(zm)
                    val elim = ArrayList<Pair<Int, Int>>()
                    for (j in 0 until 81) {
                        if (j == p || j == a || j == c || b.c[j] and zm == 0) continue
                        if (sees(j, p) && sees(j, a) && sees(j, c)) elim.add(j to z)
                    }
                    if (elim.isNotEmpty()) return Step(Technique.XYZ_WING, emptyList(), elim, listOf(p, a, c).sorted(), null, Geo.digits(pm).toList())
                }
            }
        }
        return null
    }

    /**
     * Two non-seeing cells {x,y} joined by a strong link on x (each end of the link sees one of
     * the cells): one of the two cells is y, so y leaves every cell seeing both.
     */
    private fun wWing(b: Board): Step? {
        for (a in 0 until 81) {
            val m = b.c[a]
            if (Integer.bitCount(m) != 2) continue
            for (z in a + 1 until 81) {
                if (b.c[z] != m || sees(a, z)) continue
                for (w in Geo.digits(m)) {
                    val other = Integer.numberOfTrailingZeros(m and (1 shl w).inv())
                    for (u in 0 until 27) {
                        val link = b.cellsWith(u, w)
                        if (link.size != 2 || a in link || z in link) continue
                        val ok = (sees(link[0], a) && sees(link[1], z)) || (sees(link[1], a) && sees(link[0], z))
                        if (!ok) continue
                        val elim = seeBoth(b, other, a, z, intArrayOf(a, z))
                        if (elim.isNotEmpty()) return Step(Technique.W_WING, emptyList(), elim, listOf(a, z, link[0], link[1]).sorted(), u, Geo.digits(m).toList())
                    }
                }
            }
        }
        return null
    }

    /**
     * Unique rectangle type 1: four empty cells in two rows, two columns and two boxes; three hold
     * exactly {a,b}. If the fourth were a or b too the puzzle would have two solutions, so a and b
     * leave it. Valid only because the puzzle is unique (decision B5: allowed).
     */
    private fun uniqueRectangle1(b: Board): Step? {
        for (r1 in 0 until 9) for (r2 in r1 + 1 until 9) for (c1 in 0 until 9) for (c2 in c1 + 1 until 9) {
            val sameBand = r1 / 3 == r2 / 3; val sameStack = c1 / 3 == c2 / 3
            if (sameBand == sameStack) continue                         // must span exactly two boxes
            val cs = intArrayOf(r1 * 9 + c1, r1 * 9 + c2, r2 * 9 + c1, r2 * 9 + c2)
            if (cs.any { b.v[it] != 0 }) continue
            for (k in 0 until 4) {
                val others = cs.filterIndexed { j, _ -> j != k }
                val m = b.c[others[0]]
                if (Integer.bitCount(m) != 2 || others.any { b.c[it] != m }) continue
                val f = cs[k]
                if (b.c[f] and m != m || b.c[f] == m) continue
                val elim = Geo.digits(m).map { f to it }
                return Step(Technique.UNIQUE_RECTANGLE_1, emptyList(), elim, cs.sorted(), null, Geo.digits(m).toList())
            }
        }
        return null
    }

    /**
     * Bivalue universal grave + 1: every empty cell has two candidates except one with three, and
     * every unit holds each candidate exactly twice once that cell's extra digit is ignored. The
     * extra digit is the one that appears three times in the cell's units, and it must go there.
     */
    private fun bugPlus1(b: Board): Step? {
        var tri = -1
        for (i in 0 until 81) {
            if (b.v[i] != 0) continue
            when (Integer.bitCount(b.c[i])) {
                2 -> {}
                3 -> { if (tri >= 0) return null; tri = i }
                else -> return null
            }
        }
        if (tri < 0) return null
        for (d in Geo.digits(b.c[tri])) {
            if (CELL_UNITS[tri].any { b.countIn(it, d) != 3 }) continue
            // ignoring d at tri, every unit must hold every candidate 0 or 2 times
            var ok = true
            loop@ for (u in 0 until 27) for (e in 1..9) {
                var n = b.countIn(u, e)
                if (e == d && tri in UNITS[u]) n--
                if (n != 0 && n != 2) { ok = false; break@loop }
            }
            if (ok) return Step(Technique.BUG_PLUS_1, listOf(tri to d), emptyList(), listOf(tri), null, listOf(d))
        }
        return null
    }

    // ------------------------------------------------------------------------------- util

    /** Enumerates n-combinations of 0 until size in lexicographic order; stops at the first non-null result. */
    private inline fun <T> combos(size: Int, n: Int, idx: IntArray, f: () -> T?): T? {
        for (k in 0 until n) idx[k] = k
        if (n > size) return null
        while (true) {
            f()?.let { return it }
            var k = n - 1
            while (k >= 0 && idx[k] == size - n + k) k--
            if (k < 0) return null
            idx[k]++
            for (j in k + 1 until n) idx[j] = idx[j - 1] + 1
        }
    }
}
