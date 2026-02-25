package app.shosetsu.android.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchCachePolicyTest {
    data class Entry(val t: Long)

    @Test
    fun trimNewest_limitsToMaxEntries() {
        val data = listOf(Entry(1), Entry(10), Entry(3), Entry(8))
        val trimmed = SearchCachePolicy.trimNewest(data, 2) { it.t }
        assertEquals(listOf(10L, 8L), trimmed.map { it.t })
    }
}
