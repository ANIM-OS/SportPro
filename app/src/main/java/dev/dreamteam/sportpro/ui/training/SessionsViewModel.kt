package dev.dreamteam.sportpro.ui.training

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.TrainingSession
import dev.dreamteam.sportpro.data.repository.TeamDirectoryRepository
import dev.dreamteam.sportpro.data.repository.TrainingSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SessionsUiState(
    val coachSessions: List<TrainingSession> = emptyList(),
    val playerSessions: List<TrainingSession> = emptyList(),
    val coachLoading: Boolean = false,
    val playerLoading: Boolean = false,
    val error: String? = null
)

/** US-06: sesiones que el entrenador planificó y las que corresponden a los equipos del jugador. */
class SessionsViewModel : ViewModel() {

    private val sessionRepository = TrainingSessionRepository()
    private val teamRepository = TeamDirectoryRepository()
    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    private val listeners = mutableListOf<ListenerRegistration>()
    private val sessionsByTeam = mutableMapOf<String, List<TrainingSession>>()
    private var started = false

    fun start(isCoach: Boolean, isPlayer: Boolean) {
        if (started) return
        started = true
        if (isCoach) {
            _uiState.update { it.copy(coachLoading = true) }
            sessionRepository.listenCreatedByMe(
                onChange = { list -> _uiState.update { it.copy(coachSessions = list.sortedForDisplay(), coachLoading = false) } },
                onError = { message -> _uiState.update { it.copy(coachLoading = false, error = message) } }
            )?.let(listeners::add)
        }
        if (isPlayer) {
            _uiState.update { it.copy(playerLoading = true) }
            teamRepository.loadMemberships(
                onResult = { memberships ->
                    if (memberships.isEmpty()) _uiState.update { it.copy(playerLoading = false) }
                    memberships.forEach { membership ->
                        listeners += sessionRepository.listenTeam(
                            teamId = membership.team.id,
                            onChange = { list ->
                                sessionsByTeam[membership.team.id] = list.filter { it.appliesTo(membership.category) }
                                publishPlayerSessions()
                            },
                            onError = { message -> _uiState.update { it.copy(playerLoading = false, error = message) } }
                        )
                    }
                },
                onError = { message -> _uiState.update { it.copy(playerLoading = false, error = message) } }
            )
        }
    }

    private fun publishPlayerSessions() {
        val all = sessionsByTeam.values.flatten().distinctBy { it.id }
        _uiState.update { it.copy(playerSessions = all.sortedForDisplay(), playerLoading = false) }
    }

    /** Próximas primero (la más cercana arriba) y luego las realizadas (la más reciente arriba). */
    private fun List<TrainingSession>.sortedForDisplay(): List<TrainingSession> {
        val (upcoming, done) = partition { it.isEditable() }
        return upcoming.sortedBy { it.date?.seconds ?: Long.MAX_VALUE } +
            done.sortedByDescending { it.date?.seconds ?: 0L }
    }

    override fun onCleared() {
        listeners.forEach { it.remove() }
    }
}
