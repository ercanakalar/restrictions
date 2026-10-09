package io.github.ercanakalar.restrictions.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Why the phone is locked. Earlier entries win when several apply. */
enum class LockReason {
    /** Someone tried to switch off the protection; only the parent PIN clears it. */
    TAMPER,

    /** Automatic date & time is off, so the schedules cannot be trusted. */
    CLOCK,

    /** A parent pressed "Lock now". */
    MANUAL,

    /** A schedule is running. */
    SCHEDULE,
}

sealed interface LockDecision {
    data object Unlocked : LockDecision

    /** A parent gave extra time; the phone is usable until [untilMillis]. */
    data class ParentPass(val untilMillis: Long) : LockDecision

    /** [untilMillis] is null when the lock lasts until a parent ends it. */
    data class Locked(
        val reason: LockReason,
        val untilMillis: Long?,
        val scheduleName: String? = null,
    ) : LockDecision
}

/** Everything the lock decision depends on, gathered in one place so it can be unit tested. */
data class LockInputs(
    val nowMillis: Long,
    val zone: ZoneId,
    val schedules: List<Schedule>,
    val passUntilMillis: Long = 0,
    val manualLockUntilMillis: Long = 0,
    val tamperLocked: Boolean = false,
    val requireAutoTime: Boolean = false,
    val autoTimeEnabled: Boolean = true,
)

object LockPolicy {

    /** Value for [LockInputs.manualLockUntilMillis] meaning "until a parent unlocks". */
    const val UNTIL_PARENT_UNLOCKS = Long.MAX_VALUE

    fun evaluate(inputs: LockInputs): LockDecision {
        val now = inputs.nowMillis
        if (inputs.tamperLocked) {
            return LockDecision.Locked(LockReason.TAMPER, untilMillis = null)
        }
        if (inputs.passUntilMillis > now) {
            return LockDecision.ParentPass(inputs.passUntilMillis)
        }
        if (inputs.requireAutoTime && !inputs.autoTimeEnabled) {
            return LockDecision.Locked(LockReason.CLOCK, untilMillis = null)
        }
        if (inputs.manualLockUntilMillis > now) {
            val until = inputs.manualLockUntilMillis.takeIf { it != UNTIL_PARENT_UNLOCKS }
            return LockDecision.Locked(LockReason.MANUAL, until)
        }
        val local = toLocal(now, inputs.zone)
        val active = ScheduleEngine.activeOccurrences(inputs.schedules, local)
        if (active.isNotEmpty()) {
            val until = ScheduleEngine.lockedUntil(inputs.schedules, local)
            return LockDecision.Locked(
                reason = LockReason.SCHEDULE,
                untilMillis = until?.let { toMillis(it, inputs.zone) },
                scheduleName = active.first().schedule.name,
            )
        }
        return LockDecision.Unlocked
    }

    /** When the schedule lock running at [nowMillis] ends, or null when no schedule is running. */
    fun scheduleLockEndMillis(schedules: List<Schedule>, nowMillis: Long, zone: ZoneId): Long? =
        ScheduleEngine.lockedUntil(schedules, toLocal(nowMillis, zone))?.let { toMillis(it, zone) }

    /** The next schedule block starting within [withinMinutes], or null. */
    fun nextBlock(schedules: List<Schedule>, nowMillis: Long, zone: ZoneId, withinMinutes: Long): Occurrence? =
        ScheduleEngine.nextStart(schedules, toLocal(nowMillis, zone), withinMinutes)

    fun toLocal(millis: Long, zone: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    fun toMillis(time: LocalDateTime, zone: ZoneId): Long =
        time.atZone(zone).toInstant().toEpochMilli()
}
