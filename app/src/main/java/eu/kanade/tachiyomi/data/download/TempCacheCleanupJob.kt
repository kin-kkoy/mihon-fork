package eu.kanade.tachiyomi.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.download.Downloader.Companion.TMP_DIR_SUFFIX
import eu.kanade.tachiyomi.util.system.workManager
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Deletes orphaned temporary download folders (`<chapter>_tmp` and
 * `<chapter>.cbz_tmp`) left behind by interrupted downloads. Mihon never cleans
 * these up on its own, so they can silently accumulate to many gigabytes — one
 * of the main causes of runaway storage use.
 */
class TempCacheCleanupJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val storageManager: StorageManager = Injekt.get()
    private val downloadCache: DownloadCache = Injekt.get()

    override suspend fun doWork(): Result {
        // Never clean while downloads are active — an in-progress download owns a *_tmp dir.
        if (DownloadJob.isRunning(context)) return Result.success()

        return withIOContext {
            try {
                val deleted = purgeOrphanedTempDirs()
                if (deleted > 0) {
                    logcat(LogPriority.INFO) { "Removed $deleted orphaned *_tmp download entries" }
                    downloadCache.invalidateCache()
                }
                Result.success()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.failure()
            }
        }
    }

    /**
     * Walks the downloads tree (downloads/<source>/<manga>/<chapter>) and deletes
     * any entry whose name ends with [TMP_DIR_SUFFIX] at the manga-directory level.
     */
    private fun purgeOrphanedTempDirs(): Int {
        val downloadsDir = storageManager.getDownloadsDirectory() ?: return 0
        var deleted = 0
        downloadsDir.listFiles().orEmpty().forEach { sourceDir ->
            if (!sourceDir.isDirectory) return@forEach
            sourceDir.listFiles().orEmpty().forEach { mangaDir ->
                if (!mangaDir.isDirectory) return@forEach
                mangaDir.listFiles().orEmpty().forEach { entry ->
                    if (entry.name?.endsWith(TMP_DIR_SUFFIX) == true && entry.delete()) {
                        deleted++
                    }
                }
            }
        }
        return deleted
    }

    companion object {
        private const val TAG = "TempCacheCleanup"

        fun startNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<TempCacheCleanupJob>()
                .addTag(TAG)
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }
    }
}
