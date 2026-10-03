package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Public
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.domain.chapter.service.ChapterPinStore
import eu.kanade.domain.chapter.service.PinSection
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
    entryKey: String?,
    onBookmarkTap: () -> Unit,
    onToggleSection: (Long) -> Unit,
    onUnpinFromSections: () -> Unit,
    onNewSection: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val sectionLabel = pin?.primary?.let { first ->
        val extra = pin.sections.size - 1
        if (extra > 0) "${first.name} +$extra" else first.name
    }
    AppBar(
        modifier = modifier,
        backgroundColor = Color.Transparent,
        title = mangaTitle,
        subtitle = if (sectionLabel != null && chapterTitle != null) "$chapterTitle · $sectionLabel" else chapterTitle,
        navigateUp = navigateUp,
        actions = {
            NotesButton(
                hasNote = !pin?.note.isNullOrBlank(),
                tint = pin?.primary?.let { Color(it.color) },
                onClick = onOpenNotes,
            )
            BookmarkPinButton(
                bookmarked = bookmarked,
                pin = pin,
                pinStore = pinStore,
                entryKey = entryKey,
                onTap = onBookmarkTap,
                onToggleSection = onToggleSection,
                onUnpinFromSections = onUnpinFromSections,
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
    entryKey: String?,
    onTap: () -> Unit,
    onToggleSection: (Long) -> Unit,
    onUnpinFromSections: () -> Unit,
    onNewSection: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val sectionColor = pin?.primary?.let { Color(it.color) }

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
                entryKey = entryKey,
                onDismiss = { menuOpen = false },
                // Stays open so several sections can be ticked in one go
                onToggleSection = onToggleSection,
                onUnpinFromSections = {
                    menuOpen = false
                    onUnpinFromSections()
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
    entryKey: String?,
    onDismiss: () -> Unit,
    onToggleSection: (Long) -> Unit,
    onUnpinFromSections: () -> Unit,
    onNewSection: () -> Unit,
) {
    val sections by pinStore.sections.collectAsState(pinStore.getSections())
    val quickPin by pinStore.quickPinToLast.changes().collectAsState(pinStore.quickPinToLast.get())
    val lastIds by pinStore.lastSections.collectAsState(emptyMap())
    val own = sections.filter { it.manga == entryKey }
    val shared = sections.filter { it.isShared }
    val last = lastIds[entryKey]?.let { id -> (own + shared).find { it.id == id } }

    @Composable
    fun Header(icon: ImageVector, text: String) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        val pinnedIds = pin?.sections.orEmpty().map { it.id }
        @Composable
        fun Item(section: PinSection) {
            DropdownMenuItem(
                text = { Text(section.name) },
                leadingIcon = { Icon(Icons.Filled.Bookmark, null, tint = Color(section.color)) },
                trailingIcon = { Checkbox(checked = section.id in pinnedIds, onCheckedChange = null) },
                onClick = { onToggleSection(section.id) },
            )
        }
        Header(Icons.AutoMirrored.Outlined.MenuBook, "THIS ENTRY")
        own.forEach { Item(it) }
        if (own.isEmpty()) {
            Text(
                text = "No sections for this entry yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Header(Icons.Outlined.Public, "ALL ENTRIES")
        shared.forEach { Item(it) }
        DropdownMenuItem(
            text = { Text("New section…") },
            leadingIcon = { Icon(Icons.Outlined.Add, null) },
            onClick = onNewSection,
        )
        if (pinnedIds.isNotEmpty()) {
            DropdownMenuItem(
                text = { Text(if (pinnedIds.size > 1) "Unpin from all (keep bookmark)" else "Unpin (keep bookmark)") },
                leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                onClick = onUnpinFromSections,
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Column {
                    Text("Tap pins to last section")
                    Text(
                        text = last?.let { "Last here: ${it.name}" } ?: "Pick a section first",
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
