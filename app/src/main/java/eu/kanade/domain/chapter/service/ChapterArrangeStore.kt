package eu.kanade.domain.chapter.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.manga.model.Manga

/**
 * Per-entry chapter arrangement the user made: a custom reading order (for sources that list
 * chapters out of order) and hidden chapters. Stored as app preferences keyed by
 * [ChapterPinStore.mangaKey], with chapters identified by URL, so it's backed up and restore-safe.
 */
class ChapterArrangeStore(preferenceStore: PreferenceStore) {

    private val json = Json { ignoreUnknownKeys = true }

    /** mangaKey -> chapter URLs in reading order (first to read first). */
    private val orderPref = preferenceStore.getString("chapter_custom_order", "{}")

    /** mangaKey -> hidden chapter URLs. */
    private val hiddenPref = preferenceStore.getString("chapter_hidden", "{}")

    val orders: Flow<Map<String, List<String>>> = orderPref.changes().map(::decode)
    val hidden: Flow<Map<String, List<String>>> = hiddenPref.changes().map(::decode)

    fun getOrder(manga: Manga): List<String>? = decode(orderPref.get())[ChapterPinStore.mangaKey(manga)]
    fun getHidden(manga: Manga): Set<String> = decode(hiddenPref.get())[ChapterPinStore.mangaKey(manga)].orEmpty().toSet()

    /** Saves the reading order (chapter URLs); null goes back to the source's order. */
    @Synchronized
    fun setOrder(manga: Manga, urls: List<String>?) {
        val key = ChapterPinStore.mangaKey(manga)
        val current = decode(orderPref.get())
        orderPref.set(json.encodeToString(if (urls == null) current - key else current + (key to urls)))
    }

    @Synchronized
    fun setHidden(manga: Manga, urls: Collection<String>, hidden: Boolean) {
        val key = ChapterPinStore.mangaKey(manga)
        val current = decode(hiddenPref.get())
        val set = current[key].orEmpty().toSet().let { if (hidden) it + urls else it - urls.toSet() }
        hiddenPref.set(json.encodeToString(if (set.isEmpty()) current - key else current + (key to set.toList())))
    }

    private fun decode(raw: String): Map<String, List<String>> =
        runCatching { json.decodeFromString<Map<String, List<String>>>(raw) }.getOrDefault(emptyMap())

    companion object {
        /**
         * Applies a saved reading order to [items], which must already be in the source's reading
         * order (ascending). Items not in [order] (e.g. new chapters) keep their place relative to
         * each other and go after the ordered ones, i.e. they count as the newest.
         */
        fun <T> applyOrder(items: List<T>, order: List<String>?, url: (T) -> String): List<T> {
            if (order.isNullOrEmpty()) return items
            val byUrl = items.associateBy(url)
            val ordered = order.mapNotNull { byUrl[it] }
            val orderedSet = order.toSet()
            return ordered + items.filterNot { url(it) in orderedSet }
        }
    }
}
