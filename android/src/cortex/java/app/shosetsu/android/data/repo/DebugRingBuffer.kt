package app.shosetsu.android.data.repo

class DebugRingBuffer<T>(private val maxSize: Int) {
    private val items = ArrayDeque<T>()

    fun add(item: T) {
        items.addLast(item)
        while (items.size > maxSize) items.removeFirst()
    }

    fun clear() = items.clear()

    fun asList(): List<T> = items.toList()
}
