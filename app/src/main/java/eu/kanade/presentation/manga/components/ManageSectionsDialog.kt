package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.domain.chapter.service.ChapterPinStore
import eu.kanade.domain.chapter.service.PinSection

/**
 * Rename, recolor, delete and add sections. Only [sections] are listed (on the Pinned screen
 * that's the sections visible on the current side, so OtherSide-only ones stay hidden).
 */
@Composable
fun ManageSectionsDialog(
    store: ChapterPinStore,
    sections: List<PinSection>,
    subtitle: String,
    startAdding: Boolean,
    onDismissRequest: () -> Unit,
) {
    var adding by remember { mutableStateOf(startAdding) }
    var newName by remember { mutableStateOf("") }
    var newColor by remember { mutableStateOf(ChapterPinStore.PALETTE[sections.size % ChapterPinStore.PALETTE.size]) }

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
                if (sections.isEmpty() && !adding) {
                    Text(
                        text = "No sections yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                sections.forEach { section ->
                    ManageSectionRow(
                        section = section,
                        onRename = { store.renameSection(section.id, it) },
                        onRecolor = {
                            val palette = ChapterPinStore.PALETTE
                            store.recolorSection(section.id, palette[(palette.indexOf(section.color) + 1) % palette.size])
                        },
                        onDelete = { store.deleteSection(section.id) },
                    )
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
                                store.addSection(newName, newColor)
                                adding = false
                                newName = ""
                                newColor = ChapterPinStore.PALETTE[(sections.size + 1) % ChapterPinStore.PALETTE.size]
                            },
                        ) { Text("Add") }
                    }
                } else {
                    TextButton(onClick = { adding = true }) { Text("+ New section") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Done") }
        },
    )
}
