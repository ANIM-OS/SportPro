package dev.dreamteam.sportpro.ui.training

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.AttendanceRecord
import dev.dreamteam.sportpro.data.repository.AttendanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AttendanceHistoryUiState(
    val records: List<AttendanceRecord> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val attended: Int get() = records.count { it.status.countsAsAttended }
    val percentage: Int get() = if (records.isEmpty()) 0 else attended * 100 / records.size
}

/** US-08: historial de participación de un jugador. */
class AttendanceHistoryViewModel : ViewModel() {

    private val repository = AttendanceRepository()
    private val _uiState = MutableStateFlow(AttendanceHistoryUiState())
    val uiState: StateFlow<AttendanceHistoryUiState> = _uiState.asStateFlow()

    private var listener: ListenerRegistration? = null
    private var started = false

    fun start(playerId: String, asCoach: Boolean) {
        if (started) return
        started = true
        listener = repository.listenPlayer(
            playerId = playerId,
            asCoach = asCoach,
            onChange = { records -> _uiState.update { it.copy(records = records, isLoading = false, error = null) } },
            onError = { message -> _uiState.update { it.copy(isLoading = false, error = message) } }
        )
    }

    override fun onCleared() {
        listener?.remove()
    }
}
