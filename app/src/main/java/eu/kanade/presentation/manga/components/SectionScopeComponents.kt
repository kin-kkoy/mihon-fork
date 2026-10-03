package eu.kanade.presentation.manga.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.chapter.service.ChapterPinStore
import eu.kanade.domain.chapter.service.PinSection
import eu.kanade.tachiyomi.util.system.toast

/** An entry (manga) a section can belong to: its [ChapterPinStore.mangaKey] and title. */
data class EntryRef(val key: String, val title: String)

/**
 * The section list of the pin dialogs for one entry: "This entry" sections first, then
 * "All entries" ones. Normal mode ticks sections; [managing] mode renames, recolors, changes
 * scope or deletes. Ends with the new-section form (or its "+ New section" button).
 */
@Composable
internal fun SectionPickerList(
    store: ChapterPinStore,
    entry: EntryRef,
    picks: List<Long>,
    onPicksChange: (List<Long>) -> Unit,
    managing: Boolean,
    startAdding: Boolean = false,
    showCounts: Boolean = false,
) {
    val sections by store.sections.collectAsState(store.getSections())
    val pins by store.pins.collectAsState(store.getPins())
    var adding by remember { mutableStateOf(startAdding) }
    val changeScope = rememberSectionScopeChanger(store, contextEntry = entry.key)

    val own = sections.filter { it.manga == entry.key }
    val shared = sections.filter { it.isShared }

    @Composable
    fun Rows(list: List<PinSection>, empty: String) {
        if (list.isEmpty()) {
            Text(empty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        list.forEach { section ->
            if (managing) {
                ManageSectionRow(
                    section = section,
                    scopeLabel = if (section.isShared) "All" else entry.title,
                    onScopeClick = { changeScope(section) },
                    onRename = { store.renameSection(section.id, it) },
                    onRecolor = { store.recolorSection(section.id, nextColor(section.color)) },
                    onDelete = {
                        store.deleteSection(section.id)
                        onPicksChange(picks - section.id)
                    },
                )
            } else {
                SectionOption(
                    section = section,
                    count = if (showCounts) pins.values.count { section.id in it.sectionIds } else null,
                    selected = section.id in picks,
                    onClick = { onPicksChange(if (section.id in picks) picks - section.id else picks + section.id) },
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionGroupLabel(Icons.AutoMirrored.Outlined.MenuBook, "This entry")
        Rows(own, "No sections for this entry yet")
        SectionGroupLabel(Icons.Outlined.Public, "All entries")
        Rows(shared, "No shared sections")
        if (adding) {
            NewSectionForm(
                store = store,
                entries = listOf(entry),
                initialEntry = entry.key,
                onAdded = {
                    onPicksChange(picks + it)
                    adding = false
                },
                onCancel = { adding = false },
            )
        } else if (!managing) {
            TextButton(onClick = { adding = true }) { Text("+ New section") }
        }
    }
}

@Composable
internal fun SectionGroupLabel(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Name + color + scope for a new section. Scope defaults to one entry ([initialEntry]); when
 * several [entries] are possible a dropdown picks which. With no entries it can only be shared.
 */
@Composable
internal fun NewSectionForm(
    store: ChapterPinStore,
    entries: List<EntryRef>,
    initialEntry: String?,
    onAdded: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(ChapterPinStore.PALETTE[store.getSections().size % ChapterPinStore.PALETTE.size]) }
    var shared by remember { mutableStateOf(entries.isEmpty()) }
    var entryKey by remember { mutableStateOf(initialEntry ?: entries.firstOrNull()?.key) }
    var pickerOpen by remember { mutableStateOf(false) }
    val entry = entries.find { it.key == entryKey }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("Section name, e.g. Re-read") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChapterPinStore.PALETTE.forEach { c -> ColorDot(color = Color(c), selected = c == color, onClick = { color = c }) }
        }
        if (entries.isNotEmpty()) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !shared,
                    onClick = { shared = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(16.dp)) },
                ) { Text(if (entries.size == 1) "This entry" else "One entry", maxLines = 1) }
                SegmentedButton(
                    selected = shared,
                    onClick = { shared = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Outlined.Public, null, Modifier.size(16.dp)) },
                ) { Text("All entries", maxLines = 1) }
            }
            if (!shared && entries.size > 1) {
                Box {
                    Surface(
                        onClick = { pickerOpen = true },
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = entry?.title ?: "Pick an entry",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Outlined.ArrowDropDown, null)
                        }
                    }
                    DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                        entries.forEach { e ->
                            DropdownMenuItem(
                                text = { Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    entryKey = e.key
                                    pickerOpen = false
                                },
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = (if (shared) "Shows when pinning in any entry." else "Only shows when pinning in ${entry?.title ?: "that entry"}.") +
                " You can change this later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(
                enabled = name.isNotBlank() && (shared || entry != null),
                onClick = { onAdded(store.addSection(name, color, if (shared) null else entry?.key)) },
            ) { Text("Add") }
        }
    }
}

/** Small tappable chip on a managed section showing its scope ("All" or the entry's title). */
@Composable
internal fun ScopeChip(shared: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = Color.Transparent,
        modifier = Modifier.widthIn(max = 104.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (shared) Icons.Outlined.Public else Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = if (shared) "Shared by all entries" else "This entry only",
                modifier = Modifier.size(14.dp),
                tint = if (shared) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Returns a function that flips a section's scope. Entry → shared always works. Shared → entry
 * works directly when one entry (or none, given [contextEntry]) uses it; when several do, it asks
 * to split it into one section per entry.
 */
@Composable
internal fun rememberSectionScopeChanger(store: ChapterPinStore, contextEntry: String?): (PinSection) -> Unit {
    val context = LocalContext.current
    var pendingSplit by remember { mutableStateOf<Pair<PinSection, Int>?>(null) }
    pendingSplit?.let { (section, users) ->
        AlertDialog(
            onDismissRequest = { pendingSplit = null },
            title = { Text("Split \"${section.name}\"?") },
            text = {
                Text(
                    "It's used in $users entries. Making it entry-only splits it into one section per entry, " +
                        "each keeping the same name, color and chapters.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.splitSection(section.id)
                    pendingSplit = null
                }) { Text("Split") }
            },
            dismissButton = { TextButton(onClick = { pendingSplit = null }) { Text("Cancel") } },
        )
    }
    return { section ->
        if (!section.isShared) {
            store.setScope(section.id, null)
        } else {
            val users = store.mangaUsing(section.id)
            when {
                users.size > 1 -> pendingSplit = section to users.size
                users.size == 1 -> store.setScope(section.id, users.first())
                contextEntry != null -> store.setScope(section.id, contextEntry)
                else -> context.toast("Pin a chapter to it first, so it knows which entry")
            }
        }
    }
}

internal fun nextColor(color: Int): Int {
    val palette = ChapterPinStore.PALETTE
    return palette[(palette.indexOf(color) + 1) % palette.size]
}
