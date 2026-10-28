package app.materialsudoku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SolverTest {
    // The mockups' shared position (sudoku-mockups/html/_sudoku.js), verified there.
    private val givens = "000390002500040091000000470000400030000032000059060700000000003640007800100000900"
    private val solution = "471398652582746391936215478218479536764532189359861724897154263643927815125683947"

    @Test fun uniqueAndSolved() {
        assertEquals(1, Solver.countSolutions(givens))
        assertEquals(solution, Solver.solve(givens))
    }

    @Test fun emptyGridHasMany() = assertEquals(2, Solver.countSolutions("0".repeat(81)))

    @Test fun contradictionHasNone() {
        assertEquals(0, Solver.countSolutions("11" + "0".repeat(79)))
        assertNull(Solver.solve("11" + "0".repeat(79)))
    }

    @Test fun deadlyRectangleHasTwo() {
        // Empty a rectangle {a,b}/{b,a} spanning two boxes in the solved grid: both fillings work.
        val g = Geo.parse(solution)
        var found = false
        loop@ for (r1 in 0 until 9) for (r2 in r1 + 1 until 9) for (c1 in 0 until 9) for (c2 in c1 + 1 until 9) {
            if ((r1 / 3 == r2 / 3) == (c1 / 3 == c2 / 3)) continue
            if (g[r1 * 9 + c1] != g[r2 * 9 + c2] || g[r1 * 9 + c2] != g[r2 * 9 + c1]) continue
            val h = g.copyOf(); for (i in intArrayOf(r1 * 9 + c1, r1 * 9 + c2, r2 * 9 + c1, r2 * 9 + c2)) h[i] = 0
            assertEquals(2, Solver.countSolutions(Geo.format(h), 5))
            found = true; break@loop
        }
        assert(found)
    }

    @Test fun randomSolutionIsValid() {
        val g = Solver.randomSolution(SplitMix64(1L))
        for (u in Geo.UNITS) assert(u.fold(0) { m, i -> m or (1 shl g[i]) } == 0x3FE)
    }

    @Test fun aiEscargotIsUnique() {
        // AI Escargot (Inkala 2006), checked in 07-SPECIAL-PUZZLES.md
        assertEquals(1, Solver.countSolutions("100007090030020008009600500005300900010080002600004000300000010040000007007000300"))
    }
}
