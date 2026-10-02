package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.domain.chapter.service.ChapterPinStore
import eu.kanade.domain.chapter.service.ResolvedPin
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.R
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderTopBar(
    mangaTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    bookmarked: Boolean,
    pin: ResolvedPin?,
    pinStore: ChapterPinStore,
    onBookmarkTap: () -> Unit,
    onPinToSection: (Long) -> Unit,
    onUnpinFromSection: () -> Unit,
    onNewSection: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val sectionName = pin?.section?.name
    AppBar(
        modifier = modifier,
        backgroundColor = Color.Transparent,
        title = mangaTitle,
        subtitle = if (sectionName != null && chapterTitle != null) "$chapterTitle · $sectionName" else chapterTitle,
        navigateUp = navigateUp,
        actions = {
            NotesButton(
                hasNote = !pin?.note.isNullOrBlank(),
                tint = pin?.section?.let { Color(it.color) },
                onClick = onOpenNotes,
            )
            BookmarkPinButton(
                bookmarked = bookmarked,
                pin = pin,
                pinStore = pinStore,
                onTap = onBookmarkTap,
                onPinToSection = onPinToSection,
                onUnpinFromSection = onUnpinFromSection,
                onNewSection = onNewSection,
            )
            AppBarActions(
                actions = buildList {
                    onOpenInWebView?.let {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_open_in_web_view),
                                onClick = it,
                            ),
                        )
                    }
                    onOpenInBrowser?.let {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_open_in_browser),
                                onClick = it,
                            ),
                        )
                    }
                    onShare?.let {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_share),
                                onClick = it,
                            ),
                        )
                    }
                },
            )
        },
    )
}

@Composable
private fun NotesButton(hasNote: Boolean, tint: Color?, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(
                if (hasNote) R.drawable.ic_chapter_note_24dp else R.drawable.ic_chapter_note_outline_24dp,
            ),
            contentDescription = "Notes",
            tint = if (hasNote) tint ?: MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

/**
 * Tap = bookmark (or quick-pin to the last section); long-press = section menu right under the button.
 */
@Composable
private fun RowScope.BookmarkPinButton(
    bookmarked: Boolean,
    pin: ResolvedPin?,
    pinStore: ChapterPinStore,
    onTap: () -> Unit,
    onPinToSection: (Long) -> Unit,
    onUnpinFromSection: () -> Unit,
    onNewSection: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val sectionColor = pin?.section?.let { Color(it.color) }

    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    indication = ripple(bounded = false, radius = 20.dp),
                    interactionSource = null,
                    onClick = onTap,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                    onClickLabel = "Bookmark",
                    onLongClickLabel = "Pin to section",
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (bookmarked) R.drawable.ic_bookmark_pin_24dp else R.drawable.ic_bookmark_pin_outline_24dp,
                ),
                contentDescription = "Bookmark (hold to pin)",
                tint = when {
                    sectionColor != null -> sectionColor
                    bookmarked -> MaterialTheme.colorScheme.primary
                    else -> LocalContentColor.current
                },
            )
        }

        if (menuOpen) {
            BookmarkPinMenu(
                pin = pin,
                pinStore = pinStore,
                onDismiss = { menuOpen = false },
                onPinToSection = {
                    menuOpen = false
                    onPinToSection(it)
                },
                onUnpinFromSection = {
                    menuOpen = false
                    onUnpinFromSection()
                },
                onNewSection = {
                    menuOpen = false
                    onNewSection()
                },
            )
        }
    }
}

@Composable
private fun BookmarkPinMenu(
    pin: ResolvedPin?,
    pinStore: ChapterPinStore,
    onDismiss: () -> Unit,
    onPinToSection: (Long) -> Unit,
    onUnpinFromSection: () -> Unit,
    onNewSection: () -> Unit,
) {
    val sections by pinStore.sections.collectAsState(pinStore.getSections())
    val quickPin by pinStore.quickPinToLast.changes().collectAsState(pinStore.quickPinToLast.get())
    val lastId by pinStore.lastSection.changes().collectAsState(pinStore.lastSection.get())
    val last = sections.find { it.id == lastId }

    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        Text(
            text = "PIN TO SECTION",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        sections.forEach { section ->
            DropdownMenuItem(
                text = { Text(section.name) },
                leadingIcon = { Icon(Icons.Filled.Bookmark, null, tint = Color(section.color)) },
                trailingIcon = if (pin?.section?.id == section.id) {
                    { Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) }
                } else {
                    null
                },
                onClick = { onPinToSection(section.id) },
            )
        }
        DropdownMenuItem(
            text = { Text("New section…") },
            leadingIcon = { Icon(Icons.Outlined.Add, null) },
            onClick = onNewSection,
        )
        if (pin?.section != null) {
            DropdownMenuItem(
                text = { Text("Unpin (keep bookmark)") },
                leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                onClick = onUnpinFromSection,
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Column {
                    Text("Tap pins to last section")
                    Text(
                        text = last?.let { "Last: ${it.name}" } ?: "Pick a section first",
                        style = MaterialTheme.typography.bodySmall,
                        color = last?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingIcon = {
                Checkbox(checked = quickPin, onCheckedChange = null)
            },
            onClick = { pinStore.quickPinToLast.set(!quickPin) },
        )
    }
}
