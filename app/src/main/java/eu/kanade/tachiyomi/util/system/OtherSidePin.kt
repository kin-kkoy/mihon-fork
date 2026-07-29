package eu.kanade.tachiyomi.util.system

import java.security.MessageDigest

/**
 * Helper for hashing and verifying the OtherSide "Suppress" PIN.
 *
 * The raw PIN is never stored: only a salted SHA-256 hash is persisted.
 */
object OtherSidePin {

    // Fixed app salt. Not a secret store, just to avoid trivially precomputed rainbow tables
    // against short numeric PINs stored on-device.
    private const val SALT = "uttori::otherside::suppress::v1"

    /**
     * Returns the lowercase hex-encoded SHA-256 of [SALT] + [pin].
     */
    fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((SALT + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns true if [pin] hashes to the [stored] hash. Always false if [stored] is blank.
     */
    fun verify(pin: String, stored: String): Boolean {
        if (stored.isEmpty()) return false
        return hash(pin) == stored
    }
}
