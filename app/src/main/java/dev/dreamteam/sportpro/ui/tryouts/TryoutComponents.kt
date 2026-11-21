package dev.dreamteam.sportpro.ui.tryouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dreamteam.sportpro.domain.tryouts.datePickerMillisToDate
import dev.dreamteam.sportpro.domain.tryouts.epochMillisToDate
import dev.dreamteam.sportpro.domain.tryouts.toDatePickerMillis
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")

internal fun LocalDate.formatted(): String = format(DATE_FORMAT)

internal fun Long.formattedDate(): String = epochMillisToDate(this).formatted()

@Composable
internal fun FieldLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
internal fun FieldError(error: String?) {
    if (error != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = error, color = Color.Red, fontSize = 13.sp)
    }
}

@Composable
internal fun TryoutTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    FieldLabel(label)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        isError = error != null,
        singleLine = singleLine,
        minLines = minLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            errorBorderColor = Color.Red,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            errorContainerColor = MaterialTheme.colorScheme.surface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            errorTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
    FieldError(error)
}

/** Dropdown in the same style as CreatePostScreen; null is the "open to all" option. */
@Composable
internal fun OptionPicker(
    label: String,
    value: String?,
    emptyText: String,
    options: List<String>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    FieldLabel(label)
    Box {
        SelectorRow(text = value ?: emptyText, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(emptyText) }, onClick = { onSelect(null); expanded = false })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
    label: String,
    value: LocalDate?,
    emptyText: String,
    onSelect: (LocalDate?) -> Unit,
    error: String? = null
) {
    var open by remember { mutableStateOf(false) }
    FieldLabel(label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            SelectorRow(text = value?.formatted() ?: emptyText, onClick = { open = true }, isError = error != null)
        }
        if (value != null) {
            TextButton(onClick = { onSelect(null) }) { Text("Quitar") }
        }
    }
    FieldError(error)

    if (open) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = value?.toDatePickerMillis())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onSelect(datePickerMillisToDate(it)) }
                    open = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SelectorRow(text: String, onClick: () -> Unit, isError: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, if (isError) Color.Red else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
