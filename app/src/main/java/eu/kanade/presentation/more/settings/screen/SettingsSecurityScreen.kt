package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.security.OtherSidePinDialog
import eu.kanade.presentation.security.OtherSidePinMode
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.ui.security.OtherSideLock
import eu.kanade.tachiyomi.ui.security.RepressManager
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.util.system.OtherSidePin
import eu.kanade.tachiyomi.util.system.telemetryIncluded
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsSecurityScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_security

    @Composable
    override fun getPreferences(): List<Preference> {
        val securityPreferences = remember { Injekt.get<SecurityPreferences>() }
        val privacyPreferences = remember { Injekt.get<PrivacyPreferences>() }
        return buildList(3) {
            add(getSecurityGroup(securityPreferences))
            add(getOtherSideGroup(securityPreferences))
            if (!telemetryIncluded) return@buildList
            add(getFirebaseGroup(privacyPreferences))
        }
    }

    /**
     * OtherSide "Suppress" PIN lock. STAGE A: set/change/remove the PIN and the re-lock timeout.
     * The PIN is stored as a salted SHA-256 hash; an empty hash means Suppress is OFF.
     */
    @Composable
    private fun getOtherSideGroup(
        securityPreferences: SecurityPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val pinHash by securityPreferences.otherSidePinHash.collectAsState()
        val hasPin = pinHash.isNotEmpty()

        val repressedUntil by securityPreferences.otherSideRepressedUntil.collectAsState()
        val repressed = repressedUntil > 0

        // Dialog state: null = closed, otherwise the active flow.
        var dialogMode by remember { mutableStateOf<OtherSideDialog?>(null) }
        var showRepressDialog by remember { mutableStateOf(false) }
        var showExtendDialog by remember { mutableStateOf(false) }

        if (showRepressDialog) {
            RepressDurationDialog(
                title = "Repress OtherSide",
                warning = "OtherSide will be hidden and completely inert for the chosen time. " +
                    "This can't be cancelled or shortened — it can only be waited out (and only " +
                    "clears once the device is back online).",
                confirmLabel = "Repress",
                onConfirm = { millis ->
                    RepressManager.repress(millis)
                    showRepressDialog = false
                    context.toast("OtherSide repressed")
                },
                onDismiss = { showRepressDialog = false },
            )
        }

        if (showExtendDialog) {
            RepressDurationDialog(
                title = "Extend repression",
                warning = "This adds time to the current repression. Time can only be added — " +
                    "never removed.",
                confirmLabel = "Add time",
                onConfirm = { millis ->
                    RepressManager.extend(millis)
                    showExtendDialog = false
                    context.toast("Repression extended")
                },
                onDismiss = { showExtendDialog = false },
            )
        }

        when (dialogMode) {
            OtherSideDialog.SetPin, OtherSideDialog.ChangePinNew -> {
                OtherSidePinDialog(
                    mode = OtherSidePinMode.SET,
                    onUnlocked = {},
                    onPinSet = { pin ->
                        securityPreferences.otherSidePinHash.set(OtherSidePin.hash(pin))
                        // Leave OtherSide LOCKED after (re)setting the PIN so the gate is active
                        // immediately — otherwise the session stays unlocked until app close.
                        OtherSideLock.lock()
                        dialogMode = null
                        context.toast("OtherSide PIN saved")
                    },
                    onDismiss = { dialogMode = null },
                )
            }
            OtherSideDialog.VerifyForChange -> {
                OtherSidePinDialog(
                    mode = OtherSidePinMode.UNLOCK,
                    onUnlocked = { dialogMode = OtherSideDialog.ChangePinNew },
                    onDismiss = { dialogMode = null },
                    onMaxAttempts = {
                        dialogMode = null
                        context.toast("Too many attempts")
                    },
                )
            }
            OtherSideDialog.VerifyForRemove -> {
                OtherSidePinDialog(
                    mode = OtherSidePinMode.UNLOCK,
                    onUnlocked = {
                        securityPreferences.otherSidePinHash.set("")
                        OtherSideLock.lock()
                        dialogMode = null
                        context.toast("OtherSide PIN removed")
                    },
                    onDismiss = { dialogMode = null },
                    onMaxAttempts = {
                        dialogMode = null
                        context.toast("Too many attempts")
                    },
                )
            }
            null -> {}
        }

        return Preference.PreferenceGroup(
            title = "OtherSide lock",
            preferenceItems = buildList {
                if (repressed) {
                    // Hard time-lock is active: OtherSide is inert and the PIN can't be changed.
                    add(
                        Preference.PreferenceItem.TextPreference(
                            title = "Repressed — ${formatRemaining(RepressManager.remainingMillisApprox())} left",
                            subtitle = "OtherSide is hidden and inert. Can't be cancelled or " +
                                "shortened — only waited out. Clears automatically once the time " +
                                "passes and the device is online.",
                            onClick = {},
                        ),
                    )
                    add(
                        Preference.PreferenceItem.TextPreference(
                            title = "Extend repression",
                            subtitle = "Add more time (add-only).",
                            onClick = { showExtendDialog = true },
                        ),
                    )
                    return@buildList
                }

                if (!hasPin) {
                    add(
                        Preference.PreferenceItem.TextPreference(
                            title = "OtherSide PIN",
                            subtitle = "Suppress is off. Set a PIN to gate OtherSide.",
                            onClick = { dialogMode = OtherSideDialog.SetPin },
                        ),
                    )
                } else {
                    add(
                        Preference.PreferenceItem.TextPreference(
                            title = "Change PIN",
                            subtitle = "Suppress is on.",
                            onClick = { dialogMode = OtherSideDialog.VerifyForChange },
                        ),
                    )
                    add(
                        Preference.PreferenceItem.TextPreference(
                            title = "Remove PIN",
                            subtitle = "Turn Suppress off.",
                            onClick = { dialogMode = OtherSideDialog.VerifyForRemove },
                        ),
                    )
                    add(
                        Preference.PreferenceItem.ListPreference(
                            preference = securityPreferences.otherSideLockAfter,
                            entries = OtherSideLockAfterValues
                                .associateWith { value ->
                                    when (value) {
                                        0 -> "Immediately"
                                        else -> "$value min"
                                    }
                                },
                            title = "Lock OtherSide after",
                        ),
                    )
                }

                add(
                    Preference.PreferenceItem.TextPreference(
                        title = "Repress OtherSide",
                        subtitle = "Hard time-lock. Can't be cancelled or shortened once set.",
                        onClick = { showRepressDialog = true },
                    ),
                )
                // TEST-ONLY: verify the full repress -> trusted-time -> auto-clear cycle in 2 minutes.
                // Remove this (and RepressManager.repressForTest) once verified.
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = "Test repress — 2 minutes",
                        subtitle = "TEST BUILD ONLY: represses for 2 minutes so you can confirm it " +
                            "auto-unlocks. Tap the OtherSide toggle after ~2 min to trigger the check.",
                        onClick = {
                            RepressManager.repressForTest(2 * 60 * 1000L)
                            context.toast("Test repress started — ~2 minutes")
                        },
                    ),
                )
            },
        )
    }

    @Composable
    private fun getSecurityGroup(
        securityPreferences: SecurityPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val authSupported = remember { context.isAuthenticationSupported() }
        val useAuthPref = securityPreferences.useAuthenticator
        val useAuth by useAuthPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_security),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = useAuthPref,
                    title = stringResource(MR.strings.lock_with_biometrics),
                    enabled = authSupported,
                    onValueChanged = {
                        (context as FragmentActivity).authenticate(
                            title = context.stringResource(MR.strings.lock_with_biometrics),
                        )
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = securityPreferences.lockAppAfter,
                    entries = LockAfterValues
                        .associateWith {
                            when (it) {
                                -1 -> stringResource(MR.strings.lock_never)
                                0 -> stringResource(MR.strings.lock_always)
                                else -> pluralStringResource(MR.plurals.lock_after_mins, count = it, it)
                            }
                        },
                    title = stringResource(MR.strings.lock_when_idle),
                    enabled = authSupported && useAuth,
                    onValueChanged = {
                        (context as FragmentActivity).authenticate(
                            title = context.stringResource(MR.strings.lock_when_idle),
                        )
                    },
                ),

                Preference.PreferenceItem.SwitchPreference(
                    preference = securityPreferences.hideNotificationContent,
                    title = stringResource(MR.strings.hide_notification_content),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = securityPreferences.secureScreen,
                    entries = SecurityPreferences.SecureScreenMode.entries
                        .associateWith { stringResource(it.titleRes) },
                    title = stringResource(MR.strings.secure_screen),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.secure_screen_summary)),
            ),
        )
    }

    @Composable
    private fun getFirebaseGroup(
        privacyPreferences: PrivacyPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_firebase),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = privacyPreferences.crashlytics,
                    title = stringResource(MR.strings.onboarding_permission_crashlytics),
                    subtitle = stringResource(MR.strings.onboarding_permission_crashlytics_description),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = privacyPreferences.analytics,
                    title = stringResource(MR.strings.onboarding_permission_analytics),
                    subtitle = stringResource(MR.strings.onboarding_permission_analytics_description),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.firebase_summary)),
            ),
        )
    }
}

/**
 * Duration picker used for both starting a Repression and extending one. Presets only; the minimum
 * (one day) is enforced by [RepressManager.repress] regardless.
 */
@Composable
private fun RepressDurationDialog(
    title: String,
    warning: String,
    confirmLabel: String,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(RepressPresets.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(warning)
                RepressPresets.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = preset == selected,
                                onClick = { selected = preset },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = preset == selected,
                            onClick = { selected = preset },
                        )
                        Text(
                            text = preset.label,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.millis) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun formatRemaining(millis: Long): String {
    val totalMinutes = millis / 60_000L
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    return if (days > 0) {
        "${days}d ${hours}h"
    } else {
        val minutes = totalMinutes % 60
        "${hours}h ${minutes}m"
    }
}

private data class RepressPreset(val label: String, val millis: Long)

private val RepressPresets = listOf(
    RepressPreset("1 day", RepressManager.ONE_DAY),
    RepressPreset("3 days", 3 * RepressManager.ONE_DAY),
    RepressPreset("1 week", 7 * RepressManager.ONE_DAY),
    RepressPreset("1 month", 30 * RepressManager.ONE_DAY),
)

private val LockAfterValues = listOf(
    0, // Always
    1,
    2,
    5,
    10,
    -1, // Never
)

private val OtherSideLockAfterValues = listOf(
    0, // Immediately
    1,
    2,
    5,
    10,
)

private enum class OtherSideDialog {
    SetPin,
    VerifyForChange,
    ChangePinNew,
    VerifyForRemove,
}
