package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.download.model.Download
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground

@Composable
fun MangaChapterListItem(
    title: String,
    date: String?,
    readProgress: String?,
    scanlator: String?,
    read: Boolean,
    bookmark: Boolean,
    selected: Boolean,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDownloadClick: ((ChapterDownloadAction) -> Unit)?,
    onChapterSwipe: (LibraryPreferences.ChapterSwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    // Pinned to user sections: [pinColor] (first section) tints the bookmark and glow, each of
    // [pinExtraColors] (other sections) adds a dot after the title, the stripe is split between all
    pinColor: Color? = null,
    pinExtraColors: List<Color> = emptyList(),
    pinNote: String? = null,
) {
    val start = getSwipeAction(
        action = chapterSwipeStartAction,
        read = read,
        bookmark = bookmark,
        downloadState = downloadStateProvider(),
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = { onChapterSwipe(chapterSwipeStartAction) },
    )
    val end = getSwipeAction(
        action = chapterSwipeEndAction,
        read = read,
        bookmark = bookmark,
        downloadState = downloadStateProvider(),
        background = MaterialTheme.colorScheme.primaryContainer,
        onSwipe = { onChapterSwipe(chapterSwipeEndAction) },
    )

    SwipeableActionsBox(
        modifier = Modifier.clipToBounds(),
        startActions = listOfNotNull(start),
        endActions = listOfNotNull(end),
        swipeThreshold = swipeActionThreshold,
        backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = modifier
                .then(if (pinColor != null && !selected) Modifier.pinnedGlow(listOf(pinColor) + pinExtraColors) else Modifier)
                .selectedBackground(selected)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var textHeight by remember { mutableIntStateOf(0) }
                    if (!read) {
                        Icon(
                            imageVector = Icons.Filled.Circle,
                            contentDescription = stringResource(MR.strings.unread),
                            modifier = Modifier
                                .height(8.dp)
                                .padding(end = 4.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (bookmark || pinColor != null) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = stringResource(MR.strings.action_filter_bookmarked),
                            modifier = Modifier
                                .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp }),
                            tint = pinColor ?: MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { textHeight = it.size.height },
                        color = LocalContentColor.current.copy(alpha = if (read) DISABLED_ALPHA else 1f),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    pinExtraColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .size(7.dp)
                                .background(color, CircleShape),
                        )
                    }
                }

                if (!pinNote.isNullOrBlank()) {
                    val noteColor = pinColor ?: MaterialTheme.colorScheme.primary
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = null,
                            tint = noteColor,
                            modifier = Modifier.height(14.dp),
                        )
                        Text(
                            text = pinNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = noteColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row {
                    val subtitleStyle = MaterialTheme.typography.bodySmall
                        .merge(
                            color = LocalContentColor.current
                                .copy(alpha = if (read) DISABLED_ALPHA else SECONDARY_ALPHA),
                        )
                    ProvideTextStyle(value = subtitleStyle) {
                        if (date != null) {
                            Text(
                                text = date,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (readProgress != null || scanlator != null) DotSeparatorText()
                        }
                        if (readProgress != null) {
                            Text(
                                text = readProgress,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
                            )
                            if (scanlator != null) DotSeparatorText()
                        }
                        if (scanlator != null) {
                            Text(
                                text = scanlator,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            ChapterDownloadIndicator(
                enabled = downloadIndicatorEnabled,
                modifier = Modifier.padding(start = 4.dp),
                downloadStateProvider = downloadStateProvider,
                downloadProgressProvider = downloadProgressProvider,
                onClick = { onDownloadClick?.invoke(it) },
            )
        }
    }
}

/** Glow in the first color plus a left stripe split evenly between all [colors]. */
private fun Modifier.pinnedGlow(colors: List<Color>): Modifier = this
    .background(Brush.horizontalGradient(0f to colors.first().copy(alpha = 0.14f), 0.7f to Color.Transparent))
    .drawBehind {
        val inset = 8.dp.toPx()
        val width = 3.dp.toPx()
        val segment = (size.height - inset * 2) / colors.size
        colors.forEachIndexed { i, color ->
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, inset + segment * i),
                size = Size(width, segment),
                cornerRadius = CornerRadius(width, width),
            )
        }
    }

private fun getSwipeAction(
    action: LibraryPreferences.ChapterSwipeAction,
    read: Boolean,
    bookmark: Boolean,
    downloadState: Download.State,
    background: Color,
    onSwipe: () -> Unit,
): me.saket.swipe.SwipeAction? {
    return when (action) {
        LibraryPreferences.ChapterSwipeAction.ToggleRead -> swipeAction(
            icon = if (!read) Icons.Outlined.Done else Icons.Outlined.RemoveDone,
            background = background,
            isUndo = read,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> swipeAction(
            icon = if (!bookmark) Icons.Outlined.BookmarkAdd else Icons.Outlined.BookmarkRemove,
            background = background,
            isUndo = bookmark,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.Download -> swipeAction(
            icon = when (downloadState) {
                Download.State.NOT_DOWNLOADED, Download.State.ERROR -> Icons.Outlined.Download
                Download.State.QUEUE, Download.State.DOWNLOADING -> Icons.Outlined.FileDownloadOff
                Download.State.DOWNLOADED -> Icons.Outlined.Delete
            },
            background = background,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.Disabled -> null
    }
}

private fun swipeAction(
    onSwipe: () -> Unit,
    icon: ImageVector,
    background: Color,
    isUndo: Boolean = false,
): me.saket.swipe.SwipeAction {
    return me.saket.swipe.SwipeAction(
        icon = {
            Icon(
                modifier = Modifier.padding(16.dp),
                imageVector = icon,
                tint = contentColorFor(background),
                contentDescription = null,
            )
        },
        background = background,
        onSwipe = onSwipe,
        isUndo = isUndo,
    )
}

private val swipeActionThreshold = 56.dp
