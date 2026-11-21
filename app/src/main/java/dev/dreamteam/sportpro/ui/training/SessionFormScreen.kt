package dev.dreamteam.sportpro.ui.training

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dreamteam.sportpro.data.model.TrainingSession
import java.util.Calendar

/** US-06: formulario para planificar o modificar una sesión de entrenamiento. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionFormScreen(
    sessionId: String?,
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: SessionFormViewModel = viewModel()
) {
    LaunchedEffect(sessionId) { viewModel.start(sessionId) }
    val uiState by viewModel.uiState.collectAsState()

    var teamId by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf(TrainingSession.ALL_CATEGORIES) }
    var dateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var duration by rememberSaveable { mutableStateOf("") }
    var objectives by rememberSaveable { mutableStateOf("") }
    var selectedExerciseIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var pendingUtcDate by rememberSaveable { mutableStateOf<Long?>(null) }

    // Al editar, se carga la sesión existente una sola vez.
    LaunchedEffect(uiState.session) {
        val session = uiState.session
        if (session != null && !prefilled) {
            teamId = session.teamId
            category = session.category
            dateMillis = session.date?.toDate()?.time
            duration = session.durationMinutes.toString()
            objectives = session.objectives
            selectedExerciseIds = session.exercises.map { it.exerciseId }
            prefilled = true
        }
    }
    // Con un solo equipo se selecciona automáticamente.
    LaunchedEffect(uiState.teams) {
        if (teamId == null && uiState.teams.size == 1) teamId = uiState.teams.first().id
    }

    val isEditing = sessionId != null
    val selectedTeam = uiState.teams.firstOrNull { it.id == teamId }
    val locked = uiState.session?.isEditable() == false

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { SportTopBar(title = if (isEditing) "EDITAR SESIÓN" else "NUEVA SESIÓN", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.teamsLoading || uiState.sessionLoading) {
                LoadingBox()
                return@Column
            }
            if (locked) {
                MessageText("Esta sesión ya se realizó; solo se pueden modificar sesiones antes de su fecha.")
                return@Column
            }
            if (uiState.teams.isEmpty()) {
                EmptyState("Primero crea un equipo en la pestaña Equipos para poder planificar sesiones.")
                return@Column
            }

            SectionLabel("EQUIPO")
            TeamChips(teams = uiState.teams, selectedTeamId = teamId, onSelect = {
                teamId = it.id
                category = TrainingSession.ALL_CATEGORIES
            })

            if (selectedTeam != null) {
                SectionLabel("CATEGORÍA")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf(TrainingSession.ALL_CATEGORIES) + selectedTeam.categories).forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(categoryLabel(option)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            SectionLabel("FECHA Y HORA")
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dateMillis?.toDateTimeText()?.replaceFirstChar { it.uppercase() } ?: "Seleccionar fecha y hora",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            SportTextField(
                value = duration,
                onValueChange = { value -> duration = value.filter { it.isDigit() }.take(3) },
                label = "Duración (min)",
                keyboardType = KeyboardType.Number
            )
            SportTextField(
                value = objectives,
                onValueChange = { objectives = it },
                label = "Objetivos",
                placeholder = "ej. Mejorar la salida con balón desde el fondo",
                singleLine = false,
                minLines = 3
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("EJERCICIOS", modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenLibrary) { Text("Abrir biblioteca") }
            }
            val previousOnly = uiState.session?.exercises.orEmpty()
                .filter { old -> uiState.exercises.none { it.id == old.exerciseId } }
            if (uiState.exercises.isEmpty() && previousOnly.isEmpty()) {
                EmptyState("Tu biblioteca está vacía. Agrega ejercicios para incluirlos en la sesión.")
            }
            uiState.exercises.forEach { exercise ->
                ExerciseCheckRow(
                    name = exercise.name,
                    detail = "${exercise.durationMinutes} min",
                    checked = exercise.id in selectedExerciseIds,
                    onCheckedChange = { checked ->
                        selectedExerciseIds = if (checked) selectedExerciseIds + exercise.id else selectedExerciseIds - exercise.id
                    }
                )
            }
            // Ejercicios que ya no están en la biblioteca pero siguen guardados en esta sesión.
            previousOnly.forEach { old ->
                ExerciseCheckRow(
                    name = old.name,
                    detail = "${old.durationMinutes} min · ya no está en la biblioteca",
                    checked = old.exerciseId in selectedExerciseIds,
                    onCheckedChange = { checked ->
                        selectedExerciseIds = if (checked) selectedExerciseIds + old.exerciseId else selectedExerciseIds - old.exerciseId
                    }
                )
            }
            val selectedMinutes = uiState.exercises.filter { it.id in selectedExerciseIds }.sumOf { it.durationMinutes } +
                previousOnly.filter { it.exerciseId in selectedExerciseIds }.sumOf { it.durationMinutes }
            if (selectedExerciseIds.isNotEmpty()) {
                Text(
                    text = "${selectedExerciseIds.size} seleccionados · $selectedMinutes min en ejercicios",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            uiState.error?.let { MessageText(it) }

            Button(
                onClick = {
                    viewModel.save(teamId, category, dateMillis, duration, objectives, selectedExerciseIds, onBack)
                },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = if (isEditing) "GUARDAR CAMBIOS" else "PLANIFICAR SESIÓN",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val today = remember { todayUtcMidnight() }
        val dateState = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= today
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingUtcDate = dateState.selectedDateMillis
                        showDatePicker = false
                    },
                    enabled = dateState.selectedDateMillis != null
                ) { Text("Siguiente") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = dateState) }
    }

    pendingUtcDate?.let { utcDate ->
        val initial = remember(dateMillis) { Calendar.getInstance().apply { dateMillis?.let { timeInMillis = it } } }
        val timeState = rememberTimePickerState(
            initialHour = if (dateMillis != null) initial.get(Calendar.HOUR_OF_DAY) else 16,
            initialMinute = if (dateMillis != null) initial.get(Calendar.MINUTE) else 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { pendingUtcDate = null },
            title = { Text("Hora de la sesión") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    dateMillis = combineDateAndTime(utcDate, timeState.hour, timeState.minute)
                    pendingUtcDate = null
                    viewModel.clearError()
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { pendingUtcDate = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ExerciseCheckRow(name: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    InfoCard(onClick = { onCheckedChange(!checked) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}
