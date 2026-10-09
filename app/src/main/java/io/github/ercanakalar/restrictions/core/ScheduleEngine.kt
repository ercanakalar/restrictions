package io.github.ercanakalar.restrictions.core

import java.time.LocalDateTime

/** One concrete block of a [Schedule], e.g. Monday 21:00 until Tuesday 07:00. */
data class Occurrence(
    val schedule: Schedule,
    val start: LocalDateTime,
    val end: LocalDateTime,
)

/**
 * Answers "is the phone locked right now and until when?" for a list of schedules.
 * Works on wall-clock time so that "21:00" always means 21:00 on the phone's clock.
 */
object ScheduleEngine {

    /** Upper bound when following back-to-back blocks, so a 24/7 setup cannot loop forever. */
    private const val MAX_CHAINED_BLOCKS = 32

    /** The block of [schedule] that contains [now], if any. */
    fun occurrenceAt(schedule: Schedule, now: LocalDateTime): Occurrence? {
        if (!schedule.enabled || schedule.days.isEmpty()) return null
        // A block that contains `now` started either today or yesterday.
        for (daysBack in 0L..1L) {
            val day = now.toLocalDate().minusDays(daysBack)
            if (day.dayOfWeek !in schedule.days) continue
            val start = day.atStartOfDay().plusMinutes(schedule.startMinute.toLong())
            val end = start.plusMinutes(schedule.durationMinutes.toLong())
            if (!now.isBefore(start) && now.isBefore(end)) {
                return Occurrence(schedule, start, end)
            }
        }
        return null
    }

    /** All blocks that contain [now]. */
    fun activeOccurrences(schedules: List<Schedule>, now: LocalDateTime): List<Occurrence> =
        schedules.mapNotNull { occurrenceAt(it, now) }

    /**
     * When the schedule lock that is active at [now] ends, or null when no schedule is active.
     * Overlapping and back-to-back blocks are merged, so "21:00–00:00" followed by
     * "00:00–07:00" reports 07:00.
     */
    fun lockedUntil(schedules: List<Schedule>, now: LocalDateTime): LocalDateTime? {
        val active = activeOccurrences(schedules, now)
        if (active.isEmpty()) return null
        var end = active.maxOf { it.end }
        repeat(MAX_CHAINED_BLOCKS) {
            val next = activeOccurrences(schedules, end)
            if (next.isEmpty()) return end
            val nextEnd = next.maxOf { it.end }
            if (!nextEnd.isAfter(end)) return end
            end = nextEnd
        }
        return end
    }

    /**
     * The earliest block that starts after [now] and no later than [now] + [withinMinutes].
     * Blocks that are already running are not reported.
     */
    fun nextStart(schedules: List<Schedule>, now: LocalDateTime, withinMinutes: Long): Occurrence? {
        val limit = now.plusMinutes(withinMinutes)
        val maxDaysAhead = withinMinutes / Schedule.MINUTES_PER_DAY + 1
        var best: Occurrence? = null
        for (schedule in schedules) {
            if (!schedule.enabled) continue
            for (daysAhead in 0L..maxDaysAhead) {
                val day = now.toLocalDate().plusDays(daysAhead)
                if (day.dayOfWeek !in schedule.days) continue
                val start = day.atStartOfDay().plusMinutes(schedule.startMinute.toLong())
                if (!start.isAfter(now) || start.isAfter(limit)) continue
                if (best == null || start.isBefore(best.start)) {
                    best = Occurrence(schedule, start, start.plusMinutes(schedule.durationMinutes.toLong()))
                }
            }
        }
        return best
    }
}
