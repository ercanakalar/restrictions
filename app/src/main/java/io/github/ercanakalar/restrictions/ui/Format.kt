package io.github.ercanakalar.restrictions.ui

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import io.github.ercanakalar.restrictions.R
import io.github.ercanakalar.restrictions.core.Schedule
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** "21:00" today, "Tue 07:00" on another day. Follows the phone's 12/24-hour setting. */
fun formatClock(context: Context, millis: Long): String {
    var flags = DateUtils.FORMAT_SHOW_TIME
    if (!DateUtils.isToday(millis)) flags = flags or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
    return DateUtils.formatDateTime(context, millis, flags)
}

fun formatTimeOnly(context: Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)

fun formatMinuteOfDay(context: Context, minuteOfDay: Int): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        .format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

/** Monday..Sunday or Sunday..Saturday, depending on the phone's language. */
fun orderedWeek(locale: Locale = Locale.getDefault()): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return (0L..6L).map { first.plus(it) }
}

fun dayShortName(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

fun formatDays(context: Context, days: Set<DayOfWeek>): String = when (days) {
    Schedule.EVERY_DAY -> context.getString(R.string.days_every_day)
    Schedule.WEEKDAYS -> context.getString(R.string.days_weekdays)
    Schedule.WEEKEND -> context.getString(R.string.days_weekend)
    emptySet<DayOfWeek>() -> context.getString(R.string.days_none)
    else -> orderedWeek().filter { it in days }.joinToString(", ") { dayShortName(it) }
}

fun formatScheduleTimes(context: Context, schedule: Schedule): String {
    val range = formatMinuteOfDay(context, schedule.startMinute) + " – " + formatMinuteOfDay(context, schedule.endMinute)
    return when {
        schedule.durationMinutes == Schedule.MINUTES_PER_DAY -> context.getString(R.string.schedule_all_day)
        schedule.crossesMidnight -> context.getString(R.string.schedule_next_day, range)
        else -> range
    }
}

fun formatWait(context: Context, millis: Long): String {
    val seconds = (millis + 999) / 1000
    return if (seconds < 60) {
        context.getString(R.string.wait_seconds, seconds)
    } else {
        context.getString(R.string.wait_minutes, (seconds + 59) / 60)
    }
}

/** The current time, updated every [intervalMillis]. */
@Composable
fun rememberNow(intervalMillis: Long = 1_000): State<Long> =
    produceState(System.currentTimeMillis(), intervalMillis) {
        while (true) {
            value = System.currentTimeMillis()
            delay(intervalMillis)
        }
    }
