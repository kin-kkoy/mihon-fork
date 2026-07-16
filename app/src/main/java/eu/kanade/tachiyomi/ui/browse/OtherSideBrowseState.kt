package eu.kanade.tachiyomi.ui.browse

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Session-only shared holder that ties the Browse-wide OtherSide toggle together.
 *
 * The [BrowseTab] toolbar button flips [enabled]; the sub-tab screen models read it to filter
 * their lists to NSFW-only (when enabled) or non-NSFW-only (when disabled). Defaults to false and
 * is intentionally not persisted, so every app launch starts in the normal (non-NSFW) state.
 */
object OtherSideBrowseState {
    val enabled = MutableStateFlow(false)
}
