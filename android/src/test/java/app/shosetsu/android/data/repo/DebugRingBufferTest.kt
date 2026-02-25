package app.shosetsu.android.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class DebugRingBufferTest {
    @Test
    fun ringBuffer_trimsOldestEntries() {
        val ring = DebugRingBuffer<Int>(3)
        ring.add(1)
        ring.add(2)
        ring.add(3)
        ring.add(4)

        assertEquals(listOf(2, 3, 4), ring.asList())
    }
}
