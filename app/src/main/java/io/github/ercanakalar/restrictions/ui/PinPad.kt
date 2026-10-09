package io.github.ercanakalar.restrictions.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ercanakalar.restrictions.R
import io.github.ercanakalar.restrictions.core.Secrets
import io.github.ercanakalar.restrictions.data.PinResult

/**
 * A number pad drawn with buttons, so it also works on the lock window where the system
 * keyboard cannot appear.
 */
@Composable
fun PinPad(
    title: String,
    maxLength: Int,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    message: String? = null,
    enabled: Boolean = true,
    onCancel: (() -> Unit)? = null,
) {
    var input by remember { mutableStateOf("") }

    Column(
        modifier = modifier.widthIn(max = 360.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.height(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(input.length) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = message.orEmpty(),
            minLines = 2,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(DEL, "0", OK))
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                for (key in row) {
                    FilledTonalButton(
                        onClick = {
                            when (key) {
                                DEL -> input = input.dropLast(1)
                                OK -> if (input.isNotEmpty()) {
                                    val value = input
                                    input = ""
                                    onSubmit(value)
                                }
                                else -> if (input.length < maxLength) input += key
                            }
                        },
                        enabled = enabled,
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(72.dp),
                    ) {
                        when (key) {
                            DEL -> Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.pin_delete))
                            OK -> Icon(Icons.Filled.Check, stringResource(R.string.ok))
                            else -> Text(key, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (onCancel != null) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

private const val DEL = "del"
private const val OK = "ok"

/** Asks for the parent PIN (or recovery code) and handles wrong tries and the wait after them. */
@Composable
fun ParentPinPrompt(
    title: String,
    check: (String) -> PinResult,
    lockoutUntil: () -> Long,
    onSuccess: (PinResult.Ok) -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var lockedUntil by remember { mutableLongStateOf(lockoutUntil()) }
    val now by rememberNow()
    val lockedOut = lockedUntil > now

    PinPad(
        title = title,
        subtitle = stringResource(R.string.pin_recovery_hint),
        maxLength = Secrets.RECOVERY_CODE_LENGTH,
        message = if (lockedOut) stringResource(R.string.pin_locked_out, formatWait(context, lockedUntil - now)) else error,
        enabled = !lockedOut,
        onCancel = onCancel,
        modifier = modifier,
        onSubmit = { input ->
            when (val result = check(input)) {
                is PinResult.Ok -> {
                    error = null
                    onSuccess(result)
                }
                is PinResult.Wrong -> error = context.resources.getQuantityString(
                    R.plurals.pin_wrong,
                    result.attemptsLeft,
                    result.attemptsLeft,
                )
                is PinResult.LockedOut -> {
                    error = null
                    lockedUntil = result.untilMillis
                }
            }
        },
    )
}
