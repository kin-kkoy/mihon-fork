package eu.kanade.tachiyomi.ui.security

import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.system.OtherSidePin
import kotlinx.coroutines.flow.MutableStateFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Session-scoped lock state for the OtherSide "Suppress" PIN gate.
 *
 * This is STAGE A infrastructure: it owns the unlocked/locked state, attempt counting and the
 * re-lock-on-background logic. Wiring the gate into the actual OtherSide entry points is a later
 * stage; the max-attempts "Repress" behaviour is left as an unset hook.
 */
object OtherSideLock {

    private val securityPreferences: SecurityPreferences
        get() = Injekt.get()

    private const val MAX_ATTEMPTS = 3

    /**
     * True when a PIN has been set, i.e. the Suppress lock is active.
     */
    val enabled: Boolean
        get() = securityPreferences.otherSidePinHash.get().isNotEmpty()

    /**
     * Session unlock state. Resets to locked on process death (default false).
     */
    val unlocked = MutableStateFlow(false)

    /**
     * True when the lock is enabled but the session has not been unlocked.
     */
    val isLocked: Boolean
        get() = enabled && !unlocked.value

    /**
     * Remaining attempts before the max-attempts hook fires.
     */
    private var remainingAttempts = MAX_ATTEMPTS

    /**
     * Timestamp of the last time the app was backgrounded, used to decide whether enough idle
     * time has elapsed to re-lock.
     */
    private var lastActive: Long = 0

    /**
     * Invoked when failed attempts reach the max. Left unset for now; the auto-Repress stage
     * will wire this. When invoked, the attempt counter has already been reset.
     */
    var onMaxAttempts: (() -> Unit)? = null

    /**
     * Attempts to unlock with [pin]. On success, marks unlocked and resets attempts.
     */
    fun tryUnlock(pin: String): Boolean {
        val ok = OtherSidePin.verify(pin, securityPreferences.otherSidePinHash.get())
        if (ok) {
            unlocked.value = true
            remainingAttempts = MAX_ATTEMPTS
        }
        return ok
    }

    /**
     * Registers a failed attempt and returns the number of attempts remaining. When it reaches 0
     * the counter is reset and [onMaxAttempts] is invoked (if set).
     */
    fun onFailedAttempt(): Int {
        remainingAttempts -= 1
        if (remainingAttempts <= 0) {
            remainingAttempts = MAX_ATTEMPTS
            onMaxAttempts?.invoke()
            return 0
        }
        return remainingAttempts
    }

    /**
     * Re-locks the session and resets the attempt counter.
     */
    fun lock() {
        unlocked.value = false
        remainingAttempts = MAX_ATTEMPTS
    }

    /**
     * Records the background time so [onAppForegrounded] can decide whether to re-lock.
     */
    fun onAppBackgrounded() {
        if (!enabled) return
        lastActive = System.currentTimeMillis()
    }

    /**
     * Re-locks OtherSide when the configured idle timeout has elapsed since backgrounding.
     * A timeout of 0 re-locks immediately.
     */
    fun onAppForegrounded() {
        if (!enabled) return
        val lockAfterMinutes = securityPreferences.otherSideLockAfter.get()
        val shouldLock = when {
            lockAfterMinutes <= 0 -> true
            else -> lastActive + lockAfterMinutes * 60_000L <= System.currentTimeMillis()
        }
        if (shouldLock) {
            lock()
        }
    }
}
