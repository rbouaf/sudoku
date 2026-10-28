package app.materialsudoku.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {
    private val givens = "000390002500040091000000470000400030000032000059060700000000003640007800100000900"
    private val solution = "471398652582746391936215478218479536764532189359861724897154263643927815125683947"
    private fun game(lives: Int = 3, flag: Boolean = true) =
        Game("t", Deal(givens, solution, 1, 1, 0L, 1), Rules(flag, lives, true))

    @Test fun rightDigitPlacesAndWrongDigitCostsALife() {
        val g = game()
        g.select(0); g.input(4)
        assertEquals(4, g.values[0])
        g.select(1); g.input(1)
        assertEquals(0, g.values[1])
        assertEquals(2, g.lives)
        assertEquals(1, g.events.count { it.kind == Ev.MISTAKE })
    }

    @Test fun lastLifeEndsTheRun() {
        val g = game(lives = 1)
        g.select(1); g.input(1)
        assertEquals(Status.LOST, g.status)
        g.select(0); g.input(4)
        assertEquals(0, g.values[0])
    }

    @Test fun placingTidiesPeerNotesAndUndoRestoresThem() {
        val g = game()
        g.select(1); g.input(4, Mode.NOTES)
        assertTrue(g.notes[1].hasBit(4))
        g.select(0); g.input(4)
        assertFalse(g.notes[1].hasBit(4))
        g.undo()
        assertEquals(0, g.values[0])
        assertTrue(g.notes[1].hasBit(4))
    }

    @Test fun noNetLetsAWrongDigitStand() {
        val g = game(flag = false)
        g.select(1); g.input(1)
        assertEquals(1, g.values[1])
        assertEquals(Status.PLAYING, g.status)
    }

    @Test fun fillingTheGridWinsAndTheReportCountsIt() {
        val g = game()
        for (i in 0 until 81) if (givens[i] == '0') { g.select(i); g.tick(1000); g.input(solution[i] - '0') }
        assertEquals(Status.WON, g.status)
        val r = Report(g, null)
        assertEquals(givens.count { it == '0' }, r.placements.size)
        assertEquals(1.0, r.accuracy, 1e-9)
        assertTrue(r.byHand)
    }

    @Test fun autoCompleteOnlyWhenEveryEmptyCellIsForced() {
        val g = game()
        assertFalse(g.autoCompleteAvailable)
        // Fill all but two cells: each remaining cell then has one candidate.
        val empties = (0 until 81).filter { givens[it] == '0' }
        for (i in empties.dropLast(2)) { g.select(i); g.input(solution[i] - '0') }
        assertTrue(g.autoCompleteAvailable)
        while (g.autoCompleteStep()) Unit
        assertEquals(Status.WON, g.status)
        assertFalse(Report(g, null).byHand)
    }

    @Test fun numberFirstKeyLetsGoOfAFilledCell() {
        val g = game().apply { numberFirst = true }
        g.arm(5)
        assertEquals(-1, g.selected)
        assertEquals(5, g.highlight)
        g.select(0)                      // empty: writes the armed digit (wrong here, so a strike)
        assertEquals(2, g.lives)
        g.select(4)                      // a given 9: arms 9 instead
        assertEquals(9, g.armed)
        assertEquals(-1, g.selected)
    }

    @Test fun numberFirstTappingAFilledCellArmsItEvenWithNothingArmed() {
        val g = game().apply { numberFirst = true }
        g.select(3)                      // a given 3, nothing armed yet
        assertEquals(3, g.armed)
        val target = (0 until 81).first { givens[it] == '0' && solution[it] == '3' }
        g.select(target)                 // an empty cell now receives the 3
        assertEquals(3, g.values[target])
        assertEquals(3, g.lives)
    }

    @Test fun numberFirstMovesToTheNextUnfinishedNumberWhenOneIsComplete() {
        val g = game().apply { numberFirst = true }
        fun complete(d: Int) {
            g.select((0 until 81).first { g.values[it] == d })
            assertEquals(d, g.armed)
            for (i in (0 until 81).filter { g.values[it] == 0 && solution[it] - '0' == d }) g.select(i)
            assertEquals(0, g.remaining(d))
        }
        complete(4)
        assertEquals(5, g.armed)         // 4 done → 5
        assertEquals(5, g.highlight)
        complete(5)
        complete(6)
        assertEquals(7, g.armed)
        complete(9)
        assertEquals(1, g.armed)         // 9 wraps to 1
        complete(1); complete(2); complete(3)
        assertEquals(7, g.armed)         // 4, 5, 6 are done: skips to 7
    }

    @Test fun fillAllNotesCountsLikeAHint() {
        val g = game()
        assertFalse(g.assisted)
        g.fillNotes()
        assertTrue(g.assisted)
        assertTrue(g.recordKey().assisted)
        assertEquals(0, g.band)          // not a band any more
    }

    @Test fun halfwayIsLoggedOnceAtHalfTheEmptyCells() {
        val g = game()
        val empties = (0 until 81).filter { givens[it] == '0' }
        val half = (empties.size + 1) / 2
        for ((k, i) in empties.withIndex()) {
            g.select(i); g.input(solution[i] - '0')
            val n = g.events.count { it.kind == Ev.HALFWAY }
            assertEquals(if (k + 1 >= half) 1 else 0, n)
        }
    }

    @Test fun halfwayLinePrefersPaceThenNoMistakes() {
        val g = game()
        assertEquals("Halfway" to "No mistakes yet", halfwayLine(g, emptyList(), true))
        g.select(1); g.input(1)          // a strike
        assertEquals("Halfway there" to null, halfwayLine(g, emptyList(), true))
        // A slower earlier win at the same key makes this record pace; hidden timer never mentions time.
        val slow = game()
        for (i in 0 until 81) if (givens[i] == '0') { slow.select(i); slow.tick(5000); slow.input(solution[i] - '0') }
        assertEquals("Halfway" to "Record pace", halfwayLine(g, listOf(slow), true))
        assertEquals("Halfway there" to null, halfwayLine(g, listOf(slow), false))
    }
}
