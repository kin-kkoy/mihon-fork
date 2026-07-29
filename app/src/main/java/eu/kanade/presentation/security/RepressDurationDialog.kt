package eu.kanade.presentation.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.security.RepressManager

/**
 * Reusable duration picker for starting a Repression or extending one. Presets only; the minimum
 * (one day) is enforced by [RepressManager.repress] regardless. Reused by the security settings
 * screen, the More screen, and (later) the in-OtherSide shortcut.
 *
 * @param onConfirm invoked with the chosen duration in millis.
 */
@Composable
fun RepressDurationDialog(
    title: String,
    warning: String,
    confirmLabel: String,
    onConfirm: (millis: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(RepressPresets.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(warning)
                RepressPresets.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = preset == selected,
                                onClick = { selected = preset },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = preset == selected,
                            onClick = { selected = preset },
                        )
                        Text(
                            text = preset.label,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.millis) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private data class RepressPreset(val label: String, val millis: Long)

private val RepressPresets = listOf(
    RepressPreset("1 day", RepressManager.ONE_DAY),
    RepressPreset("3 days", 3 * RepressManager.ONE_DAY),
    RepressPreset("1 week", 7 * RepressManager.ONE_DAY),
    RepressPreset("1 month", 30 * RepressManager.ONE_DAY),
)
