package dev.dreamteam.sportpro.ui.teams

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AcademyInvitation(val id: String, val academyId: String, val teamId: String,
    val academyOwnerId: String, val coachId: String, val status: String, val academyName: String)
data class AcademyLinksState(val invitations: List<AcademyInvitation> = emptyList(),
    val busy: Boolean = false, val message: String? = null, val noticePermissions: Set<String> = emptySet())

class AcademyLinksViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val state = MutableStateFlow(AcademyLinksState())
    val uiState = state.asStateFlow()
    private val listeners = mutableListOf<ListenerRegistration>()
    private val groups = mutableMapOf<String, List<AcademyInvitation>>()
    private var responsibilitiesListener: ListenerRegistration? = null
    private var responsibilityGeneration = 0
    fun watchResponsibilities(academyId: String) {
        responsibilitiesListener?.remove()
        val current = ++responsibilityGeneration
        state.update { it.copy(noticePermissions = emptySet()) }
        if (academyId.isBlank()) return
        responsibilitiesListener = db.collection("academyResponsibilities").whereEqualTo("academyId", academyId)
            .addSnapshotListener { snapshot, error ->
                if (current != responsibilityGeneration) return@addSnapshotListener
                if (error != null) { fail(error); return@addSnapshotListener }
                val ids = snapshot?.documents.orEmpty().filter { it.getBoolean("active") == true }
                    .mapNotNull { it.getString("teamId") }.toSet()
                state.update { it.copy(noticePermissions = ids) }
            }
    }
    fun setNoticeResponsibility(academyId: String, teamId: String, active: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        state.update { it.copy(busy = true, message = null) }
        db.collection("teams").document(teamId).get().addOnSuccessListener { team ->
            val coachId = team.getString("createdBy")
            if (coachId == null || team.getString("academyId") != academyId) {
                state.update { it.copy(busy = false, message = "El equipo ya no pertenece a esta academia.") }
                return@addOnSuccessListener
            }
            db.collection("academyResponsibilities").document(academyId + "_" + teamId).set(mapOf(
                "academyId" to academyId, "teamId" to teamId, "coachId" to coachId,
                "permissions" to listOf("READ_NOTICES"), "active" to active,
                "linkId" to team.getString("academyInviteId").orEmpty(),
                "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid
            )).addOnSuccessListener { state.update { it.copy(busy = false, message = "Permiso actualizado.") } }
                .addOnFailureListener(::fail)
        }.addOnFailureListener(::fail)
    }
    fun unlinkTeam(teamId: String) {
        state.update { it.copy(busy = true, message = null) }
        db.collection("teams").document(teamId).update(mapOf(
            "academyId" to FieldValue.delete(), "academyInviteId" to FieldValue.delete()
        )).addOnSuccessListener { state.update { it.copy(busy = false, message = "Equipo desvinculado.") } }
            .addOnFailureListener(::fail)
    }

    init {
        auth.currentUser?.uid?.let { uid ->
            listOf("academyOwnerId", "coachId").forEach { field ->
                listeners += db.collection("academyInvites").whereEqualTo(field, uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) { fail(error); return@addSnapshotListener }
                        groups[field] = snapshot?.documents.orEmpty().mapNotNull { doc ->
                            AcademyInvitation(doc.id, doc.getString("academyId") ?: return@mapNotNull null,
                                doc.getString("teamId") ?: return@mapNotNull null,
                                doc.getString("academyOwnerId") ?: return@mapNotNull null,
                                doc.getString("coachId") ?: return@mapNotNull null, doc.getString("status").orEmpty(), doc.getString("academyName").orEmpty())
                        }
                        state.update { it.copy(invitations = groups.values.flatten().distinctBy { invitation -> invitation.id }) }
                    }
            }
        }
    }

    private fun fail(error: Exception) {
        state.update { it.copy(busy = false, message = error.localizedMessage ?: "No se pudo completar la operación") }
    }

    fun propose(academyId: String, academyName: String, code: String) {
        val uid = auth.currentUser?.uid ?: return
        val parts = code.trim().removePrefix("sportpro://team/").split("/")
        if (!code.trim().startsWith("sportpro://team/") || parts.size != 2 ||
            parts.any { it.isBlank() || it.length > 128 }) {
            state.update { it.copy(message = "Pega el código compartido por el entrenador del equipo.") }
            return
        }
        state.update { it.copy(busy = true, message = null) }
        db.collection("academyInvites").document().set(mapOf(
            "academyId" to academyId, "academyName" to academyName, "teamId" to parts[0], "coachId" to parts[1],
            "academyOwnerId" to uid, "status" to "PENDING",
            "createdAt" to FieldValue.serverTimestamp(),
            "expiresAt" to Timestamp(Date(System.currentTimeMillis() + 6 * 24 * 60 * 60 * 1000L))
        )).addOnSuccessListener { state.update { it.copy(busy = false, message = "Propuesta enviada al entrenador.") } }
            .addOnFailureListener(::fail)
    }

    fun respond(invitation: AcademyInvitation, status: String) {
        state.update { it.copy(busy = true, message = null) }
        val batch = db.batch()
        batch.update(db.collection("academyInvites").document(invitation.id),
            mapOf("status" to status, "resolvedAt" to FieldValue.serverTimestamp()))
        if (status == "ACCEPTED") batch.update(db.collection("teams").document(invitation.teamId),
            mapOf("academyId" to invitation.academyId, "academyInviteId" to invitation.id))
        batch.commit().addOnSuccessListener { state.update { it.copy(busy = false, message = "Propuesta actualizada.") } }
            .addOnFailureListener(::fail)
    }

    override fun onCleared() {
        listeners.forEach { it.remove() }
        responsibilityGeneration++
        responsibilitiesListener?.remove()
        super.onCleared()
    }
}

@Composable
fun AcademyLinksScreen(onBack: () -> Unit, links: AcademyLinksViewModel = viewModel(),
                       teams: TeamsViewModel = viewModel(), academyTeams: FeesViewModel = viewModel()) {
    val state by links.uiState.collectAsState()
    val teamState by teams.uiState.collectAsState()
    val academyTeamState by academyTeams.uiState.collectAsState()
    val context = LocalContext.current
    var academyId by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(academyId) {
        academyTeams.watchAcademyTeams(academyId)
        links.watchResponsibilities(academyId)
    }
    val owned = teamState.teams.filter { it.createdBy == teamState.currentUserId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Volver") }
        Text("Vínculos con academias", style = MaterialTheme.typography.headlineSmall)
        state.message?.let { Text(it) }
        teamState.teamsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        owned.filter { it.type == "EQUIPO" }.forEach { team ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(team.name, style = MaterialTheme.typography.titleMedium)
                    Text("Comparte el código con el responsable de tu academia para recibir su propuesta.")
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Equipo", "sportpro://team/${team.id}/${team.createdBy}"))
                        android.widget.Toast.makeText(context, "Código del equipo copiado", android.widget.Toast.LENGTH_SHORT).show()
                    }) { Text("Copiar código del equipo") }
                    if (team.academyId != null) TextButton(enabled = !state.busy, onClick = { links.unlinkTeam(team.id) }) { Text("Desvincular academia") }
                }
            }
        }
        val academies = owned.filter { it.type == "ACADEMIA" }
        if (academies.isNotEmpty()) {
            Text("Proponer vínculo", style = MaterialTheme.typography.titleMedium)
            academies.forEach { academy ->
                FilterChip(selected = academyId == academy.id, onClick = { academyId = academy.id }, label = { Text(academy.name) })
            }
            academyTeamState.academyTeams.forEach { team ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(team.name, style = MaterialTheme.typography.titleMedium)
                        val granted = team.id in state.noticePermissions
                        Text(if (granted) "Entrenador autorizado para leer avisos de academia" else "Lectura de avisos de academia sin conceder")
                        TextButton(enabled = !state.busy, onClick = { links.setNoticeResponsibility(academyId, team.id, !granted) }) {
                            Text(if (granted) "Revocar lectura de avisos" else "Autorizar lectura de avisos")
                        }
                        TextButton(enabled = !state.busy, onClick = { links.unlinkTeam(team.id) }) { Text("Desvincular equipo") }
                    }
                }
            }
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Código compartido por el entrenador") }, modifier = Modifier.fillMaxWidth())
            Button(enabled = academyId.isNotBlank() && code.isNotBlank() && !state.busy,
                onClick = { academies.firstOrNull { it.id == academyId }?.let { links.propose(it.id, it.name, code) } }) { Text("Enviar propuesta") }
        }
        Text("Propuestas pendientes", style = MaterialTheme.typography.titleMedium)
        val pending = state.invitations.filter { it.status == "PENDING" }
        if (pending.isEmpty()) Text("No hay propuestas pendientes.")
        pending.forEach { invitation ->
            val teamName = teamState.teams.firstOrNull { it.id == invitation.teamId }?.name ?: "Equipo invitado"
            val academyName = teamState.teams.firstOrNull { it.id == invitation.academyId }?.name ?: invitation.academyName.ifBlank { "Academia · ${invitation.academyId}" }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("$teamName · $academyName")
                    if (invitation.coachId == teamState.currentUserId) {
                        TextButton(enabled = !state.busy, onClick = { links.respond(invitation, "ACCEPTED") }) { Text("Aceptar vínculo") }
                        TextButton(enabled = !state.busy, onClick = { links.respond(invitation, "DECLINED") }) { Text("Rechazar") }
                    }
                    if (invitation.academyOwnerId == teamState.currentUserId) {
                        TextButton(enabled = !state.busy, onClick = { links.respond(invitation, "CANCELLED") }) { Text("Cancelar propuesta") }
                    }
                }
            }
        }
    }
}
