package app.materialsudoku.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratorTest {

    @Test fun easyIsUniqueEasyAndDeterministic() {
        for (seed in 1L..20L) {
            val a = Generator.generate(Difficulty.EASY, Length.NORMAL, seed)
            assertEquals(1, Solver.countSolutions(a.givens))
            assertEquals(a.solution, Solver.solve(a.givens))
            assertEquals(Difficulty.EASY, a.grade.difficulty)
            assertEquals(a.givens, Generator.generate(Difficulty.EASY, Length.NORMAL, seed).givens)
        }
    }

    @Test fun everyGeneratedLevelMatches() {
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) for (seed in 1L..8L) {
            val p = Generator.generate(d, Length.NORMAL, seed)
            assertEquals(d, p.grade.difficulty)
            assertEquals(1, Solver.countSolutions(p.givens))
            assertEquals(p.grade.difficulty, Grader.grade(p.givens).difficulty)
        }
    }

    @Test fun easyIsGentle() {
        // v3: "all easy puzzles should be solved purely by scanning, like instantly".
        val want = mapOf(Length.QUICK to (45 to 1500), Length.NORMAL to (41 to 1200), Length.LONG to (37 to 900))
        for ((l, w) in want) for (seed in 1L..10L) {
            val p = Generator.generate(Difficulty.EASY, l, seed)
            assertEquals(w.first, p.grade.clues)
            assert(p.grade.availability >= w.second) { "$l seed $seed open ${p.grade.availability}" }
            assertEquals(0, p.grade.squeezes)
        }
    }

    @Test fun easyIsSolvableByScanningOnly() {
        for (l in Length.entries) for (seed in 1L..25L) {
            val p = Generator.generate(Difficulty.EASY, l, seed)
            // the grade used nothing but full houses and hidden singles…
            assert(p.grade.techniques.keys.all { it.isScanning }) { "$l seed $seed used ${p.grade.techniques.keys}" }
            // …and the scanning closure on its own finishes the grid (Quick: boxes only)
            val scan = if (l == Length.QUICK) Technique.HIDDEN_SINGLE_BOX else Technique.HIDDEN_SINGLE_LINE
            assert(Generator.closes(Geo.parse(p.givens), scan, Difficulty.EASY)) { "$l seed $seed" }
        }
    }

    @Test fun everyLevelKeepsItsFloorAndNeedsItsLevel() {
        for ((d, floors) in Generator.FLOORS) for (l in Length.entries) {
            if (d == Difficulty.EASY) continue
            val p = Generator.generate(d, l, 2)
            assertEquals(d, p.grade.difficulty)
            assert(p.grade.techniques.keys.any { it.difficulty == d }) { "$d $l" }
            assert(p.grade.clues in floors[l.ordinal] - 3..floors[l.ordinal] + 3) { "$d $l clues ${p.grade.clues}" }
        }
    }
}
