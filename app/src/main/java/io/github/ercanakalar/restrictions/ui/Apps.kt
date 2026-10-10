package io.github.ercanakalar.restrictions.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

class AppEntry(val packageName: String, val label: String, val icon: ImageBitmap?)

/** Every app with a home-screen icon, sorted by name, leaving out [hidden]. */
fun loadLaunchableApps(context: Context, hidden: Set<String>): List<AppEntry> =
    queryLauncherActivities(context.packageManager)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName !in hidden }
        .map { toEntry(context, it) }
        .sortedBy { it.label.lowercase() }

/** The given apps that are still installed and can be opened, sorted by name. */
fun loadApps(context: Context, packages: Collection<String>): List<AppEntry> {
    val pm = context.packageManager
    return packages
        .filter { pm.getLaunchIntentForPackage(it) != null }
        .mapNotNull { pkg ->
            try {
                toEntry(context, applicationInfo(pm, pkg))
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
        .sortedBy { it.label.lowercase() }
}

private fun toEntry(context: Context, info: ApplicationInfo): AppEntry {
    val pm = context.packageManager
    val iconSize = (48 * context.resources.displayMetrics.density).toInt()
    val icon = try {
        pm.getApplicationIcon(info).toBitmap(iconSize, iconSize).asImageBitmap()
    } catch (e: RuntimeException) {
        null
    }
    return AppEntry(info.packageName, pm.getApplicationLabel(info).toString(), icon)
}

private fun queryLauncherActivities(pm: PackageManager): List<ResolveInfo> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
    }
}

private fun applicationInfo(pm: PackageManager, packageName: String): ApplicationInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getApplicationInfo(packageName, 0)
    }
