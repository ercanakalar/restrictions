package io.github.ercanakalar.restrictions.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ercanakalar.restrictions.R
import io.github.ercanakalar.restrictions.data.AppPrefs

private sealed interface Screen {
    data object Dashboard : Screen
    data class EditSchedule(val id: String?) : Screen
    data object AllowedApps : Screen
    data object Settings : Screen
    data object ChangePin : Screen
}

@Composable
fun ParentApp(
    unlocked: Boolean,
    onUnlocked: () -> Unit,
    onLock: () -> Unit,
    openExternal: (Intent) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs.get(context) }
    val revision by prefs.revision.collectAsState()
    val pinSet = remember(revision) { prefs.isPinSet }
    val mustChangePin = remember(revision) { prefs.mustChangePin }
    var screen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

    when {
        !pinSet -> SetupFlow(prefs, onDone = onUnlocked)
        !unlocked -> CenteredPage {
            ParentPinPrompt(
                title = stringResource(R.string.gate_title),
                check = { prefs.checkSecret(it) },
                lockoutUntil = { prefs.lockoutUntilMillis },
                onSuccess = { onUnlocked() },
            )
        }
        mustChangePin -> ChangePinScreen(prefs, forced = true, onDone = {})
        else -> when (val current = screen) {
            Screen.Dashboard -> DashboardScreen(
                prefs = prefs,
                openExternal = openExternal,
                onLock = onLock,
                onEditSchedule = { screen = Screen.EditSchedule(it) },
                onAllowedApps = { screen = Screen.AllowedApps },
                onSettings = { screen = Screen.Settings },
            )
            is Screen.EditSchedule -> ScheduleEditorScreen(
                prefs = prefs,
                scheduleId = current.id,
                onDone = { screen = Screen.Dashboard },
            )
            Screen.AllowedApps -> AllowedAppsScreen(prefs, onDone = { screen = Screen.Dashboard })
            Screen.Settings -> SettingsScreen(
                prefs = prefs,
                openExternal = openExternal,
                onChangePin = { screen = Screen.ChangePin },
                onBack = { screen = Screen.Dashboard },
            )
            Screen.ChangePin -> {
                BackHandler { screen = Screen.Settings }
                ChangePinScreen(prefs, forced = false, onDone = { screen = Screen.Settings })
            }
        }
    }
}

/** A full-screen page with its content centred and scrollable. */
@Composable
fun CenteredPage(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize()) {
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
                content = content,
            )
        }
    }
}
