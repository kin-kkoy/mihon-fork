package eu.kanade.presentation.more.settings.screen.data

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import tachiyomi.core.common.storage.recursiveSize
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * The storage buckets shown in the inline storage usage breakdown.
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

data class StorageBucketSize(
    val type: StorageBucket,
    val size: Long,
) {
    val label: String get() = type.label
    val description: String get() = type.description
}

/**
 * Reusable storage size computation and clear logic shared by the settings breakdown.
 */
object StorageUsage {

    private val storageManager: StorageManager get() = Injekt.get()
    private val downloadCache: DownloadCache get() = Injekt.get()
    private val chapterCache: ChapterCache get() = Injekt.get()

    fun computeBuckets(context: Context): List<StorageBucketSize> {
        val downloadsDir = storageManager.getDownloadsDirectory()
        val autoBackupsDir = storageManager.getAutomaticBackupsDirectory()

        return listOf(
            StorageBucketSize(StorageBucket.Downloads, downloadsDir?.recursiveSize() ?: 0L),
            StorageBucketSize(
                StorageBucket.CoverCache,
                coverCacheDir(context)?.let { DiskUtil.getDirectorySize(it) } ?: 0L,
            ),
            StorageBucketSize(StorageBucket.ImageCache, DiskUtil.getDirectorySize(imageCacheDir(context))),
            StorageBucketSize(StorageBucket.ChapterCache, DiskUtil.getDirectorySize(chapterCacheDir(context))),
            StorageBucketSize(StorageBucket.AutoBackups, autoBackupsDir?.recursiveSize() ?: 0L),
        )
    }

    fun clear(context: Context, bucket: StorageBucket) {
        when (bucket) {
            StorageBucket.Downloads -> {
                storageManager.getDownloadsDirectory()?.listFiles()?.forEach { it.delete() }
                downloadCache.invalidateCache()
            }
            StorageBucket.CoverCache -> coverCacheDir(context)?.let { deleteContents(it) }
            StorageBucket.ImageCache -> deleteContents(imageCacheDir(context))
            StorageBucket.ChapterCache -> chapterCache.clear()
            StorageBucket.AutoBackups -> {
                storageManager.getAutomaticBackupsDirectory()?.listFiles()?.forEach { it.delete() }
            }
        }
    }

    private fun coverCacheDir(context: Context): File? = context.getExternalFilesDir("covers")

    private fun imageCacheDir(context: Context): File = File(context.cacheDir, "image_cache")

    private fun chapterCacheDir(context: Context): File = File(context.cacheDir, "chapter_disk_cache")

    private fun deleteContents(dir: File) {
        dir.listFiles()?.forEach { it.deleteRecursively() }
    }
}

/**
 * Inline per-bucket storage breakdown rendered inside the Data & storage settings screen.
 * Sizes are computed off the main thread and recomputed after any Clear.
 */
@Composable
fun StorageUsageBreakdown(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var buckets by remember { mutableStateOf<List<StorageBucketSize>?>(null) }

    LaunchedEffect(Unit) {
        val result = withIOContext { StorageUsage.computeBuckets(context) }
        withUIContext { buckets = result }
    }

    val current = buckets
    if (current == null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        current.forEach { bucket ->
            BucketRow(
                label = bucket.label,
                description = bucket.description,
                sizeText = Formatter.formatFileSize(context, bucket.size),
                onClear = {
                    scope.launch {
                        withIOContext { StorageUsage.clear(context, bucket.type) }
                        withUIContext { context.toast("Cleared ${bucket.label}") }
                        val result = withIOContext { StorageUsage.computeBuckets(context) }
                        withUIContext { buckets = result }
                    }
                },
            )
        }

        HorizontalDivider()

        TotalRow(
            totalText = Formatter.formatFileSize(context, current.sumOf { it.size }),
        )
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
        modifier = Modifier.fillMaxWidth(),
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
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Total used by Mihon",
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
