package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.extension.extensionsTab
import eu.kanade.tachiyomi.ui.browse.migration.sources.migrateSourceTab
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.sourcesTab
import eu.kanade.tachiyomi.ui.library.OtherSideColorScheme
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data object BrowseTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(GlobalSearchScreen())
    }

    private val switchToExtensionTabChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    fun showExtension() {
        switchToExtensionTabChannel.trySend(Unit)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // Hoisted for extensions tab's search bar
        val extensionsScreenModel = rememberScreenModel { ExtensionsScreenModel() }
        val extensionsState by extensionsScreenModel.state.collectAsState()

        val tabs = listOf(
            sourcesTab(),
            extensionsTab(extensionsScreenModel),
            migrateSourceTab(),
        )

        val state = rememberPagerState { tabs.size }

        // OtherSide: reveal NSFW sources/extensions with a distinct dark-purple theme and a
        // circular-reveal wipe expanding from the toggle button beside the "Browse" title.
        val otherSideEnabled by OtherSideBrowseState.enabled.collectAsState()
        val graphicsLayer = rememberGraphicsLayer()
        var revealOrigin by remember { mutableStateOf<Offset?>(null) }
        var revealing by remember { mutableStateOf(false) }
        var oldSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }
        val revealProgress = remember { Animatable(0f) }

        val onToggleOtherSide: () -> Unit = {
            scope.launch {
                // Capture the OLD-themed frame, THEN flip the mode, then wipe a growing circle
                // that reveals the NEW theme underneath the old snapshot. Icon + ripple are one.
                val old = runCatching { graphicsLayer.toImageBitmap() }.getOrNull()
                OtherSideBrowseState.enabled.update { !it }
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

        val browseContent = @Composable {
            TabbedScreen(
                titleRes = MR.strings.browse,
                tabs = tabs,
                state = state,
                searchQuery = extensionsState.searchQuery,
                onChangeSearchQuery = extensionsScreenModel::search,
                otherSideEnabled = otherSideEnabled,
                onClickOtherSide = onToggleOtherSide,
                onOtherSideOriginChanged = { revealOrigin = it },
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Continuously record the live (already-themed) Browse frame into a graphics layer
            // so we can snapshot the OLD theme the instant the toggle is tapped.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    },
            ) {
                if (otherSideEnabled) {
                    MaterialTheme(colorScheme = OtherSideColorScheme) { browseContent() }
                } else {
                    browseContent()
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

        LaunchedEffect(Unit) {
            switchToExtensionTabChannel.receiveAsFlow()
                .collectLatest { state.scrollToPage(1) }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
