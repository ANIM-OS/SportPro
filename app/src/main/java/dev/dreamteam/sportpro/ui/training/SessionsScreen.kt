package dev.dreamteam.sportpro.ui.training

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dreamteam.sportpro.data.model.TrainingSession

/** US-06: planificación de sesiones (entrenador) y consulta de entrenamientos (jugador). */
@Composable
fun SessionsScreen(
    isCoach: Boolean,
    isPlayer: Boolean,
    onBack: () -> Unit,
    onCreateSession: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: SessionsViewModel = viewModel()
) {
    LaunchedEffect(isCoach, isPlayer) { viewModel.start(isCoach, isPlayer) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { SportTopBar(title = "SESIONES DE ENTRENAMIENTO", onBack = onBack) },
        floatingActionButton = {
            if (isCoach) {
                ExtendedFloatingActionButton(
                    onClick = onCreateSession,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nueva sesión", fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.error?.let { error -> item { MessageText(error) } }
            if (isCoach) {
                sessionSection(
                    title = "PLANIFICADAS POR MÍ",
                    sessions = uiState.coachSessions,
                    loading = uiState.coachLoading,
                    emptyText = "Aún no planificaste sesiones. Crea una con \"Nueva sesión\".",
                    onOpenSession = onOpenSession
                )
            }
            if (isPlayer) {
                sessionSection(
                    title = "MIS ENTRENAMIENTOS",
                    sessions = uiState.playerSessions,
                    loading = uiState.playerLoading,
                    emptyText = "Tus equipos aún no tienen sesiones planificadas para tu categoría.",
                    onOpenSession = onOpenSession
                )
            }
        }
    }
}

private fun LazyListScope.sessionSection(
    title: String,
    sessions: List<TrainingSession>,
    loading: Boolean,
    emptyText: String,
    onOpenSession: (String) -> Unit
) {
    item { SectionLabel(title, modifier = Modifier.padding(top = 8.dp)) }
    when {
        loading -> item { LoadingBox() }
        sessions.isEmpty() -> item { EmptyState(emptyText) }
        else -> items(sessions, key = { "$title-${it.id}" }) { session ->
            SessionCard(session = session, onClick = { onOpenSession(session.id) })
        }
    }
}

@Composable
fun SessionCard(session: TrainingSession, onClick: () -> Unit) {
    InfoCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = session.date.toDateTimeText().replaceFirstChar { it.uppercase() },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (session.isEditable()) {
                StatusPill("Programada", MaterialTheme.colorScheme.primary)
            } else {
                StatusPill("Realizada", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${session.teamName} · ${categoryLabel(session.category)} · ${session.durationMinutes} min",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        if (session.objectives.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = session.objectives,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when (session.exercises.size) {
                0 -> "Sin ejercicios"
                1 -> "1 ejercicio"
                else -> "${session.exercises.size} ejercicios"
            },
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
