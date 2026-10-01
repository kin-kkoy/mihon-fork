package eu.kanade.domain.chapter.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

/**
 * A user-made group of pinned chapters ("Best moments", "Lore", ...).
 * [color] is an ARGB int picked from [PALETTE].
 */
@Serializable
data class PinSection(val id: Long, val name: String, val color: Int)

/** A chapter pinned to [section], with an optional [note]. */
@Serializable
data class ChapterPin(val section: Long, val note: String = "")

/** A pin resolved against its section, ready for display. */
data class ResolvedPin(val section: PinSection, val note: String)

/**
 * Stores chapter pins, pin sections and note presets as app preferences (so they are
 * included in backups without a DB migration). Pins are keyed by source + manga URL +
 * chapter URL rather than by database id, so they survive a backup restore.
 */
class ChapterPinStore(preferenceStore: PreferenceStore) {

    private val json = Json { ignoreUnknownKeys = true }

    private val sectionsPref = preferenceStore.getString("chapter_pin_sections", "[]")
    private val pinsPref = preferenceStore.getString("chapter_pins", "{}")
    private val presetsPref = preferenceStore.getString("chapter_pin_note_presets", "[]")

    val sections: Flow<List<PinSection>> = sectionsPref.changes().map(::decodeSections)
    val pins: Flow<Map<String, ChapterPin>> = pinsPref.changes().map(::decodePins)
    val presets: Flow<List<String>> = presetsPref.changes().map(::decodePresets)

    fun getSections(): List<PinSection> = decodeSections(sectionsPref.get())
    fun getPins(): Map<String, ChapterPin> = decodePins(pinsPref.get())

    fun isPinned(manga: Manga, chapter: Chapter): Boolean = key(manga, chapter) in getPins()

    // Pins

    @Synchronized
    fun pin(manga: Manga, chapters: List<Chapter>, sectionId: Long, note: String) {
        val pin = ChapterPin(sectionId, note.trim())
        savePins(getPins() + chapters.associate { key(manga, it) to pin })
    }

    @Synchronized
    fun unpin(manga: Manga, chapters: List<Chapter>) {
        val keys = chapters.map { key(manga, it) }.toSet()
        val current = getPins()
        if (current.keys.none { it in keys }) return
        savePins(current - keys)
    }

    // Sections

    @Synchronized
    fun addSection(name: String, color: Int): Long {
        val current = getSections()
        val id = (current.maxOfOrNull { it.id } ?: 0L) + 1
        saveSections(current + PinSection(id, name.trim(), color))
        return id
    }

    @Synchronized
    fun renameSection(id: Long, name: String) {
        if (name.isBlank()) return
        saveSections(getSections().map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    @Synchronized
    fun recolorSection(id: Long, color: Int) {
        saveSections(getSections().map { if (it.id == id) it.copy(color = color) else it })
    }

    /** Deletes the section and unpins its chapters (their bookmarks are kept). */
    @Synchronized
    fun deleteSection(id: Long) {
        saveSections(getSections().filterNot { it.id == id })
        savePins(getPins().filterValues { it.section != id })
    }

    // Note presets

    @Synchronized
    fun addPreset(text: String) {
        val t = text.trim()
        val current = decodePresets(presetsPref.get())
        if (t.isEmpty() || t in current) return
        presetsPref.set(json.encodeToString(current + t))
    }

    @Synchronized
    fun removePreset(text: String) {
        presetsPref.set(json.encodeToString(decodePresets(presetsPref.get()) - text))
    }

    private fun savePins(pins: Map<String, ChapterPin>) = pinsPref.set(json.encodeToString(pins))
    private fun saveSections(sections: List<PinSection>) = sectionsPref.set(json.encodeToString(sections))

    private fun decodeSections(raw: String): List<PinSection> =
        runCatching { json.decodeFromString<List<PinSection>>(raw) }.getOrDefault(emptyList())

    private fun decodePins(raw: String): Map<String, ChapterPin> =
        runCatching { json.decodeFromString<Map<String, ChapterPin>>(raw) }.getOrDefault(emptyMap())

    private fun decodePresets(raw: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())

    companion object {
        fun key(manga: Manga, chapter: Chapter) = "${manga.source}|${manga.url}|${chapter.url}"

        /** Gold, coral, teal, sky, pink, lime — same as the approved prototype. */
        val PALETTE = listOf(
            0xFFF2B544.toInt(),
            0xFFFF7A6B.toInt(),
            0xFF3FD1B5.toInt(),
            0xFF5FB4FF.toInt(),
            0xFFFF6FB5.toInt(),
            0xFFA6E05A.toInt(),
        )
    }
}
