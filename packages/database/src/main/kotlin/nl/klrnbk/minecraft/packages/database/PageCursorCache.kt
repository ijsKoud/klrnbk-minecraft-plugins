package nl.klrnbk.minecraft.packages.database

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * The position in an ordered result: the sort value of a row (as text) and the row's id, which breaks ties
 * between rows with the same sort value.
 */
data class KeysetCursor(
    val sort: String,
    val id: Uuid,
)

/**
 * Remembers where pages start, so [KeysetPaginator] can open the next (or an already visited) page by
 * seeking instead of counting rows with OFFSET.
 *
 * The entry for `(scope, page)` is the cursor of the last row of the page before it. Entries expire after
 * [ttl], which also limits how far page boundaries can drift when rows are added in the meantime, and the
 * least recently used entries are dropped beyond [maxEntries].
 */
class PageCursorCache(
    private val maxEntries: Int = 2_000,
    private val ttl: Duration = 5.minutes,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private class Entry(
        val cursor: KeysetCursor,
        val storedAt: Long,
    )

    private val entries =
        object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > maxEntries
        }

    @Synchronized
    fun get(
        scope: String,
        page: Int,
    ): KeysetCursor? {
        val key = key(scope, page)
        val entry = entries[key] ?: return null

        if (nanoTime() - entry.storedAt > ttl.inWholeNanoseconds) {
            entries.remove(key)
            return null
        }

        return entry.cursor
    }

    @Synchronized
    fun put(
        scope: String,
        page: Int,
        cursor: KeysetCursor,
    ) {
        entries[key(scope, page)] = Entry(cursor, nanoTime())
    }

    @Synchronized
    fun clear() = entries.clear()

    private fun key(
        scope: String,
        page: Int,
    ) = "$scope#$page"
}
