package eu.kanade.presentation.manga.components

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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.domain.chapter.service.ChapterPinStore
import eu.kanade.domain.chapter.service.PinSection

/**
 * "Pin to sections" sheet: tick (or create / manage) sections, add an optional note,
 * optionally from the user's own presets.
 */
@Composable
fun ChapterPinDialog(
    store: ChapterPinStore,
    chapterCount: Int,
    initialSectionIds: List<Long>,
    initialNote: String,
    canUnpin: Boolean,
    onPin: (sectionIds: List<Long>, note: String) -> Unit,
    onUnpin: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sections by store.sections.collectAsState(store.getSections())
    val pins by store.pins.collectAsState(store.getPins())
    val presets by store.presets.collectAsState(emptyList())

    var picks by remember { mutableStateOf(initialSectionIds) }
    var note by remember { mutableStateOf(initialNote) }
    var managing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newColor by remember { mutableStateOf(ChapterPinStore.PALETTE[sections.size % ChapterPinStore.PALETTE.size]) }
    var managingPresets by remember { mutableStateOf(false) }

    val validPicks = picks.filter { id -> sections.any { it.id == id } }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Pin to sections", modifier = Modifier.weight(1f))
                if (sections.isNotEmpty()) {
                    TextButton(onClick = { managing = !managing; adding = false }) {
                        Text(if (managing) "Done" else "Manage")
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = buildString {
                        append(if (chapterCount == 1) "1 chapter" else "$chapterCount chapters")
                        if (chapterCount > 1 && !managing) append(" · ticked sections are added to each")
                        if (managing) append(" · rename, recolor or delete sections")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (sections.isEmpty() && !adding) {
                    Text(
                        text = "No sections yet — create one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                sections.forEach { section ->
                    if (managing) {
                        ManageSectionRow(
                            section = section,
                            onRename = { store.renameSection(section.id, it) },
                            onRecolor = {
                                val palette = ChapterPinStore.PALETTE
                                val next = palette[(palette.indexOf(section.color) + 1) % palette.size]
                                store.recolorSection(section.id, next)
                            },
                            onDelete = {
                                store.deleteSection(section.id)
                                picks = picks - section.id
                            },
                        )
                    } else {
                        SectionOption(
                            section = section,
                            count = pins.values.count { section.id in it.sectionIds },
                            selected = section.id in validPicks,
                            onClick = { picks = if (section.id in picks) picks - section.id else picks + section.id },
                        )
                    }
                }

                if (adding) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("Section name, e.g. Re-read") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChapterPinStore.PALETTE.forEach { c ->
                            ColorDot(color = Color(c), selected = c == newColor, onClick = { newColor = c })
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { adding = false; newName = "" }) { Text("Cancel") }
                        TextButton(
                            enabled = newName.isNotBlank(),
                            onClick = {
                                picks = picks + store.addSection(newName, newColor)
                                adding = false
                                newName = ""
                                newColor = ChapterPinStore.PALETTE[(sections.size + 1) % ChapterPinStore.PALETTE.size]
                            },
                        ) { Text("Add") }
                    }
                } else if (!managing) {
                    TextButton(onClick = { adding = true }) { Text("+ New section") }
                }

                if (!managing) {
                    HorizontalDivider()
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { Text("Note (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
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
                            text = "No presets yet. Type a note above and tap \"Save as preset\".",
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
                }
            }
        },
        confirmButton = {
            if (managing) {
                TextButton(onClick = { managing = false }) { Text("Done") }
            } else {
                TextButton(
                    enabled = validPicks.isNotEmpty(),
                    onClick = { onPin(validPicks, note) },
                ) { Text("Pin") }
            }
        },
        dismissButton = {
            Row {
                if (canUnpin && !managing) {
                    TextButton(onClick = onUnpin) { Text("Unpin") }
                }
                TextButton(onClick = onDismissRequest) { Text("Cancel") }
            }
        },
    )
}

@Composable
internal fun SectionOption(
    section: PinSection,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = Color(section.color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = if (selected) color else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .background(if (selected) color.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Bookmark, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text = section.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun ManageSectionRow(
    section: PinSection,
    onRename: (String) -> Unit,
    onRecolor: () -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(section.id) { mutableStateOf(section.name) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ColorDot(color = Color(section.color), selected = false, onClick = onRecolor)
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                onRename(it)
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Close, contentDescription = "Delete section")
        }
    }
}

@Composable
internal fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
    )
}
