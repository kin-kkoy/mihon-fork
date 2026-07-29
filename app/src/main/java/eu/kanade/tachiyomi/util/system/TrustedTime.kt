package eu.kanade.tachiyomi.util.system

import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Request
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

/**
 * Fetches the current time from a trusted network source instead of the (spoofable) device clock.
 *
 * Used by the OtherSide "Repress" time-lock so that the repression deadline can only be cleared by
 * proof from the network — manipulating the device clock cannot end a repression early.
 */
object TrustedTime {

    private val hosts = listOf(
        "https://cloudflare.com",
        "https://www.google.com",
        "https://www.apple.com",
        "https://www.microsoft.com",
    )

    /**
     * Returns the current epoch millis from the first host that answers with a usable `Date` header,
     * or null if every host fails (offline / blocked). A null result means "unknown" and callers
     * must fail closed (stay repressed).
     */
    suspend fun nowMillis(): Long? = withIOContext {
        // Use a short-timeout client so an unreachable host doesn't hang the whole check.
        val client = Injekt.get<NetworkHelper>().client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()

        for (host in hosts) {
            try {
                val request = Request.Builder().url(host).head().build()
                client.newCall(request).execute().use { response ->
                    val date = response.headers.getDate("Date")
                    if (date != null) {
                        return@withIOContext date.time
                    }
                }
            } catch (e: Exception) {
                logcat { "TrustedTime: failed to reach $host (${e.message})" }
            }
        }
        null
    }
}
