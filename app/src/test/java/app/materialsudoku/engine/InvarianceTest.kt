package app.materialsudoku.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** A puzzle's difficulty must not depend on how it is drawn (06 §5.3). Length may move a little. */
class InvarianceTest {
    @Test fun difficultyIsInvariantUnderTheGroup() {
        var lengthMoves = 0; var total = 0
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD, Difficulty.EXPERT, Difficulty.MASTER, Difficulty.EXTREME)) for (seed in 1L..12L) {
            val p = Generator.generate(d, Length.NORMAL, seed)
            val rng = SplitMix64(seed * 31)
            repeat(6) {
                val g = Canonical.Transform.random(rng).apply(p.givens)
                val gr = Grader.grade(g)
                assertEquals("$d seed $seed ${p.givens} vs $g", p.grade.difficulty, gr.difficulty)
                assertEquals(p.grade.length, gr.length)   // public grade is canonical: fully invariant
                assertEquals(p.grade.load, gr.load)
                total++
                if (Grader.grade(Geo.parse(g)).length != p.grade.length) lengthMoves++
            }
        }
        println("INV raw-form length moved in $lengthMoves of $total transforms")
    }
}
