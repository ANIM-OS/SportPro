package dev.dreamteam.sportpro.ui.training

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.AttendanceRecord
import dev.dreamteam.sportpro.data.model.AttendanceStatus
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.data.model.TrainingSession
import dev.dreamteam.sportpro.data.model.canMarkAttendance
import dev.dreamteam.sportpro.data.model.recordsOfFormerMembers
import dev.dreamteam.sportpro.data.repository.AttendanceRepository
import dev.dreamteam.sportpro.data.repository.TeamDirectoryRepository
import dev.dreamteam.sportpro.data.repository.TrainingSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SessionDetailUiState(
    val session: TrainingSession? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val isOwner: Boolean = false,
    val roster: List<TeamMember> = emptyList(),
    val rosterLoading: Boolean = false,
    val attendance: Map<String, AttendanceRecord> = emptyMap(),
    // Marcas de jugadores que ya no están en el equipo (solo lectura).
    val formerMemberRecords: List<AttendanceRecord> = emptyList(),
    // Admin: asistencia de la sesión en solo lectura (US-08).
    val isAdminView: Boolean = false,
    val adminRecords: List<AttendanceRecord> = emptyList(),
    val savingPlayerIds: Set<String> = emptySet(),
    val error: String? = null,
    val message: String? = null
) {
    /** US-08: la asistencia se registra cuando la sesión ya empezó. */
    val canMark: Boolean get() = canMarkAttendance(session?.date?.toDate())
}

/** US-06 (detalle de la sesión) y US-08 (toma de asistencia del entrenador). */
class SessionDetailViewModel : ViewModel() {

    private val sessionRepository = TrainingSessionRepository()
    private val attendanceRepository = AttendanceRepository()
    private val teamRepository = TeamDirectoryRepository()
    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    private var sessionListener: ListenerRegistration? = null
    private var rosterListener: ListenerRegistration? = null
    private var attendanceListener: ListenerRegistration? = null
    private var fullRoster: List<TeamMember> = emptyList()
    private var rosterLoaded = false
    private var started = false

    fun start(sessionId: String, isAdmin: Boolean = false) {
        if (started) return
        started = true
        sessionListener = sessionRepository.listenSession(
            sessionId = sessionId,
            onChange = { session ->
                if (session == null) {
                    _uiState.update { it.copy(session = null, isLoading = false, notFound = true) }
                    return@listenSession
                }
                val isOwner = session.createdBy == teamRepository.currentUserId
                _uiState.update { it.copy(session = session, isLoading = false, notFound = false, isOwner = isOwner) }
                when {
                    isOwner -> watchAttendance(session)
                    isAdmin -> watchAttendanceAsAdmin(session)
                    else -> publishRoster()
                }
            },
            onError = { message -> _uiState.update { it.copy(isLoading = false, error = message) } }
        )
    }

    private fun watchAttendance(session: TrainingSession) {
        if (rosterListener == null) {
            _uiState.update { it.copy(rosterLoading = true) }
            rosterListener = teamRepository.listenRoster(
                teamId = session.teamId,
                onChange = { members ->
                    fullRoster = members
                    rosterLoaded = true
                    publishRoster()
                },
                onError = { message -> _uiState.update { it.copy(rosterLoading = false, error = message) } }
            )
        } else {
            publishRoster()
        }
        if (attendanceListener == null) {
            attendanceListener = attendanceRepository.listenSession(
                sessionId = session.id,
                onChange = { records ->
                    _uiState.update { state -> state.copy(attendance = records.associateBy { it.playerId }) }
                    publishRoster()
                },
                onError = { message -> _uiState.update { it.copy(error = message) } }
            )
        }
    }

    private fun watchAttendanceAsAdmin(session: TrainingSession) {
        if (attendanceListener != null) return
        _uiState.update { it.copy(isAdminView = true) }
        attendanceListener = attendanceRepository.listenSessionAsAdmin(
            sessionId = session.id,
            onChange = { records -> _uiState.update { it.copy(adminRecords = records.sortedBy { r -> r.playerName.lowercase() }) } },
            onError = { message -> _uiState.update { it.copy(error = message) } }
        )
    }

    /** Solo se listan los jugadores de la categoría de la sesión; los que salieron del equipo, aparte. */
    private fun publishRoster() {
        val session = _uiState.value.session ?: return
        _uiState.update {
            it.copy(
                roster = fullRoster.filter { member -> session.appliesTo(member.category) },
                // Hasta tener la plantilla no se sabe quién salió del equipo.
                formerMemberRecords = if (!rosterLoaded) emptyList()
                    else recordsOfFormerMembers(it.attendance.values, fullRoster.map { m -> m.userId }.toSet()),
                rosterLoading = false
            )
        }
    }

    fun mark(member: TeamMember, status: AttendanceStatus) {
        val session = _uiState.value.session ?: return
        if (member.userId in _uiState.value.savingPlayerIds) return
        _uiState.update { it.copy(savingPlayerIds = it.savingPlayerIds + member.userId) }
        if (!canMarkAttendance(session.date?.toDate())) {
            _uiState.update { it.copy(message = "La asistencia se registra cuando empieza la sesión") }
            return
        }
        attendanceRepository.mark(
            session = session,
            member = member,
            status = status,
            existing = _uiState.value.attendance[member.userId],
            onSuccess = { _uiState.update { it.copy(savingPlayerIds = it.savingPlayerIds - member.userId) } },
            onError = { message ->
                _uiState.update { it.copy(savingPlayerIds = it.savingPlayerIds - member.userId, message = message) }
            }
        )
    }

    fun delete(onDeleted: () -> Unit) {
        val session = _uiState.value.session ?: return
        if (!session.isEditable()) {
            _uiState.update { it.copy(message = "Solo se pueden eliminar sesiones que aún no se realizaron") }
            return
        }
        sessionRepository.delete(
            sessionId = session.id,
            onSuccess = onDeleted,
            onError = { message -> _uiState.update { it.copy(message = message) } }
        )
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    override fun onCleared() {
        sessionListener?.remove()
        rosterListener?.remove()
        attendanceListener?.remove()
    }
}
