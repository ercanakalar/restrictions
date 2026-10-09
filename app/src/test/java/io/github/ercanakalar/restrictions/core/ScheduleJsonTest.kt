package io.github.ercanakalar.restrictions.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class ScheduleJsonTest {

    @Test
    fun roundTrip() {
        val schedules = listOf(
            Schedule("a", "Bedtime", 21 * 60, 7 * 60, Schedule.EVERY_DAY),
            Schedule("b", "Homework", 16 * 60, 18 * 60, Schedule.WEEKDAYS, enabled = false),
        )
        assertEquals(schedules, ScheduleJson.decode(ScheduleJson.encode(schedules)))
    }

    @Test
    fun badInputGivesEmptyListOrSkipsEntries() {
        assertTrue(ScheduleJson.decode(null).isEmpty())
        assertTrue(ScheduleJson.decode("not json").isEmpty())
        val mixed = """[{"id":"x","name":"Bad","start":5000,"end":0,"days":[1]},
            {"id":"y","name":"Ok","start":60,"end":120,"days":[1,9]}]"""
        val decoded = ScheduleJson.decode(mixed)
        assertEquals(1, decoded.size)
        assertEquals(setOf(DayOfWeek.MONDAY), decoded.single().days)
    }
}
