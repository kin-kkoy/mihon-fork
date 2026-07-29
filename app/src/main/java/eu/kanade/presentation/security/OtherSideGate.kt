package eu.kanade.presentation.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.ui.security.OtherSideLock
import eu.kanade.tachiyomi.ui.security.RepressManager
import kotlinx.coroutines.launch

/**
 * Returns a gate function that guards OtherSide-entering actions behind the Suppress PIN.
 *
 * Call [rememberOtherSideGate] once in a composable and invoke the returned lambda around any
 * action that would enter or mutate OtherSide. When [OtherSideLock.isLocked] is true the action is
 * held and the unlock PIN dialog is shown; on success the action runs. When the lock is not enabled
 * (no PIN set) the action runs immediately, so behaviour is unchanged.
 */
@Composable
fun rememberOtherSideGate(): (action: () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    pending?.let { act ->
        OtherSidePinDialog(
            mode = OtherSidePinMode.UNLOCK,
            onUnlocked = {
                pending = null
                act()
            },
            onDismiss = { pending = null },
        )
    }
    return { action ->
        when {
            // Repressed: the entry point is clickable but completely inert — no PIN dialog and no
            // action. Kick a background refresh so it clears once trusted time proves it's over.
            RepressManager.isRepressed -> scope.launch { RepressManager.refresh(force = true) }
            OtherSideLock.isLocked -> pending = action
            else -> action()
        }
    }
}
