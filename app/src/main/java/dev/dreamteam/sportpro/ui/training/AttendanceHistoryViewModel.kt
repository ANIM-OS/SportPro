package dev.dreamteam.sportpro.ui.training

import dev.dreamteam.sportpro.data.model.AttendanceChange
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
    // Última corrección de cada registro corregido (id del registro → cambio).
    val changes: Map<String, AttendanceChange> = emptyMap(),
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
            onChange = { records ->
                _uiState.update { it.copy(records = records, isLoading = false, error = null) }
                loadChanges(records)
            },
            onError = { message -> _uiState.update { it.copy(isLoading = false, error = message) } }
        )
    }

    /** US-08: carga la última corrección de los registros corregidos que aún no se tienen. */
    private fun loadChanges(records: List<AttendanceRecord>) {
        records.forEach { record ->
            val changeId = record.lastChangeId ?: return@forEach
            if (_uiState.value.changes[record.id]?.id == changeId) return@forEach
            repository.loadChange(record.id, changeId) { change ->
                if (change != null) _uiState.update { it.copy(changes = it.changes + (record.id to change)) }
            }
        }
    }

    override fun onCleared() {
        listener?.remove()
    }
}
