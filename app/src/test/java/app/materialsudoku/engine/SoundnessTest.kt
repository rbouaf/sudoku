package app.materialsudoku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The property that matters most: on every position reached while solving a few hundred random
 * minimal puzzles, **every** technique's step is checked against the known solution. A placement
 * must be the solution digit; an elimination must never remove it. Run on all techniques at every
 * position, not only the one the solve takes, so rarely-chosen finders are exercised too.
 */
class SoundnessTest {

    @Test fun everyStepAgreesWithTheSolution() {
        val fired = IntArray(Technique.entries.size)
        var positions = 0
        for (k in 0 until 300) {
            val rng = SplitMix64(SplitMix64.subseed(99, 1, k))
            val sol = Solver.randomSolution(rng)
            val p = sol.copyOf()
            val order = IntArray(81) { it }; rng.shuffle(order)
            for (i in order) { val v = p[i]; p[i] = 0; if (Solver.count(p, 2) != 1) p[i] = v }
            val b = Board.of(p)!!
            var guard = 0
            while (!b.solved() && guard++ < 400) {
                positions++
                for (t in Technique.entries) {
                    val s = Logic.find(b, t) ?: continue
                    fired[t.ordinal]++
                    for ((i, d) in s.placements) assertEquals("$t placed wrongly in puzzle ${Geo.format(p)}", sol[i], d)
                    for ((i, d) in s.eliminations) assertNotEquals("$t removed the solution in puzzle ${Geo.format(p)}", sol[i], d)
                    assert(s.placements.isNotEmpty() || s.eliminations.isNotEmpty())
                }
                val step = Logic.cheapest(b) ?: break
                b.apply(step)
            }
        }
        println("soundness: $positions positions; fired " + Technique.entries.joinToString { "${it.name}=${fired[it.ordinal]}" })
        // every technique must have been exercised on real puzzles at least once
        for (t in Technique.entries) assert(fired[t.ordinal] > 0) { "$t never fired on 300 random puzzles" }
    }

    @Test fun hintsOnPlayerNotesStaySound() {
        // A player position: the right digits for half the empties, notes that are basic candidates
        // minus some random wrong ones; plus one wrong digit, which hints must ignore.
        val p = Generator.generate(Difficulty.MEDIUM, Length.NORMAL, 5)
        val sol = Geo.parse(p.solution)
        val rng = SplitMix64(3)
        val values = Geo.parse(p.givens)
        for (i in 0 until 81) if (values[i] == 0 && rng.nextInt(2) == 0) values[i] = sol[i]
        val wrong = (0 until 81).first { values[it] == 0 }
        values[wrong] = sol[wrong] % 9 + 1
        val notes = IntArray(81) { if (values[it] == 0) Geo.ALL and (if (rng.nextInt(3) == 0) (1 shl (sol[it] % 9 + 1)).inv() else -1) else 0 }
        assertEquals(listOf(wrong), Hints.mistakes(values, p.solution))
        val s = Hints.next(values, notes, p.solution)!!
        for ((i, d) in s.placements) assertEquals(sol[i], d)
        for ((i, d) in s.eliminations) assertNotEquals(sol[i], d)
    }
}
