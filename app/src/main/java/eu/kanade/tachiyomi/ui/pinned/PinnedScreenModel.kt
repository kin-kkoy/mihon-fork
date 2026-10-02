package eu.kanade.tachiyomi.ui.pinned

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.chapter.service.ChapterPin
import eu.kanade.domain.chapter.service.ChapterPinStore
import eu.kanade.domain.chapter.service.PinSection
import eu.kanade.domain.chapter.service.ResolvedPin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** One pinned chapter (or note) across the whole library. */
@Immutable
data class PinnedEntry(
    val manga: Manga,
    val chapter: Chapter,
    val pin: ResolvedPin,
    val otherSide: Boolean,
)

/** What the screen shows: the tile grid, or the chapters of one section / the notes-only group. */
sealed interface PinnedView {
    data object Grid : PinnedView
    data class Section(val id: Long) : PinnedView
    data object NotesOnly : PinnedView
}

class PinnedScreenModel(
    val store: ChapterPinStore = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
) : StateScreenModel<PinnedScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            combine(
                store.sections,
                store.pins,
                libraryPreferences.otherSideCategoryIds.changes(),
            ) { sections, pins, otherSideIds -> Triple(sections, pins, otherSideIds) }
                .collectLatest { (sections, pins, otherSideIds) ->
                    val entries = loadEntries(sections, pins, otherSideIds)
                    mutableState.update { it.copy(loading = false, sections = sections, entries = entries) }
                }
        }
    }

    private suspend fun loadEntries(
        sections: List<PinSection>,
        pins: Map<String, ChapterPin>,
        otherSideIds: Set<String>,
    ): List<PinnedEntry> {
        // Keys are "source|mangaUrl|chapterUrl"; look each manga up once.
        val byManga = pins.entries.mapNotNull { (key, pin) ->
            val parts = key.split('|', limit = 3)
            val source = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            if (parts.size < 3) return@mapNotNull null
            Triple(source to parts[1], parts[2], pin)
        }.groupBy({ it.first }, { it.second to it.third })

        return byManga.flatMap { (mangaKey, chapterPins) ->
            val manga = getMangaByUrlAndSourceId.await(mangaKey.second, mangaKey.first) ?: return@flatMap emptyList()
            val chaptersByUrl = getChaptersByMangaId.await(manga.id).associateBy { it.url }
            val otherSide = getCategories.await(manga.id).any { it.id.toString() in otherSideIds }
            chapterPins.mapNotNull { (chapterUrl, pin) ->
                val chapter = chaptersByUrl[chapterUrl] ?: return@mapNotNull null
                val resolved = ChapterPinStore.resolve(pin, sections) ?: return@mapNotNull null
                PinnedEntry(manga, chapter, resolved, otherSide)
            }
        }.sortedWith(compareBy({ it.manga.title.lowercase() }, { it.chapter.chapterNumber }))
    }

    fun toggleOtherSide() {
        mutableState.update { it.copy(otherSideMode = !it.otherSideMode, view = PinnedView.Grid, entryFilter = emptySet()) }
    }

    fun open(view: PinnedView) {
        mutableState.update { it.copy(view = view) }
    }

    fun toggleEntryFilter(mangaId: Long?) {
        mutableState.update {
            val filter = when {
                mangaId == null -> emptySet()
                mangaId in it.entryFilter -> it.entryFilter - mangaId
                else -> it.entryFilter + mangaId
            }
            it.copy(entryFilter = filter)
        }
    }

    @Immutable
    data class State(
        val loading: Boolean = true,
        val sections: List<PinSection> = emptyList(),
        val entries: List<PinnedEntry> = emptyList(),
        val otherSideMode: Boolean = false,
        val view: PinnedView = PinnedView.Grid,
        val entryFilter: Set<Long> = emptySet(),
    ) {
        /** Everything on the current side (normal or OtherSide). */
        val sideEntries: List<PinnedEntry> by lazy { entries.filter { it.otherSide == otherSideMode } }

        /** Manga with pins on this side, for the entry filter chips. */
        val sideManga: List<Manga> by lazy { sideEntries.map { it.manga }.distinctBy { it.id } }

        /** [sideEntries] narrowed by the entry filter. */
        val shownEntries: List<PinnedEntry> by lazy {
            val filter = entryFilter.filter { id -> sideManga.any { it.id == id } }
            if (filter.isEmpty()) sideEntries else sideEntries.filter { it.manga.id in filter }
        }

        /**
         * Sections shown on this side: the ones used here, plus brand-new empty ones. A section
         * used only on the other side stays hidden, so OtherSide section names never leak.
         */
        val visibleSections: List<PinSection> by lazy {
            sections.filter { section ->
                val uses = entries.filter { e -> e.pin.sections.any { it.id == section.id } }
                uses.isEmpty() || uses.any { it.otherSide == otherSideMode }
            }
        }

        fun entriesIn(sectionId: Long) = shownEntries.filter { e -> e.pin.sections.any { it.id == sectionId } }

        val notesOnly: List<PinnedEntry> by lazy { shownEntries.filter { it.pin.sections.isEmpty() } }
    }
}
