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

/**
 * A chapter's pin: any number of sections plus an optional note. At least one is set
 * (a note can exist without a section; the chapter then just has a plain bookmark + note).
 *
 * [section] is the pre-1.9 single-section field, still read so older pins keep working;
 * new pins only write [sections].
 */
@Serializable
data class ChapterPin(
    val section: Long? = null,
    val sections: List<Long> = emptyList(),
    val note: String = "",
) {
    val sectionIds: List<Long> get() = sections.ifEmpty { listOfNotNull(section) }
    val isEmpty get() = sectionIds.isEmpty() && note.isBlank()

    fun withSections(ids: List<Long>) = ChapterPin(sections = ids.distinct(), note = note)
}

/**
 * A pin resolved against the existing sections, ready for display. [sections] are in the user's
 * section order (deleted ones dropped); the first one is the chapter's main color.
 */
data class ResolvedPin(val sections: List<PinSection>, val note: String) {
    val primary: PinSection? get() = sections.firstOrNull()
}

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

    /** Section last picked in the reader; -1 = none. */
    val lastSection = preferenceStore.getLong("chapter_pin_last_section", -1L)

    /** When on, tapping the reader's bookmark+pin button pins straight to [lastSection]. */
    val quickPinToLast = preferenceStore.getBoolean("chapter_pin_quick_pin_to_last", false)

    val sections: Flow<List<PinSection>> = sectionsPref.changes().map(::decodeSections)
    val pins: Flow<Map<String, ChapterPin>> = pinsPref.changes().map(::decodePins)
    val presets: Flow<List<String>> = presetsPref.changes().map(::decodePresets)

    fun getSections(): List<PinSection> = decodeSections(sectionsPref.get())
    fun getPins(): Map<String, ChapterPin> = decodePins(pinsPref.get())

    fun isPinned(manga: Manga, chapter: Chapter): Boolean = key(manga, chapter) in getPins()

    fun resolve(manga: Manga, chapter: Chapter): ResolvedPin? =
        resolve(getPins()[key(manga, chapter)], getSections())

    // Pins

    /** Sets both sections and note; an empty pin (no sections, blank note) removes the entry. */
    @Synchronized
    fun pin(manga: Manga, chapters: List<Chapter>, sectionIds: List<Long>, note: String) {
        val pin = ChapterPin(sections = sectionIds.distinct(), note = note.trim())
        val keys = chapters.map { key(manga, it) }
        savePins(if (pin.isEmpty) getPins() - keys.toSet() else getPins() + keys.associateWith { pin })
    }

    /** Replaces only the sections (empty = unpin from all sections), keeping any note. */
    @Synchronized
    fun setSections(manga: Manga, chapters: List<Chapter>, sectionIds: List<Long>) =
        updateSections(manga, chapters) { sectionIds }

    /** Adds [sectionId] to each chapter's sections, keeping the others and the note. */
    @Synchronized
    fun addToSection(manga: Manga, chapters: List<Chapter>, sectionId: Long) =
        updateSections(manga, chapters) { it + sectionId }

    /** Removes [sectionId] from each chapter's sections, keeping the others and the note. */
    @Synchronized
    fun removeFromSection(manga: Manga, chapters: List<Chapter>, sectionId: Long) =
        updateSections(manga, chapters) { it - sectionId }

    private fun updateSections(manga: Manga, chapters: List<Chapter>, transform: (List<Long>) -> List<Long>) {
        val current = getPins().toMutableMap()
        chapters.forEach { chapter ->
            val k = key(manga, chapter)
            val old = current[k] ?: ChapterPin()
            val pin = old.withSections(transform(old.sectionIds))
            if (pin.isEmpty) current.remove(k) else current[k] = pin
        }
        savePins(current)
    }

    @Synchronized
    fun unpin(manga: Manga, chapters: List<Chapter>) {
        val keys = chapters.map { key(manga, it) }.toSet()
        val current = getPins()
        if (current.keys.none { it in keys }) return
        savePins(current - keys)
    }

    /**
     * Copies pins (section + note) from [fromManga]'s chapters to the matching [toManga] chapters,
     * e.g. after a migration. [pairs] maps each old chapter to its new counterpart.
     */
    @Synchronized
    fun copyPins(fromManga: Manga, toManga: Manga, pairs: List<Pair<Chapter, Chapter>>) {
        val current = getPins()
        val copied = pairs.mapNotNull { (old, new) ->
            current[key(fromManga, old)]?.let { key(toManga, new) to it }
        }
        if (copied.isNotEmpty()) savePins(current + copied)
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

    /** Deletes the section and unpins its chapters (their bookmarks and notes are kept). */
    @Synchronized
    fun deleteSection(id: Long) {
        saveSections(getSections().filterNot { it.id == id })
        savePins(
            getPins()
                .mapValues { (_, pin) -> if (id in pin.sectionIds) pin.withSections(pin.sectionIds - id) else pin }
                .filterValues { !it.isEmpty },
        )
        if (lastSection.get() == id) lastSection.set(-1L)
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
        fun resolve(pin: ChapterPin?, sections: List<PinSection>): ResolvedPin? {
            if (pin == null) return null
            val ids = pin.sectionIds
            val resolved = sections.filter { it.id in ids }
            if (resolved.isEmpty() && pin.note.isBlank()) return null
            return ResolvedPin(resolved, pin.note)
        }

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
