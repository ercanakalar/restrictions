package io.github.ercanakalar.restrictions.guard

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import io.github.ercanakalar.restrictions.core.LockDecision
import io.github.ercanakalar.restrictions.core.LockPolicy
import io.github.ercanakalar.restrictions.data.AppPrefs
import java.time.ZoneId

/** Small read-only checks of the phone's state. */
object DeviceState {

    /** Packages that must keep working during a lock so calls can be made and answered. */
    private val CALL_PACKAGES = setOf(
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.incallui",
        "com.samsung.android.incallui",
    )

    fun currentDecision(context: Context, nowMillis: Long = System.currentTimeMillis()): LockDecision {
        val inputs = AppPrefs.get(context).lockInputs(nowMillis, ZoneId.systemDefault(), isAutoTimeEnabled(context))
        return LockPolicy.evaluate(inputs)
    }

    /** Automatic date & time and automatic time zone are both on. */
    fun isAutoTimeEnabled(context: Context): Boolean {
        val resolver = context.contentResolver
        return Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME, 1) == 1 &&
            Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME_ZONE, 1) == 1
    }

    fun isLockServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = ComponentName(context, GuardService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun callPackages(context: Context): Set<String> {
        val dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        return if (dialer != null) CALL_PACKAGES + dialer else CALL_PACKAGES
    }

    /** A phone call is ringing or running. */
    fun isInCall(context: Context): Boolean {
        val mode = context.getSystemService(AudioManager::class.java)?.mode ?: return false
        if (mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mode == AudioManager.MODE_CALL_SCREENING
    }
}
