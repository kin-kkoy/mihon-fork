package eu.kanade.tachiyomi.ui.browse

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.components.AppBar
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Eyeglasses2
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * App bar action that toggles the global incognito mode. When incognito is on the
 * icon is highlighted with the primary color; otherwise it uses the default tint.
 */
@Composable
fun incognitoModeAction(): AppBar.Action {
    val context = LocalContext.current
    val basePreferences = remember { context.appGraph.basePreferences }
    val incognitoMode by basePreferences.incognitoMode.collectAsState()
    return AppBar.Action(
        title = stringResource(MR.strings.pref_incognito_mode),
        icon = MaterialSymbols.Rounded.Eyeglasses2,
        iconTint = if (incognitoMode) MaterialTheme.colorScheme.primary else null,
        onClick = { basePreferences.incognitoMode.set(!incognitoMode) },
    )
}
