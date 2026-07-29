package eu.kanade.tachiyomi.core.security

import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.i18n.MR

class SecurityPreferences(
    preferenceStore: PreferenceStore,
) {

    val useAuthenticator: Preference<Boolean> = preferenceStore.getBoolean("use_biometric_lock", false)

    val lockAppAfter: Preference<Int> = preferenceStore.getInt("lock_app_after", 0)

    val secureScreen: Preference<SecureScreenMode> = preferenceStore.getEnum(
        "secure_screen_v2",
        SecureScreenMode.INCOGNITO,
    )

    val hideNotificationContent: Preference<Boolean> = preferenceStore.getBoolean("hide_notification_content", false)

    /**
     * SHA-256 (salted) hash of the OtherSide "Suppress" PIN.
     * An empty value means the Suppress lock is OFF.
     */
    val otherSidePinHash: Preference<String> = preferenceStore.getString("otherside_pin_hash", "")

    /**
     * Idle minutes before the OtherSide lock re-engages. 0 = re-lock immediately on background.
     */
    val otherSideLockAfter: Preference<Int> = preferenceStore.getInt("otherside_lock_after", 0)

    /**
     * Epoch millis until which OtherSide is hard "Repressed". 0 = not repressed.
     * This is the source of truth for the time-lock. It is only ever cleared when TRUSTED network
     * time proves the deadline has passed; the device clock is never trusted to clear it.
     */
    val otherSideRepressedUntil: Preference<Long> = preferenceStore.getLong("otherside_repressed_until", 0)

    /**
     * For app lock. Will be set when there is a pending timed lock.
     * Otherwise, this pref should be deleted.
     */
    val lastAppClosed: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("last_app_closed"),
        0,
    )

    enum class SecureScreenMode(val titleRes: StringResource) {
        ALWAYS(MR.strings.lock_always),
        INCOGNITO(MR.strings.pref_incognito_mode),
        NEVER(MR.strings.lock_never),
    }
}
