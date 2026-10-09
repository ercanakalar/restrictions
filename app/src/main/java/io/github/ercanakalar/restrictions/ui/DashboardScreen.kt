package io.github.ercanakalar.restrictions.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.ercanakalar.restrictions.R
import io.github.ercanakalar.restrictions.core.LockDecision
import io.github.ercanakalar.restrictions.core.LockPolicy
import io.github.ercanakalar.restrictions.core.LockReason
import io.github.ercanakalar.restrictions.core.Occurrence
import io.github.ercanakalar.restrictions.core.Schedule
import io.github.ercanakalar.restrictions.data.AppPrefs
import io.github.ercanakalar.restrictions.guard.DeviceState
import io.github.ercanakalar.restrictions.guard.Notifications
import io.github.ercanakalar.restrictions.guard.ParentSession
import io.github.ercanakalar.restrictions.guard.Protection
import java.time.ZoneId

private data class ProtectionStatus(
    val lockService: Boolean,
    val admin: Boolean,
    val deviceOwner: Boolean,
    val notifications: Boolean,
    val autoTime: Boolean,
) {
    companion object {
        fun read(context: Context) = ProtectionStatus(
            lockService = DeviceState.isLockServiceEnabled(context),
            admin = Protection.isAdminActive(context),
            deviceOwner = Protection.isDeviceOwner(context),
            notifications = Notifications.canPost(context),
            autoTime = DeviceState.isAutoTimeEnabled(context),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    prefs: AppPrefs,
    openExternal: (Intent) -> Unit,
    onLock: () -> Unit,
    onEditSchedule: (String?) -> Unit,
    onAllowedApps: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val revision by prefs.revision.collectAsState()
    val now by rememberNow(5_000)
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }

    val schedules = remember(revision) { prefs.schedules }
    val allowedCount = remember(revision) { prefs.allowedPackages.size }
    val decision = remember(revision, now, resumes) { DeviceState.currentDecision(context, now) }
    val nextBlock = remember(schedules, now) {
        LockPolicy.nextBlock(schedules, now, ZoneId.systemDefault(), withinMinutes = 7L * Schedule.MINUTES_PER_DAY)
    }
    val status = remember(revision, resumes) { ProtectionStatus.read(context) }
    var showLockDialog by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            openExternal(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
        resumes++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                    TextButton(onClick = onLock) { Text(stringResource(R.string.dashboard_done)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StatusCard(
                    decision = decision,
                    nextBlock = nextBlock,
                    serviceOn = status.lockService,
                    onLockNow = { showLockDialog = true },
                    onEndPass = prefs::endPass,
                    onEndLock = { prefs.endCurrentLock() },
                )
            }
            item {
                ProtectionCard(
                    status = status,
                    openExternal = openExternal,
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ParentSession.grant()
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
            item {
                Text(
                    stringResource(R.string.schedules_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (schedules.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.schedules_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(schedules, key = { it.id }) { schedule ->
                ScheduleRow(
                    schedule = schedule,
                    onClick = { onEditSchedule(schedule.id) },
                    onToggle = { prefs.saveSchedule(schedule.copy(enabled = it)) },
                )
            }
            item {
                OutlinedButton(onClick = { onEditSchedule(null) }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.schedules_add))
                }
            }
            item {
                Card(onClick = onAllowedApps, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.allowed_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (allowedCount == 0) {
                                    stringResource(R.string.allowed_summary_none)
                                } else {
                                    context.resources.getQuantityString(R.plurals.allowed_summary, allowedCount, allowedCount)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }

    if (showLockDialog) {
        LockNowDialog(
            onDismiss = { showLockDialog = false },
            onChoose = { until ->
                prefs.lockNow(until)
                showLockDialog = false
            },
        )
    }
}

@Composable
private fun StatusCard(
    decision: LockDecision,
    nextBlock: Occurrence?,
    serviceOn: Boolean,
    onLockNow: () -> Unit,
    onEndPass: () -> Unit,
    onEndLock: () -> Unit,
) {
    val context = LocalContext.current
    val locked = decision is LockDecision.Locked
    val title: String
    val detail: String
    when (decision) {
        LockDecision.Unlocked -> {
            title = stringResource(R.string.status_free)
            detail = if (nextBlock != null) {
                val start = LockPolicy.toMillis(nextBlock.start, ZoneId.systemDefault())
                stringResource(R.string.status_next, formatClock(context, start), nextBlock.schedule.name)
            } else {
                stringResource(R.string.status_no_upcoming)
            }
        }
        is LockDecision.ParentPass -> {
            title = stringResource(R.string.status_pass)
            detail = stringResource(R.string.status_pass_until, formatClock(context, decision.untilMillis))
        }
        is LockDecision.Locked -> {
            val until = decision.untilMillis?.let { formatClock(context, it) }
            when (decision.reason) {
                LockReason.SCHEDULE -> {
                    title = stringResource(R.string.status_locked_schedule, decision.scheduleName.orEmpty())
                    detail = until?.let { stringResource(R.string.status_until, it) }.orEmpty()
                }
                LockReason.MANUAL -> {
                    title = stringResource(R.string.status_locked_manual)
                    detail = until?.let { stringResource(R.string.status_until, it) }
                        ?: stringResource(R.string.status_until_unlocked)
                }
                LockReason.CLOCK -> {
                    title = stringResource(R.string.lock_title_clock)
                    detail = stringResource(R.string.status_clock)
                }
                LockReason.TAMPER -> {
                    title = stringResource(R.string.lock_title_tamper)
                    detail = stringResource(R.string.lock_message_tamper)
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (locked) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
            if (!serviceOn) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.status_service_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (locked) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.status_shows_when_leaving), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    decision is LockDecision.ParentPass -> {
                        Button(onClick = onEndPass) { Text(stringResource(R.string.status_end_pass)) }
                        OutlinedButton(onClick = onLockNow) { Text(stringResource(R.string.status_lock_now)) }
                    }
                    decision is LockDecision.Locked &&
                        (decision.reason == LockReason.SCHEDULE || decision.reason == LockReason.MANUAL) -> {
                        Button(onClick = onEndLock) { Text(stringResource(R.string.options_end_lock)) }
                    }
                    decision is LockDecision.Unlocked -> {
                        Button(onClick = onLockNow) { Text(stringResource(R.string.status_lock_now)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectionCard(
    status: ProtectionStatus,
    openExternal: (Intent) -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.protection_title), style = MaterialTheme.typography.titleMedium)
            ChecklistItem(
                done = status.lockService,
                title = stringResource(R.string.check_service_title),
                text = stringResource(R.string.check_service_text),
                actionLabel = stringResource(R.string.check_turn_on),
                onAction = { openExternal(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                secondaryLabel = stringResource(R.string.check_app_info),
                onSecondary = {
                    openExternal(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
            )
            ChecklistItem(
                done = status.admin,
                title = stringResource(R.string.check_admin_title),
                text = stringResource(R.string.check_admin_text),
                actionLabel = stringResource(R.string.check_turn_on),
                onAction = { openExternal(Protection.addAdminIntent(context)) },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ChecklistItem(
                    done = status.notifications,
                    title = stringResource(R.string.check_notifications_title),
                    text = stringResource(R.string.check_notifications_text),
                    actionLabel = stringResource(R.string.check_allow),
                    onAction = onRequestNotifications,
                )
            }
            ChecklistItem(
                done = status.autoTime,
                title = stringResource(R.string.check_time_title),
                text = stringResource(R.string.check_time_text),
                actionLabel = stringResource(R.string.check_open_settings),
                onAction = { openExternal(Intent(Settings.ACTION_DATE_SETTINGS)) },
            )
            ChecklistItem(
                done = status.deviceOwner,
                optional = true,
                title = stringResource(R.string.check_owner_title),
                text = stringResource(R.string.check_owner_text),
                doneText = stringResource(R.string.check_owner_done),
            )
        }
    }
}

@Composable
private fun ChecklistItem(
    done: Boolean,
    title: String,
    text: String,
    optional: Boolean = false,
    doneText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = when {
                done -> Icons.Filled.CheckCircle
                optional -> Icons.Filled.Info
                else -> Icons.Filled.Warning
            },
            contentDescription = null,
            tint = when {
                done -> MaterialTheme.colorScheme.primary
                optional -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.error
            },
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                if (done) doneText ?: stringResource(R.string.check_done) else text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!done && (onAction != null || onSecondary != null)) {
                Row {
                    if (actionLabel != null && onAction != null) {
                        TextButton(onClick = onAction) { Text(actionLabel) }
                    }
                    if (secondaryLabel != null && onSecondary != null) {
                        TextButton(onClick = onSecondary) { Text(secondaryLabel) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleRow(schedule: Schedule, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(schedule.name, style = MaterialTheme.typography.titleMedium)
                Text(formatScheduleTimes(context, schedule), style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatDays(context, schedule.days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = schedule.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun LockNowDialog(onDismiss: () -> Unit, onChoose: (Long) -> Unit) {
    val choices = listOf(
        30 to R.string.lock_now_30,
        60 to R.string.lock_now_60,
        120 to R.string.lock_now_120,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_now_title)) },
        text = {
            Column {
                Text(stringResource(R.string.lock_now_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                for ((minutes, label) in choices) {
                    TextButton(onClick = { onChoose(System.currentTimeMillis() + minutes * 60_000L) }) {
                        Text(stringResource(label))
                    }
                }
                TextButton(onClick = { onChoose(LockPolicy.UNTIL_PARENT_UNLOCKS) }) {
                    Text(stringResource(R.string.lock_now_until_unlock))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
