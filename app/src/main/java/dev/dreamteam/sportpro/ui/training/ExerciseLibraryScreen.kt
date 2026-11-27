package dev.dreamteam.sportpro.ui.training

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import dev.dreamteam.sportpro.data.model.Exercise

/**
 * US-07: biblioteca compartida de ejercicios. Todos la ven; la entrenadora aprobada ([canManage])
 * crea ejercicios y edita o elimina solo los suyos.
 */
@Composable
fun ExerciseLibraryScreen(
    canManage: Boolean,
    onBack: () -> Unit,
    viewModel: ExerciseLibraryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = uiState.exercises.firstOrNull { it.id == editingId }
    var showForm by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Exercise?>(null) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { SportTopBar(title = "BIBLIOTECA DE EJERCICIOS", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (canManage) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingId = null
                        viewModel.clearFormError()
                        showForm = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nuevo ejercicio", fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = if (canManage) {
                        "Biblioteca compartida: usa cualquier ejercicio al planificar. Solo puedes editar o eliminar los tuyos."
                    } else {
                        "Ejercicios que usan las entrenadoras al planificar las sesiones. Solo lectura."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            if (canManage) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.onlyMine,
                            onClick = { viewModel.setOnlyMine(true) },
                            label = { Text("Mis ejercicios") }
                        )
                        FilterChip(
                            selected = !uiState.onlyMine,
                            onClick = { viewModel.setOnlyMine(false) },
                            label = { Text("Todos") }
                        )
                    }
                }
            }
            when {
                uiState.isLoading -> item { LoadingBox() }
                uiState.error != null -> item { MessageText(uiState.error.orEmpty()) }
                uiState.visibleExercises.isEmpty() -> item {
                    EmptyState(
                        when {
                            canManage && uiState.onlyMine -> "Aún no tienes ejercicios. Agrega el primero con el botón \"Nuevo ejercicio\"."
                            canManage -> "La biblioteca está vacía. Agrega el primero con el botón \"Nuevo ejercicio\"."
                            else -> "La biblioteca aún no tiene ejercicios."
                        }
                    )
                }
                else -> items(uiState.visibleExercises, key = { it.id }) { exercise ->
                    ExerciseCard(
                        exercise = exercise,
                        creatorLabel = exercise.creatorLabel(uiState.currentUserId),
                        // ✏️ y 🗑️ solo en los ejercicios propios de una entrenadora aprobada.
                        editable = canManage && exercise.isOwnedBy(uiState.currentUserId),
                        onEdit = {
                            editingId = exercise.id
                            viewModel.clearFormError()
                            showForm = true
                        },
                        onDelete = { toDelete = exercise }
                    )
                }
            }
        }
    }

    if (showForm) {
        ExerciseFormDialog(
            exercise = editing,
            isSaving = uiState.isSaving,
            error = uiState.formError,
            onDismiss = { showForm = false },
            onSave = { name, description, duration ->
                viewModel.save(editing, name, description, duration) { showForm = false }
            }
        )
    }

    toDelete?.let { exercise ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminar ejercicio") },
            text = {
                Text("Se quitará \"${exercise.name}\" de la biblioteca. Las sesiones ya registradas conservarán su copia del ejercicio.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(exercise)
                    toDelete = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ExerciseCard(
    exercise: Exercise,
    creatorLabel: String,
    editable: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Creado por $creatorLabel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                StatusPill(text = "${exercise.durationMinutes} min", color = MaterialTheme.colorScheme.primary)
            }
            if (editable) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (exercise.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = exercise.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ExerciseFormDialog(
    exercise: Exercise?,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.name.orEmpty()) }
    var description by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.description.orEmpty()) }
    var duration by rememberSaveable(exercise?.id) {
        mutableStateOf(exercise?.durationMinutes?.takeIf { it > 0 }?.toString().orEmpty())
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (exercise == null) "Nuevo ejercicio" else "Editar ejercicio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SportTextField(value = name, onValueChange = { name = it }, label = "Nombre", placeholder = "ej. Rondo 4 vs 2")
                SportTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Descripción",
                    singleLine = false,
                    minLines = 3
                )
                SportTextField(
                    value = duration,
                    onValueChange = { value -> duration = value.filter { it.isDigit() }.take(3) },
                    label = "Duración referencial (min)",
                    keyboardType = KeyboardType.Number
                )
                if (error != null) MessageText(error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, description, duration) }, enabled = !isSaving) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") } }
    )
}
