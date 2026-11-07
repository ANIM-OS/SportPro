package dev.dreamteam.sportpro.ui.players

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.GuardianLink
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.data.repository.PlayerProfileRepository
import dev.dreamteam.sportpro.data.repository.TeamDirectoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlayersUiState(
    val teams: List<Team> = emptyList(),
    val teamsLoading: Boolean = false,
    val selectedTeamId: String? = null,
    val roster: List<TeamMember> = emptyList(),
    val rosterLoading: Boolean = false,
    val linkedPlayers: List<GuardianLink> = emptyList(),
    val linkedLoading: Boolean = false,
    val emailVerified: Boolean = true,
    val verificationMessage: String? = null,
    val error: String? = null
)

/** US-04: jugadores del entrenador (por equipo) y jugadores a cargo del padre o tutor. */
class PlayersViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val teamRepository = TeamDirectoryRepository()
    private val profileRepository = PlayerProfileRepository()
    private val _uiState = MutableStateFlow(PlayersUiState())
    val uiState: StateFlow<PlayersUiState> = _uiState.asStateFlow()

    private var teamsListener: ListenerRegistration? = null
    private var rosterListener: ListenerRegistration? = null
    private var linkedListener: ListenerRegistration? = null
    private var started = false

    fun start(isCoach: Boolean, isParent: Boolean) {
        if (started) return
        started = true
        if (isCoach) {
            _uiState.update { it.copy(teamsLoading = true) }
            teamsListener = teamRepository.listenOwnedTeams(
                onChange = { teams ->
                    _uiState.update { it.copy(teams = teams, teamsLoading = false) }
                    val selected = _uiState.value.selectedTeamId
                    if (teams.none { it.id == selected }) teams.firstOrNull()?.let(::selectTeam)
                },
                onError = { message -> _uiState.update { it.copy(teamsLoading = false, error = message) } }
            )
        }
        if (isParent) {
            val verified = auth.currentUser?.isEmailVerified == true
            _uiState.update { it.copy(emailVerified = verified) }
            if (verified) watchLinkedPlayers()
        }
    }

    fun selectTeam(team: Team) {
        rosterListener?.remove()
        _uiState.update { it.copy(selectedTeamId = team.id, roster = emptyList(), rosterLoading = true) }
        rosterListener = teamRepository.listenRoster(
            teamId = team.id,
            onChange = { members -> _uiState.update { it.copy(roster = members, rosterLoading = false) } },
            onError = { message -> _uiState.update { it.copy(rosterLoading = false, error = message) } }
        )
    }

    private fun watchLinkedPlayers() {
        linkedListener?.remove()
        _uiState.update { it.copy(linkedLoading = true) }
        linkedListener = profileRepository.listenLinkedPlayers(
            onChange = { links -> _uiState.update { it.copy(linkedPlayers = links, linkedLoading = false) } },
            onError = { message -> _uiState.update { it.copy(linkedLoading = false, error = message) } }
        )
    }

    /** Los datos de menores solo se muestran a padres o tutores con el correo verificado. */
    fun sendVerificationEmail() {
        val user = auth.currentUser ?: return
        user.sendEmailVerification()
            .addOnSuccessListener {
                _uiState.update { it.copy(verificationMessage = "Te enviamos un correo de verificación a ${user.email}") }
            }
            .addOnFailureListener { error ->
                _uiState.update { it.copy(verificationMessage = error.localizedMessage ?: "No se pudo enviar el correo") }
            }
    }

    fun refreshVerification() {
        val user = auth.currentUser ?: return
        user.reload().addOnCompleteListener {
            val verified = auth.currentUser?.isEmailVerified == true
            if (!verified) {
                _uiState.update { it.copy(emailVerified = false, verificationMessage = "Tu correo todavía no está verificado") }
                return@addOnCompleteListener
            }
            // El token debe renovarse para que Firestore reconozca el correo verificado.
            auth.currentUser?.getIdToken(true)?.addOnCompleteListener {
                _uiState.update { it.copy(emailVerified = true, verificationMessage = null) }
                watchLinkedPlayers()
            }
        }
    }

    override fun onCleared() {
        teamsListener?.remove()
        rosterListener?.remove()
        linkedListener?.remove()
    }
}
