package pl.usypianka.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.usypianka.R

private const val MAX_FIELD_DIGITS = 2

/**
 * Custom time as two numeric fields (D-16). The Material TimePicker is deliberately not used: it has
 * clock semantics (AM/PM), and a duration is not a time of day.
 *
 * [validate] is the single source of truth (PlayerUiState.applyCustomDuration); the dialog only filters
 * input to digits and renders the live result. OK is enabled only for an accepted value.
 */
@Composable
fun TimePickerDialog(
    initial: DurationParts,
    validate: (String, String) -> CustomDurationResult,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var hours by rememberSaveable { mutableStateOf(initial.hours.toString()) }
    var minutes by rememberSaveable { mutableStateOf(initial.minutes.toString()) }
    val result = validate(hours, minutes)
    val accepted = result as? CustomDurationResult.Accepted

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_time_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DigitField(
                        value = hours,
                        onValueChange = { hours = it.toFieldDigits() },
                        label = R.string.custom_time_hours,
                        isError = result.isHoursError(),
                        imeAction = ImeAction.Next,
                        modifier = Modifier.weight(1f),
                    )
                    DigitField(
                        value = minutes,
                        onValueChange = { minutes = it.toFieldDigits() },
                        label = R.string.custom_time_minutes,
                        isError = result.isMinutesError(),
                        imeAction = ImeAction.Done,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Reserve the line so the dialog does not jump between message and preview.
                Text(
                    text = when {
                        accepted != null -> durationText(splitDuration(accepted.state.durationMs), accusative = false)
                        else -> result.errorMessage()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (accepted != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hours, minutes) }, enabled = accepted != null) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun DigitField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    isError: Boolean,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        modifier = modifier,
    )
}

/** Digits only, at most two characters (T-01-13). */
private fun String.toFieldDigits(): String = filter { it in '0'..'9' }.take(MAX_FIELD_DIGITS)

private fun CustomDurationResult.isHoursError(): Boolean =
    this == CustomDurationResult.TooLong || this == CustomDurationResult.InvalidNumber

private fun CustomDurationResult.isMinutesError(): Boolean =
    this == CustomDurationResult.InvalidMinutes || this == CustomDurationResult.TooShort

@Composable
private fun CustomDurationResult.errorMessage(): String = when (this) {
    CustomDurationResult.TooShort -> stringResource(R.string.custom_time_error_too_short)
    CustomDurationResult.TooLong -> stringResource(R.string.custom_time_error_too_long)
    CustomDurationResult.InvalidMinutes -> stringResource(R.string.custom_time_error_minutes)
    CustomDurationResult.InvalidNumber -> stringResource(R.string.custom_time_error_number)
    // Locked cannot be reached from the dialog (the chip is disabled while running); Accepted has no error.
    CustomDurationResult.Locked, is CustomDurationResult.Accepted -> ""
}

/**
 * "2 hours 30 minutes" built only from plural resources, never by gluing a number to a unit (D-18).
 * Hours are omitted when 0, minutes are omitted when 0, unless both are 0.
 * Polish "przez" takes the accusative (1 minutę, 1 godzinę), so callers pick [accusative].
 */
@Composable
fun durationText(parts: DurationParts, accusative: Boolean): String {
    val hoursText = if (parts.hours > 0) {
        pluralStringResource(
            if (accusative) R.plurals.duration_hours_acc else R.plurals.duration_hours,
            parts.hours,
            parts.hours,
        )
    } else {
        null
    }
    val minutesText = if (parts.minutes > 0 || parts.hours == 0) {
        pluralStringResource(
            if (accusative) R.plurals.duration_minutes_acc else R.plurals.duration_minutes,
            parts.minutes,
            parts.minutes,
        )
    } else {
        null
    }
    return when {
        hoursText != null && minutesText != null ->
            stringResource(R.string.duration_hours_minutes, hoursText, minutesText)
        hoursText != null -> hoursText
        else -> minutesText.orEmpty()
    }
}
