package eu.kanade.domain.download.interactor

import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import logcat.LogPriority
import tachiyomi.core.common.storage.recursiveSize
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager

/**
 * Enforces the user-configured maximum total download size. When the downloads
 * directory exceeds the cap, the oldest already-READ downloaded chapters are
 * deleted until the total is back under the cap. Unread chapters are never
 * touched, so nothing you still intend to read is lost.
 */
class EnforceDownloadSizeCap(
    private val downloadPreferences: DownloadPreferences,
    private val storageManager: StorageManager,
    private val getLibraryManga: GetLibraryManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val downloadProvider: DownloadProvider,
    private val downloadManager: DownloadManager,
    private val sourceManager: SourceManager,
) {

    suspend fun await() = withIOContext {
        val cap = downloadPreferences.maxDownloadSizeBytes.get()
        if (cap <= 0L) return@withIOContext

        val downloadsDir = storageManager.getDownloadsDirectory() ?: return@withIOContext
        var total = downloadsDir.recursiveSize()
        if (total <= cap) return@withIOContext

        // Collect every already-read, downloaded chapter as an eviction candidate.
        val candidates = mutableListOf<Candidate>()
        getLibraryManga.await().forEach { libraryManga ->
            val manga = libraryManga.manga
            val source = sourceManager.getOrStub(manga.source)
            getChaptersByMangaId.await(manga.id)
                .asSequence()
                .filter { it.read }
                .forEach { chapter ->
                    val dir = downloadProvider.findChapterDir(
                        chapter.name,
                        chapter.scanlator,
                        chapter.url,
                        manga.title,
                        source,
                    ) ?: return@forEach
                    candidates += Candidate(chapter, manga, source, dir.recursiveSize())
                }
        }

        // Oldest first, so recently-read chapters survive longest.
        candidates.sortBy { it.chapter.lastModifiedAt }

        var evicted = 0
        for (candidate in candidates) {
            if (total <= cap) break
            downloadManager.deleteChapters(listOf(candidate.chapter), candidate.manga, candidate.source)
            total -= candidate.size
            evicted++
        }

        if (evicted > 0) {
            logcat(LogPriority.INFO) { "Download size cap exceeded: evicted $evicted read chapter(s)" }
        }
        if (total > cap) {
            // Everything left is unread; we never delete unread downloads.
            logcat(LogPriority.INFO) { "Download size still over cap but only unread chapters remain; keeping them" }
        }
    }

    private data class Candidate(
        val chapter: Chapter,
        val manga: Manga,
        val source: eu.kanade.tachiyomi.source.Source,
        val size: Long,
    )
}
