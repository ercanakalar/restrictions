package io.github.ercanakalar.restrictions.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LockPolicyTest {

    private val zone = ZoneId.of("Europe/Istanbul")
    private val bedtime = Schedule("bed", "Bedtime", 21 * 60, 7 * 60, Schedule.EVERY_DAY)

    private fun millis(day: Int, hour: Int, minute: Int = 0) =
        LockPolicy.toMillis(LocalDateTime.of(2026, 10, day, hour, minute), zone)

    private fun inputs(now: Long) = LockInputs(nowMillis = now, zone = zone, schedules = listOf(bedtime))

    @Test
    fun freeOutsideSchedules() {
        assertEquals(LockDecision.Unlocked, LockPolicy.evaluate(inputs(millis(5, 12))))
    }

    @Test
    fun scheduleLocksUntilItsEnd() {
        val decision = LockPolicy.evaluate(inputs(millis(5, 22)))
        assertEquals(LockDecision.Locked(LockReason.SCHEDULE, millis(6, 7), "Bedtime"), decision)
    }

    @Test
    fun parentPassBeatsScheduleUntilItExpires() {
        val now = millis(5, 22)
        val pass = now + 30 * 60_000
        assertEquals(LockDecision.ParentPass(pass), LockPolicy.evaluate(inputs(now).copy(passUntilMillis = pass)))
        val later = inputs(pass).copy(passUntilMillis = pass)
        assertEquals(LockReason.SCHEDULE, (LockPolicy.evaluate(later) as LockDecision.Locked).reason)
    }

    @Test
    fun tamperLockBeatsEverything() {
        val now = millis(5, 12)
        val decision = LockPolicy.evaluate(inputs(now).copy(tamperLocked = true, passUntilMillis = now + 60_000))
        assertEquals(LockDecision.Locked(LockReason.TAMPER, null), decision)
    }

    @Test
    fun clockLockOnlyWhenRequired() {
        val now = millis(5, 12)
        assertEquals(
            LockDecision.Locked(LockReason.CLOCK, null),
            LockPolicy.evaluate(inputs(now).copy(requireAutoTime = true, autoTimeEnabled = false)),
        )
        assertEquals(
            LockDecision.Unlocked,
            LockPolicy.evaluate(inputs(now).copy(requireAutoTime = false, autoTimeEnabled = false)),
        )
    }

    @Test
    fun manualLockWithAndWithoutEnd() {
        val now = millis(5, 12)
        val until = now + 3_600_000
        assertEquals(
            LockDecision.Locked(LockReason.MANUAL, until),
            LockPolicy.evaluate(inputs(now).copy(manualLockUntilMillis = until)),
        )
        val forever = LockPolicy.evaluate(inputs(now).copy(manualLockUntilMillis = LockPolicy.UNTIL_PARENT_UNLOCKS))
        assertEquals(LockDecision.Locked(LockReason.MANUAL, null), forever)
        assertEquals(LockDecision.Unlocked, LockPolicy.evaluate(inputs(until).copy(manualLockUntilMillis = until)))
    }

    @Test
    fun scheduleLockEndAndNextBlock() {
        assertEquals(millis(6, 7), LockPolicy.scheduleLockEndMillis(listOf(bedtime), millis(5, 23), zone))
        assertNull(LockPolicy.scheduleLockEndMillis(listOf(bedtime), millis(5, 12), zone))
        val next = LockPolicy.nextBlock(listOf(bedtime), millis(5, 20, 50), zone, withinMinutes = 15)
        assertEquals(LocalDateTime.of(2026, 10, 5, 21, 0), next?.start)
    }
}
