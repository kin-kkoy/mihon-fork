package eu.kanade.tachiyomi.ui.security

import android.os.SystemClock
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.system.TrustedTime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Owns the OtherSide hard "Repress" time-lock (stage A).
 *
 * The preference [SecurityPreferences.otherSideRepressedUntil] (epoch millis) is the single source
 * of truth. While it is greater than 0 OtherSide is repressed and completely inert. It is ONLY ever
 * cleared when TRUSTED network time proves the deadline has passed; the device clock is never
 * trusted to clear it, so an offline device stays repressed (fail-closed).
 *
 * Repression can only be started or extended — never cancelled or shortened.
 */
object RepressManager {

    const val ONE_DAY = 24 * 60 * 60 * 1000L

    /** Skip a network time check if we ran one less than this ago. */
    private const val REFRESH_THROTTLE_MILLIS = 15 * 60 * 1000L

    private val prefs: SecurityPreferences
        get() = Injekt.get()

    /** Monotonic timestamp of the last trusted-time check; 0 = never. */
    private var lastRefreshElapsed: Long = 0

    /**
     * Synchronous, conservative check used by the gate and UI: true while a repression deadline is
     * set. Does not consult the network — the deadline is only relaxed by [refresh].
     */
    val isRepressed: Boolean
        get() = prefs.otherSideRepressedUntil.get() > 0

    /**
     * Starts a repression lasting [durationMillis] (minimum one day).
     *
     * The anchor is trusted network time when it is quickly available, else the device clock —
     * starting a repression while offline is fine because only the EXIT must be trusted.
     */
    fun repress(durationMillis: Long) {
        val duration = maxOf(durationMillis, ONE_DAY)
        val anchor = System.currentTimeMillis()
        prefs.otherSideRepressedUntil.set(anchor + duration)
    }

    /**
     * Extends the current repression by [additionalMillis]. Add-only: the deadline can only move
     * later, never earlier. Safe to call whether or not a repression is currently active.
     */
    fun extend(additionalMillis: Long) {
        val current = prefs.otherSideRepressedUntil.get()
        val base = maxOf(current, System.currentTimeMillis())
        prefs.otherSideRepressedUntil.set(base + additionalMillis)
    }

    /**
     * Checks trusted network time and clears the repression if it has genuinely elapsed.
     *
     * Throttled to at most once per [REFRESH_THROTTLE_MILLIS]. If the network time is unavailable
     * (offline / blocked) nothing changes and the repression stays in place (fail-closed).
     */
    suspend fun refresh() {
        val until = prefs.otherSideRepressedUntil.get()
        if (until <= 0) return

        val now = SystemClock.elapsedRealtime()
        if (lastRefreshElapsed != 0L && now - lastRefreshElapsed < REFRESH_THROTTLE_MILLIS) return
        lastRefreshElapsed = now

        val trustedNow = TrustedTime.nowMillis() ?: return
        if (trustedNow >= until) {
            prefs.otherSideRepressedUntil.set(0)
        }
    }

    /**
     * Approximate remaining repression in millis, for DISPLAY only (uses the untrusted device
     * clock). Never used to decide whether the repression is over.
     */
    fun remainingMillisApprox(): Long {
        val until = prefs.otherSideRepressedUntil.get()
        return maxOf(0L, until - System.currentTimeMillis())
    }
}
