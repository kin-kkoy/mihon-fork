package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.preference.toggle
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal

abstract class SearchViewModel(
    initialState: State = State(),
    sourcePreferences: SourcePreferences,
    private val sourceManager: SourceManager,
    private val extensionManager: ExtensionManager,
    private val networkToLocalManga: NetworkToLocalManga,
    private val getManga: GetManga,
    private val preferences: SourcePreferences,
) : ViewModel() {

    val state: StateFlow<State>
        field = MutableStateFlow<State>(initialState)

    // Subclasses can't touch the backing field (Kotlin forbids a visibility modifier on one),
    // so state writes from them go through here.
    protected fun updateState(function: (State) -> State) {
        state.update(function)
    }

    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(5)
    private var searchJob: Job? = null

    // Per-source in-flight search jobs, keyed by source id, so each can be cancelled independently.
    private val sourceJobs = mutableMapOf<Long, Job>()

    private val enabledLanguages = sourcePreferences.enabledLanguages.get()
    private val disabledSources = sourcePreferences.disabledSources.get()
    protected val pinnedSources = sourcePreferences.pinnedSources.get()
    protected val selectedSourcesPref = sourcePreferences.globalSearchSelectedSources

    private var lastQuery: String? = null

    protected var extensionFilter: String? = null

    /**
     * Seeds the enabled source list and the initial selection (persisted, or pinned as fallback).
     * Started lazily (by [loadSources] or the first [search]) so it never runs before a subclass
     * has finished constructing: [getEnabledSources] may read subclass properties.
     */
    private val sourcesSetup = viewModelScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
        val enabledSources = getEnabledSources()
        val selected = getInitialSelectedSourceIds(enabledSources)
        state.update { it.copy(enabledSources = enabledSources, selectedSourceIds = selected) }
    }

    open val sortComparator = { map: Map<Source, SearchItemResult> ->
        compareBy<Source>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            { "${it.name.lowercase()} (${it.lang})" },
        )
    }

    init {
        viewModelScope.launch {
            preferences.globalSearchFilterState.changes().collectLatest { onlyShowHasResults ->
                state.update { it.copy(onlyShowHasResults = onlyShowHasResults) }
            }
        }
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    open suspend fun getEnabledSources(): List<Source> {
        return sourceManager.getAll()
            .filter { (it.lang in enabledLanguages || it.isLocal()) && "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    /**
     * The initial set of selected source ids. By default every enabled source is selected
     * (used by the migrate flow). The global search flow overrides this to use the persisted
     * selection, falling back to the pinned sources when nothing has been persisted yet.
     */
    protected open fun getInitialSelectedSourceIds(enabledSources: List<Source>): Set<Long> {
        return enabledSources.map { it.id }.toSet()
    }

    /** Load the source list for the picker without starting a search. */
    protected fun loadSources() {
        sourcesSetup.start()
    }

    private suspend fun getSelectedSources(): List<Source> {
        val enabledSources = getEnabledSources()

        val filter = extensionFilter
        if (!filter.isNullOrEmpty()) {
            return extensionManager.getLoadedExtensions()
                .filter { it.pkgName == filter }
                .flatMap { it.sources }
                .filter { it in enabledSources }
        }

        val selected = state.value.selectedSourceIds
        return enabledSources.filter { it.id in selected }
    }

    fun updateSearchQuery(query: String?) {
        state.update { it.copy(searchQuery = query) }
    }

    fun toggleFilterResults() {
        preferences.globalSearchFilterState.toggle()
    }

    /**
     * Toggle a single source's selection. Checking a source starts *its* search and adds its
     * results; unchecking cancels its in-flight request and removes its results. Nothing else
     * is re-queried.
     */
    fun toggleSource(source: Source) {
        val current = state.value.selectedSourceIds
        val nowSelected = source.id !in current
        val updated = if (nowSelected) current + source.id else current - source.id
        state.update { it.copy(selectedSourceIds = updated) }
        persistSelection(updated)

        if (nowSelected) {
            searchSingleSource(source)
        } else {
            removeSource(source)
        }
    }

    /**
     * Set the full selection at once (Select all / None), reconciling results by searching newly
     * added sources and removing dropped ones. No already-selected source is re-queried.
     */
    fun setSelectedSources(ids: Set<Long>) {
        val current = state.value.selectedSourceIds
        val toAdd = ids - current
        val toRemove = current - ids
        if (toAdd.isEmpty() && toRemove.isEmpty()) return

        state.update { it.copy(selectedSourceIds = ids) }
        persistSelection(ids)

        val enabledById = state.value.enabledSources.associateBy { it.id }
        toRemove.forEach { id -> enabledById[id]?.let { removeSource(it) } }
        toAdd.forEach { id -> enabledById[id]?.let { searchSingleSource(it) } }
    }

    private fun persistSelection(ids: Set<Long>) {
        selectedSourcesPref.set(ids.map { it.toString() }.toSet())
    }

    fun search() {
        val query = state.value.searchQuery
        if (query.isNullOrBlank()) return
        if (this.lastQuery == query) return
        this.lastQuery = query

        // Cancel any in-flight per-source searches and start fresh for the current selection.
        searchJob?.cancel()
        cancelSourceJobs()

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            sourcesSetup.await()
            val sources = getSelectedSources()
            if (!isActive) return@launch
            updateItems(sources.associateWith { SearchItemResult.Loading })
            sources.forEach { searchSingleSource(it, query) }
        }
    }

    private fun cancelSourceJobs() {
        synchronized(sourceJobs) {
            sourceJobs.values.forEach { it.cancel() }
            sourceJobs.clear()
        }
    }

    /**
     * Launch (or relaunch) the search for a single source, tracking its job so it can be cancelled.
     */
    private fun searchSingleSource(source: Source, query: String? = lastQuery) {
        if (query.isNullOrBlank()) return

        updateItem(source, SearchItemResult.Loading)

        val job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                val page = withContext(coroutineDispatcher) {
                    source.getSearchManga(1, query, source.getFilterList())
                }

                val titles = page.mangas
                    .map { it.toDomainManga(source.id) }
                    .distinctBy { it.url }
                    .let { networkToLocalManga(it) }

                if (isActive) {
                    updateItem(source, SearchItemResult.Success(titles))
                }
            } catch (e: Exception) {
                if (isActive) {
                    updateItem(source, SearchItemResult.Error(e))
                }
            }
        }
        synchronized(sourceJobs) {
            sourceJobs.put(source.id, job)?.cancel()
        }
        job.start()
    }

    private fun removeSource(source: Source) {
        synchronized(sourceJobs) {
            sourceJobs.remove(source.id)?.cancel()
        }
        state.update {
            val items = it.items - source
            it.copy(items = items.toSortedMap(sortComparator(items)))
        }
    }

    private fun updateItems(items: Map<Source, SearchItemResult>) {
        state.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items)),
            )
        }
    }

    private fun updateItem(source: Source, result: SearchItemResult) {
        state.update { currentState ->
            val newItems = currentState.items + (source to result)
            currentState.copy(
                items = newItems.toSortedMap(sortComparator(newItems)),
            )
        }
    }

    fun setMigrateDialog(currentId: Long, target: Manga) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = getManga.await(currentId) ?: return@launch
            state.update { it.copy(dialog = Dialog.Migrate(target, current)) }
        }
    }

    fun clearDialog() {
        state.update { it.copy(dialog = null) }
    }

    @Immutable
    data class State(
        val from: Manga? = null,
        val searchQuery: String? = null,
        val enabledSources: List<Source> = emptyList(),
        val selectedSourceIds: Set<Long> = emptySet(),
        val onlyShowHasResults: Boolean = false,
        val items: Map<Source, SearchItemResult> = mapOf(),
        val dialog: Dialog? = null,
    ) {
        val progress: Int = items.count { it.value !is SearchItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (_, result) -> result.isVisible(onlyShowHasResults) }
    }

    sealed interface Dialog {
        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }
}

sealed interface SearchItemResult {
    data object Loading : SearchItemResult

    data class Error(
        val throwable: Throwable,
    ) : SearchItemResult

    data class Success(
        val result: List<Manga>,
    ) : SearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
