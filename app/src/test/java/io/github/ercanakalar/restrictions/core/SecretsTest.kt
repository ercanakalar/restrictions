package io.github.ercanakalar.restrictions.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretsTest {

    @Test
    fun encodedPinVerifiesOnlyWithSamePin() {
        val stored = Secrets.encode("4821")
        assertTrue(Secrets.verify("4821", stored))
        assertFalse(Secrets.verify("4822", stored))
        assertFalse(Secrets.verify("", stored))
    }

    @Test
    fun saltMakesEveryEncodingDifferent() {
        assertNotEquals(Secrets.encode("4821"), Secrets.encode("4821"))
    }

    @Test
    fun malformedStoredValuesNeverVerify() {
        assertFalse(Secrets.verify("4821", null))
        assertFalse(Secrets.verify("4821", ""))
        assertFalse(Secrets.verify("4821", "no-colon"))
        assertFalse(Secrets.verify("4821", "!!!:???"))
    }

    @Test
    fun pinRules() {
        assertTrue(Secrets.isValidPin("1234"))
        assertTrue(Secrets.isValidPin("12345678"))
        assertFalse(Secrets.isValidPin("123"))
        assertFalse(Secrets.isValidPin("123456789"))
        assertFalse(Secrets.isValidPin("12a4"))
    }

    @Test
    fun recoveryCodesAreTwelveDigits() {
        val code = Secrets.newRecoveryCode()
        assertTrue(Secrets.looksLikeRecoveryCode(code))
        assertFalse(Secrets.isValidPin(code))
        assertEquals("1234 5678 9012", Secrets.formatRecoveryCode("123456789012"))
    }

    @Test
    fun lockoutGrowsAndIsCapped() {
        assertEquals(0, Lockout.delayMillis(4))
        assertEquals(30_000, Lockout.delayMillis(5))
        assertEquals(60_000, Lockout.delayMillis(6))
        assertEquals(15 * 60_000L, Lockout.delayMillis(50))
    }
}
