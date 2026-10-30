package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.domain.chapter.service.ChapterPinStore
import eu.kanade.domain.chapter.service.PinSection
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.MenuBook
import mihon.icons.materialsymbols.rounded.Public

/**
 * Rename, recolor, change scope, delete and add sections, across entries. Only [sections] are
 * listed (on the Pinned screen that's the sections visible on the current side, so OtherSide-only
 * ones stay hidden). [entries] are the entries a new entry section can belong to.
 */
@Composable
fun ManageSectionsDialog(
    store: ChapterPinStore,
    sections: List<PinSection>,
    entries: List<EntryRef>,
    ownerTitle: (PinSection) -> String?,
    contextEntry: String?,
    subtitle: String,
    startAdding: Boolean,
    onDismissRequest: () -> Unit,
) {
    val draft = rememberNewSectionDraft(store, entries, contextEntry, startOpen = startAdding)
    val changeScope = rememberSectionScopeChanger(store, contextEntry)

    @Composable
    fun Rows(list: List<PinSection>) {
        if (list.isEmpty()) {
            Text("None", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        list.forEach { section ->
            ManageSectionRow(
                section = section,
                scopeLabel = if (section.isShared) "All" else ownerTitle(section) ?: "One entry",
                onScopeClick = { changeScope(section) },
                onRename = { store.renameSection(section.id, it) },
                onRecolor = { store.recolorSection(section.id, it) },
                onDelete = { store.deleteSection(section.id) },
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Sections") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionGroupLabel(MaterialSymbols.Rounded.Public, "All entries")
                Rows(sections.filter { it.isShared })
                SectionGroupLabel(MaterialSymbols.Rounded.MenuBook, "Entry sections")
                Rows(sections.filterNot { it.isShared })
                if (draft.open) {
                    NewSectionForm(draft = draft, entries = entries, onAdd = { draft.commit() })
                } else {
                    TextButton(onClick = { draft.open = true }) { Text("+ New section") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Done") }
        },
    )
}
