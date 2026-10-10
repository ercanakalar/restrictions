package io.github.ercanakalar.restrictions.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.ercanakalar.restrictions.core.LockInputs
import io.github.ercanakalar.restrictions.core.LockPolicy
import io.github.ercanakalar.restrictions.core.Lockout
import io.github.ercanakalar.restrictions.core.Schedule
import io.github.ercanakalar.restrictions.core.ScheduleJson
import io.github.ercanakalar.restrictions.core.Secrets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArraySet

sealed interface PinResult {
    data class Ok(val usedRecoveryCode: Boolean) : PinResult
    data class Wrong(val attemptsLeft: Int) : PinResult
    data class LockedOut(val untilMillis: Long) : PinResult
}

/**
 * All settings and lock state, kept in private SharedPreferences. The lock service and the
 * parent screens run in the same process and share this one instance.
 */
class AppPrefs private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val _revision = MutableStateFlow(0)

    /** Increases on every change, so Compose screens can re-read their data. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    // SharedPreferences keeps only a weak reference to listeners, so hold this one in a field.
    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _revision.value++
        listeners.forEach { it() }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(changeListener)
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    // ---- Parent PIN and recovery code ----

    val isPinSet: Boolean get() = prefs.contains(KEY_PIN)

    /** True after the recovery code was used; the parent must choose a new PIN. */
    val mustChangePin: Boolean get() = prefs.getBoolean(KEY_MUST_CHANGE_PIN, false)

    fun setPin(pin: String) {
        require(Secrets.isValidPin(pin))
        prefs.edit {
            putString(KEY_PIN, Secrets.encode(pin))
            putBoolean(KEY_MUST_CHANGE_PIN, false)
        }
    }

    fun setRecoveryCode(code: String) {
        require(Secrets.looksLikeRecoveryCode(code))
        prefs.edit { putString(KEY_RECOVERY, Secrets.encode(code)) }
    }

    val lockoutUntilMillis: Long get() = prefs.getLong(KEY_LOCKOUT_UNTIL, 0)

    /** Checks a PIN or a 12-digit recovery code and applies the wrong-PIN lockout. */
    fun checkSecret(input: String, nowMillis: Long = System.currentTimeMillis()): PinResult {
        val lockedUntil = lockoutUntilMillis
        if (lockedUntil > nowMillis) return PinResult.LockedOut(lockedUntil)

        val usedRecovery = when {
            Secrets.verify(input, prefs.getString(KEY_PIN, null)) -> false
            Secrets.looksLikeRecoveryCode(input) && Secrets.verify(input, prefs.getString(KEY_RECOVERY, null)) -> true
            else -> null
        }
        if (usedRecovery != null) {
            prefs.edit {
                putInt(KEY_FAILURES, 0)
                putLong(KEY_LOCKOUT_UNTIL, 0)
                if (usedRecovery) putBoolean(KEY_MUST_CHANGE_PIN, true)
            }
            return PinResult.Ok(usedRecovery)
        }

        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        val delay = Lockout.delayMillis(failures)
        val until = if (delay > 0) nowMillis + delay else 0L
        prefs.edit {
            putInt(KEY_FAILURES, failures)
            putLong(KEY_LOCKOUT_UNTIL, until)
        }
        return if (delay > 0) PinResult.LockedOut(until) else PinResult.Wrong(Lockout.FREE_ATTEMPTS - failures)
    }

    // ---- Schedules ----

    var schedules: List<Schedule>
        get() = ScheduleJson.decode(prefs.getString(KEY_SCHEDULES, null))
        set(value) = prefs.edit { putString(KEY_SCHEDULES, ScheduleJson.encode(value)) }

    fun saveSchedule(schedule: Schedule) {
        val current = schedules
        schedules = if (current.any { it.id == schedule.id }) {
            current.map { if (it.id == schedule.id) schedule else it }
        } else {
            current + schedule
        }
    }

    fun deleteSchedule(id: String) {
        schedules = schedules.filterNot { it.id == id }
    }

    // ---- Apps that stay usable during a lock (the phone app is always allowed) ----

    var allowedPackages: Set<String>
        get() = prefs.getStringSet(KEY_ALLOWED_PACKAGES, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_ALLOWED_PACKAGES, value.toSet()) }

    // ---- Parent overrides ----

    val passUntilMillis: Long get() = prefs.getLong(KEY_PASS_UNTIL, 0)
    val manualLockUntilMillis: Long get() = prefs.getLong(KEY_MANUAL_LOCK_UNTIL, 0)

    /** Extra time: the phone is free for [durationMillis], whatever the schedules say. */
    fun grantPass(durationMillis: Long, nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit { putLong(KEY_PASS_UNTIL, nowMillis + durationMillis) }
    }

    fun endPass() {
        prefs.edit { putLong(KEY_PASS_UNTIL, 0) }
    }

    /** "Lock now". Use [LockPolicy.UNTIL_PARENT_UNLOCKS] for no end time. */
    fun lockNow(untilMillis: Long) {
        prefs.edit {
            putLong(KEY_MANUAL_LOCK_UNTIL, untilMillis)
            putLong(KEY_PASS_UNTIL, 0)
        }
    }

    /** Ends a manual lock and the schedule block running now (the next block still locks). */
    fun endCurrentLock(nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        val scheduleEnd = LockPolicy.scheduleLockEndMillis(schedules, nowMillis, zone)
        prefs.edit {
            putLong(KEY_MANUAL_LOCK_UNTIL, 0)
            putLong(KEY_PASS_UNTIL, scheduleEnd ?: 0)
        }
    }

    // ---- Tamper lock: set when someone tries to switch the protection off ----

    val tamperLocked: Boolean get() = prefs.getBoolean(KEY_TAMPER_LOCKED, false)

    fun triggerTamperLock(nowMillis: Long = System.currentTimeMillis()) {
        if (nowMillis < prefs.getLong(KEY_TAMPER_GRACE_UNTIL, 0)) return
        if (!tamperLocked) prefs.edit { putBoolean(KEY_TAMPER_LOCKED, true) }
    }

    /** Clears the tamper lock and lets the parent change settings for a few minutes. */
    fun clearTamperLock(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit {
            putBoolean(KEY_TAMPER_LOCKED, false)
            putLong(KEY_TAMPER_GRACE_UNTIL, nowMillis + TAMPER_GRACE_MILLIS)
        }
    }

    /** The parent opened a system settings page from the app; don't treat that as tampering. */
    fun startTamperGrace(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit { putLong(KEY_TAMPER_GRACE_UNTIL, nowMillis + TAMPER_GRACE_MILLIS) }
    }

    // ---- Options ----

    /** Minutes before a scheduled lock to show a warning; 0 turns warnings off. */
    var warnMinutes: Int
        get() = prefs.getInt(KEY_WARN_MINUTES, 5)
        set(value) = prefs.edit { putInt(KEY_WARN_MINUTES, value) }

    /** Lock the phone while automatic date & time is off, so the clock cannot be turned back. */
    var requireAutoTime: Boolean
        get() = prefs.getBoolean(KEY_REQUIRE_AUTO_TIME, true)
        set(value) = prefs.edit { putBoolean(KEY_REQUIRE_AUTO_TIME, value) }

    /** App language before Android 13 (see [AppLanguage]); null follows the phone. */
    var language: String?
        get() = prefs.getString(KEY_LANGUAGE, null)
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value) }

    fun lockInputs(nowMillis: Long, zone: ZoneId, autoTimeEnabled: Boolean) = LockInputs(
        nowMillis = nowMillis,
        zone = zone,
        schedules = schedules,
        passUntilMillis = passUntilMillis,
        manualLockUntilMillis = manualLockUntilMillis,
        tamperLocked = tamperLocked,
        requireAutoTime = requireAutoTime,
        autoTimeEnabled = autoTimeEnabled,
    )

    companion object {
        private const val FILE_NAME = "family_lock"
        private const val KEY_PIN = "pin"
        private const val KEY_RECOVERY = "recovery"
        private const val KEY_MUST_CHANGE_PIN = "must_change_pin"
        private const val KEY_FAILURES = "pin_failures"
        private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"
        private const val KEY_SCHEDULES = "schedules"
        private const val KEY_ALLOWED_PACKAGES = "allowed_packages"
        private const val KEY_PASS_UNTIL = "pass_until"
        private const val KEY_MANUAL_LOCK_UNTIL = "manual_lock_until"
        private const val KEY_TAMPER_LOCKED = "tamper_locked"
        private const val KEY_TAMPER_GRACE_UNTIL = "tamper_grace_until"
        private const val KEY_WARN_MINUTES = "warn_minutes"
        private const val KEY_REQUIRE_AUTO_TIME = "require_auto_time"
        private const val KEY_LANGUAGE = "language"

        const val TAMPER_GRACE_MILLIS = 5 * 60_000L

        @Volatile
        private var instance: AppPrefs? = null

        fun get(context: Context): AppPrefs =
            instance ?: synchronized(this) {
                instance ?: AppPrefs(context.applicationContext).also { instance = it }
            }
    }
}
