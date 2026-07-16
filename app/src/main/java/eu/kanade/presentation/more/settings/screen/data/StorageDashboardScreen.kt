package eu.kanade.presentation.more.settings.screen.data

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.hippo.unifile.UniFile
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.storage.recursiveSize
import java.io.File

class StorageDashboardScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { StorageDashboardScreenModel(context) }
        val state by model.state.collectAsState()

        Scaffold(
            topBar = {
                AppBar(
                    title = "Storage dashboard",
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            if (state.loading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                StorageList(
                    context = context,
                    state = state,
                    contentPadding = contentPadding,
                    onClear = model::clear,
                )
            }
        }
    }

    @Composable
    private fun StorageList(
        context: Context,
        state: StorageDashboardScreenModel.State,
        contentPadding: PaddingValues,
        onClear: (StorageDashboardScreenModel.Bucket) -> Unit,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            item {
                StorageInfo(
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
                )
                HorizontalDivider()
            }

            items(state.buckets.size) { index ->
                val bucket = state.buckets[index]
                BucketRow(
                    label = bucket.label,
                    description = bucket.description,
                    sizeText = Formatter.formatFileSize(context, bucket.size),
                    onClear = { onClear(bucket) },
                )
            }

            item {
                HorizontalDivider()
                TotalRow(
                    totalText = Formatter.formatFileSize(context, state.buckets.sumOf { it.size }),
                )
            }
        }
    }

    @Composable
    private fun BucketRow(
        label: String,
        description: String,
        sizeText: String,
        onClear: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.small,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.secondaryItemAlpha(),
                )
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = onClear) {
                Text(text = "Clear")
            }
        }
    }

    @Composable
    private fun TotalRow(
        totalText: String,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = totalText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The storage buckets shown on the dashboard.
 */
enum class StorageBucket(val label: String, val description: String) {
    Downloads(
        "Downloads",
        "Chapters saved for offline reading. The one thing that grows without limit — usually the biggest.",
    ),
    CoverCache(
        "Library covers",
        "Cover images for the manga in your library.",
    ),
    ImageCache(
        "Browse & search cache",
        "Thumbnails from browsing and searching sources. Temporary — clears itself and is safe to clear.",
    ),
    ChapterCache(
        "Reader page cache",
        "Pages held while reading online. Capped at 100 MB; safe to clear.",
    ),
    AutoBackups(
        "Auto backups",
        "Automatic library backups (small).",
    ),
}

private class StorageDashboardScreenModel(
    private val context: Context,
) : StateScreenModel<StorageDashboardScreenModel.State>(State()) {

    private val storageManager: StorageManager = Injekt.get()
    private val downloadCache: DownloadCache = Injekt.get()
    private val chapterCache: ChapterCache = Injekt.get()

    init {
        refresh()
    }

    private fun refresh() {
        screenModelScope.launch {
            val buckets = withIOContext { computeBuckets() }
            withUIContext {
                mutableState.value = State(loading = false, buckets = buckets)
            }
        }
    }

    private fun computeBuckets(): List<Bucket> {
        val downloadsDir = storageManager.getDownloadsDirectory()
        val autoBackupsDir = storageManager.getAutomaticBackupsDirectory()

        return listOf(
            Bucket(StorageBucket.Downloads, downloadsDir?.recursiveSize() ?: 0L),
            Bucket(StorageBucket.CoverCache, coverCacheDir()?.let { DiskUtil.getDirectorySize(it) } ?: 0L),
            Bucket(StorageBucket.ImageCache, DiskUtil.getDirectorySize(imageCacheDir())),
            Bucket(StorageBucket.ChapterCache, DiskUtil.getDirectorySize(chapterCacheDir())),
            Bucket(StorageBucket.AutoBackups, autoBackupsDir?.recursiveSize() ?: 0L),
        )
    }

    private fun coverCacheDir(): File? = context.getExternalFilesDir("covers")

    private fun imageCacheDir(): File = File(context.cacheDir, "image_cache")

    private fun chapterCacheDir(): File = File(context.cacheDir, "chapter_disk_cache")

    fun clear(bucket: Bucket) {
        screenModelScope.launch {
            withIOContext {
                when (bucket.type) {
                    StorageBucket.Downloads -> {
                        storageManager.getDownloadsDirectory()?.listFiles()?.forEach { it.delete() }
                        downloadCache.invalidateCache()
                    }
                    StorageBucket.CoverCache -> coverCacheDir()?.let { deleteContents(it) }
                    StorageBucket.ImageCache -> deleteContents(imageCacheDir())
                    StorageBucket.ChapterCache -> chapterCache.clear()
                    StorageBucket.AutoBackups -> {
                        storageManager.getAutomaticBackupsDirectory()?.listFiles()?.forEach { it.delete() }
                    }
                }
            }
            withUIContext {
                context.toast("Cleared ${bucket.type.label}")
            }
            refresh()
        }
    }

    private fun deleteContents(dir: File) {
        dir.listFiles()?.forEach { it.deleteRecursively() }
    }

    @Immutable
    data class Bucket(
        val type: StorageBucket,
        val size: Long,
    ) {
        val label: String get() = type.label
        val description: String get() = type.description
    }

    @Immutable
    data class State(
        val loading: Boolean = true,
        val buckets: List<Bucket> = emptyList(),
    )
}
