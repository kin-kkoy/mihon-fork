package eu.kanade.presentation.category.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.kanade.core.preference.asToggleableState
import eu.kanade.presentation.category.visualName
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.OtherSideColorScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

@Composable
fun CategoryCreateDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String) -> Unit,
    categories: List<String>,
) {
    var name by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }
    val nameAlreadyExists = remember(name) { categories.contains(name) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = name.isNotEmpty() && !nameAlreadyExists,
                onClick = {
                    onCreate(name)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_add_category))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier
                    .focusRequester(focusRequester),
                value = name,
                onValueChange = { name = it },
                label = {
                    Text(text = stringResource(MR.strings.name))
                },
                supportingText = {
                    val msgRes = if (name.isNotEmpty() && nameAlreadyExists) {
                        MR.strings.error_category_exists
                    } else {
                        MR.strings.information_required_plain
                    }
                    Text(text = stringResource(msgRes))
                },
                isError = name.isNotEmpty() && nameAlreadyExists,
                singleLine = true,
            )
        },
    )

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun CategoryRenameDialog(
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
    categories: List<String>,
    category: String,
) {
    var name by remember { mutableStateOf(category) }
    var valueHasChanged by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val nameAlreadyExists = remember(name) { categories.contains(name) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = valueHasChanged && !nameAlreadyExists,
                onClick = {
                    onRename(name)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_rename_category))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier.focusRequester(focusRequester),
                value = name,
                onValueChange = {
                    valueHasChanged = name != it
                    name = it
                },
                label = { Text(text = stringResource(MR.strings.name)) },
                supportingText = {
                    val msgRes = if (valueHasChanged && nameAlreadyExists) {
                        MR.strings.error_category_exists
                    } else {
                        MR.strings.information_required_plain
                    }
                    Text(text = stringResource(msgRes))
                },
                isError = valueHasChanged && nameAlreadyExists,
                singleLine = true,
            )
        },
    )

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun CategoryDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    category: String,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onDelete()
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.delete_category))
        },
        text = {
            Text(text = stringResource(MR.strings.delete_category_confirmation, category))
        },
    )
}

@Composable
fun ChangeCategoryDialog(
    initialSelection: List<CheckboxState<Category>>,
    onDismissRequest: () -> Unit,
    onEditCategories: () -> Unit,
    onConfirm: (List<Long>, List<Long>) -> Unit,
) {
    if (initialSelection.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                tachiyomi.presentation.core.components.material.TextButton(
                    onClick = {
                        onDismissRequest()
                        onEditCategories()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_edit_categories))
                }
            },
            title = {
                Text(text = stringResource(MR.strings.action_move_category))
            },
            text = {
                Text(text = stringResource(MR.strings.information_empty_category_dialog))
            },
        )
        return
    }
    // Categories that belong to the "OtherSide" are hidden behind the yin-yang toggle.
    val otherSideIds = remember { Injekt.get<LibraryPreferences>().otherSideCategoryIds.get() }
    var normalSelection by remember {
        mutableStateOf(initialSelection.filter { it.value.id.toString() !in otherSideIds })
    }
    var otherSideSelection by remember {
        mutableStateOf(initialSelection.filter { it.value.id.toString() in otherSideIds })
    }
    var otherSideMode by remember { mutableStateOf(false) }

    // Circular-reveal / ripple state (snapshot version). Every toggle flips the icon AND
    // plays this reveal — the two are driven by the same lambda so they are inseparable.
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    var boxWindowOrigin by remember { mutableStateOf(Offset.Zero) }
    var buttonWindowCenter by remember { mutableStateOf(Offset.Zero) }
    var revealing by remember { mutableStateOf(false) }
    var oldSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    val revealProgress = remember { Animatable(0f) }

    val onToggleOtherSide: () -> Unit = {
        scope.launch {
            // Snapshot the OLD-themed content, flip the mode+theme, then wipe a growing
            // circle so the NEW theme is revealed from behind the frozen old frame.
            val old = runCatching { graphicsLayer.toImageBitmap() }.getOrNull()
            otherSideMode = !otherSideMode
            oldSnapshot = old
            revealing = old != null
            if (old != null) {
                revealProgress.snapTo(0f)
                revealProgress.animateTo(1f, animationSpec = tween(durationMillis = 450))
            }
            revealing = false
            oldSnapshot = null
        }
    }

    val shownSelection = if (otherSideMode) otherSideSelection else normalSelection
    val setShownSelection: (List<CheckboxState<Category>>) -> Unit = { updated ->
        if (otherSideMode) otherSideSelection = updated else normalSelection = updated
    }

    MaterialTheme(colorScheme = if (otherSideMode) OtherSideColorScheme else MaterialTheme.colorScheme) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                Row {
                    tachiyomi.presentation.core.components.material.TextButton(onClick = {
                        onDismissRequest()
                        onEditCategories()
                    }) {
                        Text(text = stringResource(MR.strings.action_edit))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    tachiyomi.presentation.core.components.material.TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    tachiyomi.presentation.core.components.material.TextButton(
                        onClick = {
                            onDismissRequest()
                            // Only diff the currently-shown side, so the other side's
                            // assignments are left untouched.
                            onConfirm(
                                shownSelection
                                    .filter {
                                        it is CheckboxState.State.Checked || it is CheckboxState.TriState.Include
                                    }
                                    .map { it.value.id },
                                shownSelection
                                    .filter { it is CheckboxState.State.None || it is CheckboxState.TriState.None }
                                    .map { it.value.id },
                            )
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(MR.strings.action_move_category))
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onToggleOtherSide,
                        modifier = Modifier.onGloballyPositioned {
                            buttonWindowCenter = it.boundsInWindow().center
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_otherside),
                            contentDescription = "Cross to OtherSide",
                            tint = if (otherSideMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier.onGloballyPositioned { boxWindowOrigin = it.positionInWindow() },
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .drawWithContent {
                                graphicsLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(graphicsLayer)
                            },
                    ) {
                        shownSelection.forEach { checkbox ->
                            val onChange: (CheckboxState<Category>) -> Unit = {
                                val index = shownSelection.indexOf(it)
                                if (index != -1) {
                                    val mutableList = shownSelection.toMutableList()
                                    mutableList[index] = it.next()
                                    setShownSelection(mutableList.toList())
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onChange(checkbox) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                when (checkbox) {
                                    is CheckboxState.TriState -> {
                                        TriStateCheckbox(
                                            state = checkbox.asToggleableState(),
                                            onClick = { onChange(checkbox) },
                                        )
                                    }
                                    is CheckboxState.State -> {
                                        Checkbox(
                                            checked = checkbox.isChecked,
                                            onCheckedChange = { onChange(checkbox) },
                                        )
                                    }
                                }

                                Text(
                                    text = checkbox.value.visualName,
                                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                                )
                            }
                        }
                    }

                    val snapshot = oldSnapshot
                    if (revealing && snapshot != null) {
                        // Draw the frozen OLD frame everywhere EXCEPT inside the growing circle,
                        // so the re-themed content underneath is revealed by the sweeping edge.
                        Canvas(modifier = Modifier.matchParentSize()) {
                            if (size.minDimension <= 0f) return@Canvas
                            val origin = buttonWindowCenter - boxWindowOrigin
                            val maxRadius = listOf(
                                Offset(0f, 0f),
                                Offset(size.width, 0f),
                                Offset(0f, size.height),
                                Offset(size.width, size.height),
                            ).maxOf { (it - origin).getDistance() }
                            val radius = revealProgress.value * maxRadius
                            val path = Path().apply {
                                addRect(Rect(Offset.Zero, size))
                                addOval(Rect(center = origin, radius = radius))
                                fillType = PathFillType.EvenOdd
                            }
                            clipPath(path) {
                                drawImage(image = snapshot)
                            }
                            // Leading-edge ripple: a faded-white ring at the wavefront.
                            val ringAlpha = (0.35f * (1f - revealProgress.value)).coerceAtLeast(0f)
                            if (ringAlpha > 0f && radius > 0f) {
                                drawCircle(
                                    color = Color.White.copy(alpha = ringAlpha),
                                    radius = radius,
                                    center = origin,
                                    style = Stroke(width = 3.dp.toPx()),
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}
