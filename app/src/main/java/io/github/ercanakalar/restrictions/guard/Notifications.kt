package io.github.ercanakalar.restrictions.guard

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.ercanakalar.restrictions.R
import io.github.ercanakalar.restrictions.ui.MainActivity
import io.github.ercanakalar.restrictions.ui.formatClock

object Notifications {
    private const val CHANNEL_WARNINGS = "lock_warnings"
    private const val ID_WARNING = 1

    fun canPost(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    @SuppressLint("MissingPermission") // Checked by canPost().
    fun showLockWarning(context: Context, scheduleName: String, startMillis: Long) {
        if (!canPost(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_WARNINGS)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(context.getString(R.string.warning_title, formatClock(context, startMillis)))
            .setContentText(context.getString(R.string.warning_text, scheduleName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(startMillis - System.currentTimeMillis() + 60_000)
            .build()
        NotificationManagerCompat.from(context).notify(ID_WARNING, notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_WARNINGS) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WARNINGS,
                context.getString(R.string.warning_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }
}
