package eu.kanade.tachiyomi.ui.browse

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.R
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * App bar action that toggles the global incognito mode. When incognito is on the
 * icon is highlighted with the primary color; otherwise it uses the default tint.
 */
@Composable
fun incognitoModeAction(): AppBar.Action {
    val basePreferences = remember { Injekt.get<BasePreferences>() }
    val incognitoMode by basePreferences.incognitoMode.collectAsState()
    return AppBar.Action(
        title = stringResource(MR.strings.pref_incognito_mode),
        icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
        iconTint = if (incognitoMode) MaterialTheme.colorScheme.primary else null,
        onClick = { basePreferences.incognitoMode.set(!incognitoMode) },
    )
}
