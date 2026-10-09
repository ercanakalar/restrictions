package io.github.ercanakalar.restrictions.guard

import android.os.SystemClock

/**
 * Lets a parent who already typed the PIN come back to the parent screens without typing it
 * again, e.g. after visiting a system settings page we opened for them.
 */
object ParentSession {
    private const val WINDOW_MILLIS = 3 * 60_000L

    @Volatile
    private var validUntil = 0L

    fun grant() {
        validUntil = SystemClock.elapsedRealtime() + WINDOW_MILLIS
    }

    /** The parent is busy with a screen opened from the app, so the lock stays out of the way. */
    fun isActive(): Boolean = SystemClock.elapsedRealtime() < validUntil

    /** True once if [grant] was called recently. */
    fun consume(): Boolean {
        val valid = SystemClock.elapsedRealtime() < validUntil
        validUntil = 0L
        return valid
    }
}
