package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import eu.kanade.domain.source.service.SourcePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GlobalSearchScreenModel(
    initialQuery: String = "",
    initialExtensionFilter: String? = null,
) : SearchScreenModel(State(searchQuery = initialQuery)) {

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            search()
        }
    }

    override fun getInitialSelectedSourceIds(): Set<Long> {
        // Use Injekt directly instead of a field: this runs during super's init, before this
        // subclass's own property initializers have executed.
        val sourcePreferences = Injekt.get<SourcePreferences>()
        val saved = sourcePreferences.globalSearchSelectedSources.get()
        // First run (nothing persisted): fall back to the pinned sources so the picker isn't empty.
        val ids = saved.ifEmpty { sourcePreferences.pinnedSources.get() }
        return getEnabledSources()
            .map { it.id }
            .filter { it.toString() in ids }
            .toSet()
    }
}
