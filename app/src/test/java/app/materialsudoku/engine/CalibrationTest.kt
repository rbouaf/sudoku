package app.materialsudoku.engine

import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Not a check: measures generation and the load distribution per difficulty, which is where
 * [Grader.CUTS] come from. Run with `-Dcalibrate=N` (puzzles per recipe) on tools/engine-jvm;
 * `-Dlevels=EASY,MEDIUM` narrows it.
 */
class CalibrationTest {
    private fun levels() = (System.getProperty("levels") ?: "EASY,MEDIUM,HARD,EXPERT,MASTER,EXTREME").split(",").map { Difficulty.valueOf(it) }
    private fun q(xs: List<Int>, f: Double) = xs.sorted()[((xs.size - 1) * f).toInt()]

    /** Load cut points: tertiles of the combined corpus of the three recipes. */
    @Test fun calibrate() {
        assumeTrue(System.getProperty("calibrate").orEmpty().isNotEmpty())
        val n = (System.getProperty("calibrate") ?: "1").toIntOrNull()?.takeIf { it > 1 } ?: 200
        for (k in 0 until 100) Generator.attempt(Difficulty.MEDIUM, Length.NORMAL, SplitMix64(k.toLong()), 0) // warm up
        for (d in levels()) {
            val all = ArrayList<Int>()
            for (l in Length.entries) {
                val loads = ArrayList<Int>(); var attempts = 0; var k = 0
                val t0 = System.nanoTime()
                while (loads.size < n && attempts < 100_000) {
                    attempts++
                    val p = Generator.attempt(d, l, SplitMix64(SplitMix64.subseed(2026, d.ordinal * 4L + l.ordinal, k++)), 0) ?: continue
                    loads.add(p.grade.load)
                }
                val ms = (System.nanoTime() - t0) / 1e6
                all.addAll(loads)
                println("CAL $d $l n=${loads.size} accept=${"%.3f".format(loads.size.toDouble() / attempts)} ms/puzzle=${"%.1f".format(ms / loads.size)} load q10=${q(loads, .1)} q50=${q(loads, .5)} q90=${q(loads, .9)}")
            }
            println("CUTS $d intArrayOf(${q(all, 1.0 / 3)}, ${q(all, 2.0 / 3)})")
        }
    }

    /** What the app actually gets from generate(): time, length hits, clues and availability. */
    @Test fun timeGenerate() {
        assumeTrue(System.getProperty("calibrate").orEmpty().isNotEmpty())
        for (s in 1L..30L) Generator.generate(Difficulty.MEDIUM, Length.NORMAL, s + 1000) // warm up
        for (d in levels()) for (l in Length.entries) {
            val times = ArrayList<Int>(); val clues = ArrayList<Int>(); val open = ArrayList<Int>(); val sq = ArrayList<Int>()
            var hit = 0
            for (k in 0 until 40) {
                val t0 = System.nanoTime()
                val p = Generator.generate(d, l, k.toLong())
                times.add(((System.nanoTime() - t0) / 1000).toInt())
                if (p.grade.length == l) hit++
                clues.add(p.grade.clues); open.add(p.grade.availability); sq.add(p.grade.squeezes)
            }
            println("GEN $d $l time med=${"%.1f".format(q(times, .5) / 1000.0)}ms p90=${"%.1f".format(q(times, .9) / 1000.0)}ms max=${"%.1f".format(times.max() / 1000.0)}ms" +
                " | clues med=${q(clues, .5)} p90=${q(clues, .9)} min=${clues.min()}" +
                " | open med=${q(open, .5) / 100.0} p10=${q(open, .1) / 100.0} p90=${q(open, .9) / 100.0}" +
                " | squeezes med=${q(sq, .5)} p90=${q(sq, .9)} | length hit $hit/40")
        }
    }
}
