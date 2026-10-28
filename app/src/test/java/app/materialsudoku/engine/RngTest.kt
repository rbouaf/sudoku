package app.materialsudoku.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pinned vectors, computed independently in Python (arbitrary-precision ints, masked to 64 bits). */
class RngTest {
    @Test fun splitMixFromZero() {
        val r = SplitMix64(0)
        assertEquals(-2152535657050944081L, r.next()) // 0xE220A8397B1DCDAF, the published first output
        assertEquals(7960286522194355700L, r.next())
        assertEquals(487617019471545679L, r.next())
    }

    @Test fun subseed() {
        assertEquals(657414060717504974L, SplitMix64.subseed(42, 0x5355444F4B5539L, 0))
    }

    @Test fun boundedIsInRangeAndStable() {
        val r = SplitMix64(7)
        val xs = IntArray(1000) { r.nextInt(9) }
        assert(xs.all { it in 0 until 9 })
        val again = SplitMix64(7)
        assert(xs.contentEquals(IntArray(1000) { again.nextInt(9) }))
    }
}
