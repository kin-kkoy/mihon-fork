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
 * [color] is an ARGB int picked from [PALETTE]. [manga] is the entry it belongs to
 * (see [ChapterPinStore.mangaKey]); null means it's shared by all entries.
 */
@Serializable
data class PinSection(val id: Long, val name: String, val color: Int, val manga: String? = null) {
    val isShared get() = manga == null
}

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
 * A pin resolved against the existing sections, ready for display. [sections] are ordered entry
 * sections first, then shared ones (deleted ones dropped); the first one is the chapter's main color.
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

    /** Section last picked in the reader, per entry (mangaKey -> section id). */
    private val lastSectionsPref = preferenceStore.getString("chapter_pin_last_section_by_entry", "{}")

    /** When on, tapping the reader's bookmark+pin button pins straight to the entry's last section. */
    val quickPinToLast = preferenceStore.getBoolean("chapter_pin_quick_pin_to_last", false)

    val sections: Flow<List<PinSection>> = sectionsPref.changes().map(::decodeSections)
    val pins: Flow<Map<String, ChapterPin>> = pinsPref.changes().map(::decodePins)
    val presets: Flow<List<String>> = presetsPref.changes().map(::decodePresets)
    val lastSections: Flow<Map<String, Long>> = lastSectionsPref.changes().map(::decodeLast)

    fun getSections(): List<PinSection> = decodeSections(sectionsPref.get())
    fun getPins(): Map<String, ChapterPin> = decodePins(pinsPref.get())

    fun isPinned(manga: Manga, chapter: Chapter): Boolean = key(manga, chapter) in getPins()

    fun resolve(manga: Manga, chapter: Chapter): ResolvedPin? =
        resolve(getPins()[key(manga, chapter)], getSections())

    /** The entry's last-picked section, if it still exists and can be used in this entry. */
    fun getLastSection(manga: Manga): PinSection? {
        val id = decodeLast(lastSectionsPref.get())[mangaKey(manga)] ?: return null
        return sectionsFor(manga, getSections()).find { it.id == id }
    }

    @Synchronized
    fun setLastSection(manga: Manga, sectionId: Long) {
        lastSectionsPref.set(json.encodeToString(decodeLast(lastSectionsPref.get()) + (mangaKey(manga) to sectionId)))
    }

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
     * Copies pins (sections + note) from [fromManga]'s chapters to the matching [toManga] chapters,
     * e.g. after a migration. [pairs] maps each old chapter to its new counterpart. Sections that
     * belonged to [fromManga] move over to [toManga] so they still show up there.
     */
    @Synchronized
    fun copyPins(fromManga: Manga, toManga: Manga, pairs: List<Pair<Chapter, Chapter>>) {
        val current = getPins()
        val copied = pairs.mapNotNull { (old, new) ->
            current[key(fromManga, old)]?.let { key(toManga, new) to it }
        }
        if (copied.isNotEmpty()) savePins(current + copied)
        val from = mangaKey(fromManga)
        val to = mangaKey(toManga)
        saveSections(getSections().map { if (it.manga == from) it.copy(manga = to) else it })
    }

    // Sections

    /** Adds a section; [manga] is the entry it belongs to (null = shared by all entries). */
    @Synchronized
    fun addSection(name: String, color: Int, manga: String?): Long {
        val current = getSections()
        val id = (current.maxOfOrNull { it.id } ?: 0L) + 1
        saveSections(current + PinSection(id, name.trim(), color, manga))
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

    /** Makes the section shared ([manga] = null) or belong to one entry. */
    @Synchronized
    fun setScope(id: Long, manga: String?) {
        saveSections(getSections().map { if (it.id == id) it.copy(manga = manga) else it })
    }

    /** Entries (mangaKeys) that have at least one chapter in the section. */
    fun mangaUsing(sectionId: Long): Set<String> =
        getPins().filterValues { sectionId in it.sectionIds }.keys.mapNotNull(::mangaKeyOf).toSet()

    /**
     * Turns a shared section used by several entries into one entry section per entry, each with
     * the same name and color and keeping its chapters.
     */
    @Synchronized
    fun splitSection(id: Long) {
        val section = getSections().find { it.id == id } ?: return
        val users = mangaUsing(id).toList()
        if (users.isEmpty()) return
        var sections = getSections().map { if (it.id == id) it.copy(manga = users.first()) else it }
        val pins = getPins().toMutableMap()
        var nextId = (sections.maxOfOrNull { it.id } ?: 0L) + 1
        users.drop(1).forEach { user ->
            val copyId = nextId++
            sections = sections + section.copy(id = copyId, manga = user)
            pins.entries.filter { (k, pin) -> mangaKeyOf(k) == user && id in pin.sectionIds }.forEach { (k, pin) ->
                pins[k] = pin.withSections(pin.sectionIds.map { if (it == id) copyId else it })
            }
        }
        saveSections(sections)
        savePins(pins)
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
        lastSectionsPref.set(json.encodeToString(decodeLast(lastSectionsPref.get()).filterValues { it != id }))
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

    private fun decodeLast(raw: String): Map<String, Long> =
        runCatching { json.decodeFromString<Map<String, Long>>(raw) }.getOrDefault(emptyMap())

    companion object {
        fun resolve(pin: ChapterPin?, sections: List<PinSection>): ResolvedPin? {
            if (pin == null) return null
            val ids = pin.sectionIds
            val resolved = entryFirst(sections).filter { it.id in ids }
            if (resolved.isEmpty() && pin.note.isBlank()) return null
            return ResolvedPin(resolved, pin.note)
        }

        /** Sections usable in [manga]: its own entry sections first, then the shared ones. */
        fun sectionsFor(manga: Manga, sections: List<PinSection>): List<PinSection> {
            val key = mangaKey(manga)
            return sections.filter { it.manga == key } + sections.filter { it.isShared }
        }

        private fun entryFirst(sections: List<PinSection>) = sections.filterNot { it.isShared } + sections.filter { it.isShared }

        /** Identifies an entry across restores: source + manga URL. */
        fun mangaKey(manga: Manga) = "${manga.source}|${manga.url}"

        fun key(manga: Manga, chapter: Chapter) = "${mangaKey(manga)}|${chapter.url}"

        /** The mangaKey part of a pin key ("source|mangaUrl|chapterUrl"). */
        fun mangaKeyOf(pinKey: String): String? {
            val parts = pinKey.split('|', limit = 3)
            return if (parts.size == 3) "${parts[0]}|${parts[1]}" else null
        }

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
