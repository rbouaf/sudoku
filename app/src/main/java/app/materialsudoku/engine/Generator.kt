package app.materialsudoku.engine

/**
 * Seed → puzzle, deterministically (06-DETERMINISM §3).
 *
 * Attempt k of a request draws from its own sub-seed `subseed(seed, domain(difficulty, length), k)`,
 * so no state flows between attempts and the first attempt that passes wins. Every budget counts
 * attempts or solver nodes, never time. A puzzle never depends on the device, the clock or the
 * Kotlin version.
 *
 * ## Version 3: gentle by construction (2026-10-27)
 *
 * Version 1 dug every puzzle to (near-)minimal, and the user found Easy "looks like a hard". The
 * model is unchanged (difficulty = hardest technique required, length = load); the user then
 * asked that "all easy puzzles should be solved purely by scanning, like instantly … Easy Normal
 * … in a minute", which shifted the ladder one step easier ([Difficulty]) and moved the floors up.
 * Puzzles come from **logic-constrained digging that stops early**:
 *
 * - **Easy** digs with a scanning closure that depends on the length and stops at a clue floor:
 *   Quick = full house + box hidden singles, 45 clues; Normal = + line hidden singles, 41;
 *   Long = the same, 37. No naked singles anywhere. Removing a clue only while that closure still
 *   finishes the grid proves uniqueness. Every Easy puzzle must keep many moves open (mean open
 *   singles ≥ 15 / 12 / 9) and never squeeze to one before the endgame.
 * - **Medium** digs with the closure capped at Medium (singles, pointing, claiming), which lands at
 *   or under it; the attempt is kept only if it needs Medium.
 * - **Hard to Extreme** dig to minimal with count-to-2 and keep the attempt if it grades exactly
 *   at the level. (A capped closure works too, but searching subsets, fish and wings on every
 *   removal was 2–4× slower in the tail: Master's worst case was 0.6 s on the JVM against 0.13 s.)
 * - Then, at every level above Easy, clues are **added back** from the solution in seeded order,
 *   each only if the puzzle still needs the level, up to the floor. Unique-solution techniques
 *   (UR, BUG) are never part of a digging closure, since a closure that assumes uniqueness cannot
 *   prove it.
 *
 * Floors per level are [FLOORS]. Length is still judged by the frozen load cut points
 * ([Grader.CUTS]), recalibrated on this generator, so the three recipes and labels agree.
 */
object Generator {
    const val VERSION = 3
    private const val DOMAIN_9X9 = 0x5355444F4B5539L // "SUDOKU9"
    private const val ATTEMPTS = 4000

    /** After this many puzzles of the right difficulty, take the one whose load came closest. */
    private const val LENGTH_TRIES = 12

    /** Clue floor per difficulty, indexed by [Length]: QUICK, NORMAL, LONG. */
    internal val FLOORS: Map<Difficulty, IntArray> = mapOf(
        Difficulty.EASY to intArrayOf(45, 41, 37),
        Difficulty.MEDIUM to intArrayOf(36, 33, 30),
        Difficulty.HARD to intArrayOf(33, 30, 27),
        Difficulty.EXPERT to intArrayOf(31, 28, 26),
        Difficulty.MASTER to intArrayOf(29, 27, 25),
        Difficulty.EXTREME to intArrayOf(28, 26, 24),
    )

    /** Minimum mean open singles × 100 for Easy, by length (0 = no target). */
    private val EASY_OPEN = intArrayOf(1500, 1200, 900)

    fun generate(difficulty: Difficulty, length: Length, seed: Long): Puzzle {
        if (difficulty == Difficulty.IMPOSSIBLE) return impossible(seed)
        val domain = DOMAIN_9X9 + difficulty.ordinal * 4L + length.ordinal
        var best: Puzzle? = null
        var bestMiss = Int.MAX_VALUE
        var matched = 0
        for (k in 0 until ATTEMPTS) {
            val p = attempt(difficulty, length, SplitMix64(SplitMix64.subseed(seed, domain, k)), seed) ?: continue
            if (p.grade.length == length) return p
            val miss = Grader.missFrom(difficulty, length, p.grade.load)
            if (miss < bestMiss) { best = p; bestMiss = miss }
            if (++matched >= LENGTH_TRIES) break
        }
        return best ?: error("no $difficulty puzzle in $ATTEMPTS attempts for seed $seed")
    }

    /**
     * Impossible: a famous puzzle the catalogue cannot finish, in a seeded disguise (rows, columns,
     * bands, stacks, transpose, digits). The pool is small (~16); [Famous.recognise] still names it
     * on the result screen. Length does not apply.
     */
    private fun impossible(seed: Long): Puzzle {
        val pool = Famous.impossible
        val rng = SplitMix64(SplitMix64.subseed(seed, DOMAIN_9X9 + 99, 0))
        val e = pool[rng.nextInt(pool.size)]
        val t = Canonical.Transform.random(rng)
        val givens = t.apply(e.givens)
        val solution = t.apply(Solver.solve(e.givens)!!)
        return Puzzle(givens, solution, Grader.grade(givens), seed, VERSION)
    }

    /** One attempt with [length]'s recipe: a puzzle of exactly [difficulty], or null. */
    internal fun attempt(difficulty: Difficulty, length: Length, rng: SplitMix64, seed: Long, targets: Boolean = true): Puzzle? {
        val sol = Solver.randomSolution(rng)
        val p = sol.copyOf()
        val order = IntArray(81) { it }
        rng.shuffle(order)
        val floor = FLOORS.getValue(difficulty)[length.ordinal]
        var clues = 81
        when (difficulty) {
            Difficulty.EASY -> {
                val singles = if (length == Length.QUICK) Technique.HIDDEN_SINGLE_BOX else Technique.HIDDEN_SINGLE_LINE
                for (i in order) {
                    if (clues <= floor) break
                    val v = p[i]; p[i] = 0
                    if (closes(p, singles, Difficulty.EASY)) clues-- else p[i] = v
                }
            }
            Difficulty.MEDIUM ->
                for (i in order) { val v = p[i]; p[i] = 0; if (closes(p, Technique.NAKED_SINGLE, difficulty)) clues-- else p[i] = v }
            else ->
                for (i in order) { val v = p[i]; p[i] = 0; if (Solver.count(p, 2, 200_000) == 1) clues-- else p[i] = v }
        }
        val raw = Grader.grade(p, measure = false)
        if (!raw.complete || raw.difficulty != difficulty) return null

        if (difficulty != Difficulty.EASY && clues < floor) {
            // refill toward the floor, keeping only additions after which the level is still needed
            val empty = (0 until 81).filter { p[it] == 0 }.toIntArray()
            rng.shuffle(empty)
            for (i in empty) {
                if (clues >= floor) break
                p[i] = sol[i]
                val g = Grader.grade(p, measure = false)
                if (g.complete && g.difficulty == difficulty) clues++ else p[i] = 0
            }
        }
        check(Solver.count(p, 2) == 1) { "not unique" } // the independent proof, every time

        // the published grade is the canonical one, so it never depends on the disguise
        val grade = Grader.grade(Geo.parse(Canonical.classId(Geo.format(p), Geo.format(sol))))
        check(grade.difficulty == difficulty) { "difficulty changed under canonicalisation" }
        if (targets && difficulty == Difficulty.EASY) {
            val open = EASY_OPEN[length.ordinal]
            if (grade.availability < open) return null
            if (grade.squeezes > 0) return null
        }
        return Puzzle(Geo.format(p), Geo.format(sol), grade, seed, VERSION)
    }

    /**
     * True if the grid finishes using singles up to [singles] (in [Technique] order) plus, when no
     * single is open, the non-single techniques up to [cap]. Unique-solution techniques (UR, BUG)
     * are never used here: a closure that assumes uniqueness cannot prove it.
     */
    internal fun closes(p: IntArray, singles: Technique, cap: Difficulty): Boolean {
        val b = Board.of(p) ?: return false
        var guard = 0
        while (!b.solved()) {
            if (++guard > 1000) return false
            val wave = Grader.singlesWave(b).filter { it[2] <= singles.ordinal }
            if (wave.isNotEmpty()) {
                for (w in wave) {
                    if (b.v[w[0]] != 0) continue
                    if (b.c[w[0]] and (1 shl w[1]) == 0) return false
                    b.place(w[0], w[1])
                }
                if (b.broken()) return false
                continue
            }
            if (cap == Difficulty.EASY) return false
            var step: Step? = null
            for (t in Technique.entries) {
                if (t.isSingle || t.difficulty > cap) continue
                if (t == Technique.UNIQUE_RECTANGLE_1 || t == Technique.BUG_PLUS_1) continue
                step = Logic.find(b, t)
                if (step != null) break
            }
            b.apply(step ?: return false)
            if (b.broken()) return false
        }
        return true
    }

    /** Singles closure: true if full houses, hidden and naked singles alone complete the grid. */
    internal fun singlesFinish(p: IntArray): Boolean = closes(p, Technique.NAKED_SINGLE, Difficulty.EASY)
}
