package app.materialsudoku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisTest {
    private val wiki = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
    private val wikiSol = Solver.solve(wiki)!!

    @Test fun hiddenSingleInBox() {
        // Wikipedia puzzle, r4c4 (0-based): column 4 and row 4 … the box 4 = {5}: check with the engine's own wave
        val v = Geo.parse(wiki)
        val b = Board.of(v)!!
        val w = Grader.singlesWave(b).first { Technique.entries[it[2]] == Technique.HIDDEN_SINGLE_BOX }
        val r = Analysis.rate(v, w[0], w[1], wikiSol)
        assertEquals(Technique.HIDDEN_SINGLE_BOX, r.technique)
        assertEquals(10, r.effort)
        assertEquals(0, r.detour) // the easiest move on this board is a box hidden single too (or cheaper)
    }

    @Test fun wrongDigitIsNotForced() {
        val v = Geo.parse(wiki)
        val i = (0 until 81).first { v[it] == 0 }
        val wrong = (wikiSol[i] - '0') % 9 + 1
        val r = Analysis.rate(v, i, wrong, wikiSol)
        assertNull(r.technique); assertEquals(0, r.effort)
    }

    /** Positions reached in a solve where no single exists on the values-only board. */
    private fun stuckPositions(d: Difficulty, count: Int): List<Pair<IntArray, String>> {
        val out = ArrayList<Pair<IntArray, String>>()
        var seed = 1L
        while (out.size < count && seed < 400) {
            val p = Generator.generate(d, Length.NORMAL, seed++)
            val sol = Geo.parse(p.solution)
            val v = Geo.parse(p.givens)
            while (true) {
                val b = Board.of(v)!!
                if (b.solved()) break
                val wave = Grader.singlesWave(b)
                if (wave.isEmpty()) { out.add(v.copyOf() to p.solution); break }
                for (w in wave) v[w[0]] = sol[w[0]]
            }
        }
        return out
    }

    @Test fun placementThatNeedsAPairFirst() {
        var found: Rating? = null
        loop@ for ((v, sol) in stuckPositions(Difficulty.HARD, 40)) for (i in 0 until 81) {
            if (v[i] != 0) continue
            val r = Analysis.rate(v, i, sol[i] - '0', sol)
            if (r.technique == Technique.NAKED_PAIR || r.technique == Technique.HIDDEN_PAIR) { found = r; break@loop }
        }
        assertNotNull("no pair-justified placement in 40 stuck Hard positions", found)
        val r = found!!
        // a single plus at least the pair: more than any single alone
        assertTrue(r.effort >= Analysis.weight(r.technique!!) + Analysis.weight(Technique.FULL_HOUSE))
        assertNotNull(r.easiest)
        assertTrue(r.detour >= 0)
    }

    @Test fun guessIsNotForced() {
        // AI Escargot is beyond the catalogue, so at its start some correct digits are forced by
        // nothing the app knows: placing one is rated as a guess. Its box hidden singles are not.
        val esc = "100007090030020008009600500005300900010080002600004000300000010040000007007000300"
        val sol = Solver.solve(esc)!!
        val v = Geo.parse(esc)
        val rated = (0 until 81).filter { v[it] == 0 }.map { Analysis.rate(v, it, sol[it] - '0', sol) }
        val guess = rated.firstOrNull { it.technique == null }
        assertNotNull(guess)
        assertEquals(0, guess!!.effort)
        assertEquals(0, guess.detour)
        assertTrue(rated.any { it.technique != null }) // the opening still has forced placements
        assertNotNull(guess.easiest)
    }

    @Test fun deterministic() {
        val (v, sol) = stuckPositions(Difficulty.MEDIUM, 1).first()
        val i = (0 until 81).first { v[it] == 0 }
        val a = Analysis.rate(v, i, sol[i] - '0', sol); val b = Analysis.rate(v, i, sol[i] - '0', sol)
        assertEquals(a.toString(), b.toString())
    }
}
