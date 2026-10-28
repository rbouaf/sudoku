package app.materialsudoku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalTest {
    private fun d(s: String) = s.replace('.', '0')

    @Test fun mathimagicsWorkedExample() {
        // Mathimagics, "Minlex Library (9x9)", enjoysudoku t38686, 2021-02-01 (example by JPF)
        val p = d(".3.89......5.....4.17....5.9......8.....76....5.12............7...5.38.2..1.6...5")
        assertEquals(d(".......89..67.........2.45.2....4.9.56....2.18........3....2.....2.48.......1.67."), Canonical.classId(p))
    }

    @Test fun sudokuWikiWorkedExample() {
        val p = d("3.9...4..2..7.9....87......75..6.23.6..9.4..8.28.5..41......59....1.6..7..6...1.4")
        assertEquals(d("...45.7.9..7.89.3......3.412..9..8..7..8..6.........17.8.....65.12.6...49.6...32."), Canonical.classId(p))
    }

    @Test fun invariantUnderRandomTransforms() {
        val p = Generator.generate(Difficulty.MEDIUM, Length.NORMAL, 3)
        val id = Canonical.classId(p.givens, p.solution)
        val rng = SplitMix64(77)
        repeat(40) {
            val t = Canonical.Transform.random(rng)
            val g = t.apply(p.givens); val s = t.apply(p.solution)
            assertEquals(s, Solver.solve(g))
            assertEquals(id, Canonical.classId(g, s))
        }
    }

    @Test fun famousRecognisedInDisguise() {
        val esc = Famous.all.first { it.name == "AI Escargot" }
        val sol = Solver.solve(esc.givens)!!
        val t = Canonical.Transform.random(SplitMix64(5))
        assertEquals("AI Escargot", Famous.recognise(t.apply(esc.givens), t.apply(sol))?.name)
        val p = Generator.generate(Difficulty.EASY, Length.NORMAL, 9)
        assertNull(Famous.recognise(p.givens, p.solution))
    }

    @Test fun everyFamousPuzzleIsUniqueAndDistinct() {
        for (e in Famous.all) assertEquals(e.name, 1, Solver.countSolutions(e.givens))
        assertEquals(Famous.all.size, Famous.all.map { Canonical.classId(it.givens) }.toSet().size)
    }

    @Test fun impossiblePoolIsTheHardestList() {
        val names = Famous.impossible.map { it.name }
        assertTrue("AI Escargot" in names && "Golden Nugget" in names)
        assertTrue("The Wikipedia sudoku" !in names)
        val p = Generator.generate(Difficulty.IMPOSSIBLE, Length.LONG, 4)
        assertNotNull(Famous.recognise(p.givens, p.solution))
        assertEquals(1, Solver.countSolutions(p.givens))
    }
}
