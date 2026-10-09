package io.github.ercanakalar.restrictions.core

import java.time.DayOfWeek

/**
 * A recurring period in which the phone is locked, e.g. "Bedtime, every day 21:00–07:00".
 *
 * Times are minutes after midnight (0..1439). When [endMinute] is not after [startMinute]
 * the block runs past midnight and ends on the following day; equal values mean a full day.
 * [days] are the days on which a block *starts*.
 */
data class Schedule(
    val id: String,
    val name: String,
    val startMinute: Int,
    val endMinute: Int,
    val days: Set<DayOfWeek>,
    val enabled: Boolean = true,
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute out of range: $startMinute" }
        require(endMinute in 0 until MINUTES_PER_DAY) { "endMinute out of range: $endMinute" }
    }

    /** Length of one block in minutes (1..1440). */
    val durationMinutes: Int
        get() {
            val diff = Math.floorMod(endMinute - startMinute, MINUTES_PER_DAY)
            return if (diff == 0) MINUTES_PER_DAY else diff
        }

    /** True when a block that starts on one day ends on the next one. */
    val crossesMidnight: Boolean
        get() = startMinute + durationMinutes > MINUTES_PER_DAY

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}
