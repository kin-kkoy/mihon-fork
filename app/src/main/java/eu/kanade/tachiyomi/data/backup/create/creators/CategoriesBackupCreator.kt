package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.backupCategoryMapper
import eu.kanade.tachiyomi.ui.security.RepressManager
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoriesBackupCreator(
    private val getCategories: GetCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupCategory> {
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
