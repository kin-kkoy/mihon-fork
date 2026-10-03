package eu.kanade.tachiyomi.ui.pinned

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.chapter.service.ChapterPinStore
import eu.kanade.domain.chapter.service.PinSection
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.EntryRef
import eu.kanade.presentation.manga.components.ManageSectionsDialog
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.manga.components.pinnedGlow
import eu.kanade.presentation.security.OtherSideRevealBox
import eu.kanade.presentation.security.rememberOtherSideGate
import eu.kanade.presentation.security.rememberOtherSideReveal
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.security.RepressManager
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Every pinned chapter and note across the library, as a grid of section tiles. Has its own
 * OtherSide crossover (yin-yang + ripple, PIN-gated, inert while repressed): each side only
 * lists its own manga, and sections used only on the other side are hidden.
 */
class PinnedScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { PinnedScreenModel() }
        val state by screenModel.state.collectAsState()

        val reveal = rememberOtherSideReveal()
        val gate = rememberOtherSideGate()
        val crossover: () -> Unit = { reveal.crossover(screenModel::toggleOtherSide) }
        val onYinYang: () -> Unit = { if (!state.otherSideMode) gate(crossover) else crossover() }

        // null = closed, false = manage, true = manage with the new-section form open
        var manageSections by remember { mutableStateOf<Boolean?>(null) }

        BackHandler(enabled = state.view != PinnedView.Grid) { screenModel.open(PinnedView.Grid) }

        val openReader: (PinnedEntry) -> Unit = {
            context.startActivity(ReaderActivity.newIntent(context, it.manga.id, it.chapter.id))
        }
        val openManga: (PinnedEntry) -> Unit = { navigator.push(MangaScreen(it.manga.id)) }

        OtherSideRevealBox(reveal = reveal, otherSide = state.otherSideMode) {
            val view = state.view
            val openSection = (view as? PinnedView.Section)?.let { v -> state.sections.find { it.id == v.id } }
            Scaffold(
                topBar = { scrollBehavior ->
                    AppBar(
                        title = when (view) {
                            PinnedView.Grid -> "Pinned"
                            PinnedView.NotesOnly -> "Notes only"
                            is PinnedView.Section -> openSection?.name ?: "Pinned"
                        },
                        navigateUp = {
                            if (view != PinnedView.Grid) screenModel.open(PinnedView.Grid) else navigator.pop()
                        },
                        actions = {
                            if (view == PinnedView.Grid) {
                                IconButton(
                                    onClick = onYinYang,
                                    modifier = Modifier
                                        .onGloballyPositioned { reveal.origin = it.boundsInWindow().center }
                                        .alpha(if (RepressManager.isRepressed) 0.38f else 1f),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_otherside),
                                        contentDescription = "OtherSide",
                                        tint = if (state.otherSideMode) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            LocalContentColor.current
                                        },
                                    )
                                }
                                IconButton(onClick = { manageSections = false }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Manage sections")
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                },
            ) { paddingValues ->
                when {
                    state.loading -> LoadingScreen(Modifier.padding(paddingValues))
                    view == PinnedView.Grid -> PinnedGrid(
                        state = state,
                        contentPadding = paddingValues,
                        onOpen = screenModel::open,
                        onToggleEntry = screenModel::toggleEntryFilter,
                        onNewSection = { manageSections = true },
                    )
                    else -> {
                        val entries = when (view) {
                            is PinnedView.Section -> state.entriesIn(view.id)
                            else -> state.notesOnly
                        }
                        if (entries.isEmpty()) {
                            EmptyScreen(message = "No chapters here yet", modifier = Modifier.padding(paddingValues))
                        } else {
                            LazyColumn(contentPadding = paddingValues) {
                                item { EntryFilterRow(state, screenModel::toggleEntryFilter) }
                                items(entries, key = { "${it.manga.id}-${it.chapter.id}" }) { entry ->
                                    PinnedRow(
                                        entry = entry,
                                        inSection = openSection,
                                        onClick = { openReader(entry) },
                                        onLongClick = { openManga(entry) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        manageSections?.let { startAdding ->
            ManageSectionsDialog(
                store = screenModel.store,
                sections = state.visibleSections,
                entries = state.sideManga.map { EntryRef(ChapterPinStore.mangaKey(it), it.title) },
                ownerTitle = state::ownerTitle,
                contextEntry = state.sideManga
                    .singleOrNull { it.id in state.entryFilter }
                    ?.let { ChapterPinStore.mangaKey(it) },
                subtitle = buildString {
                    append("Rename, recolor (tap the dot), change scope (tap the chip) or delete.")
                    if (state.otherSideMode) append(" OtherSide: only sections used here are listed.")
                },
                startAdding = startAdding,
                onDismissRequest = { manageSections = null },
            )
        }
    }
}

@Composable
private fun PinnedGrid(
    state: PinnedScreenModel.State,
    contentPadding: PaddingValues,
    onOpen: (PinnedView) -> Unit,
    onToggleEntry: (Long?) -> Unit,
    onNewSection: () -> Unit,
) {
    val filtering = state.entryFilter.any { id -> state.sideManga.any { it.id == id } }
    val (sharedSections, entrySections) = state.tileSections
    fun tilesOf(list: List<PinSection>) = list
        .map { it to state.entriesIn(it.id) }
        .filter { (_, entries) -> !filtering || entries.isNotEmpty() }
    val sharedTiles = tilesOf(sharedSections)
    val entryTiles = tilesOf(entrySections)
    val tiles = sharedTiles + entryTiles

    if (state.sideEntries.isEmpty() && tiles.isEmpty()) {
        EmptyScreen(
            message = if (state.otherSideMode) {
                "Nothing pinned on the OtherSide"
            } else {
                "Nothing pinned yet. Hold the bookmark button in the reader to pin a chapter."
            },
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    Column(Modifier.padding(top = contentPadding.calculateTopPadding())) {
        // Chip row sits above the grid, edge to edge, so a cut-off chip hints that it scrolls.
        EntryFilterRow(state, onToggleEntry)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(
                Triple("shared", "Shared by all entries", sharedTiles),
                Triple("entry", "Entry sections", entryTiles),
            ).forEach { (group, label, groupTiles) ->
                if (groupTiles.isEmpty()) return@forEach
                item(key = "header-$group", span = { GridItemSpan(maxLineSpan) }) {
                    SectionGroupHeader(shared = group == "shared", text = label)
                }
                items(groupTiles, key = { "section-${it.first.id}" }) { (section, entries) ->
                    SectionTile(
                        name = section.name,
                        color = Color(section.color),
                        count = entries.size,
                        manga = entries.map { it.manga }.distinctBy { it.id },
                        scope = if (section.isShared) "All entries" else state.ownerTitle(section) ?: "One entry",
                        onClick = { onOpen(PinnedView.Section(section.id)) },
                    )
                }
            }
            if (state.notesOnly.isNotEmpty()) {
                item(key = "notes-only") {
                    SectionTile(
                        name = "Notes only",
                        color = MaterialTheme.colorScheme.outline,
                        count = state.notesOnly.size,
                        manga = state.notesOnly.map { it.manga }.distinctBy { it.id },
                        scope = null,
                        onClick = { onOpen(PinnedView.NotesOnly) },
                    )
                }
            }
            if (!filtering) {
                item(key = "new-section") {
                    Surface(
                        onClick = onNewSection,
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .heightIn(min = 128.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.heightIn(min = 128.dp),
                        ) {
                            Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "New section",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionGroupHeader(shared: Boolean, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Icon(
            imageVector = if (shared) Icons.Outlined.Public else Icons.AutoMirrored.Outlined.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Multi-select manga filter; hidden when only one manga has pins on this side. */
@Composable
private fun EntryFilterRow(state: PinnedScreenModel.State, onToggle: (Long?) -> Unit) {
    val manga = state.sideManga
    if (manga.size < 2) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 14.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val active = state.entryFilter.filter { id -> manga.any { it.id == id } }
        FilterChip(selected = active.isEmpty(), onClick = { onToggle(null) }, label = { Text("All entries") })
        manga.forEach { m ->
            FilterChip(
                selected = m.id in active,
                onClick = { onToggle(m.id) },
                label = { Text(m.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { MangaCover.Book(data = m.asMangaCover(), modifier = Modifier.height(22.dp)) },
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun SectionTile(
    name: String,
    color: Color,
    count: Int,
    manga: List<Manga>,
    scope: String?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(6.dp).background(color))
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Bookmark, null, tint = color, modifier = Modifier.size(16.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = if (count == 1) "1 chapter" else "$count chapters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (scope != null) {
                    Text(
                        text = scope,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Stacked covers of the manga in this section
                Box(Modifier.padding(top = 10.dp).height(46.dp)) {
                    manga.take(4).forEachIndexed { i, m ->
                        MangaCover.Book(
                            data = m.asMangaCover(),
                            modifier = Modifier
                                .offset(x = (i * 22).dp)
                                .height(46.dp)
                                .border(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraSmall),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedRow(
    entry: PinnedEntry,
    inSection: PinSection?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val section = inSection ?: entry.pin.primary
    val color = section?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary
    val others = entry.pin.sections.filter { it.id != section?.id }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (section != null) Modifier.pinnedGlow(listOf(color)) else Modifier)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MangaCover.Book(data = entry.manga.asMangaCover(), modifier = Modifier.height(56.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.manga.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Bookmark, null, tint = color, modifier = Modifier.size(14.dp))
                Text(
                    text = entry.chapter.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.pin.note.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp)) {
                    Icon(painterResource(R.drawable.ic_chapter_note_24dp), null, tint = color, modifier = Modifier.size(13.dp))
                    Text(
                        text = entry.pin.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (others.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 3.dp),
                ) {
                    Text(
                        text = "Also in",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    others.forEach { other ->
                        Box(Modifier.size(7.dp).background(Color(other.color), CircleShape))
                        Text(
                            text = other.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
