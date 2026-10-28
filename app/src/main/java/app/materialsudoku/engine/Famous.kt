package app.materialsudoku.engine

/**
 * Puzzles worth recognising (07-SPECIAL-PUZZLES §1, every string verified unique there).
 *
 * The user's decision (2026-10-27): no museum and no shelf. Famous puzzles sit in the ordinary pool
 * for their difficulty; when you happen to get one, the end-of-game screen says which one it was.
 * Recognition is by [Canonical.classId], so a rotated, mirrored or relabelled copy is still found.
 * The ones the catalogue cannot finish ([Grader] stalls) form the [Difficulty.IMPOSSIBLE] pool.
 */
object Famous {
    class Entry(val name: String, val by: String, val year: Int?, val story: String, val givens: String)

    val all: List<Entry> = listOf(
        Entry("The Wikipedia sudoku", "Lawrence Leonard Gilbert", 2005, "The lead image of every Wikipedia sudoku article: probably the most-printed sudoku there is.", "530070000600195000098000060800060003400803001700020006060000280000419005000080079"),
        Entry("Project Euler 96, grid 1", "Project Euler", null, "How a generation of programmers met sudoku; Peter Norvig's essay starts from it.", "003020600900305001001806400008102900700000008006708200002609500800203009005010300"),
        Entry("AI Escargot", "Arto Inkala", 2006, "Presented to the press as the world's hardest sudoku. \"AI\" is Inkala's initials; it looks like a snail.", "100007090030020008009600500005300900010080002600004000300000010040000007007000300"),
        Entry("Inkala 2010", "Arto Inkala", 2010, "Inkala's second \"world's hardest\", said to have taken three months to make.", "005300000800000020070010500400005300010070006003200080060500009004000030000009700"),
        Entry("Inkala 2012", "Arto Inkala", 2012, "The Daily Telegraph's \"world's hardest sudoku\". 21 givens, though the press said 23.", "800000000003600000070090200050007000000045700000100030001000068008500010090000400"),
        Entry("Easter Monster", "JPF", null, "A forum classic, used as a test case by theorem provers.", "100000002090400050006000700050903000000070000000850040700000600030009080002000001"),
        Entry("Golden Nugget", "tarek", null, "At the very top of Sudoku Explainer's scale (11.9): not one placement or elimination below 11.3.", "000000039000001005003050800008090006070002000100400000009080050020000600400700000"),
        Entry("Kolk", "eleven", null, "One of the forum's hardest-list puzzles, rated 11.9.", "120300000400000300003050000004200500000080009060005070001500200000090060000007008"),
        Entry("Patience", "eleven", null, "Rated 11.9, yet with easy moves almost to the end.", "120300000405000600070000020600100300004530000000008009000450100000000080000002007"),
        Entry("Imam bayildi", "eleven", null, "Rated 11.9; it opens with easy hidden singles and then hits a wall.", "003006080000100200000070004009008060030040001070200000300005000005000600980000050"),
        Entry("Cheese", "eleven", null, "A hardest-list puzzle, rated 11.3.", "020050700400100006800003000200008003040020500000600010002090000090000005704000900"),
        Entry("Red Dwarf", "eleven", null, "A hardest-list puzzle, rated 10.9.", "120300004350000100004000000005400200600070000000008090003100500000009070000060008"),
        Entry("cigarette", "eleven", null, "A hardest-list puzzle, rated 10.8.", "120300000340000100005000000602400500000060070000008006004200300000070009000009080"),
        Entry("Fata Morgana", "tarek", null, "Rated 10.8; easy singles first, then nothing.", "000000003001005600090040070000009050700000008050402000080020090003500100600000000"),
        Entry("Platinum Blonde", "coloin", null, "Top of two raters' lists and the hardest by \"transient chaos\"; only 10.6 by Sudoku Explainer.", "000000012000000003002300400001800005060070800000009000008500000900040500470006000"),
        Entry("Discrepancy", "eleven", null, "Raters disagree about it more than about almost any other: hence the name.", "120400300300010050006000100700090000040603000003002000500080700007000005000000098"),
        Entry("Star Burst", "Rico Alan", null, "Built with 17 givens to make a brute-force computer solver take as long as possible. Easy for a human.", "000000000000003085001020000000507000004000100090000000500000073002010000000040009"),
        Entry("A 17-clue sudoku", "Gordon Royle's list (#39451)", null, "17 givens is the fewest a sudoku can have (McGuire, Tugemann & Civario, 2012). Wikipedia's example.", "000000010000002003000400000000000500401600000007100000050000200000080040030910000"),
        Entry("A symmetric 17", "RedEd, from Gordon Royle's list", null, "17 givens, the minimum, and symmetric about the main diagonal.", "000000001000000023004005000000100000000030600007000580000067000010004000520000000"),
        Entry("top95 #1", "Peter Norvig's benchmark", null, "The programmers' \"hard\" benchmark. Easy for a human.", "400000805030000000000700000020000060000080400000010000000603070500200000104000000"),
        Entry("Raindrops", "Rico Alan", null, "Givens in diagonal runs, 1-2-3-4 down to 5-6-7-8.", "100200300200300400300400500400500600000000000003004005004005006005006007006007008"),
        Entry("Total symmetry", "Rico Alan", null, "Givens with all eight symmetries of the square.", "000603000030010050009000200700106009020000080100409003008000100050090070000704000"),
        Entry("An 18-clue complementary", "Rico Alan", null, "Automorphic about both diagonals, with 18 givens.", "000210000007300000058000000430000000200000008000000076000000250000007300000098000"),
        Entry("Raphael", "attributed to Vic Reinhart", null, "A mirror-symmetric 18.", "000000000090010030006020700000304000210000098000000000002506400080000010000000000"),
        Entry("Tourmaline", "Rico Alan", null, "Both mirrors and 180° symmetry, and it needs chains.", "000902000040000050002000300200000007000456000600000009007000800030000040000207000"),
    )

    private val byClass: Map<String, Entry> by lazy {
        val m = LinkedHashMap<String, Entry>()
        for (e in all) Canonical.classId(e.givens)?.let { m[it] = e }
        m
    }

    /** The famous puzzle this is, in any disguise, or null. */
    fun recognise(givens: String, solution: String): Entry? = byClass[Canonical.classId(givens, solution)]

    /** The pool behind [Difficulty.IMPOSSIBLE]: famous puzzles the catalogue cannot finish. */
    val impossible: List<Entry> by lazy { all.filter { !Grader.grade(it.givens).complete } }
}
