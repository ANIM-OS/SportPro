package dev.dreamteam.sportpro.ui.players

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.FeeStatus
import dev.dreamteam.sportpro.data.model.MonthlyFee
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.data.repository.MonthlyFeeRepository
import dev.dreamteam.sportpro.data.repository.TeamDirectoryRepository
import dev.dreamteam.sportpro.ui.training.currentPeriod
import dev.dreamteam.sportpro.ui.training.shiftPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MonthlyFeesUiState(
    val teams: List<Team> = emptyList(),
    val teamsLoading: Boolean = true,
    val selectedTeamId: String? = null,
    val period: String = currentPeriod(),
    val roster: List<TeamMember> = emptyList(),
    val rosterLoading: Boolean = false,
    val fees: Map<String, MonthlyFee> = emptyMap(),
    val busyPlayerIds: Set<String> = emptySet(),
    val error: String? = null,
    val message: String? = null
)

/** US-05: el entrenador registra el estado simulado de mensualidades por equipo y periodo. */
class MonthlyFeesViewModel : ViewModel() {

    private val teamRepository = TeamDirectoryRepository()
    private val feeRepository = MonthlyFeeRepository()
    private val _uiState = MutableStateFlow(MonthlyFeesUiState())
    val uiState: StateFlow<MonthlyFeesUiState> = _uiState.asStateFlow()

    private var rosterListener: ListenerRegistration? = null
    private var feesListener: ListenerRegistration? = null
    private val teamsListener = teamRepository.listenOwnedTeams(
        onChange = { teams ->
            _uiState.update { it.copy(teams = teams, teamsLoading = false) }
            if (teams.none { it.id == _uiState.value.selectedTeamId }) teams.firstOrNull()?.let(::selectTeam)
        },
        onError = { message -> _uiState.update { it.copy(teamsLoading = false, error = message) } }
    )

    fun selectTeam(team: Team) {
        rosterListener?.remove()
        _uiState.update { it.copy(selectedTeamId = team.id, roster = emptyList(), rosterLoading = true) }
        rosterListener = teamRepository.listenRoster(
            teamId = team.id,
            onChange = { members -> _uiState.update { it.copy(roster = members, rosterLoading = false) } },
            onError = { message -> _uiState.update { it.copy(rosterLoading = false, error = message) } }
        )
        watchFees()
    }

    fun changePeriod(months: Int) {
        _uiState.update { it.copy(period = shiftPeriod(it.period, months)) }
        watchFees()
    }

    private fun watchFees() {
        feesListener?.remove()
        val state = _uiState.value
        val teamId = state.selectedTeamId ?: return
        _uiState.update { it.copy(fees = emptyMap()) }
        feesListener = feeRepository.listenTeamPeriod(
            teamId = teamId,
            period = state.period,
            onChange = { fees -> _uiState.update { it.copy(fees = fees.associateBy { fee -> fee.playerId }) } },
            onError = { message -> _uiState.update { it.copy(error = message) } }
        )
    }

    fun register(member: TeamMember, amountText: String, status: FeeStatus) {
        val state = _uiState.value
        val team = state.teams.firstOrNull { it.id == state.selectedTeamId } ?: return
        val amount = amountText.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        if (amountText.isNotBlank() && (amount == null || amount < 0 || amount > 100_000)) {
            _uiState.update { it.copy(message = "Ingresa un monto referencial válido") }
            return
        }
        setBusy(member.userId, true)
        feeRepository.register(
            team = team,
            member = member,
            period = state.period,
            amount = amount,
            status = status,
            onSuccess = { setBusy(member.userId, false) },
            onError = { message ->
                setBusy(member.userId, false)
                _uiState.update { it.copy(message = message) }
            }
        )
    }

    fun toggleStatus(fee: MonthlyFee) {
        val next = if (fee.status == FeeStatus.PAGADA) FeeStatus.PENDIENTE else FeeStatus.PAGADA
        setBusy(fee.playerId, true)
        feeRepository.updateStatus(
            fee = fee,
            status = next,
            onSuccess = { setBusy(fee.playerId, false) },
            onError = { message ->
                setBusy(fee.playerId, false)
                _uiState.update { it.copy(message = message) }
            }
        )
    }

    private fun setBusy(playerId: String, busy: Boolean) = _uiState.update {
        it.copy(busyPlayerIds = if (busy) it.busyPlayerIds + playerId else it.busyPlayerIds - playerId)
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    override fun onCleared() {
        teamsListener?.remove()
        rosterListener?.remove()
        feesListener?.remove()
    }
}
