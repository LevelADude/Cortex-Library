package app.shosetsu.android.data.repo

object SearchCachePolicy {
    fun <T> trimNewest(entries: List<T>, maxEntries: Int, timestampSelector: (T) -> Long): List<T> {
        return entries.sortedByDescending(timestampSelector).take(maxEntries)
    }
}
