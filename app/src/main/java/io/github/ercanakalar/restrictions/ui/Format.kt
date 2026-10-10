package io.github.ercanakalar.restrictions.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import io.github.ercanakalar.restrictions.R
import io.github.ercanakalar.restrictions.core.Schedule
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** The language the app is shown in, which can differ from the phone's before Android 13. */
fun appLocale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

private fun timePattern(context: Context) = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"

/** "21:00" today, "Tue 21:00" on another day. Follows the phone's 12/24-hour setting. */
fun formatClock(context: Context, millis: Long): String {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(millis).atZone(zone)
    val pattern = if (time.toLocalDate() == LocalDate.now(zone)) timePattern(context) else "EEE " + timePattern(context)
    return time.format(DateTimeFormatter.ofPattern(pattern, appLocale(context)))
}

fun formatTimeOnly(context: Context, millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(timePattern(context), appLocale(context)))

fun formatMinuteOfDay(context: Context, minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        .format(DateTimeFormatter.ofPattern(timePattern(context), appLocale(context)))

/** Monday..Sunday or Sunday..Saturday, depending on the language. */
fun orderedWeek(context: Context): List<DayOfWeek> {
    val first = WeekFields.of(appLocale(context)).firstDayOfWeek
    return (0L..6L).map { first.plus(it) }
}

fun dayShortName(context: Context, day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, appLocale(context))

fun formatDays(context: Context, days: Set<DayOfWeek>): String = when (days) {
    Schedule.EVERY_DAY -> context.getString(R.string.days_every_day)
    Schedule.WEEKDAYS -> context.getString(R.string.days_weekdays)
    Schedule.WEEKEND -> context.getString(R.string.days_weekend)
    emptySet<DayOfWeek>() -> context.getString(R.string.days_none)
    else -> orderedWeek(context).filter { it in days }.joinToString(", ") { dayShortName(context, it) }
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
