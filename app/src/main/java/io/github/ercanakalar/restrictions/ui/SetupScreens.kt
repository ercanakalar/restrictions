package io.github.ercanakalar.restrictions.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ercanakalar.restrictions.R
import io.github.ercanakalar.restrictions.core.Secrets
import io.github.ercanakalar.restrictions.data.AppPrefs

private enum class SetupStep { WELCOME, PIN, CONFIRM, RECOVERY }

/** First start: explain the app, create the parent PIN and show the recovery code. */
@Composable
fun SetupFlow(prefs: AppPrefs, onDone: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(SetupStep.WELCOME) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val recoveryCode = remember { Secrets.newRecoveryCode() }

    if (step != SetupStep.WELCOME) {
        BackHandler {
            error = null
            step = SetupStep.entries[step.ordinal - 1]
        }
    }

    CenteredPage {
        when (step) {
            SetupStep.WELCOME -> {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.welcome_text),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp),
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = { step = SetupStep.PIN }) { Text(stringResource(R.string.welcome_start)) }
            }
            SetupStep.PIN -> PinPad(
                title = stringResource(R.string.setup_pin_title),
                subtitle = stringResource(R.string.setup_pin_subtitle),
                maxLength = Secrets.PIN_MAX_LENGTH,
                message = error,
                onSubmit = {
                    if (Secrets.isValidPin(it)) {
                        pin = it
                        error = null
                        step = SetupStep.CONFIRM
                    } else {
                        error = context.getString(R.string.setup_pin_invalid)
                    }
                },
            )
            SetupStep.CONFIRM -> PinPad(
                title = stringResource(R.string.setup_confirm_title),
                maxLength = Secrets.PIN_MAX_LENGTH,
                message = error,
                onSubmit = {
                    if (it == pin) {
                        error = null
                        step = SetupStep.RECOVERY
                    } else {
                        error = context.getString(R.string.setup_pin_mismatch)
                        pin = ""
                        step = SetupStep.PIN
                    }
                },
            )
            SetupStep.RECOVERY -> RecoveryCodeContent(
                code = recoveryCode,
                buttonLabel = stringResource(R.string.setup_finish),
                onConfirmed = {
                    prefs.setRecoveryCode(recoveryCode)
                    prefs.setPin(pin)
                    onDone()
                },
            )
        }
    }
}

@Composable
fun RecoveryCodeContent(code: String, buttonLabel: String, onConfirmed: () -> Unit) {
    var wroteItDown by remember { mutableStateOf(false) }
    Text(
        stringResource(R.string.recovery_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.recovery_text),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 420.dp),
    )
    Spacer(Modifier.height(24.dp))
    RecoveryCodeBox(code)
    Spacer(Modifier.height(24.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.toggleable(value = wroteItDown, role = Role.Checkbox, onValueChange = { wroteItDown = it }),
    ) {
        Checkbox(checked = wroteItDown, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.recovery_confirm))
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onConfirmed, enabled = wroteItDown) { Text(buttonLabel) }
}

@Composable
fun RecoveryCodeBox(code: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            Secrets.formatRecoveryCode(code),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

/** Choose a new PIN. [forced] after the recovery code was used. */
@Composable
fun ChangePinScreen(prefs: AppPrefs, forced: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val cancel: (() -> Unit)? = if (forced) null else onDone

    CenteredPage {
        val chosen = first
        if (chosen == null) {
            PinPad(
                title = stringResource(if (forced) R.string.change_pin_forced_title else R.string.change_pin_title),
                subtitle = stringResource(if (forced) R.string.change_pin_forced_text else R.string.setup_pin_subtitle),
                maxLength = Secrets.PIN_MAX_LENGTH,
                message = error,
                onCancel = cancel,
                onSubmit = {
                    if (Secrets.isValidPin(it)) {
                        first = it
                        error = null
                    } else {
                        error = context.getString(R.string.setup_pin_invalid)
                    }
                },
            )
        } else {
            PinPad(
                title = stringResource(R.string.setup_confirm_title),
                maxLength = Secrets.PIN_MAX_LENGTH,
                message = error,
                onCancel = cancel,
                onSubmit = {
                    if (it == chosen) {
                        prefs.setPin(it)
                        Toast.makeText(context, R.string.change_pin_done, Toast.LENGTH_SHORT).show()
                        onDone()
                    } else {
                        error = context.getString(R.string.setup_pin_mismatch)
                        first = null
                    }
                },
            )
        }
    }
}
