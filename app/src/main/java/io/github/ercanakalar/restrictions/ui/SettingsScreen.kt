package io.github.ercanakalar.restrictions.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ercanakalar.restrictions.BuildConfig
import io.github.ercanakalar.restrictions.R
import io.github.ercanakalar.restrictions.core.Secrets
import io.github.ercanakalar.restrictions.data.AppLanguage
import io.github.ercanakalar.restrictions.data.AppPrefs
import io.github.ercanakalar.restrictions.guard.ParentSession
import io.github.ercanakalar.restrictions.guard.Protection

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    prefs: AppPrefs,
    openExternal: (Intent) -> Unit,
    onChangePin: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val revision by prefs.revision.collectAsState()
    val warnMinutes = remember(revision) { prefs.warnMinutes }
    val requireAutoTime = remember(revision) { prefs.requireAutoTime }
    val activity = LocalActivity.current
    val selectedLanguage = remember(revision) { AppLanguage.selected(context) }
    var newRecoveryCode by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    var removed by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_language))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val choices = listOf(
                    null to stringResource(R.string.settings_language_system),
                    "en" to stringResource(R.string.language_en),
                    "tr" to stringResource(R.string.language_tr),
                )
                for ((tag, label) in choices) {
                    FilterChip(
                        selected = selectedLanguage == tag,
                        onClick = {
                            if (selectedLanguage != tag) {
                                // The screen is rebuilt in the new language; don't ask for the PIN again.
                                ParentSession.grant()
                                AppLanguage.select(context, tag)
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) activity?.recreate()
                            }
                        },
                        label = { Text(label) },
                    )
                }
            }
            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_security))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_change_pin)) },
                modifier = Modifier.clickable(onClick = onChangePin),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_new_recovery)) },
                supportingContent = { Text(stringResource(R.string.settings_new_recovery_text)) },
                modifier = Modifier.clickable { newRecoveryCode = Secrets.newRecoveryCode() },
            )
            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_warning))
            Text(
                stringResource(R.string.settings_warning_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                for (minutes in listOf(0, 5, 10, 15)) {
                    FilterChip(
                        selected = warnMinutes == minutes,
                        onClick = { prefs.warnMinutes = minutes },
                        label = {
                            Text(
                                if (minutes == 0) {
                                    stringResource(R.string.settings_warning_off)
                                } else {
                                    stringResource(R.string.settings_warning_minutes, minutes)
                                },
                            )
                        },
                    )
                }
            }
            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_auto_time)) },
                supportingContent = { Text(stringResource(R.string.settings_auto_time_text)) },
                trailingContent = { Switch(checked = requireAutoTime, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = requireAutoTime,
                    role = Role.Switch,
                    onValueChange = { prefs.requireAutoTime = it },
                ),
            )
            HorizontalDivider()

            SectionTitle(stringResource(R.string.settings_remove_section))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!removed) {
                    Text(stringResource(R.string.settings_remove_text), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { confirmRemove = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.settings_remove))
                    }
                } else {
                    Text(stringResource(R.string.settings_removed_text), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = {
                        openExternal(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", context.packageName, null)))
                    }) {
                        Text(stringResource(R.string.settings_uninstall))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    newRecoveryCode?.let { code ->
        AlertDialog(
            onDismissRequest = { newRecoveryCode = null },
            title = { Text(stringResource(R.string.recovery_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.recovery_text))
                    RecoveryCodeBox(code)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.setRecoveryCode(code)
                    newRecoveryCode = null
                }) { Text(stringResource(R.string.recovery_save)) }
            },
            dismissButton = {
                TextButton(onClick = { newRecoveryCode = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.settings_remove_confirm_title)) },
            text = { Text(stringResource(R.string.settings_remove_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    Protection.removeAll(context)
                    removed = true
                    confirmRemove = false
                }) { Text(stringResource(R.string.settings_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
