package eu.kanade.presentation.manga.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.chapter.service.ChapterPinStore

/**
 * Reader notes dialog: the note (and the user's presets) is the main part; "Pin to sections"
 * sits below in a collapsible accordion. Saving with a note but no section is allowed.
 */
@Composable
fun ChapterNotesDialog(
    store: ChapterPinStore,
    entry: EntryRef,
    subtitle: String?,
    initialSectionIds: List<Long>,
    initialNote: String,
    startWithNewSection: Boolean,
    canClear: Boolean,
    onSave: (sectionIds: List<Long>, note: String) -> Unit,
    onClear: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sections by store.sections.collectAsState(store.getSections())
    val presets by store.presets.collectAsState(emptyList())

    var note by remember { mutableStateOf(initialNote) }
    var picks by remember { mutableStateOf(initialSectionIds) }
    var accordionOpen by remember { mutableStateOf(startWithNewSection) }
    var managing by remember { mutableStateOf(false) }
    var managingPresets by remember { mutableStateOf(false) }

    val picked = (sections.filter { it.manga == entry.key } + sections.filter { it.isShared }).filter { it.id in picks }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column {
                Text("Notes")
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Write a note about this chapter…") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "YOUR PRESETS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    if (presets.isNotEmpty()) {
                        TextButton(onClick = { managingPresets = !managingPresets }) {
                            Text(if (managingPresets) "Done" else "Manage")
                        }
                    }
                }
                if (presets.isEmpty()) {
                    Text(
                        text = "No presets yet. Type a note and tap \"Save as preset\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        presets.forEach { preset ->
                            Surface(
                                shape = CircleShape,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                color = Color.Transparent,
                                onClick = {
                                    if (managingPresets) {
                                        store.removePreset(preset)
                                        if (presets.size == 1) managingPresets = false
                                    } else {
                                        note = preset
                                    }
                                },
                            ) {
                                Text(
                                    text = if (managingPresets) "$preset  ✕" else preset,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                TextButton(
                    enabled = note.isNotBlank() && note.trim() !in presets,
                    onClick = { store.addPreset(note) },
                ) { Text("+ Save as preset") }

                // "Pin to sections" accordion
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                ) {
                    val caretRotation by animateFloatAsState(if (accordionOpen) 180f else 0f, label = "caret")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { accordionOpen = !accordionOpen }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Pin to sections", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                picked.forEach {
                                    Box(Modifier.size(8.dp).background(Color(it.color), CircleShape))
                                }
                                Text(
                                    text = when (picked.size) {
                                        0 -> "None"
                                        1 -> picked[0].name
                                        else -> "${picked[0].name} +${picked.size - 1}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            null,
                            Modifier.rotate(caretRotation),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    AnimatedVisibility(visible = accordionOpen) {
                        Column(
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (managing) {
                                        "Rename, recolor, change scope (tap the chip) or delete"
                                    } else {
                                        "Tap to add or remove (pick several)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                if (sections.isNotEmpty()) {
                                    TextButton(onClick = { managing = !managing }) {
                                        Text(if (managing) "Done" else "Manage")
                                    }
                                }
                            }
                            SectionPickerList(
                                store = store,
                                entry = entry,
                                picks = picks,
                                onPicksChange = { picks = it },
                                managing = managing,
                                startAdding = startWithNewSection,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(picks.filter { id -> sections.any { it.id == id } }, note) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (canClear) {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
                TextButton(onClick = onDismissRequest) { Text("Cancel") }
            }
        },
    )
}
