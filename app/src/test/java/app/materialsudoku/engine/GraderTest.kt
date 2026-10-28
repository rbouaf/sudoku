package app.materialsudoku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraderTest {
    @Test fun wikipediaPuzzleIsEasy() {
        // The most-printed sudoku (Wikipedia; public domain), 07-SPECIAL-PUZZLES §1.1
        val g = Grader.grade("530070000600195000098000060800060003400803001700020006060000280000419005000080079")
        assertEquals(Difficulty.EASY, g.difficulty)
        assertTrue(g.complete)
        assertEquals(30, g.clues)
    }

    @Test fun mockupPuzzleIsSinglesOnly() {
        // The mockups label it level 2; it is really singles (noted in the session summary).
        assertEquals(Difficulty.EASY, Grader.grade("000390002500040091000000470000400030000032000059060700000000003640007800100000900").difficulty)
    }

    @Test fun aiEscargotStalls() {
        val g = Grader.grade("100007090030020008009600500005300900010080002600004000300000010040000007007000300")
        assertFalse(g.complete)
        assertEquals(Difficulty.IMPOSSIBLE, g.difficulty)
    }

    @Test fun gradeIsInvariantUnderRelabelling() {
        // Swapping digit names cannot change what a puzzle needs.
        val p = Generator.generate(Difficulty.HARD, Length.NORMAL, 11).givens
        val swapped = p.map { if (it == '0') it else ('0' + (10 - (it - '0'))) }.joinToString("")
        val a = Grader.grade(p); val b = Grader.grade(swapped)
        assertEquals(a.difficulty, b.difficulty)
        assertEquals(a.hardest, b.hardest)
    }
}
