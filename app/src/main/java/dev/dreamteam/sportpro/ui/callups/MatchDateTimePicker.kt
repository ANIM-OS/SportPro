package dev.dreamteam.sportpro.ui.callups

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.delay

/** Calendar dates in Material are UTC; the chosen encounter time is local. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MatchDateTimePicker(
    title: String,
    initial: Long,
    maximum: Long? = null,
    onDismiss: () -> Unit,
    onSelected: (Long) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val spanish = remember(configuration) {
        Configuration(configuration).apply { setLocale(Locale.forLanguageTag("es-PE")) }
    }
    val localizedContext = remember(context, spanish) { context.createConfigurationContext(spanish) }
    CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides spanish, LocalResources provides localizedContext.resources) {
        val zone = ZoneId.systemDefault()
        val initialTime = remember { Instant.ofEpochMilli(initial.takeIf { it > System.currentTimeMillis() } ?: (System.currentTimeMillis() + 3600000)).atZone(zone) }
        val dates = rememberDatePickerState(
            initialSelectedDateMillis = initialTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !date.isBefore(java.time.LocalDate.now(zone)) &&
                        (maximum == null || !date.isAfter(Instant.ofEpochMilli(maximum).atZone(zone).toLocalDate()))
                }
            }
        )
        var choosingTime by remember { mutableStateOf(false) }
        val time = rememberTimePickerState(initialHour = initialTime.hour, initialMinute = initialTime.minute, is24Hour = true)
        val now by produceState(System.currentTimeMillis()) {
            while (true) { value = System.currentTimeMillis(); delay(1000) }
        }
        val selected = dates.selectedDateMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                .atTime(time.hour, time.minute).atZone(zone).toInstant().toEpochMilli()
        }
        val valid = selected != null && selected > now && (maximum == null || selected <= maximum)
        if (!choosingTime) {
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = { TextButton(enabled = dates.selectedDateMillis != null, onClick = { choosingTime = true }) { Text("Elegir hora") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
            ) {
                DatePicker(
                    state = dates,
                    title = { Text(title, Modifier.padding(start = 24.dp, top = 16.dp), style = MaterialTheme.typography.titleMedium) },
                    headline = {
                        val label = dates.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE")))
                        } ?: "Selecciona una fecha"
                        Text(label, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineSmall)
                    }
                )
            }
        } else {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Hora · $title", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        selected?.let { Text(dateLabel(it), style = MaterialTheme.typography.bodyLarge) }
                        TimeInput(state = time)
                        Text("Formato de 24 horas · hora local", style = MaterialTheme.typography.bodySmall)
                        if (!valid) Text(if (selected != null && selected <= now) "Elige una hora futura." else "No puede superar el inicio del partido.", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { choosingTime = false }) { Text("Cambiar fecha") }
                    }
                },
                confirmButton = { Button(enabled = valid, onClick = { selected?.let(onSelected) }) { Text("Confirmar") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
            )
        }
    }
}
