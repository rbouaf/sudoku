package app.materialsudoku.engine

/**
 * The ceiling: what you need to know (DIFFICULTY-MODEL.md, decided 2026-10-27; shifted one step
 * easier the same day, grader v3: "all easy puzzles should be solved purely by scanning").
 *
 * EASY scanning (full house, hidden singles) · MEDIUM + naked single, pointing, claiming ·
 * HARD + subsets · EXPERT + one-digit patterns · MASTER + wings, UR1, BUG+1 · EXTREME + chains.
 */
enum class Difficulty { EASY, MEDIUM, HARD, EXPERT, MASTER, EXTREME, IMPOSSIBLE }

/** The load: how long it runs. Tertiles of [Grade.load] within one difficulty, frozen per grader version. */
enum class Length { QUICK, NORMAL, LONG }

/**
 * Every technique the grader knows, **in the order it tries them**. The order is grouped by
 * difficulty first, so a puzzle that some Medium technique can finish is never labelled Hard just
 * because an X-Wing happened to be cheaper on Sudoku Explainer's scale than a naked triple.
 * Within a difficulty the order follows SE / HoDoKu cost.
 *
 * [weight] is this technique's contribution to [Grade.load] each time it is needed (singles are
 * costed per round instead). Invented, versioned with [Grader.VERSION].
 */
enum class Technique(val difficulty: Difficulty, val weight: Int, val displayName: String) {
    FULL_HOUSE(Difficulty.EASY, 0, "Full house"),
    HIDDEN_SINGLE_BOX(Difficulty.EASY, 0, "Hidden single in a box"),
    HIDDEN_SINGLE_LINE(Difficulty.EASY, 0, "Hidden single in a line"),
    /** Not scanning: you must check one cell against all eight other digits. Medium since v3. */
    NAKED_SINGLE(Difficulty.MEDIUM, 0, "Naked single"),

    POINTING(Difficulty.MEDIUM, 300, "Pointing"),
    CLAIMING(Difficulty.MEDIUM, 300, "Claiming"),
    NAKED_PAIR(Difficulty.HARD, 400, "Naked pair"),
    HIDDEN_PAIR(Difficulty.HARD, 500, "Hidden pair"),
    NAKED_TRIPLE(Difficulty.HARD, 600, "Naked triple"),
    HIDDEN_TRIPLE(Difficulty.HARD, 700, "Hidden triple"),
    NAKED_QUAD(Difficulty.HARD, 900, "Naked quad"),
    HIDDEN_QUAD(Difficulty.HARD, 900, "Hidden quad"),

    X_WING(Difficulty.EXPERT, 800, "X-Wing"),
    SKYSCRAPER(Difficulty.EXPERT, 900, "Skyscraper"),
    TWO_STRING_KITE(Difficulty.EXPERT, 900, "Two-string kite"),
    EMPTY_RECTANGLE(Difficulty.EXPERT, 1000, "Empty rectangle"),
    SWORDFISH(Difficulty.EXPERT, 1100, "Swordfish"),
    JELLYFISH(Difficulty.EXPERT, 1400, "Jellyfish"),

    XY_WING(Difficulty.MASTER, 1200, "XY-Wing"),
    XYZ_WING(Difficulty.MASTER, 1300, "XYZ-Wing"),
    W_WING(Difficulty.MASTER, 1300, "W-Wing"),
    UNIQUE_RECTANGLE_1(Difficulty.MASTER, 1000, "Unique rectangle"),
    BUG_PLUS_1(Difficulty.MASTER, 1000, "BUG+1"),

    /** One digit; strong links are conjugate pairs. */
    X_CHAIN(Difficulty.EXTREME, 1800, "X-Chain"),
    /** Bivalue cells joined by a shared digit. */
    XY_CHAIN(Difficulty.EXTREME, 1900, "XY-Chain"),

    /** Alternating inference chain mixing cell and unit links over several digits. */
    AIC(Difficulty.EXTREME, 2600, "Alternating inference chain"),
    ;

    /** Places a digit by elimination within one cell or unit (costed per round, not by [weight]). */
    val isSingle: Boolean get() = ordinal <= NAKED_SINGLE.ordinal

    /** Found by scanning alone: the Easy techniques. */
    val isScanning: Boolean get() = difficulty == Difficulty.EASY
}

/**
 * A puzzle's grade. [difficulty] is the difficulty of the hardest technique the solve needed
 * (max-step, never a sum along the solver's path: 02 §1.2). [complete] is false when the grader
 * stalled; such a puzzle is labelled [Difficulty.IMPOSSIBLE] because nothing in the catalogue
 * finishes it.
 */
class Grade(
    val difficulty: Difficulty,
    val length: Length,
    /** How many times each technique was needed (singles: per placement). Ordered by [Technique]. */
    val techniques: Map<Technique, Int>,
    val hardest: Technique?,
    val complete: Boolean,
    val clues: Int,
    /** Rounds of the order-free wave solve: every available single is placed at once each round. */
    val rounds: Int,
    /** Rounds in which exactly one single was available. */
    val bottlenecks: Int,
    /** Steps that needed something beyond singles. */
    val hardSteps: Int,
    /** The load measure behind [length]; see [Grader]. */
    val load: Int,
    val graderVersion: Int,
    /**
     * Mean number of singles open per placement × 100, for a player who places one digit at a time
     * (lowest cell first). Pelánek's availability: the newcomer-friendliness of a puzzle.
     */
    val availability: Int = 0,
    /** Placements at which exactly one single was open, before the last 9 cells (the endgame always narrows). */
    val squeezes: Int = 0,
) {
    override fun toString() = "Grade($difficulty/$length, hardest=$hardest, clues=$clues, open=${availability / 100.0}, squeezes=$squeezes, hard=$hardSteps, load=$load)"
}

class Puzzle(
    /** 81 characters, row-major, '0' for empty. This string *is* the puzzle's identity (06 §1.1). */
    val givens: String,
    val solution: String,
    val grade: Grade,
    val seed: Long,
    val generatorVersion: Int,
)

/**
 * One logical step, as a structured witness rather than text (02 §5): what to place, what to
 * remove, which cells form the pattern, and which unit to point at first.
 */
class Step(
    val technique: Technique,
    /** cell to digit */
    val placements: List<Pair<Int, Int>>,
    /** cell to digit removed */
    val eliminations: List<Pair<Int, Int>>,
    val focusCells: List<Int>,
    /** 0–8 rows, 9–17 columns, 18–26 boxes. */
    val focusUnit: Int?,
    /** The digit(s) the pattern is about, ascending. */
    val digits: List<Int> = emptyList(),
) {
    override fun toString() = "Step($technique, place=$placements, elim=$eliminations, focus=$focusCells, unit=$focusUnit)"
}
