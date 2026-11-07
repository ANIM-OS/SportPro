package dev.dreamteam.sportpro.ui.training

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dreamteam.sportpro.data.model.AttendanceStatus

/** US-08: historial de asistencia de un jugador. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttendanceHistoryScreen(
    playerId: String,
    asCoach: Boolean,
    title: String,
    onBack: () -> Unit,
    viewModel: AttendanceHistoryViewModel = viewModel()
) {
    LaunchedEffect(playerId) { viewModel.start(playerId, asCoach) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { SportTopBar(title = title, onBack = onBack) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                uiState.isLoading -> item { LoadingBox() }
                uiState.error != null -> item { MessageText(uiState.error.orEmpty()) }
                uiState.records.isEmpty() -> item { EmptyState("Aún no hay asistencias registradas.") }
                else -> {
                    item {
                        val playerName = uiState.records.first().playerName
                        InfoCard {
                            if (playerName.isNotBlank()) {
                                Text(playerName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${uiState.percentage}%",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "de participación (${uiState.attended} de ${uiState.records.size} sesiones)",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AttendanceStatus.entries.forEach { status ->
                                    val count = uiState.records.count { it.status == status }
                                    StatusPill("${status.label}: $count", attendanceColor(status))
                                }
                            }
                        }
                    }
                    item { SectionLabel("HISTORIAL", modifier = Modifier.padding(top = 8.dp)) }
                    items(uiState.records, key = { it.id }) { record ->
                        InfoCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = record.sessionDate.toDateTimeText().replaceFirstChar { it.uppercase() },
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(record.teamName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                                StatusPill(record.status.label, attendanceColor(record.status))
                            }
                        }
                    }
                }
            }
        }
    }
}
