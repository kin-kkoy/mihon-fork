package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.backupCategoryMapper
import eu.kanade.tachiyomi.ui.security.RepressManager
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
class CategoriesBackupCreator(
    private val getCategories: GetCategories,
    private val libraryPreferences: LibraryPreferences,
) {

    suspend operator fun invoke(): List<BackupCategory> {
        // While OtherSide is repressed, its categories are left out of backups
        val otherSideCategoryIds = if (RepressManager.isRepressed) {
            libraryPreferences.otherSideCategoryIds.get()
        } else {
            emptySet()
        }
        return getCategories.await()
            .filterNot(Category::isSystemCategory)
            .filterNot { it.id.toString() in otherSideCategoryIds }
            .map(backupCategoryMapper)
    }
}
