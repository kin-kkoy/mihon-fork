package eu.kanade.tachiyomi.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.library.DeleteLibraryMangaDialog
import eu.kanade.presentation.library.LibrarySettingsDialog
import eu.kanade.presentation.library.components.LibraryContent
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.manga.components.LibraryBottomActionMenu
import eu.kanade.presentation.more.onboarding.GETTING_STARTED_URL
import eu.kanade.presentation.security.RepressDurationDialog
import eu.kanade.presentation.security.rememberOtherSideGate
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.LibraryUpdateWorker
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.security.RepressManager
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import mihon.feature.migration.config.MigrationConfigScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.Help
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.isLocal

data object LibraryTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.animated_library)
            return TabOptions(
                index = 0u,
                title = stringResource(MR.strings.label_library),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        requestOpenSettingsSheet()
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        val viewModel = metroViewModel<LibraryViewModel>()
        val settingsViewModel = metroViewModel<LibrarySettingsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        val snackbarHostState = remember { SnackbarHostState() }

        // OtherSide circular-reveal state.
        val graphicsLayer = rememberGraphicsLayer()
        var revealOrigin by remember { mutableStateOf<Offset?>(null) }
        var revealing by remember { mutableStateOf(false) }
        var oldSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }
        val revealProgress = remember { Animatable(0f) }

        // Gate entering OtherSide behind the Suppress PIN (no-op when Suppress is off).
        val gate = rememberOtherSideGate()
        val doToggleOtherSide: () -> Unit = {
            scope.launch {
                // Capture the OLD-themed frame, THEN swap the theme, then wipe a growing
                // circle that reveals the NEW theme underneath the old snapshot.
                val old = runCatching { graphicsLayer.toImageBitmap() }.getOrNull()
                viewModel.toggleOtherSide()
                oldSnapshot = old
                revealing = old != null
                if (old != null) {
                    revealProgress.snapTo(0f)
                    revealProgress.animateTo(1f, animationSpec = tween(durationMillis = 550))
                }
                revealing = false
                oldSnapshot = null
            }
        }
        val onToggleOtherSide: () -> Unit = {
            // Only entering OtherSide (normal -> other) requires the PIN; leaving is ungated.
            if (!state.otherSideMode) gate(doToggleOtherSide) else doToggleOtherSide()
        }

        // In-OtherSide repress shortcut: shake the scaffold + ripple back to Normal on confirm.
        var showRepressDialog by remember { mutableStateOf(false) }
        val shakeX = remember { Animatable(0f) }
        var shakeTrigger by remember { mutableIntStateOf(0) }
        LaunchedEffect(shakeTrigger) {
            if (shakeTrigger > 0) {
                shakeX.snapTo(0f)
                for (target in listOf(-16f, 14f, -10f, 7f, -3f, 0f)) {
                    shakeX.animateTo(target, animationSpec = tween(durationMillis = 75))
                }
            }
        }

        val onClickRefresh: suspend (Category?) -> Boolean = { category ->
            val started = LibraryUpdateWorker.startNow(context.workManager, category)
            scope.launch {
                val msgRes = when {
                    !started -> MR.strings.update_already_running
                    category != null -> MR.strings.updating_category
                    else -> MR.strings.updating_library
                }
                snackbarHostState.showSnackbar(context.stringResource(msgRes))
            }
            started
        }

        // OtherSide: swap to a distinct dark-purple theme when in OtherSide mode,
        // with a circular-reveal wipe expanding from the toggle button position.
        val libraryScaffold = @Composable {
            Scaffold(
                topBar = { scrollBehavior ->
                    val title = state.getToolbarTitle(
                        defaultTitle = stringResource(MR.strings.label_library),
                        defaultCategoryTitle = stringResource(MR.strings.label_default),
                        page = state.coercedActiveCategoryIndex,
                    )
                    LibraryToolbar(
                        hasActiveFilters = state.hasActiveFilters,
                        selectedCount = state.selection.size,
                        title = title,
                        onClickUnselectAll = viewModel::clearSelection,
                        onClickSelectAll = viewModel::selectAll,
                        onClickInvertSelection = viewModel::invertSelection,
                        onClickFilter = viewModel::showSettingsDialog,
                        onClickRefresh = { scope.launch { onClickRefresh(state.activeCategory) } },
                        onClickGlobalUpdate = { scope.launch { onClickRefresh(null) } },
                        onClickOpenRandomManga = {
                            scope.launch {
                                val randomItem = viewModel.getRandomLibraryItemForCurrentCategory()
                                if (randomItem != null) {
                                    navigator.push(MangaScreen(randomItem.libraryManga.manga.id))
                                } else {
                                    snackbarHostState.showSnackbar(
                                        context.stringResource(MR.strings.information_no_entries_found),
                                    )
                                }
                            }
                        },
                        otherSideMode = state.otherSideMode,
                        onClickOtherSide = onToggleOtherSide,
                        onOtherSideOriginChanged = { revealOrigin = it },
                        onRepressShortcut = { showRepressDialog = true },
                        searchQuery = state.searchQuery,
                        onSearchQueryChange = viewModel::search,
                        // For scroll overlay when no tab
                        scrollBehavior = scrollBehavior.takeIf { !state.showCategoryTabs },
                    )
                },
                bottomBar = {
                    LibraryBottomActionMenu(
                        visible = state.selectionMode,
                        onChangeCategoryClicked = viewModel::openChangeCategoryDialog,
                        onMarkAsReadClicked = { viewModel.markReadSelection(true) },
                        onMarkAsUnreadClicked = { viewModel.markReadSelection(false) },
                        onDownloadClicked = viewModel::performDownloadAction
                            .takeIf { state.selectedManga.fastAll { !it.isLocal() } },
                        onDeleteClicked = viewModel::openDeleteMangaDialog,
                        onMigrateClicked = {
                            val selection = state.selection
                            viewModel.clearSelection()
                            navigator.push(MigrationConfigScreen(selection))
                        },
                    )
                },
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            ) { contentPadding ->
                when {
                    state.isLoading -> {
                        LoadingScreen(Modifier.padding(contentPadding))
                    }
                    state.searchQuery.isNullOrEmpty() && !state.hasActiveFilters && state.isLibraryEmpty -> {
                        val handler = LocalUriHandler.current
                        EmptyScreen(
                            stringRes = MR.strings.information_empty_library,
                            modifier = Modifier.padding(contentPadding),
                            actions = listOf(
                                EmptyScreenAction(
                                    stringRes = MR.strings.getting_started_guide,
                                    icon = MaterialSymbols.AutoMirroredRounded.Help,
                                    onClick = { handler.openUri(GETTING_STARTED_URL) },
                                ),
                            ),
                        )
                    }
                    else -> {
                        LibraryContent(
                            categories = state.displayedCategories,
                            searchQuery = state.searchQuery,
                            selection = state.selection,
                            contentPadding = contentPadding,
                            currentPage = state.coercedActiveCategoryIndex,
                            hasActiveFilters = state.hasActiveFilters,
                            showPageTabs = state.showCategoryTabs || !state.searchQuery.isNullOrEmpty(),
                            onChangeCurrentPage = viewModel::updateActiveCategoryIndex,
                            onClickManga = { navigator.push(MangaScreen(it)) },
                            onContinueReadingClicked = { it: LibraryManga ->
                                scope.launch {
                                    val chapter = viewModel.getNextUnreadChapter(it.manga)
                                    if (chapter != null) {
                                        context.startActivity(
                                            ReaderActivity.newIntent(context, chapter.mangaId, chapter.id),
                                        )
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            context.stringResource(MR.strings.no_next_chapter),
                                        )
                                    }
                                }
                                Unit
                            }.takeIf { state.showMangaContinueButton },
                            onToggleSelection = viewModel::toggleSelection,
                            onToggleRangeSelection = { category, manga ->
                                viewModel.toggleRangeSelection(category, manga)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onRefresh = { onClickRefresh(state.activeCategory) },
                            onGlobalSearchClicked = {
                                navigator.push(GlobalSearchScreen(viewModel.state.value.searchQuery ?: ""))
                            },
                            getItemCountForCategory = { state.getItemCountForCategory(it) },
                            getDisplayMode = { viewModel.getDisplayMode() },
                            getColumnsForOrientation = { viewModel.getColumnsForOrientation(it) },
                            getItemsForCategory = { state.getItemsForCategory(it) },
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = shakeX.value.dp.toPx() },
        ) {
            // Continuously record the live (already-themed) Library frame into a graphics layer
            // so we can snapshot the OLD theme the instant the toggle is tapped.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    },
            ) {
                if (state.otherSideMode) {
                    MaterialTheme(colorScheme = OtherSideColorScheme) { libraryScaffold() }
                } else {
                    libraryScaffold()
                }
            }

            val snapshot = oldSnapshot
            if (revealing && snapshot != null) {
                // Draw the OLD-theme snapshot everywhere EXCEPT inside the growing circle, so the
                // NEW theme underneath is revealed element-by-element as the edge sweeps past.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (size.minDimension <= 0f) return@Canvas
                    val origin = revealOrigin ?: Offset(size.width, 0f)
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

        val onDismissRequest = viewModel::closeDialog
        when (val dialog = state.dialog) {
            is LibraryViewModel.Dialog.SettingsSheet -> run {
                LibrarySettingsDialog(
                    onDismissRequest = onDismissRequest,
                    viewModel = settingsViewModel,
                    category = state.activeCategory,
                )
            }
            is LibraryViewModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        viewModel.clearSelection()
                        navigator.push(CategoryScreen())
                    },
                    onConfirm = { include, exclude ->
                        viewModel.clearSelection()
                        viewModel.setMangaCategories(dialog.manga, include, exclude)
                    },
                )
            }
            is LibraryViewModel.Dialog.DeleteManga -> {
                DeleteLibraryMangaDialog(
                    containsLocalManga = dialog.manga.any(Manga::isLocal),
                    onDismissRequest = onDismissRequest,
                    onConfirm = { deleteManga, deleteChapter ->
                        viewModel.removeMangas(dialog.manga, deleteManga, deleteChapter)
                        viewModel.clearSelection()
                    },
                )
            }
            null -> {}
        }

        if (showRepressDialog) {
            RepressDurationDialog(
                title = "Repress OtherSide",
                warning = "This locks OtherSide for the whole time you pick. " +
                    "You cannot cancel it, shorten it, or undo it once it starts.",
                confirmLabel = "Repress",
                onConfirm = { millis ->
                    showRepressDialog = false
                    RepressManager.repress(millis)
                    // Ripple back to Normal (same path the crossover uses) and shake together.
                    if (state.otherSideMode) doToggleOtherSide()
                    shakeTrigger++
                },
                onDismiss = { showRepressDialog = false },
            )
        }

        BackHandler(enabled = state.selectionMode || state.searchQuery != null) {
            when {
                state.selectionMode -> viewModel.clearSelection()
                state.searchQuery != null -> viewModel.search(null)
            }
        }

        LaunchedEffect(state.selectionMode, state.dialog) {
            HomeScreen.showBottomNav(!state.selectionMode)
        }

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                (context as? MainActivity)?.ready = true
            }
        }
        // Lets startup be measured up to the library, not just its loading screen
        ReportDrawnWhen { !state.isLoading }

        LaunchedEffect(Unit) {
            launch { queryEvent.receiveAsFlow().collect(viewModel::search) }
            launch { requestSettingsSheetEvent.receiveAsFlow().collectLatest { viewModel.showSettingsDialog() } }
        }
    }

    // For invoking search from other screen
    private val queryEvent = Channel<String>()
    suspend fun search(query: String) = queryEvent.send(query)

    // For opening settings sheet in LibraryController
    private val requestSettingsSheetEvent = Channel<Unit>()
    private suspend fun requestOpenSettingsSheet() = requestSettingsSheetEvent.send(Unit)
}
