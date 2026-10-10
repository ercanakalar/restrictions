package io.github.ercanakalar.restrictions.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import io.github.ercanakalar.restrictions.R
import io.github.ercanakalar.restrictions.core.Schedule
import io.github.ercanakalar.restrictions.data.AppPrefs
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditorScreen(prefs: AppPrefs, scheduleId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val existing = remember(scheduleId) { scheduleId?.let { id -> prefs.schedules.find { it.id == id } } }
    val defaultName = stringResource(R.string.editor_default_name)
    var name by remember { mutableStateOf(existing?.name ?: defaultName) }
    var start by remember { mutableIntStateOf(existing?.startMinute ?: (21 * 60)) }
    var end by remember { mutableIntStateOf(existing?.endMinute ?: (7 * 60)) }
    var days by remember { mutableStateOf(existing?.days ?: Schedule.EVERY_DAY) }
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(onBack = onDone)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (existing == null) R.string.editor_new_title else R.string.editor_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.editor_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeButton(
                    label = stringResource(R.string.editor_from),
                    minuteOfDay = start,
                    onPick = { start = it },
                    modifier = Modifier.weight(1f),
                )
                TimeButton(
                    label = stringResource(R.string.editor_until),
                    minuteOfDay = end,
                    onPick = { end = it },
                    modifier = Modifier.weight(1f),
                )
            }
            val preview = Schedule("preview", name, start, end, days)
            Text(
                when {
                    preview.durationMinutes == Schedule.MINUTES_PER_DAY -> stringResource(R.string.editor_hint_all_day)
                    preview.crossesMidnight -> stringResource(R.string.editor_hint_overnight)
                    else -> stringResource(R.string.editor_hint_same_day)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(stringResource(R.string.editor_days), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (day in orderedWeek(context)) {
                    FilterChip(
                        selected = day in days,
                        onClick = { days = if (day in days) days - day else days + day },
                        label = { Text(dayShortName(context, day)) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { days = Schedule.EVERY_DAY }, label = { Text(stringResource(R.string.days_every_day)) })
                AssistChip(onClick = { days = Schedule.WEEKDAYS }, label = { Text(stringResource(R.string.days_weekdays)) })
                AssistChip(onClick = { days = Schedule.WEEKEND }, label = { Text(stringResource(R.string.days_weekend)) })
            }
            if (days.isEmpty()) {
                Text(
                    stringResource(R.string.editor_pick_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    prefs.saveSchedule(
                        Schedule(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim().ifEmpty { defaultName },
                            startMinute = start,
                            endMinute = end,
                            days = days,
                            enabled = existing?.enabled ?: true,
                        ),
                    )
                    onDone()
                },
                enabled = days.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.editor_save))
            }
            if (existing != null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.editor_delete))
                }
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.editor_delete_title, existing.name)) },
            confirmButton = {
                TextButton(onClick = {
                    prefs.deleteSchedule(existing.id)
                    confirmDelete = false
                    onDone()
                }) { Text(stringResource(R.string.editor_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun TimeButton(label: String, minuteOfDay: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onPick(hour * 60 + minute) },
                minuteOfDay / 60,
                minuteOfDay % 60,
                DateFormat.is24HourFormat(context),
            ).show()
        },
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(formatMinuteOfDay(context, minuteOfDay), style = MaterialTheme.typography.titleLarge)
        }
    }
}

private const val MAX_NAME_LENGTH = 40
