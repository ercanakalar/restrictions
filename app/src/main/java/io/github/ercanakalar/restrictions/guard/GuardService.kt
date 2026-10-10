package io.github.ercanakalar.restrictions.guard

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.github.ercanakalar.restrictions.R
import io.github.ercanakalar.restrictions.core.LockDecision
import io.github.ercanakalar.restrictions.core.LockPolicy
import io.github.ercanakalar.restrictions.core.LockReason
import io.github.ercanakalar.restrictions.data.AppPrefs
import io.github.ercanakalar.restrictions.data.PinResult
import io.github.ercanakalar.restrictions.ui.LockScreenActions
import io.github.ercanakalar.restrictions.ui.MainActivity
import java.time.ZoneId

/**
 * The heart of the app. Runs as an accessibility service so that it
 *  - keeps running in the background and starts again after a reboot,
 *  - knows which app is in front (to let the phone app through during a lock),
 *  - can draw a full-screen lock above every other app, the status bar and the navigation bar.
 */
class GuardService : AccessibilityService(), LockScreenActions {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: AppPrefs
    private lateinit var overlay: LockOverlay

    private var connected = false
    private var foregroundPackage: String? = null
    private var transientPackages: Set<String> = emptySet()
    private var lastWarnedStart = 0L
    private var stopCallModeWatcher: (() -> Unit)? = null

    private val refreshRunnable = Runnable { refresh() }
    private val prefsListener: () -> Unit = { requestRefresh() }

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, TICK_MILLIS)
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            requestRefresh()
        }
    }

    private val settingsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            requestRefresh()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = AppPrefs.get(this)
        overlay = LockOverlay(this, this)
        transientPackages = findTransientPackages()
        prefs.addListener(prefsListener)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, systemReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        for (key in listOf(Settings.Global.AUTO_TIME, Settings.Global.AUTO_TIME_ZONE)) {
            contentResolver.registerContentObserver(Settings.Global.getUriFor(key), false, settingsObserver)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val watcher = CallModeWatcher(this) { requestRefresh() }
            stopCallModeWatcher = watcher::stop
        }

        Protection.applyDeviceOwnerPolicies(this)
        connected = true
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!connected || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Our own lock window also sends events; only the parent screens count as "our app".
        if (pkg == packageName && event.className?.toString() != MainActivity::class.java.name) return
        // The notification shade and keyboards appear on top of apps without replacing them.
        if (pkg in transientPackages) return

        foregroundPackage = pkg
        if (isProtectionSettingsPage(pkg, event)) prefs.triggerTamperLock()
        refresh()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (connected) {
            connected = false
            handler.removeCallbacksAndMessages(null)
            overlay.hide()
            prefs.removeListener(prefsListener)
            unregisterReceiver(systemReceiver)
            contentResolver.unregisterContentObserver(settingsObserver)
            stopCallModeWatcher?.invoke()
            stopCallModeWatcher = null
        }
        super.onDestroy()
    }

    private fun requestRefresh() {
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }

    private fun refresh() {
        if (!connected) return
        val now = System.currentTimeMillis()
        val decision = DeviceState.currentDecision(this, now)
        if (decision is LockDecision.Unlocked) maybeWarn(now)
        val locked = decision as? LockDecision.Locked
        if (locked != null && !isExempt(locked)) overlay.show(locked) else overlay.hide()
    }

    /** Apps that stay usable during a lock: calls always, plus what the parent allowed. */
    private fun isExempt(locked: LockDecision.Locked): Boolean {
        if (DeviceState.isInCall(this)) return true
        if (locked.reason == LockReason.TAMPER) return false
        if (ParentSession.isActive()) return true
        val app = foregroundPackage ?: return false
        return app == packageName || app in DeviceState.callPackages(this) || app in prefs.allowedPackages
    }

    private fun maybeWarn(now: Long) {
        val minutes = prefs.warnMinutes
        if (minutes <= 0) return
        val zone = ZoneId.systemDefault()
        val next = LockPolicy.nextBlock(prefs.schedules, now, zone, minutes.toLong()) ?: return
        val start = LockPolicy.toMillis(next.start, zone)
        if (start == lastWarnedStart) return
        lastWarnedStart = start
        Notifications.showLockWarning(this, next.schedule.name, start)
    }

    /**
     * Best effort: the Settings page that switches this service off has the app's name as title.
     * Device admin removal is caught separately in [AdminReceiver.onDisableRequested].
     */
    private fun isProtectionSettingsPage(pkg: String, event: AccessibilityEvent): Boolean {
        if (!pkg.contains("settings", ignoreCase = true) && !pkg.contains("accessibility", ignoreCase = true)) {
            return false
        }
        val label = getString(R.string.app_name)
        return event.text.any { it?.contains(label, ignoreCase = true) == true }
    }

    private fun findTransientPackages(): Set<String> {
        val keyboards = getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.map { it.packageName }
            .orEmpty()
        return setOf("com.android.systemui") + keyboards
    }

    // ---- Buttons on the lock screen ----

    override fun checkPin(input: String): PinResult = prefs.checkSecret(input)

    override fun lockoutUntilMillis(): Long = prefs.lockoutUntilMillis

    override fun giveExtraTime(minutes: Int) = prefs.grantPass(minutes * 60_000L)

    override fun endLock() = prefs.endCurrentLock()

    override fun clearTamperLock() = prefs.clearTamperLock()

    override fun openParentSettings() {
        ParentSession.grant()
        launch(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    override fun openDateSettings() {
        prefs.grantPass(DATE_FIX_MILLIS)
        launch(Intent(Settings.ACTION_DATE_SETTINGS))
    }

    override fun openPhone() {
        launch(Intent(Intent.ACTION_DIAL))
    }

    override fun allowedPackages(): Set<String> = prefs.allowedPackages

    override fun openApp(packageName: String) {
        if (packageName !in prefs.allowedPackages) return
        packageManager.getLaunchIntentForPackage(packageName)?.let(::launch)
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app for $intent", e)
        }
    }

    /** Re-checks the lock as soon as a call starts ringing or ends (Android 12+). */
    @RequiresApi(Build.VERSION_CODES.S)
    private class CallModeWatcher(context: Context, onChange: () -> Unit) {
        private val audio = context.getSystemService(AudioManager::class.java)
        private val listener = AudioManager.OnModeChangedListener { onChange() }

        init {
            audio.addOnModeChangedListener(context.mainExecutor, listener)
        }

        fun stop() {
            audio.removeOnModeChangedListener(listener)
        }
    }

    private companion object {
        const val TAG = "GuardService"
        const val TICK_MILLIS = 5_000L
        const val DATE_FIX_MILLIS = 5 * 60_000L
    }
}
