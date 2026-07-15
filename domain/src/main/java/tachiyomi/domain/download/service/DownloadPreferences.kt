package tachiyomi.domain.download.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class DownloadPreferences(
    preferenceStore: PreferenceStore,
) {

    val downloadOnlyOverWifi: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_download_only_over_wifi_key",
        true,
    )

    val saveChaptersAsCBZ: Preference<Boolean> = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    val splitTallImages: Preference<Boolean> = preferenceStore.getBoolean("split_tall_images", true)

    val autoDownloadWhileReading: Preference<Int> = preferenceStore.getInt("auto_download_while_reading", 0)

    val removeAfterReadSlots: Preference<Int> = preferenceStore.getInt("remove_after_read_slots", -1)

    val removeAfterMarkedAsRead: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_remove_after_marked_as_read_key",
        false,
    )

    val removeBookmarkedChapters: Preference<Boolean> = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    val removeExcludeCategories: Preference<Set<String>> = preferenceStore.getStringSet(
        REMOVE_EXCLUDE_CATEGORIES_PREF_KEY,
        emptySet(),
    )

    val downloadNewChapters: Preference<Boolean> = preferenceStore.getBoolean("download_new", false)

    val downloadNewChapterCategories: Preference<Set<String>> = preferenceStore.getStringSet(
        DOWNLOAD_NEW_CATEGORIES_PREF_KEY,
        emptySet(),
    )

    val downloadNewChapterCategoriesExclude: Preference<Set<String>> = preferenceStore.getStringSet(
        DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY,
        emptySet(),
    )

    val downloadNewUnreadChaptersOnly: Preference<Boolean> = preferenceStore.getBoolean(
        "download_new_unread_chapters_only",
        false,
    )

    val parallelSourceLimit: Preference<Int> = preferenceStore.getInt("download_parallel_source_limit", 5)

    val parallelPageLimit: Preference<Int> = preferenceStore.getInt("download_parallel_page_limit", 5)

    /**
     * Automatically delete orphaned temporary download folders (`*_tmp`) left
     * behind by interrupted downloads. These can otherwise silently accumulate.
     */
    val cleanupOrphanedDownloads: Preference<Boolean> = preferenceStore.getBoolean(
        "cleanup_orphaned_downloads",
        true,
    )

    /**
     * Maximum total size of downloaded chapters in bytes. When exceeded, the
     * oldest already-read downloaded chapters are evicted until under the cap.
     * 0 = unlimited.
     */
    val maxDownloadSizeBytes: Preference<Long> = preferenceStore.getLong("max_download_size_bytes", 0L)

    /**
     * When enabled, each downloaded page image is decoded, optionally downscaled and
     * re-encoded (WebP or JPEG) to shrink its on-disk size. Default OFF.
     */
    val recompressDownloadedImages: Preference<Boolean> = preferenceStore.getBoolean(
        "recompress_downloaded_images",
        false,
    )

    /** Maximum page dimension (longest side) before downscaling. 0 = no downscale. */
    val recompressMaxDimension: Preference<Int> = preferenceStore.getInt("recompress_max_dimension", 0)

    /** Re-encode quality (1-100). */
    val recompressQuality: Preference<Int> = preferenceStore.getInt("recompress_quality", 80)

    /** Use WebP for re-encoding; else JPEG. */
    val recompressUseWebp: Preference<Boolean> = preferenceStore.getBoolean("recompress_use_webp", true)

    companion object {
        private const val REMOVE_EXCLUDE_CATEGORIES_PREF_KEY = "remove_exclude_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_PREF_KEY = "download_new_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY = "download_new_categories_exclude"
        val categoryPreferenceKeys = setOf(
            REMOVE_EXCLUDE_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY,
        )
    }
}
