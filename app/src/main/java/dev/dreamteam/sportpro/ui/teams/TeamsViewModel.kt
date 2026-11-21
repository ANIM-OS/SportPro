package dev.dreamteam.sportpro.ui.teams

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import android.net.Uri
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.Timestamp
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.model.TeamInvite
import dev.dreamteam.sportpro.data.model.TeamJoinCode
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.domain.access.TeamAccess
import java.io.ByteArrayOutputStream
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TeamsUiState(
    val currentUserId: String = "",
    val teams: List<Team> = emptyList(),
    val pendingInvites: List<TeamInvite> = emptyList(),
    val myJoinRequests: List<MyJoinRequest> = emptyList(),
    val myJoinRequestsError: String? = null,
    val emailVerified: Boolean = true,
    val isSendingVerification: Boolean = false,
    val verificationMessage: String? = null,
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val isInviting: Boolean = false,
    val processingInviteId: String? = null,
    val teamsError: String? = null,
    val invitesError: String? = null,
    val createError: String? = null,
    val inviteActionError: String? = null,
    val teamActionError: String? = null,
    val isSavingTeam: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val archivingTeamId: String? = null,
    val selectedTeamId: String? = null,
    val members: List<TeamMember> = emptyList(),
    val membersLoading: Boolean = false,
    val managementError: String? = null,
    val managementBusy: Boolean = false,
    val joinCode: TeamJoinCode? = null,
    val pendingJoinCode: TeamJoinCode? = null,
    val joinRequests: List<TeamJoinRequest> = emptyList(),
    val requestMessage: String? = null
)

data class MyJoinRequest(val id: String, val teamName: String, val category: String, val status: String)

data class TeamJoinRequest(val id: String, val teamId: String, val playerId: String, val playerName: String, val category: String)

data class TeamDetails(
    val name: String,
    val city: String,
    val description: String
)

class TeamsViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val _uiState = MutableStateFlow(TeamsUiState())
    val uiState: StateFlow<TeamsUiState> = _uiState.asStateFlow()

    private var ownedListener: ListenerRegistration? = null
    private var membershipsListener: ListenerRegistration? = null
    private val memberTeamListeners = mutableMapOf<String, ListenerRegistration>()
    private var invitesListener: ListenerRegistration? = null
    private var myRequestsListener: ListenerRegistration? = null
    private var rosterListener: ListenerRegistration? = null
    private var requestsListener: ListenerRegistration? = null
    private var joinCodeListener: ListenerRegistration? = null
    private var managementGeneration = 0
    private var joinCodeGeneration = 0
    private var generation = 0
    private var ownedLoaded = false
    private var membershipsLoaded = false
    private var ownedError: String? = null
    private var membershipsError: String? = null
    private var ownedTeams: List<Team> = emptyList()
    private var memberTeams: Map<String, Team> = emptyMap()
    private var memberTeamIds: Set<String> = emptySet()
    private var failedTeamIds: Set<String> = emptySet()
    private var loadingTeamIds: Set<String> = emptySet()

    init {
        loadTeams()
    }

    fun loadTeams() {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email == null) {
            _uiState.update { it.copy(isLoading = false, teamsError = "Debes iniciar sesión") }
            return
        }

        ownedListener?.remove()
        membershipsListener?.remove()
        memberTeamListeners.values.forEach { it.remove() }
        memberTeamListeners.clear()
        invitesListener?.remove()
        myRequestsListener?.remove()
        val currentGeneration = ++generation
        ownedLoaded = false
        membershipsLoaded = false
        ownedError = null
        membershipsError = null
        ownedTeams = emptyList()
        memberTeams = emptyMap()
        memberTeamIds = emptySet()
        failedTeamIds = emptySet()
        loadingTeamIds = emptySet()
        _uiState.update {
            it.copy(
                currentUserId = user.uid,
                emailVerified = user.isEmailVerified,
                teams = emptyList(),
                pendingInvites = emptyList(),
                myJoinRequests = emptyList(),
                myJoinRequestsError = null,
                isLoading = true,
                teamsError = null,
                invitesError = null
            )
        }

        ownedListener = firestore.collection("teams")
            .whereEqualTo("createdBy", user.uid)
            .addSnapshotListener { snapshot, error ->
                if (currentGeneration != generation) return@addSnapshotListener
                ownedLoaded = true
                ownedError = error?.localizedMessage
                ownedTeams = snapshot?.documents?.mapNotNull(::teamFromDocument) ?: emptyList()
                publishTeams()
            }

        membershipsListener = firestore.collection("teamMemberships")
            .whereEqualTo("userId", user.uid)
            .addSnapshotListener { snapshot, error ->
                if (currentGeneration != generation) return@addSnapshotListener
                membershipsLoaded = true
                membershipsError = error?.localizedMessage
                if (error == null) {
                    memberTeamIds = snapshot?.documents?.mapNotNull { it.getString("teamId") }?.toSet() ?: emptySet()
                    (memberTeamListeners.keys - memberTeamIds).forEach { id ->
                        memberTeamListeners.remove(id)?.remove()
                    }
                    memberTeams = memberTeams.filterKeys { it in memberTeamIds }
                    failedTeamIds = failedTeamIds.filterTo(mutableSetOf()) { it in memberTeamIds }
                    memberTeamIds.filterNot { it in memberTeamListeners || it in loadingTeamIds || it in failedTeamIds }
                        .forEach { loadMemberTeam(it, currentGeneration) }
                }
                publishTeams()
            }

        if (user.isEmailVerified) {
            myRequestsListener = firestore.collection("teamJoinRequests")
                .whereEqualTo("playerId", user.uid)
                .addSnapshotListener { snapshot, error ->
                    if (currentGeneration != generation) return@addSnapshotListener
                    if (error != null) {
                        _uiState.update { it.copy(myJoinRequestsError = "No se pudieron cargar tus solicitudes") }
                    } else {
                        val requests = snapshot?.documents.orEmpty().mapNotNull { doc ->
                            val status = doc.getString("status") ?: return@mapNotNull null
                            MyJoinRequest(doc.id, doc.getString("teamName") ?: "Equipo", doc.getString("category").orEmpty(), status)
                        }
                        _uiState.update { it.copy(myJoinRequests = requests, myJoinRequestsError = null) }
                    }
                }
            invitesListener = firestore.collection("teamInvites")
                .whereEqualTo("invitedEmail", email)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener { snapshot, error ->
                    if (currentGeneration != generation) return@addSnapshotListener
                    if (error != null) {
                        _uiState.update { it.copy(invitesError = error.localizedMessage ?: "No se pudieron cargar las invitaciones") }
                        return@addSnapshotListener
                    }
                    val invites = snapshot?.documents?.mapNotNull { doc ->
                        val teamId = doc.getString("teamId") ?: return@mapNotNull null
                        TeamInvite(
                            id = doc.id,
                            teamId = teamId,
                            teamName = doc.getString("teamName") ?: "Equipo",
                            invitedEmail = doc.getString("invitedEmail") ?: "",
                            invitedBy = doc.getString("invitedBy") ?: "",
                            status = doc.getString("status") ?: "",
                            createdAt = doc.getTimestamp("createdAt"),
                            category = doc.getString("category").orEmpty()
                        )
                    } ?: emptyList()
                    _uiState.update { it.copy(pendingInvites = invites, invitesError = null) }
                }
        }
    }

    private fun loadMemberTeam(teamId: String, currentGeneration: Int) {
        loadingTeamIds = loadingTeamIds + teamId
        memberTeamListeners[teamId] = firestore.collection("teams").document(teamId)
            .addSnapshotListener { doc, error ->
                if (currentGeneration != generation || teamId !in memberTeamIds) return@addSnapshotListener
                loadingTeamIds = loadingTeamIds - teamId
                val team = doc?.let(::teamFromDocument)
                if (error != null || team == null) {
                    memberTeams = memberTeams - teamId
                    failedTeamIds = failedTeamIds + teamId
                } else {
                    failedTeamIds = failedTeamIds - teamId
                    memberTeams = memberTeams + (teamId to team)
                }
                publishTeams()
            }
    }

    private fun publishTeams() {
        val ownedIds = ownedTeams.map { it.id }.toSet()
        val missingIds = memberTeamIds - ownedIds - memberTeams.keys
        val teams = (ownedTeams + memberTeams.values).distinctBy { it.id }.sortedByDescending { it.createdAt }
        _uiState.update {
            it.copy(
                teams = teams,
                isLoading = !ownedLoaded || !membershipsLoaded || missingIds.any { id -> id in loadingTeamIds },
                teamsError = ownedError ?: membershipsError
                    ?: if (missingIds.any { id -> id in failedTeamIds }) "No se pudo cargar uno de tus equipos" else null
            )
        }
    }

    private fun teamFromDocument(doc: DocumentSnapshot): Team? {
        val name = doc.getString("name") ?: return null
        return Team(
            id = doc.id,
            createdBy = doc.getString("createdBy") ?: "",
            name = name,
            type = doc.getString("type") ?: "EQUIPO",
            academyId = doc.getString("academyId"),
            categories = (doc.get("categories") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            city = doc.getString("city") ?: "",
            description = doc.getString("description") ?: "",
            photoBase64 = doc.getString("photoBase64") ?: "",
            photoUpdatedAt = doc.getTimestamp("photoUpdatedAt"),
            archived = doc.getBoolean("archived") == true,
            activeJoinCodeId = doc.getString("activeJoinCodeId"),
            createdAt = doc.getTimestamp("createdAt")
        )
    }

    private fun owns(team: Team): Boolean = TeamAccess.canManage(team, auth.currentUser?.uid)

    fun clearTeamError() {
        _uiState.update { it.copy(teamActionError = null) }
    }

    fun setTeamAcademy(team: Team, academyId: String?) {
        if (!owns(team) || team.type != "EQUIPO") {
            _uiState.update { it.copy(teamActionError = "Solo puedes vincular equipos que administras") }
            return
        }
        if (team.academyId != null && academyId != null && team.academyId != academyId) {
            _uiState.update { it.copy(teamActionError = "Desvincula primero la academia actual") }
            return
        }
        if (academyId != null) {
            val academy = _uiState.value.teams.firstOrNull { it.id == academyId && it.type == "ACADEMIA" }
            if (academy == null || !owns(academy)) {
                _uiState.update { it.copy(teamActionError = "Selecciona una academia que administras") }
                return
            }
        }
        _uiState.update { it.copy(isSavingTeam = true, teamActionError = null) }
        firestore.collection("teams").document(team.id)
            .update("academyId", academyId?.let { it as Any } ?: FieldValue.delete())
            .addOnSuccessListener { _uiState.update { it.copy(isSavingTeam = false) } }
            .addOnFailureListener { error -> _uiState.update { it.copy(isSavingTeam = false,
                teamActionError = error.localizedMessage ?: "No se pudo actualizar la academia del equipo") } }
    }

    fun openTeam(team: Team) {
        requestsListener?.remove()
        rosterListener?.remove()
        joinCodeListener?.remove()
        joinCodeGeneration++
        val currentGeneration = ++managementGeneration
        _uiState.update {
            it.copy(selectedTeamId = team.id, members = emptyList(), membersLoading = true,
                managementError = null, joinCode = null)
        }
        if (owns(team)) {
            requestsListener = firestore.collection("teamJoinRequests")
                .whereEqualTo("teamId", team.id)
                .addSnapshotListener { snapshot, error ->
                    if (currentGeneration != managementGeneration) return@addSnapshotListener
                    if (error != null) {
                        _uiState.update { it.copy(managementError = "No se pudieron cargar las solicitudes") }
                    } else {
                        val requests = snapshot?.documents?.mapNotNull { doc ->
                            if (doc.getString("status") != "PENDING") return@mapNotNull null
                            TeamJoinRequest(doc.id, doc.getString("teamId") ?: return@mapNotNull null,
                                doc.getString("playerId") ?: return@mapNotNull null,
                                doc.getString("playerName") ?: return@mapNotNull null,
                                doc.getString("category") ?: return@mapNotNull null)
                        } ?: emptyList()
                        _uiState.update { it.copy(joinRequests = requests) }
                    }
                }
        }
        rosterListener = firestore.collection("teamMemberships")
            .whereEqualTo("teamId", team.id)
            .addSnapshotListener { snapshot, error ->
                if (currentGeneration != managementGeneration) return@addSnapshotListener
                if (error != null) {
                    _uiState.update { it.copy(membersLoading = false, managementError = error.localizedMessage ?: "No se pudo cargar la plantilla") }
                } else {
                    val members = snapshot?.documents?.mapNotNull { doc ->
                        val uid = doc.getString("userId") ?: return@mapNotNull null
                        TeamMember(uid, doc.getString("playerName") ?: "Jugador ${uid.take(6)}",
                            doc.getString("category") ?: "", doc.getLong("jerseyNumber")?.toInt())
                    }?.sortedWith(compareBy({ it.category }, { it.playerName })) ?: emptyList()
                    _uiState.update { it.copy(members = members, membersLoading = false, managementError = null) }
                }
            }
    }

    fun closeTeam() {
        managementGeneration++
        joinCodeGeneration++
        rosterListener?.remove()
        joinCodeListener?.remove()
        requestsListener?.remove()
        _uiState.update { it.copy(selectedTeamId = null, members = emptyList(), joinRequests = emptyList(),
            joinCode = null, managementError = null) }
    }

    fun updateMember(team: Team, member: TeamMember) {
        if (!owns(team) || member.playerName.isBlank() || member.playerName.length > 80 ||
            member.category !in team.categories || (member.jerseyNumber != null && member.jerseyNumber !in 0..99)) {
            _uiState.update { it.copy(managementError = "Revisa los datos del jugador") }
            return
        }
        _uiState.update { it.copy(managementBusy = true, managementError = null) }
        val save: () -> Unit = {
            firestore.collection("teamMemberships").document("${team.id}_${member.userId}").update(mapOf(
                "playerName" to member.playerName.trim(),
                "category" to member.category,
                "jerseyNumber" to (member.jerseyNumber ?: FieldValue.delete())
            )).addOnSuccessListener { _uiState.update { it.copy(managementBusy = false) } }
                .addOnFailureListener { error -> _uiState.update { it.copy(managementBusy = false, managementError = error.localizedMessage ?: "No se pudo editar al jugador") } }
        }
        val currentCategory = _uiState.value.members.firstOrNull { it.userId == member.userId }?.category
        if (currentCategory != member.category) {
            withNoFutureCallups(team.id, member.userId, save)
        } else save()
    }

    fun removeMember(team: Team, member: TeamMember) {
        if (!owns(team)) return
        _uiState.update { it.copy(managementBusy = true, managementError = null) }
        withNoFutureCallups(team.id, member.userId) {
            firestore.collection("teamMemberships").document("${team.id}_${member.userId}").delete()
                .addOnSuccessListener { _uiState.update { it.copy(managementBusy = false) } }
                .addOnFailureListener { error -> _uiState.update { it.copy(managementBusy = false, managementError = error.localizedMessage ?: "No se pudo retirar al jugador") } }
        }
    }

    private fun withNoFutureCallups(teamId: String, playerId: String, proceed: () -> Unit) {
        val ownerId = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(managementBusy = false, managementError = "La sesión terminó. Inicia sesión de nuevo.") }
            return
        }
        firestore.collection("matchCallupPlayers").whereEqualTo("ownerId", ownerId).get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val now = System.currentTimeMillis()
                val called = snapshot.documents.any { doc ->
                    if (doc.getString("teamId") != teamId || doc.getString("playerId") != playerId || doc.getBoolean("selected") != true) false
                    else {
                        val encounter = doc.get("encounter") as? Map<*, *>
                        val kickoff = encounter?.get("kickoff") as? Timestamp
                        kickoff == null || kickoff.toDate().time > now
                    }
                }
                if (called) {
                    _uiState.update { it.copy(managementBusy = false, managementError =
                        "Primero retira al jugador de sus convocatorias futuras en Equipos → Convocatorias.") }
                } else proceed()
            }.addOnFailureListener { error ->
                _uiState.update { it.copy(managementBusy = false, managementError =
                    error.localizedMessage ?: "No se pudo comprobar las convocatorias del jugador") }
            }
    }

    fun loadOrCreateJoinCode(team: Team) {
        if (!owns(team)) return
        _uiState.update { it.copy(managementBusy = true, managementError = null) }
        firestore.collection("teamJoinCodes")
            .whereEqualTo("teamId", team.id)
            .get()
            .addOnSuccessListener { snapshot ->
                val now = System.currentTimeMillis()
                val validDoc = snapshot.documents.firstOrNull { doc ->
                    val expiresAt = doc.getTimestamp("expiresAt")
                    val usedBy = doc.getString("usedBy")
                    expiresAt != null && expiresAt.toDate().time > now && usedBy == null
                }
                if (validDoc != null) {
                    val code = joinCodeFromDocument(validDoc)
                    _uiState.update { it.copy(managementBusy = false, joinCode = code, managementError = null) }
                } else {
                    createJoinCode(team)
                }
            }
            .addOnFailureListener {
                createJoinCode(team)
            }
    }

    fun createJoinCode(team: Team) {
        if (!owns(team)) return
        _uiState.update { it.copy(managementBusy = true, managementError = null) }
        val codeRef = firestore.collection("teamJoinCodes").document()
        // Firestore validates the expiry against server time, so leave room for clock skew.
        val expiresAt = Timestamp(Date(System.currentTimeMillis() + 55 * 60 * 1000L))
        val codeMap = mapOf(
            "teamId" to team.id,
            "teamName" to team.name,
            "categories" to team.categories,
            "createdBy" to team.createdBy,
            "createdAt" to FieldValue.serverTimestamp(),
            "expiresAt" to expiresAt
        )
        codeRef.set(codeMap)
            .addOnSuccessListener {
                val code = TeamJoinCode(codeRef.id, team.id, team.name, team.categories, expiresAt)
                _uiState.update { it.copy(managementBusy = false, joinCode = code, managementError = null) }
            }
            .addOnFailureListener { error ->
                _uiState.update { it.copy(managementBusy = false, managementError = error.localizedMessage ?: "No se pudo generar el QR") }
            }
    }

    private fun joinCodeFromDocument(doc: DocumentSnapshot): TeamJoinCode? {
        val teamId = doc.getString("teamId") ?: return null
        val teamName = doc.getString("teamName") ?: return null
        val expiresAt = doc.getTimestamp("expiresAt") ?: return null
        val categories = (doc.get("categories") as? List<*>)?.filterIsInstance<String>() ?: return null
        return TeamJoinCode(doc.id, teamId, teamName, categories, expiresAt)
    }

    fun clearJoinCode() { _uiState.update { it.copy(joinCode = null) } }

    fun loadJoinCode(rawValue: String) {
        val codeId = rawValue.removePrefix("sportpro://join/")
        if (codeId == rawValue || !codeId.matches(Regex("[A-Za-z0-9]{20}"))) {
            _uiState.update { it.copy(managementError = "Este QR no es una invitación de SportPro") }
            return
        }
        if (auth.currentUser?.isEmailVerified != true) {
            _uiState.update { it.copy(managementError = "Verifica tu correo antes de unirte con QR") }
            return
        }
        _uiState.update { it.copy(managementBusy = true, managementError = null) }
        firestore.collection("teamJoinCodes").document(codeId).get()
            .addOnSuccessListener { doc ->
                val teamId = doc.getString("teamId")
                val teamName = doc.getString("teamName")
                val expiresAt = doc.getTimestamp("expiresAt")
                if (!doc.exists() || teamId == null || teamName == null || expiresAt == null ||
                    doc.contains("usedBy") || expiresAt.toDate().time <= System.currentTimeMillis()) {
                    _uiState.update { it.copy(managementBusy = false, managementError = "El QR ya se usó o venció") }
                } else if (_uiState.value.teams.any { it.id == teamId }) {
                    _uiState.update { it.copy(managementBusy = false, managementError = "Ya perteneces a este equipo") }
                } else {
                    val categories = (doc.get("categories") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    _uiState.update { it.copy(managementBusy = false,
                        pendingJoinCode = TeamJoinCode(codeId, teamId, teamName, categories, expiresAt)) }
                }
            }.addOnFailureListener { error ->
                _uiState.update { it.copy(managementBusy = false, managementError = "El QR no es válido, ya se usó o venció") }
            }
    }

    fun clearPendingJoinCode() { _uiState.update { it.copy(pendingJoinCode = null) } }

    fun showManagementError(message: String) { _uiState.update { it.copy(managementError = message) } }

    fun joinWithCode(code: TeamJoinCode, playerName: String, category: String) {
        val user = auth.currentUser ?: return
        if (!user.isEmailVerified || playerName.isBlank() || playerName.length > 80 || category !in code.categories) {
            _uiState.update { it.copy(managementError = "Ingresa tu nombre y selecciona una categoría") }
            return
        }
        _uiState.update { it.copy(managementBusy = true, managementError = null) }
        firestore.collection("teamJoinRequests").document("${code.teamId}_${user.uid}").set(mapOf(
            "teamId" to code.teamId, "teamName" to code.teamName, "playerId" to user.uid, "codeId" to code.id,
            "playerName" to playerName.trim(), "category" to category,
            "status" to "PENDING", "createdAt" to FieldValue.serverTimestamp()
        )).addOnSuccessListener {
            _uiState.update { it.copy(managementBusy = false, pendingJoinCode = null,
                requestMessage = "Solicitud enviada. El entrenador debe aprobarla.") }
        }.addOnFailureListener { error ->
            _uiState.update { it.copy(managementBusy = false, managementError = error.localizedMessage ?: "No se pudo enviar la solicitud") }
        }
    }

    fun respondToJoinRequest(team: Team, request: TeamJoinRequest, approve: Boolean) {
        if (!owns(team) || request.teamId != team.id || _uiState.value.managementBusy) return
        _uiState.update { it.copy(managementBusy = true, managementError = null) }
        val batch = firestore.batch()
        val requestRef = firestore.collection("teamJoinRequests").document(request.id)
        batch.update(requestRef, mapOf("status" to if (approve) "APPROVED" else "DECLINED",
            "resolvedAt" to FieldValue.serverTimestamp()))
        if (approve) batch.set(firestore.collection("teamMemberships").document("${team.id}_${request.playerId}"), mapOf(
            "teamId" to team.id, "userId" to request.playerId, "requestId" to request.id,
            "playerName" to request.playerName, "category" to request.category,
            "acceptedAt" to FieldValue.serverTimestamp()))
        batch.commit().addOnSuccessListener { _uiState.update { it.copy(managementBusy = false) } }
            .addOnFailureListener { error -> _uiState.update { it.copy(managementBusy = false,
                managementError = error.localizedMessage ?: "No se pudo resolver la solicitud") } }
    }

    fun saveTeamDetails(team: Team, details: TeamDetails, onSuccess: () -> Unit) {
        if (!owns(team)) {
            _uiState.update { it.copy(teamActionError = "Solo el propietario puede editar el equipo") }
            return
        }
        if (details.name.trim().isEmpty() || details.name.length > 80 ||
            details.city.length > 80 || details.description.length > 500) {
            _uiState.update { it.copy(teamActionError = "Revisa los datos del equipo") }
            return
        }
        _uiState.update { it.copy(isSavingTeam = true, teamActionError = null) }
        val data = mutableMapOf<String, Any>(
            "name" to details.name.trim(),
            "city" to details.city.trim(),
            "description" to details.description.trim()
        )
        val ref = firestore.collection("teams").document(team.id)
        ref.update(data).addOnSuccessListener {
            _uiState.update { it.copy(isSavingTeam = false) }
            onSuccess()
        }.addOnFailureListener { error ->
            _uiState.update { it.copy(isSavingTeam = false, teamActionError = error.localizedMessage ?: "No se pudo guardar el equipo") }
        }
    }

    fun uploadTeamPhoto(context: Context, team: Team, uri: Uri) {
        if (!owns(team)) {
            _uiState.update { it.copy(teamActionError = "Solo el propietario puede cambiar la foto") }
            return
        }
        _uiState.update { it.copy(isUploadingPhoto = true, teamActionError = null) }
        val photoBase64 = try {
            compactTeamPhoto(context, uri)
        } catch (_: Exception) {
            _uiState.update { it.copy(isUploadingPhoto = false, teamActionError = "No se pudo procesar la imagen. Elige otra foto.") }
            return
        }
        firestore.collection("teams").document(team.id)
            .update(mapOf("photoBase64" to photoBase64, "photoUpdatedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                _uiState.update { it.copy(isUploadingPhoto = false) }
            }
            .addOnFailureListener { error ->
                _uiState.update { it.copy(isUploadingPhoto = false, teamActionError = error.localizedMessage ?: "No se pudo guardar la foto") }
            }
    }

    fun setTeamArchived(team: Team, archived: Boolean, onSuccess: () -> Unit) {
        if (!owns(team) || _uiState.value.archivingTeamId != null) return
        _uiState.update { it.copy(archivingTeamId = team.id, teamActionError = null) }
        firestore.collection("teams").document(team.id).update("archived", archived)
            .addOnSuccessListener {
                _uiState.update { it.copy(archivingTeamId = null) }
                onSuccess()
            }
            .addOnFailureListener {
                _uiState.update { state -> state.copy(archivingTeamId = null, teamActionError = "No se pudo actualizar el estado del equipo.") }
            }
    }

    fun leaveTeam(team: Team, onSuccess: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        if (owns(team) || _uiState.value.managementBusy) return
        _uiState.update { it.copy(managementBusy = true, managementError = null) }
        firestore.collection("teamMemberships").document("${team.id}_$uid").delete()
            .addOnSuccessListener {
                _uiState.update { it.copy(managementBusy = false) }
                onSuccess()
            }
            .addOnFailureListener {
                _uiState.update { state -> state.copy(managementBusy = false, managementError = "No se pudo salir del equipo.") }
            }
    }

    fun createTeam(context: Context, name: String, type: String, categories: List<String>, photoUri: Uri? = null, onSuccess: (String) -> Unit, details: TeamDetails = TeamDetails(name, "", ""), academyId: String? = null) {
        if (name.isBlank() || categories.isEmpty() || type !in setOf("EQUIPO", "ACADEMIA")) {
            _uiState.update { it.copy(createError = "Ingresa un nombre y selecciona al menos una categoría") }
            return
        }
        if (name.length > 80 || details.city.length > 80 || details.description.length > 500) {
            _uiState.update { it.copy(createError = "Revisa los datos del equipo") }
            return
        }
        val uid = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(createError = "Debes iniciar sesión") }
            return
        }
        _uiState.update { it.copy(isCreating = true, createError = null) }

        val photoBase64 = if (photoUri == null) "" else try {
            compactTeamPhoto(context, photoUri)
        } catch (_: Exception) {
            _uiState.update { it.copy(isCreating = false, createError = "No se pudo procesar la imagen. Elige otra foto.") }
            return
        }

        val teamData = mutableMapOf<String, Any>(
            "createdBy" to uid,
            "name" to name.trim(),
            "type" to type,
            "categories" to categories.distinct(),
            "city" to details.city.trim(),
            "description" to details.description.trim(),
            "photoBase64" to photoBase64,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (photoBase64.isNotEmpty()) teamData["photoUpdatedAt"] = FieldValue.serverTimestamp()
        if (type == "EQUIPO" && academyId != null) {
            val academy = _uiState.value.teams.firstOrNull { it.id == academyId && it.type == "ACADEMIA" }
            if (academy == null || !owns(academy)) {
                _uiState.update { it.copy(isCreating = false, createError = "Selecciona una academia que administres") }
                return
            }
            teamData["academyId"] = academy.id
        } else if (type == "ACADEMIA" && academyId != null) {
            _uiState.update { it.copy(isCreating = false, createError = "Una academia no puede depender de otra academia") }
            return
        }
        val teamRef = firestore.collection("teams").document()
        teamRef.set(teamData).addOnSuccessListener {
            _uiState.update { it.copy(isCreating = false) }
            onSuccess(teamRef.id)
        }.addOnFailureListener(::reportCreateError)
    }

    private fun compactTeamPhoto(context: Context, uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Imagen no válida")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalArgumentException("Imagen no válida")

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 768) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("Imagen no válida")
        val scale = minOf(1f, 384f / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1), true
        ) else bitmap
        return try {
            val output = ByteArrayOutputStream()
            if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IllegalArgumentException("Imagen no válida")
            }
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
    }

    private fun reportCreateError(error: Exception) {
        val message = if ((error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
            "Firestore rechazó los datos del equipo. Comprueba las reglas publicadas."
        else error.localizedMessage ?: "No se pudo crear el equipo"
        _uiState.update { it.copy(isCreating = false, createError = message) }
    }

    fun invitePlayer(team: Team, email: String, category: String, onSuccess: () -> Unit) {
        val user = auth.currentUser
        val invitedEmail = email.trim()
        if (user == null || !TeamAccess.canManage(team, user.uid)) {
            _uiState.update { it.copy(inviteActionError = "Solo el entrenador creador puede invitar jugadores") }
            return
        }
        if (!invitedEmail.contains('@') || invitedEmail.equals(user.email, ignoreCase = true)) {
            _uiState.update { it.copy(inviteActionError = "Ingresa el correo de otro usuario") }
            return
        }
        if (team.type != "EQUIPO" || category !in team.categories) {
            _uiState.update { it.copy(inviteActionError = "Selecciona una categoría del equipo") }
            return
        }
        _uiState.update { it.copy(isInviting = true, inviteActionError = null) }
        firestore.collection("teamInvites").add(mapOf(
            "teamId" to team.id,
            "teamName" to team.name,
            "invitedEmail" to invitedEmail,
            "category" to category,
            "invitedBy" to user.uid,
            "status" to "PENDING",
            "createdAt" to FieldValue.serverTimestamp()
        )).addOnSuccessListener {
            _uiState.update { it.copy(isInviting = false) }
            onSuccess()
        }.addOnFailureListener { error ->
            _uiState.update { it.copy(isInviting = false, inviteActionError = error.localizedMessage ?: "No se pudo enviar la invitación") }
        }
    }

    fun acceptInvite(invite: TeamInvite) {
        val user = auth.currentUser ?: return
        if (!user.isEmailVerified || invite.invitedEmail != user.email || _uiState.value.processingInviteId != null) return
        if (invite.category.isBlank()) {
            _uiState.update { it.copy(inviteActionError = "Pide al entrenador que reenvíe la invitación con una categoría.") }
            return
        }
        _uiState.update { it.copy(processingInviteId = invite.id, inviteActionError = null) }
        val memberRef = firestore.collection("teamMemberships").document("${invite.teamId}_${user.uid}")
        val inviteRef = firestore.collection("teamInvites").document(invite.id)
        val batch = firestore.batch()
        batch.set(memberRef, mapOf(
            "teamId" to invite.teamId,
            "userId" to user.uid,
            "inviteId" to invite.id,
            "category" to invite.category,
            "playerName" to ((user.displayName?.takeIf(String::isNotBlank) ?: "Jugador ${user.uid.take(6)}").take(80)),
            "acceptedAt" to FieldValue.serverTimestamp()
        ))
        batch.update(inviteRef, "status", "ACCEPTED")
        batch.commit().addOnSuccessListener {
            _uiState.update { it.copy(processingInviteId = null) }
            loadTeams()
        }.addOnFailureListener { error ->
            _uiState.update { it.copy(processingInviteId = null, inviteActionError = error.localizedMessage ?: "No se pudo aceptar la invitación") }
        }
    }

    fun declineInvite(invite: TeamInvite) {
        val user = auth.currentUser ?: return
        if (!user.isEmailVerified || invite.invitedEmail != user.email || _uiState.value.processingInviteId != null) return
        _uiState.update { it.copy(processingInviteId = invite.id, inviteActionError = null) }
        firestore.collection("teamInvites").document(invite.id)
            .update("status", "DECLINED")
            .addOnSuccessListener { _uiState.update { it.copy(processingInviteId = null) } }
            .addOnFailureListener { error ->
                _uiState.update { it.copy(processingInviteId = null, inviteActionError = error.localizedMessage ?: "No se pudo rechazar la invitación") }
            }
    }

    fun clearInviteError() {
        _uiState.update { it.copy(inviteActionError = null) }
    }

    fun sendVerificationEmail() {
        val user = auth.currentUser ?: return
        _uiState.update { it.copy(isSendingVerification = true, verificationMessage = null) }
        user.sendEmailVerification()
            .addOnSuccessListener {
                _uiState.update { it.copy(isSendingVerification = false, verificationMessage = "Revisa tu correo y abre el enlace de verificación") }
            }
            .addOnFailureListener { error ->
                _uiState.update { it.copy(isSendingVerification = false, verificationMessage = error.localizedMessage ?: "No se pudo enviar el correo") }
            }
    }

    fun refreshEmailVerification() {
        val user = auth.currentUser ?: return
        user.reload().addOnSuccessListener {
            user.getIdToken(true).addOnSuccessListener {
                if (user.isEmailVerified) loadTeams()
                else _uiState.update { it.copy(verificationMessage = "El correo aún no está verificado") }
            }.addOnFailureListener { error ->
                _uiState.update { it.copy(verificationMessage = error.localizedMessage ?: "No se pudo actualizar la sesión") }
            }
        }.addOnFailureListener { error ->
            _uiState.update { it.copy(verificationMessage = error.localizedMessage ?: "No se pudo comprobar el correo") }
        }
    }

    override fun onCleared() {
        generation++
        managementGeneration++
        ownedListener?.remove()
        membershipsListener?.remove()
        memberTeamListeners.values.forEach { it.remove() }
        memberTeamListeners.clear()
        invitesListener?.remove()
        myRequestsListener?.remove()
        rosterListener?.remove()
        requestsListener?.remove()
        joinCodeListener?.remove()
        super.onCleared()
    }
}
