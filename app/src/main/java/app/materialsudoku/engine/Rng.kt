package app.materialsudoku.engine

/**
 * SplitMix64 (Steele, Lea & Flood 2014; public domain), never `kotlin.random.Random(seed)`, whose
 * documentation reserves the right to change its sequence between Kotlin versions
 * (06-DETERMINISM §3.1). Test vectors are pinned in `RngTest`.
 */
class SplitMix64(private var state: Long) {
    fun next(): Long { state += GAMMA; return mix64(state) }

    /** Lemire's nearly divisionless bounded integer on the top 32 bits; bound in [1, 2^31). */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        var m = (next() ushr 32) * bound
        var l = m and 0xFFFFFFFFL
        if (l < bound) {
            val t = (0x100000000L - bound) % bound
            while (l < t) { m = (next() ushr 32) * bound; l = m and 0xFFFFFFFFL }
        }
        return (m ushr 32).toInt()
    }

    fun shuffle(a: IntArray) {
        for (i in a.size - 1 downTo 1) { val j = nextInt(i + 1); val t = a[i]; a[i] = a[j]; a[j] = t }
    }

    companion object {
        const val GAMMA = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15

        fun mix64(z0: Long): Long {
            var z = z0
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
            return z xor (z ushr 31)
        }

        /** Attempt [k] of ([seed], [domain]) → an independent sub-seed. No state flows between attempts. */
        fun subseed(seed: Long, domain: Long, k: Int): Long = mix64(mix64(seed xor mix64(domain)) + (k + 1L) * GAMMA)
    }
}
