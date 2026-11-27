package dev.dreamteam.sportpro.ui.training

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.TrainingSession
import dev.dreamteam.sportpro.data.repository.TrainingSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ReadOnlySessionsUiState(
    val sessions: List<TrainingSession> = emptyList(),
    // null = todos los equipos; el filtro se aplica sobre lo ya cargado.
    val selectedTeamId: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    /** Equipos presentes en la lista (id → nombre), para los chips de filtro. */
    val teams: List<Pair<String, String>>
        get() = sessions.map { it.teamId to it.teamName.ifBlank { "Equipo" } }.distinctBy { it.first }.sortedBy { it.second }

    val visibleSessions: List<TrainingSession>
        get() = sessions.filter { selectedTeamId == null || it.teamId == selectedTeamId }
}

/** US-06 criterio 5: el tutor ve las sesiones de los equipos de su jugador (su categoría o TODAS). */
class GuardianSessionsViewModel : ViewModel() {

    private val repository = TrainingSessionRepository()
    private val _uiState = MutableStateFlow(ReadOnlySessionsUiState())
    val uiState: StateFlow<ReadOnlySessionsUiState> = _uiState.asStateFlow()
    private val listeners = mutableListOf<ListenerRegistration>()
    private val sessionsByTeam = mutableMapOf<String, List<TrainingSession>>()
    private var started = false

    fun start(playerId: String) {
        if (started) return
        started = true
        repository.loadPlayerMemberships(
            playerId = playerId,
            onResult = { memberships ->
                if (memberships.isEmpty()) _uiState.update { it.copy(isLoading = false) }
                memberships.forEach { (teamId, category) ->
                    // Primero el enlace que las reglas exigen al tutor; luego las consultas.
                    repository.ensureGuardianLinks(
                        teamId = teamId,
                        playerId = playerId,
                        playerCategory = category,
                        onSuccess = {
                            listeners += repository.listenForMember(
                                teamId = teamId,
                                memberCategory = category,
                                onChange = { list ->
                                    sessionsByTeam[teamId] = list
                                    publish()
                                },
                                onError = ::showError
                            )
                        },
                        onError = ::showError
                    )
                }
            },
            onError = ::showError
        )
    }

    fun selectTeam(teamId: String?) = _uiState.update { it.copy(selectedTeamId = teamId) }

    private fun publish() = _uiState.update {
        it.copy(sessions = sessionsByTeam.values.flatten().distinctBy { s -> s.id }.sortedForDisplay(), isLoading = false)
    }

    private fun showError(message: String) = _uiState.update { it.copy(isLoading = false, error = message) }

    override fun onCleared() {
        listeners.forEach { it.remove() }
    }
}

/** US-06 criterio 5: el admin consulta las sesiones de todos los equipos en solo lectura. */
class AdminSessionsViewModel : ViewModel() {

    private val repository = TrainingSessionRepository()
    private val _uiState = MutableStateFlow(ReadOnlySessionsUiState())
    val uiState: StateFlow<ReadOnlySessionsUiState> = _uiState.asStateFlow()
    private val listener = repository.listenAll(
        onChange = { list -> _uiState.update { it.copy(sessions = list.sortedForDisplay(), isLoading = false, error = null) } },
        onError = { message -> _uiState.update { it.copy(isLoading = false, error = message) } }
    )

    fun selectTeam(teamId: String?) = _uiState.update { it.copy(selectedTeamId = teamId) }

    override fun onCleared() {
        listener.remove()
    }
}

/** Lista de sesiones en solo lectura del tutor (de un jugador). Al tocar una se abre el detalle sin edición. */
@Composable
fun GuardianSessionsScreen(
    playerId: String,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: GuardianSessionsViewModel = viewModel()
) {
    LaunchedEffect(playerId) { viewModel.start(playerId) }
    val uiState by viewModel.uiState.collectAsState()
    ReadOnlySessionsContent(
        title = "ENTRENAMIENTOS",
        note = "Sesiones de los equipos del jugador para su categoría. Solo lectura.",
        emptyText = "Los equipos del jugador aún no tienen sesiones planificadas para su categoría.",
        uiState = uiState,
        onSelectTeam = viewModel::selectTeam,
        onBack = onBack,
        onOpenSession = onOpenSession
    )
}

/** Lista de sesiones del admin, con filtro por equipo. Al tocar una se abre el detalle sin edición. */
@Composable
fun AdminSessionsScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: AdminSessionsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    ReadOnlySessionsContent(
        title = "SESIONES · ADMIN",
        note = "Sesiones planificadas en todos los equipos (las más recientes). Solo lectura.",
        emptyText = "Aún no hay sesiones planificadas.",
        uiState = uiState,
        onSelectTeam = viewModel::selectTeam,
        onBack = onBack,
        onOpenSession = onOpenSession
    )
}

@Composable
private fun ReadOnlySessionsContent(
    title: String,
    note: String,
    emptyText: String,
    uiState: ReadOnlySessionsUiState,
    onSelectTeam: (String?) -> Unit,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit
) {
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
            item { Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            uiState.error?.let { error -> item { MessageText(error) } }
            when {
                uiState.isLoading -> item { LoadingBox() }
                uiState.sessions.isEmpty() -> item { EmptyState(emptyText) }
                else -> {
                    if (uiState.teams.size > 1) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = uiState.selectedTeamId == null,
                                    onClick = { onSelectTeam(null) },
                                    label = { Text("Todos") }
                                )
                                uiState.teams.forEach { (id, name) ->
                                    FilterChip(
                                        selected = uiState.selectedTeamId == id,
                                        onClick = { onSelectTeam(id) },
                                        label = { Text(name) }
                                    )
                                }
                            }
                        }
                    }
                    items(uiState.visibleSessions, key = { it.id }) { session ->
                        SessionCard(session = session, onClick = { onOpenSession(session.id) })
                    }
                }
            }
        }
    }
}
