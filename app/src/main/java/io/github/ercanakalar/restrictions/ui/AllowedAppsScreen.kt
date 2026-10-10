package io.github.ercanakalar.restrictions.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ercanakalar.restrictions.R
import io.github.ercanakalar.restrictions.data.AppPrefs
import io.github.ercanakalar.restrictions.guard.DeviceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Apps the child may still use during a lock, e.g. messages or a school app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowedAppsScreen(prefs: AppPrefs, onDone: () -> Unit) {
    val context = LocalContext.current
    val revision by prefs.revision.collectAsState()
    val allowed = remember(revision) { prefs.allowedPackages }
    val apps by produceState<List<AppEntry>?>(initialValue = null) {
        val hidden = DeviceState.callPackages(context) + context.packageName
        value = withContext(Dispatchers.Default) { loadLaunchableApps(context, hidden) }
    }

    BackHandler(onBack = onDone)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.allowed_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.allowed_text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.allowed_phone)) },
                    supportingContent = { Text(stringResource(R.string.allowed_phone_always)) },
                    leadingContent = { Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(40.dp)) },
                    trailingContent = { Checkbox(checked = true, onCheckedChange = null, enabled = false) },
                )
            }
            val list = apps
            if (list == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(list, key = { it.packageName }) { app ->
                    val checked = app.packageName in allowed
                    ListItem(
                        headlineContent = { Text(app.label) },
                        leadingContent = {
                            val icon = app.icon
                            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        },
                        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        modifier = Modifier.toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { allow ->
                                prefs.allowedPackages = if (allow) allowed + app.packageName else allowed - app.packageName
                            },
                        ),
                    )
                }
            }
        }
    }
}
