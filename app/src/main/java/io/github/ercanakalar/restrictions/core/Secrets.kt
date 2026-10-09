package io.github.ercanakalar.restrictions.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashing for the parent PIN and the recovery code. Only salted PBKDF2 hashes are stored,
 * never the PIN itself.
 */
object Secrets {
    const val PIN_MIN_LENGTH = 4
    const val PIN_MAX_LENGTH = 8

    /** Recovery codes are digits only, so they can be typed on the lock screen's keypad. */
    const val RECOVERY_CODE_LENGTH = 12

    private const val ITERATIONS = 20_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    private val random = SecureRandom()

    fun isValidPin(pin: String): Boolean =
        pin.length in PIN_MIN_LENGTH..PIN_MAX_LENGTH && pin.all { it in '0'..'9' }

    fun looksLikeRecoveryCode(input: String): Boolean =
        input.length == RECOVERY_CODE_LENGTH && input.all { it in '0'..'9' }

    fun newRecoveryCode(): String = buildString {
        repeat(RECOVERY_CODE_LENGTH) { append(random.nextInt(10)) }
    }

    /** "123456789012" -> "1234 5678 9012" */
    fun formatRecoveryCode(code: String): String = code.chunked(4).joinToString(" ")

    /** Returns "salt:hash", both Base64, ready to store. */
    fun encode(secret: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(salt) + ":" + encoder.encodeToString(hash(secret, salt))
    }

    fun verify(secret: String, encoded: String?): Boolean {
        val parts = encoded?.split(':') ?: return false
        if (parts.size != 2) return false
        val decoder = Base64.getDecoder()
        return try {
            val salt = decoder.decode(parts[0])
            val expected = decoder.decode(parts[1])
            MessageDigest.isEqual(hash(secret, salt), expected)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun hash(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/** Slows down guessing: after a few wrong PINs, entry is blocked for a growing time. */
object Lockout {
    const val FREE_ATTEMPTS = 5
    private const val BASE_DELAY_MILLIS = 30_000L
    private const val MAX_DELAY_MILLIS = 15 * 60_000L

    /** How long PIN entry is blocked after [failures] wrong attempts in a row. */
    fun delayMillis(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0
        val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        return (BASE_DELAY_MILLIS shl doublings).coerceAtMost(MAX_DELAY_MILLIS)
    }
}
