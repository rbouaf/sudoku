package app.materialsudoku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Each technique on a hand-built position whose answer is worked out in the comment. Positions
 * are candidate grids on an empty board: every cell starts with all nine candidates and the
 * pattern is carved out by removing candidates, so nothing but the pattern can fire.
 */
class TechniqueTest {

    private fun blank() = Board(IntArray(81), IntArray(81) { Geo.ALL })
    private fun rc(r: Int, c: Int) = r * 9 + c
    private fun Board.remove(d: Int, cells: Iterable<Int>) { for (i in cells) c[i] = c[i] and (1 shl d).inv() }
    private fun Board.set(i: Int, vararg ds: Int) { c[i] = ds.fold(0) { m, d -> m or (1 shl d) } }
    /** Keep d in unit u only at [keep]. */
    private fun Board.only(d: Int, u: Int, vararg keep: Int) = remove(d, Geo.UNITS[u].filter { it !in keep })
    private fun elims(s: Step?) = s!!.eliminations.map { it.first to it.second }.toSet()

    @Test fun fullHouse() {
        val b = Board.of(Geo.parse("123456780" + "0".repeat(72)))!!
        val s = Logic.find(b, Technique.FULL_HOUSE)!!
        assertEquals(listOf(8 to 9), s.placements)
        assertEquals(0, s.focusUnit)
    }

    @Test fun hiddenSingleInBox() {
        val b = blank()
        b.only(7, 18, rc(1, 1))
        val s = Logic.find(b, Technique.HIDDEN_SINGLE_BOX)!!
        assertEquals(listOf(rc(1, 1) to 7), s.placements)
        assertNull(Logic.find(b, Technique.HIDDEN_SINGLE_LINE)) // row 1 and col 1 still hold 7 elsewhere
    }

    @Test fun hiddenSingleInLine() {
        val b = blank()
        b.only(4, 9 + 6, rc(5, 6)) // column 6
        assertNull(Logic.find(b, Technique.HIDDEN_SINGLE_BOX))
        assertEquals(listOf(rc(5, 6) to 4), Logic.find(b, Technique.HIDDEN_SINGLE_LINE)!!.placements)
    }

    @Test fun nakedSingle() {
        val b = blank(); b.set(rc(4, 4), 3)
        assertEquals(listOf(rc(4, 4) to 3), Logic.find(b, Technique.NAKED_SINGLE)!!.placements)
    }

    @Test fun pointing() {
        // box 0 holds 9 only in row 0: 9 leaves row 0 outside the box
        val b = blank(); b.only(9, 18, rc(0, 0), rc(0, 1))
        val s = Logic.find(b, Technique.POINTING)
        assertEquals((2..8).map { rc(0, it) to 9 }.toSet().minus(setOf(rc(0, 2) to 9)), elims(s))
    }

    @Test fun claiming() {
        // row 0 holds 9 only inside box 0: 9 leaves the rest of box 0
        val b = blank(); b.only(9, 0, rc(0, 0), rc(0, 2))
        val s = Logic.find(b, Technique.CLAIMING)
        assertEquals((1..2).flatMap { r -> (0..2).map { rc(r, it) to 9 } }.toSet(), elims(s))
        assertNull(Logic.find(b, Technique.POINTING)) // box 0 still has 9 in rows 1–2
    }

    @Test fun nakedPair() {
        val b = blank(); b.set(rc(0, 0), 1, 2); b.set(rc(0, 5), 1, 2)
        val s = Logic.find(b, Technique.NAKED_PAIR)!!
        assertEquals(0, s.focusUnit)
        assertEquals((0..8).filter { it != 0 && it != 5 }.flatMap { listOf(rc(0, it) to 1, rc(0, it) to 2) }.toSet(), elims(s))
    }

    @Test fun hiddenPair() {
        val b = blank(); b.only(3, 0, rc(0, 2), rc(0, 7)); b.only(4, 0, rc(0, 2), rc(0, 7))
        val s = Logic.find(b, Technique.HIDDEN_PAIR)!!
        assertEquals(listOf(rc(0, 2), rc(0, 7)), s.focusCells)
        assertEquals(listOf(rc(0, 2), rc(0, 7)).flatMap { i -> (1..9).filter { it != 3 && it != 4 }.map { i to it } }.toSet(), elims(s))
    }

    @Test fun nakedTripleWithoutFullCells() {
        // {1,2} {2,3} {1,3}: a triple where no cell holds all three
        val b = blank(); b.set(rc(4, 0), 1, 2); b.set(rc(4, 4), 2, 3); b.set(rc(4, 8), 1, 3)
        assertNull(Logic.find(b, Technique.NAKED_PAIR))
        val s = Logic.find(b, Technique.NAKED_TRIPLE)!!
        assertEquals(listOf(rc(4, 0), rc(4, 4), rc(4, 8)), s.focusCells)
        assertEquals(18, s.eliminations.size) // 6 other cells × 3 digits
    }

    @Test fun hiddenTriple() {
        val b = blank()
        b.only(5, 9, rc(1, 0), rc(4, 0)); b.only(6, 9, rc(4, 0), rc(7, 0)); b.only(7, 9, rc(1, 0), rc(7, 0))
        assertNull(Logic.find(b, Technique.HIDDEN_PAIR))
        val s = Logic.find(b, Technique.HIDDEN_TRIPLE)!!
        assertEquals(9, s.focusUnit)
        assertEquals(18, s.eliminations.size) // 3 cells × 6 other digits
    }

    @Test fun nakedQuad() {
        val b = blank(); b.set(rc(8, 0), 1, 2); b.set(rc(8, 1), 2, 3); b.set(rc(8, 2), 3, 4); b.set(rc(8, 3), 4, 1)
        val s = Logic.find(b, Technique.NAKED_QUAD)!!
        assertEquals(8, s.focusUnit)
        assertEquals(20, s.eliminations.size) // 5 cells × 4 digits
    }

    @Test fun xWing() {
        // 5 in rows 1 and 5 only at columns 2 and 6: 5 leaves columns 2 and 6 elsewhere
        val b = blank(); b.only(5, 1, rc(1, 2), rc(1, 6)); b.only(5, 5, rc(5, 2), rc(5, 6))
        val s = Logic.find(b, Technique.X_WING)!!
        val want = (0..8).filter { it != 1 && it != 5 }.flatMap { listOf(rc(it, 2) to 5, rc(it, 6) to 5) }.toSet()
        assertEquals(want, elims(s))
        assertEquals(listOf(rc(1, 2), rc(1, 6), rc(5, 2), rc(5, 6)), s.focusCells)
    }

    @Test fun swordfish() {
        val b = blank()
        b.only(8, 0, rc(0, 1), rc(0, 4)); b.only(8, 4, rc(4, 4), rc(4, 7)); b.only(8, 8, rc(8, 1), rc(8, 7))
        assertNull(Logic.find(b, Technique.X_WING))
        val s = Logic.find(b, Technique.SWORDFISH)!!
        val want = (0..8).filter { it !in listOf(0, 4, 8) }.flatMap { r -> listOf(1, 4, 7).map { rc(r, it) to 8 } }.toSet()
        assertEquals(want, elims(s))
    }

    @Test fun jellyfish() {
        val b = blank()
        b.only(2, 0, rc(0, 0), rc(0, 3)); b.only(2, 2, rc(2, 3), rc(2, 6))
        b.only(2, 4, rc(4, 6), rc(4, 8)); b.only(2, 7, rc(7, 8), rc(7, 0))
        assertNull(Logic.find(b, Technique.X_WING)); assertNull(Logic.find(b, Technique.SWORDFISH))
        val s = Logic.find(b, Technique.JELLYFISH)!!
        assertEquals(5 * 4, s.eliminations.size)
    }

    @Test fun skyscraper() {
        // 3 in row 0 only at c1, c4; in row 6 only at c1, c5. Base column 1; tops r0c4 and r6c5.
        val b = blank(); b.only(3, 0, rc(0, 1), rc(0, 4)); b.only(3, 6, rc(6, 1), rc(6, 5))
        val s = Logic.find(b, Technique.SKYSCRAPER)
        assertEquals(setOf(rc(1, 5) to 3, rc(2, 5) to 3, rc(7, 4) to 3, rc(8, 4) to 3), elims(s))
    }

    @Test fun twoStringKite() {
        // 7: row 1 only at c1, c6; column 0 only at r2, r7. r1c1 and r2c0 share box 0.
        // Far ends r1c6 and r7c0: r7c6 sees both.
        val b = blank(); b.only(7, 1, rc(1, 1), rc(1, 6)); b.only(7, 9, rc(2, 0), rc(7, 0))
        val s = Logic.find(b, Technique.TWO_STRING_KITE)
        assertEquals(setOf(rc(7, 6) to 7), elims(s))
    }

    @Test fun emptyRectangle() {
        // 4 in box 0 only on row 0 and column 1 (r0c0 r0c2 r1c1 r2c1); conjugate pair on 4 in
        // column 5 at r0 and r6. Either r0c5 is 4 (then box 0's 4 is in column 1) or r6c5 is:
        // both kill 4 at r6c1.
        val b = blank()
        b.only(4, 18, rc(0, 0), rc(0, 2), rc(1, 1), rc(2, 1))
        b.only(4, 9 + 5, rc(0, 5), rc(6, 5))
        val s = Logic.find(b, Technique.EMPTY_RECTANGLE)
        assertEquals(setOf(rc(6, 1) to 4), elims(s))
    }

    @Test fun xyWing() {
        val b = blank(); b.set(rc(4, 4), 1, 2); b.set(rc(4, 0), 1, 3); b.set(rc(0, 4), 2, 3)
        val s = Logic.find(b, Technique.XY_WING)
        assertEquals(setOf(rc(0, 0) to 3), elims(s))
    }

    @Test fun xyzWing() {
        val b = blank(); b.set(rc(4, 4), 1, 2, 3); b.set(rc(4, 3), 1, 3); b.set(rc(3, 4), 2, 3)
        val s = Logic.find(b, Technique.XYZ_WING)
        // cells seeing all three are the rest of box 4
        val want = listOf(rc(3, 3), rc(3, 5), rc(4, 5), rc(5, 3), rc(5, 4), rc(5, 5)).map { it to 3 }.toSet()
        assertEquals(want, elims(s))
    }

    @Test fun wWing() {
        // r0c0 and r4c8 are {5,6} and don't see each other; row 8 holds 5 only at c0 (sees r0c0)
        // and c8 (sees r4c8). One of the two cells must be 6: 6 leaves r0c8 and r4c0.
        val b = blank(); b.set(rc(0, 0), 5, 6); b.set(rc(4, 8), 5, 6); b.only(5, 8, rc(8, 0), rc(8, 8))
        val s = Logic.find(b, Technique.W_WING)
        assertEquals(setOf(rc(0, 8) to 6, rc(4, 0) to 6), elims(s))
    }

    @Test fun uniqueRectangle1() {
        val b = blank()
        b.set(rc(0, 0), 1, 2); b.set(rc(0, 4), 1, 2); b.set(rc(1, 0), 1, 2); b.set(rc(1, 4), 1, 2, 7)
        val s = Logic.find(b, Technique.UNIQUE_RECTANGLE_1)
        assertEquals(setOf(rc(1, 4) to 1, rc(1, 4) to 2), elims(s))
    }

    @Test fun uniqueRectangleNeedsExactlyTwoBoxes() {
        val b = blank() // four boxes: not a deadly pattern
        b.set(rc(0, 0), 1, 2); b.set(rc(0, 4), 1, 2); b.set(rc(4, 0), 1, 2); b.set(rc(4, 4), 1, 2, 7)
        assertNull(Logic.find(b, Technique.UNIQUE_RECTANGLE_1))
    }

    @Test fun bugPlusOne() {
        // Candidates {S[i], S[i] mod 9 + 1} on a solved grid S put every digit exactly twice in
        // every unit (a BUG). Adding a third digit to one cell makes it BUG+1: that digit goes there.
        val s = Geo.parse("471398652582746391936215478218479536764532189359861724897154263643927815125683947")
        val b = Board(IntArray(81), IntArray(81) { (1 shl s[it]) or (1 shl (s[it] % 9 + 1)) })
        val t = rc(4, 4) // S = 3, so {3,4}; add 9
        b.c[t] = b.c[t] or (1 shl 9)
        val step = Logic.find(b, Technique.BUG_PLUS_1)
        assertNotNull(step)
        assertEquals(listOf(t to 9), step!!.placements)
    }

    @Test fun cheapestRespectsDifficultyOrder() {
        // An X-Wing and a naked triple both present: Medium comes before Hard.
        val b = blank()
        b.only(5, 1, rc(1, 2), rc(1, 6)); b.only(5, 5, rc(5, 2), rc(5, 6))
        b.set(rc(8, 0), 1, 2); b.set(rc(8, 4), 2, 3); b.set(rc(8, 8), 1, 3)
        assertEquals(Technique.NAKED_TRIPLE, Logic.cheapest(b)!!.technique)
    }

    @Test fun xChain() {
        // 6 is a conjugate pair in rows 0, 3 and 6: r0c0=r0c4 − r3c4=r3c8 − r6c8=r6c1.
        // One of r0c0 and r6c1 is 6, so 6 leaves r1c1 r2c1 (box 0, column 1) and r7c0 r8c0 (box 6, column 0).
        val b = blank()
        b.only(6, 0, rc(0, 0), rc(0, 4)); b.only(6, 3, rc(3, 4), rc(3, 8)); b.only(6, 6, rc(6, 8), rc(6, 1))
        val s = Logic.find(b, Technique.X_CHAIN)
        assertEquals(setOf(rc(1, 1) to 6, rc(2, 1) to 6, rc(7, 0) to 6, rc(8, 0) to 6), elims(s))
    }

    @Test fun xyChain() {
        // {1,2} r0c0 − {2,3} r0c4 − {3,4} r4c4 − {4,1} r4c8: one end is 1, so 1 leaves r0c8 and r4c0.
        val b = blank(); b.set(rc(0, 0), 1, 2); b.set(rc(0, 4), 2, 3); b.set(rc(4, 4), 3, 4); b.set(rc(4, 8), 4, 1)
        assertNull(Logic.find(b, Technique.XY_WING))
        val s = Logic.find(b, Technique.XY_CHAIN)
        assertEquals(setOf(rc(0, 8) to 1, rc(4, 0) to 1), elims(s))
    }

    @Test fun alternatingInferenceChain() {
        // r0c0{1,2}: (r0c0=1) = (r0c0=2) − (r0c3=2) = [column 3 pair] (r5c3=2) − (r5c3=1) [r5c3 is {1,2}]
        // = [row 5 pair on 1] (r5c8=1). One of r0c0, r5c8 is 1: 1 leaves r0c8 (r5c0 has no 1 left).
        val b = blank()
        b.set(rc(0, 0), 1, 2); b.set(rc(5, 3), 1, 2)
        b.only(2, 9 + 3, rc(0, 3), rc(5, 3)); b.only(1, 5, rc(5, 3), rc(5, 8))
        assertNull(Logic.find(b, Technique.X_CHAIN)); assertNull(Logic.find(b, Technique.XY_CHAIN))
        val s = Logic.find(b, Technique.AIC)
        assertEquals(setOf(rc(0, 8) to 1), elims(s))
    }
}
