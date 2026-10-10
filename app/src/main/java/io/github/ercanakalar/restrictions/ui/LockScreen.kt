package io.github.ercanakalar.restrictions.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ercanakalar.restrictions.R
import io.github.ercanakalar.restrictions.core.LockDecision
import io.github.ercanakalar.restrictions.core.LockReason
import io.github.ercanakalar.restrictions.data.PinResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the buttons on the lock screen do; implemented by the lock service. */
interface LockScreenActions {
    fun checkPin(input: String): PinResult
    fun lockoutUntilMillis(): Long
    fun giveExtraTime(minutes: Int)
    fun endLock()
    fun clearTamperLock()
    fun openParentSettings()
    fun openDateSettings()
    fun openPhone()

    /** Apps the parent allowed during locks; they are offered on the lock screen. */
    fun allowedPackages(): Set<String>
    fun openApp(packageName: String)
}

private enum class Stage { INFO, PIN, OPTIONS }

@Composable
fun LockScreen(decision: LockDecision.Locked, actions: LockScreenActions) {
    var stage by remember(decision.reason) { mutableStateOf(Stage.INFO) }
    var usedRecoveryCode by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (stage) {
                    Stage.INFO -> LockInfo(
                        decision = decision,
                        allowedPackages = actions::allowedPackages,
                        onOpenApp = actions::openApp,
                        onPhone = actions::openPhone,
                        onParent = { stage = Stage.PIN },
                    )
                    Stage.PIN -> ParentPinPrompt(
                        title = stringResource(R.string.lock_parent_pin),
                        check = actions::checkPin,
                        lockoutUntil = actions::lockoutUntilMillis,
                        onCancel = { stage = Stage.INFO },
                        onSuccess = { result ->
                            if (decision.reason == LockReason.TAMPER) {
                                actions.clearTamperLock()
                                stage = Stage.INFO
                            } else {
                                usedRecoveryCode = result.usedRecoveryCode
                                stage = Stage.OPTIONS
                            }
                        },
                    )
                    Stage.OPTIONS -> ParentOptions(
                        decision = decision,
                        usedRecoveryCode = usedRecoveryCode,
                        actions = actions,
                        onClose = { stage = Stage.INFO },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LockInfo(
    decision: LockDecision.Locked,
    allowedPackages: () -> Set<String>,
    onOpenApp: (String) -> Unit,
    onPhone: () -> Unit,
    onParent: () -> Unit,
) {
    val context = LocalContext.current
    val now by rememberNow()
    // The lock covers the home screen, so allowed apps can only be started from here.
    val allowedApps by produceState(emptyList<AppEntry>()) {
        val packages = allowedPackages()
        if (packages.isNotEmpty()) value = withContext(Dispatchers.Default) { loadApps(context, packages) }
    }
    val until = decision.untilMillis?.let { formatClock(context, it) }
    val title: String
    val message: String
    when (decision.reason) {
        LockReason.SCHEDULE -> {
            title = stringResource(R.string.lock_title_schedule)
            message = if (until != null) {
                stringResource(R.string.lock_message_schedule, decision.scheduleName.orEmpty(), until)
            } else {
                decision.scheduleName.orEmpty()
            }
        }
        LockReason.MANUAL -> {
            title = stringResource(R.string.lock_title_manual)
            message = if (until != null) {
                stringResource(R.string.lock_message_manual_until, until)
            } else {
                stringResource(R.string.lock_message_manual)
            }
        }
        LockReason.CLOCK -> {
            title = stringResource(R.string.lock_title_clock)
            message = stringResource(R.string.lock_message_clock)
        }
        LockReason.TAMPER -> {
            title = stringResource(R.string.lock_title_tamper)
            message = stringResource(R.string.lock_message_tamper)
        }
    }

    Column(
        modifier = Modifier.widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(formatTimeOnly(context, now), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onPhone) {
                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lock_phone))
            }
            Button(onClick = onParent) {
                Text(stringResource(R.string.lock_parent))
            }
        }
        if (decision.reason != LockReason.TAMPER && allowedApps.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.lock_allowed_apps), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (app in allowedApps) {
                    AppTile(app, onClick = { onOpenApp(app.packageName) })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.lock_calls_allowed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppTile(app: AppEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(48.dp))
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ParentOptions(
    decision: LockDecision.Locked,
    usedRecoveryCode: Boolean,
    actions: LockScreenActions,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.options_title), style = MaterialTheme.typography.headlineSmall)
        if (usedRecoveryCode) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.options_recovery_used),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.options_extra_time), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { actions.giveExtraTime(15) }) { Text(stringResource(R.string.extra_15)) }
            OutlinedButton(onClick = { actions.giveExtraTime(30) }) { Text(stringResource(R.string.extra_30)) }
            OutlinedButton(onClick = { actions.giveExtraTime(60) }) { Text(stringResource(R.string.extra_60)) }
        }
        Spacer(Modifier.height(24.dp))
        if (decision.reason == LockReason.SCHEDULE || decision.reason == LockReason.MANUAL) {
            Button(onClick = actions::endLock, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.options_end_lock))
            }
            Spacer(Modifier.height(8.dp))
        }
        if (decision.reason == LockReason.CLOCK) {
            Button(onClick = actions::openDateSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.options_date_settings))
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = actions::openParentSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.options_parent_settings))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
    }
}
