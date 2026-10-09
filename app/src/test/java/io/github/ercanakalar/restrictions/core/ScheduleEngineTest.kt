package io.github.ercanakalar.restrictions.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ScheduleEngineTest {

    // 2026-10-05 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    private fun schedule(
        start: String,
        end: String,
        days: Set<DayOfWeek> = Schedule.EVERY_DAY,
        enabled: Boolean = true,
        id: String = "s",
    ): Schedule {
        fun minutes(t: String) = t.substringBefore(':').toInt() * 60 + t.substringAfter(':').toInt()
        return Schedule(id, "Test $id", minutes(start), minutes(end), days, enabled)
    }

    @Test
    fun daytimeBlockIncludesStartAndExcludesEnd() {
        val homework = schedule("16:00", "18:00")
        assertNull(ScheduleEngine.occurrenceAt(homework, at(5, 15, 59)))
        assertNotNull(ScheduleEngine.occurrenceAt(homework, at(5, 16, 0)))
        assertNotNull(ScheduleEngine.occurrenceAt(homework, at(5, 17, 59)))
        assertNull(ScheduleEngine.occurrenceAt(homework, at(5, 18, 0)))
    }

    @Test
    fun overnightBlockContinuesIntoNextDay() {
        val bedtime = schedule("21:00", "07:00", days = setOf(DayOfWeek.MONDAY))
        assertTrue(bedtime.crossesMidnight)
        assertNotNull(ScheduleEngine.occurrenceAt(bedtime, at(5, 21, 0)))
        // Tuesday morning belongs to Monday's block even though Tuesday is not selected.
        val morning = ScheduleEngine.occurrenceAt(bedtime, at(6, 6, 59))
        assertEquals(at(5, 21, 0), morning?.start)
        assertEquals(at(6, 7, 0), morning?.end)
        assertNull(ScheduleEngine.occurrenceAt(bedtime, at(6, 7, 0)))
        // Sunday night is not selected.
        assertNull(ScheduleEngine.occurrenceAt(bedtime, at(4, 23, 0)))
        assertNull(ScheduleEngine.occurrenceAt(bedtime, at(5, 3, 0)))
    }

    @Test
    fun equalStartAndEndMeansWholeDay() {
        val sunday = schedule("00:00", "00:00", days = setOf(DayOfWeek.SUNDAY))
        assertEquals(Schedule.MINUTES_PER_DAY, sunday.durationMinutes)
        assertNotNull(ScheduleEngine.occurrenceAt(sunday, at(4, 0, 0)))
        assertNotNull(ScheduleEngine.occurrenceAt(sunday, at(4, 23, 59)))
        assertNull(ScheduleEngine.occurrenceAt(sunday, at(5, 0, 0)))
    }

    @Test
    fun disabledOrDaylessSchedulesNeverLock() {
        assertNull(ScheduleEngine.occurrenceAt(schedule("00:00", "00:00", enabled = false), at(5, 12)))
        assertNull(ScheduleEngine.occurrenceAt(schedule("00:00", "00:00", days = emptySet()), at(5, 12)))
    }

    @Test
    fun lockedUntilMergesBackToBackBlocks() {
        val evening = schedule("21:00", "00:00", id = "a")
        val night = schedule("00:00", "07:00", id = "b")
        assertEquals(at(6, 7, 0), ScheduleEngine.lockedUntil(listOf(evening, night), at(5, 22, 0)))
    }

    @Test
    fun lockedUntilTakesLatestOverlappingEnd() {
        val short = schedule("20:00", "21:00", id = "a")
        val long = schedule("20:30", "22:00", id = "b")
        assertEquals(at(5, 22, 0), ScheduleEngine.lockedUntil(listOf(short, long), at(5, 20, 45)))
        assertNull(ScheduleEngine.lockedUntil(listOf(short, long), at(5, 19, 0)))
    }

    @Test
    fun lockedUntilStopsForRoundTheClockSetups() {
        val always = schedule("00:00", "00:00")
        val until = ScheduleEngine.lockedUntil(listOf(always), at(5, 12, 0))
        assertNotNull(until)
        assertTrue(until!!.isAfter(at(12, 12, 0)))
    }

    @Test
    fun nextStartFindsUpcomingBlockInsideWindow() {
        val bedtime = schedule("21:00", "07:00")
        assertEquals(at(5, 21, 0), ScheduleEngine.nextStart(listOf(bedtime), at(5, 20, 55), 10)?.start)
        assertNull(ScheduleEngine.nextStart(listOf(bedtime), at(5, 20, 55), 4))
        // A block that already started is not "next".
        assertEquals(at(6, 21, 0), ScheduleEngine.nextStart(listOf(bedtime), at(5, 21, 0), 24 * 60)?.start)
    }

    @Test
    fun nextStartLooksAcrossDays() {
        val mondayMorning = schedule("08:00", "12:00", days = setOf(DayOfWeek.MONDAY))
        val next = ScheduleEngine.nextStart(listOf(mondayMorning), at(10, 12, 0), 3 * 24 * 60L)
        assertEquals(at(12, 8, 0), next?.start)
        assertFalse(ScheduleEngine.nextStart(listOf(mondayMorning), at(10, 12, 0), 24 * 60L) != null)
    }
}
