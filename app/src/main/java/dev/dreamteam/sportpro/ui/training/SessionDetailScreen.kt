package dev.dreamteam.sportpro.ui.training

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dreamteam.sportpro.data.model.AttendanceRecord
import dev.dreamteam.sportpro.data.model.AttendanceStatus
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.data.model.TrainingSession

/** Detalle de la sesión (US-06) y registro de asistencia por el entrenador (US-08). */
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenPlayerHistory: (String) -> Unit,
    viewModel: SessionDetailViewModel = viewModel()
) {
    LaunchedEffect(sessionId) { viewModel.start(sessionId) }
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val session = uiState.session
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SportTopBar(title = "SESIÓN", onBack = onBack, actions = {
                if (session != null && uiState.isOwner && session.isEditable()) {
                    IconButton(onClick = { onEdit(session.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar sesión")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar sesión", tint = MaterialTheme.colorScheme.error)
                    }
                }
            })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                uiState.isLoading -> item { LoadingBox() }
                uiState.notFound -> item { EmptyState("La sesión ya no existe.") }
                session == null -> item { MessageText(uiState.error ?: "No se pudo cargar la sesión") }
                else -> {
                    item { SessionSummary(session) }
                    item { SectionLabel("EJERCICIOS (${session.exercises.size})", modifier = Modifier.padding(top = 8.dp)) }
                    if (session.exercises.isEmpty()) {
                        item { EmptyState("La sesión no tiene ejercicios asignados.") }
                    } else {
                        items(session.exercises) { exercise ->
                            InfoCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = exercise.name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${exercise.durationMinutes} min",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (exercise.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(exercise.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    if (uiState.isOwner) {
                        item { AttendanceHeader(uiState.roster, uiState.attendance) }
                        when {
                            uiState.rosterLoading -> item { LoadingBox() }
                            uiState.roster.isEmpty() -> item {
                                EmptyState("No hay jugadores en ${categoryLabel(session.category).lowercase()} de este equipo.")
                            }
                            else -> items(uiState.roster, key = { it.userId }) { member ->
                                AttendanceRow(
                                    member = member,
                                    record = uiState.attendance[member.userId],
                                    saving = member.userId in uiState.savingPlayerIds,
                                    onMark = { status -> viewModel.mark(member, status) },
                                    onOpenHistory = { onOpenPlayerHistory(member.userId) }
                                )
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = "El entrenador registra la asistencia. Puedes consultarla en \"Mi asistencia\".",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar sesión") },
            text = { Text("La sesión se eliminará de la planificación del equipo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SessionSummary(session: TrainingSession) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = session.date.toDateTimeText().replaceFirstChar { it.uppercase() },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (session.isEditable()) {
                StatusPill("Programada", MaterialTheme.colorScheme.primary)
            } else {
                StatusPill("Realizada", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        DetailLine("Equipo", session.teamName)
        DetailLine("Categoría", categoryLabel(session.category))
        DetailLine("Duración", "${session.durationMinutes} min")
        Spacer(modifier = Modifier.height(8.dp))
        SectionLabel("OBJETIVOS")
        Spacer(modifier = Modifier.height(4.dp))
        Text(session.objectives, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
    }
}

@Composable
fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(value.ifBlank { "No registrado" }, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AttendanceHeader(roster: List<TeamMember>, attendance: Map<String, AttendanceRecord>) {
    val marked = roster.count { it.userId in attendance }
    val attended = roster.count { attendance[it.userId]?.status?.countsAsAttended == true }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        SectionLabel("ASISTENCIA")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$marked de ${roster.size} registrados · $attended asistieron",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttendanceRow(
    member: TeamMember,
    record: AttendanceRecord?,
    saving: Boolean,
    onMark: (AttendanceStatus) -> Unit,
    onOpenHistory: () -> Unit
) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(member.playerName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = listOfNotNull(member.category.ifBlank { null }, member.jerseyNumber?.let { "#$it" }).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onOpenHistory) { Text("Historial", fontSize = 12.sp) }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AttendanceStatus.entries.forEach { status ->
                val color = attendanceColor(status)
                FilterChip(
                    selected = record?.status == status,
                    onClick = { if (record?.status != status) onMark(status) },
                    enabled = !saving,
                    label = { Text(status.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = color.copy(alpha = 0.18f),
                        selectedLabelColor = color
                    )
                )
            }
        }
    }
}
