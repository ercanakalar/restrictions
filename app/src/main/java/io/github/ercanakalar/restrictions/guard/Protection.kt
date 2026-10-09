package io.github.ercanakalar.restrictions.guard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import io.github.ercanakalar.restrictions.R

/**
 * Uninstall protection (device admin) and, when the app was made device owner with adb,
 * the extra restrictions that close the remaining loopholes.
 */
object Protection {

    private const val TAG = "Protection"

    private fun ownerRestrictions(): List<String> = buildList {
        add(UserManager.DISALLOW_SAFE_BOOT)
        add(UserManager.DISALLOW_FACTORY_RESET)
        add(UserManager.DISALLOW_ADD_USER)
        // Stops adb, which could otherwise switch the lock service off from a computer.
        add(UserManager.DISALLOW_DEBUGGING_FEATURES)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(UserManager.DISALLOW_USER_SWITCH)
    }

    fun admin(context: Context) = ComponentName(context, AdminReceiver::class.java)

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    fun isAdminActive(context: Context): Boolean = dpm(context).isAdminActive(admin(context))

    fun isDeviceOwner(context: Context): Boolean = dpm(context).isDeviceOwnerApp(context.packageName)

    fun addAdminIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.admin_explanation))

    /** Safe to call often; does nothing unless the app is device owner. */
    fun applyDeviceOwnerPolicies(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = dpm(context)
        val admin = admin(context)
        try {
            ownerRestrictions().forEach { dpm.addUserRestriction(admin, it) }
            dpm.setUninstallBlocked(admin, context.packageName, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dpm.setAutoTimeEnabled(admin, true)
                dpm.setAutoTimeZoneEnabled(admin, true)
            } else {
                @Suppress("DEPRECATION")
                dpm.setAutoTimeRequired(admin, true)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not apply device owner policies", e)
        }
    }

    /** Removes every protection so the app can be uninstalled. Only reachable behind the PIN. */
    fun removeAll(context: Context) {
        val dpm = dpm(context)
        val admin = admin(context)
        try {
            if (isDeviceOwner(context)) {
                ownerRestrictions().forEach { dpm.clearUserRestriction(admin, it) }
                dpm.setUninstallBlocked(admin, context.packageName, false)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    @Suppress("DEPRECATION")
                    dpm.setAutoTimeRequired(admin, false)
                }
                @Suppress("DEPRECATION")
                dpm.clearDeviceOwnerApp(context.packageName)
            }
            if (dpm.isAdminActive(admin)) dpm.removeActiveAdmin(admin)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not remove protection", e)
        }
    }
}
