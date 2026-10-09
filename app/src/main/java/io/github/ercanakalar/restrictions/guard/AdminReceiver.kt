package io.github.ercanakalar.restrictions.guard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import io.github.ercanakalar.restrictions.R
import io.github.ercanakalar.restrictions.data.AppPrefs

/**
 * While this device admin is active the app cannot be uninstalled or force-stopped.
 * Trying to switch it off in Settings locks the phone until the parent PIN is entered.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Protection.applyDeviceOwnerPolicies(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        AppPrefs.get(context).triggerTamperLock()
        return context.getString(R.string.admin_disable_warning)
    }
}
