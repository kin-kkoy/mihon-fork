package eu.kanade.presentation.manga.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.kanade.domain.chapter.service.ChapterPinStore

/**
 * The preset color swatches plus a rainbow "+" that opens a custom picker (saturation/brightness
 * square, hue bar, hex field). A custom color shows in the "+" swatch's place.
 */
@Composable
internal fun SectionColorSwatches(color: Int, onColorChange: (Int) -> Unit) {
    var pickerOpen by remember { mutableStateOf(false) }
    val isCustom = color !in ChapterPinStore.PALETTE

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChapterPinStore.PALETTE.forEach { c ->
                ColorDot(
                    color = Color(c),
                    selected = c == color,
                    onClick = {
                        onColorChange(c)
                        pickerOpen = false
                    },
                )
            }
            CustomColorDot(
                color = if (isCustom) Color(color) else null,
                selected = isCustom || pickerOpen,
                onClick = { pickerOpen = !pickerOpen },
            )
        }
        AnimatedVisibility(visible = pickerOpen) {
            ColorPickerPanel(color = color, onColorChange = onColorChange)
        }
    }
}

/** Rainbow "+" swatch, or the custom [color] once one is picked. */
@Composable
private fun CustomColorDot(color: Color?, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color?.let { SolidColor(it) } ?: Brush.sweepGradient(RAINBOW))
            .border(2.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (color == null) {
            Text("+", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ColorPickerPanel(color: Int, onColorChange: (Int) -> Unit) {
    // Hue/saturation/brightness are kept here (not re-derived from the color) so the hue survives
    // dragging to pure black or gray
    val initial = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) } }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var sat by remember { mutableFloatStateOf(initial[1]) }
    var value by remember { mutableFloatStateOf(initial[2]) }
    var hex by remember { mutableStateOf(toHex(color)) }

    fun emit() {
        val c = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        hex = toHex(c)
        onColorChange(c)
    }

    LaunchedEffect(color) {
        if (parseHex(hex) != color) hex = toHex(color)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Saturation (left → right) and brightness (top → bottom)
        val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .dragPosition { pos, size ->
                    sat = (pos.x / size.width).coerceIn(0f, 1f)
                    value = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                    emit()
                },
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val knob = Offset(sat * size.width, (1f - value) * size.height)
            drawCircle(Color(color), radius = 9.dp.toPx(), center = knob)
            drawCircle(Color.White, radius = 9.dp.toPx(), center = knob, style = Stroke(2.dp.toPx()))
        }

        // Hue bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .dragPosition { pos, size ->
                    hue = (pos.x / size.width).coerceIn(0f, 1f) * 359.99f
                    emit()
                },
        ) {
            val barHeight = 14.dp.toPx()
            val top = (size.height - barHeight) / 2
            drawRoundRect(
                brush = Brush.horizontalGradient(RAINBOW),
                topLeft = Offset(0f, top),
                size = Size(size.width, barHeight),
                cornerRadius = CornerRadius(barHeight / 2),
            )
            val thumb = Offset(hue / 360f * size.width, size.height / 2)
            drawCircle(Color.White, radius = 10.dp.toPx(), center = thumb)
            drawCircle(Color.Black, radius = 10.dp.toPx(), center = thumb, style = Stroke(2.dp.toPx()))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(Color(color)))
            OutlinedTextField(
                value = hex,
                onValueChange = { text ->
                    hex = text.take(7)
                    parseHex(hex)?.let { c ->
                        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(c, it) }
                        hue = hsv[0]
                        sat = hsv[1]
                        value = hsv[2]
                        onColorChange(c)
                    }
                },
                label = { Text("Hex") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Reports the touch position on tap and while dragging (consumed, so a scrolling parent stays put). */
private fun Modifier.dragPosition(onPosition: (Offset, IntSize) -> Unit): Modifier = composed {
    val latest by rememberUpdatedState(onPosition)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            latest(down.position, size)
            drag(down.id) { change ->
                change.consume()
                latest(change.position, size)
            }
        }
    }
}

private val RAINBOW = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
)

private fun toHex(color: Int) = "#%06X".format(color and 0xFFFFFF)

private fun parseHex(text: String): Int? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6) return null
    return digits.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
}

/**
 * The new-section form's draft (name, color, scope), held by the dialog so its Save / Pin button can
 * add a half-typed section too, not only the form's own Add button.
 */
@Stable
internal class NewSectionDraft(
    private val store: ChapterPinStore,
    private val initialEntry: String?,
    private val defaultShared: Boolean,
    startOpen: Boolean,
) {
    var open by mutableStateOf(startOpen)
    var name by mutableStateOf("")
    var color by mutableIntStateOf(nextDefaultColor())
    var shared by mutableStateOf(defaultShared)
    var entryKey by mutableStateOf(initialEntry)

    /** Open with a name and a usable scope, so it would be added on Save. */
    val isPending: Boolean get() = open && name.isNotBlank() && (shared || entryKey != null)

    /** Adds the section if [isPending] and closes the form; returns the new id. */
    fun commit(): Long? {
        if (!isPending) return null
        val id = store.addSection(name, color, if (shared) null else entryKey)
        reset()
        return id
    }

    fun reset() {
        open = false
        name = ""
        color = nextDefaultColor()
        shared = defaultShared
        entryKey = initialEntry
    }

    private fun nextDefaultColor() = ChapterPinStore.PALETTE[store.getSections().size % ChapterPinStore.PALETTE.size]
}

/** A [NewSectionDraft]; with no [entries] a new section can only be shared. */
@Composable
internal fun rememberNewSectionDraft(
    store: ChapterPinStore,
    entries: List<EntryRef>,
    initialEntry: String?,
    startOpen: Boolean = false,
): NewSectionDraft = remember {
    NewSectionDraft(
        store = store,
        initialEntry = initialEntry ?: entries.firstOrNull()?.key,
        defaultShared = entries.isEmpty(),
        startOpen = startOpen,
    )
}
