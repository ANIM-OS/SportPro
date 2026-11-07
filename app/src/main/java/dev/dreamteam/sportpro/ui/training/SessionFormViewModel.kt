package dev.dreamteam.sportpro.ui.training

import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.Exercise
import dev.dreamteam.sportpro.data.model.SessionExercise
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.model.TrainingSession
import dev.dreamteam.sportpro.data.repository.ExerciseRepository
import dev.dreamteam.sportpro.data.repository.TeamDirectoryRepository
import dev.dreamteam.sportpro.data.repository.TrainingSessionRepository
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SessionFormUiState(
    val teams: List<Team> = emptyList(),
    val teamsLoading: Boolean = true,
    val exercises: List<Exercise> = emptyList(),
    val session: TrainingSession? = null,
    val sessionLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

/** US-06: crear o modificar una sesión (solo antes de su realización). */
class SessionFormViewModel : ViewModel() {

    private val sessionRepository = TrainingSessionRepository()
    private val teamRepository = TeamDirectoryRepository()
    private val exerciseRepository = ExerciseRepository()
    private val _uiState = MutableStateFlow(SessionFormUiState())
    val uiState: StateFlow<SessionFormUiState> = _uiState.asStateFlow()

    private val listeners = mutableListOf<ListenerRegistration>()
    private var started = false

    fun start(sessionId: String?) {
        if (started) return
        started = true
        teamRepository.listenOwnedTeams(
            onChange = { teams -> _uiState.update { it.copy(teams = teams, teamsLoading = false) } },
            onError = { message -> _uiState.update { it.copy(teamsLoading = false, error = message) } }
        )?.let(listeners::add)
        exerciseRepository.listenMine(
            onChange = { list -> _uiState.update { it.copy(exercises = list) } },
            onError = { message -> _uiState.update { it.copy(error = message) } }
        )?.let(listeners::add)
        if (sessionId != null) {
            _uiState.update { it.copy(sessionLoading = true) }
            listeners += sessionRepository.listenSession(
                sessionId = sessionId,
                onChange = { session -> _uiState.update { it.copy(session = session, sessionLoading = false) } },
                onError = { message -> _uiState.update { it.copy(sessionLoading = false, error = message) } }
            )
        }
    }

    fun save(
        teamId: String?,
        category: String,
        dateMillis: Long?,
        durationText: String,
        objectives: String,
        selectedExerciseIds: List<String>,
        onSaved: () -> Unit
    ) {
        val state = _uiState.value
        val team = state.teams.firstOrNull { it.id == teamId }
        val duration = durationText.trim().toIntOrNull()
        val cleanObjectives = objectives.trim()
        val existing = state.session
        val error = when {
            existing != null && !existing.isEditable() -> "La sesión ya se realizó y no puede modificarse"
            team == null -> "Selecciona el equipo de la sesión"
            category != TrainingSession.ALL_CATEGORIES && category !in team.categories ->
                "Selecciona una categoría válida del equipo"
            dateMillis == null -> "Indica la fecha y hora de la sesión"
            dateMillis <= System.currentTimeMillis() -> "La fecha de la sesión debe ser futura"
            duration == null || duration !in 1..600 -> "La duración debe estar entre 1 y 600 minutos"
            cleanObjectives.isEmpty() -> "Describe los objetivos de la sesión"
            cleanObjectives.length > 500 -> "Los objetivos admiten hasta 500 caracteres"
            else -> null
        }
        if (error != null || team == null || dateMillis == null || duration == null) {
            _uiState.update { it.copy(error = error) }
            return
        }

        // Se guarda una copia de cada ejercicio; si uno ya no está en la biblioteca se conserva su copia anterior.
        val library = state.exercises.associateBy { it.id }
        val previous = existing?.exercises.orEmpty().associateBy { it.exerciseId }
        val exercises = selectedExerciseIds.mapNotNull { id ->
            library[id]?.let { SessionExercise(it.id, it.name, it.description, it.durationMinutes) } ?: previous[id]
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        sessionRepository.save(
            existingId = existing?.id,
            team = team,
            category = category,
            date = Timestamp(Date(dateMillis)),
            durationMinutes = duration,
            objectives = cleanObjectives,
            exercises = exercises,
            onSuccess = {
                _uiState.update { it.copy(isSaving = false) }
                onSaved()
            },
            onError = { message -> _uiState.update { it.copy(isSaving = false, error = message) } }
        )
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        listeners.forEach { it.remove() }
    }
}
