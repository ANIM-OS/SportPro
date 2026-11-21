package dev.dreamteam.sportpro.ui.teams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import org.json.JSONObject
import org.json.JSONArray
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NoticeRow(val id: String, val title: String, val content: String)
data class GuardianNoticeTeam(val id: String, val label: String, val academyId: String? = null)
data class NoticesState(val guardianTeams: List<GuardianNoticeTeam> = emptyList(), val rows: List<NoticeRow> = emptyList(), val busy: Boolean = false, val message: String? = null, val canRetry: Boolean = false)

class NoticesViewModel(private val saved: SavedStateHandle) : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val state = MutableStateFlow(NoticesState())
    val uiState = state.asStateFlow()
    private var listener: ListenerRegistration? = null
    private var generation = 0
    private var guardianMemberships: ListenerRegistration? = null
    private var guardianGeneration = 0
    private var guardianTeamIds = emptySet<String>()
    private val guardianTeamListeners = mutableMapOf<String, ListenerRegistration>()
    fun watchGuardianTeams(playerId: String) {
        guardianMemberships?.remove()
        guardianTeamListeners.values.forEach { it.remove() }
        guardianTeamListeners.clear()
        listener?.remove()
        generation++
        val current = ++guardianGeneration
        guardianTeamIds = emptySet()
        state.update { it.copy(rows = emptyList(), guardianTeams = emptyList()) }
        if (playerId.isBlank()) return
        guardianMemberships = db.collection("teamMemberships").whereEqualTo("userId", playerId)
            .addSnapshotListener { snapshot, error ->
                if (current != guardianGeneration) return@addSnapshotListener
                if (error != null) {
                    listener?.remove()
                    listener = null
                    generation++
                    guardianTeamListeners.values.forEach { it.remove() }
                    guardianTeamListeners.clear()
                    state.update { it.copy(rows = emptyList(), guardianTeams = emptyList(), message = "No se pudieron cargar los equipos vinculados.") }
                    return@addSnapshotListener
                }
                val ids = snapshot?.documents.orEmpty().mapNotNull { it.getString("teamId") }.toSet()
                val teamsChanged = ids != guardianTeamIds
                if (teamsChanged) {
                    listener?.remove()
                    listener = null
                    generation++
                }
                guardianTeamIds = ids
                (guardianTeamListeners.keys - ids).forEach { guardianTeamListeners.remove(it)?.remove() }
                state.update { it.copy(guardianTeams = it.guardianTeams.filter { team -> team.id in ids }, rows = if (teamsChanged) emptyList() else it.rows) }
                ids.filterNot { it in guardianTeamListeners }.forEach { teamId ->
                    db.collection("guardianTeamAccess").document(teamId + "_" + uid).set(mapOf(
                        "teamId" to teamId, "playerId" to playerId, "guardianId" to uid
                    )).addOnSuccessListener {
                        if (current != guardianGeneration || teamId !in guardianTeamIds) return@addOnSuccessListener
                        guardianTeamListeners[teamId]?.remove()
                        guardianTeamListeners[teamId] = db.collection("teams").document(teamId)
                            .addSnapshotListener teamListener@{ team, teamError ->
                                if (current != guardianGeneration || teamId !in guardianTeamIds) return@teamListener
                                val context = if (teamError == null && team?.exists() == true)
                                    GuardianNoticeTeam(teamId, team.getString("name") ?: "Equipo", team.getString("academyId"))
                                    else null
                                val previousTeam = state.value.guardianTeams.firstOrNull { it.id == teamId }
                                if (previousTeam != null && previousTeam != context) {
                                    listener?.remove()
                                    listener = null
                                    generation++
                                }
                                state.update { previous ->
                                    val remaining = previous.guardianTeams.filterNot { it.id == teamId }
                                    previous.copy(guardianTeams = if (context == null) remaining else remaining + context,
                                        rows = if (context == null || (previousTeam != null && previousTeam != context)) emptyList() else previous.rows)
                                }
                            }
                    }.addOnFailureListener {
                        if (current == guardianGeneration) state.update { it.copy(message = "No se pudo cargar la información del equipo.") }
                    }
                }
            }
    }
    init { state.update { it.copy(canRetry = saved.get<String>("noticeJob") != null) } }

    fun watchAuthored() {
        observe(db.collection("internalNotices").whereEqualTo("authorId", uid))
    }
    private fun observe(query: com.google.firebase.firestore.Query) {
        listener?.remove()
        val current = ++generation
        state.update { it.copy(rows = emptyList(), message = null) }
        listener = query.addSnapshotListener { snapshot, error ->
            if (generation != current) return@addSnapshotListener
            if (error != null) {
                state.update { it.copy(rows = emptyList(), message = "No se pudieron consultar los avisos.") }
            } else {
                val rows = snapshot?.documents.orEmpty().sortedByDescending { it.getTimestamp("createdAt") }
                    .map { NoticeRow(it.id, it.getString("title").orEmpty(), it.getString("content").orEmpty()) }
                state.update { it.copy(rows = rows.distinctBy { row -> row.id.substringBefore("_") }) }
            }
        }
    }
    fun watchTeam(teamId: String, playerId: String, audience: String, academyId: String?) {
        var query = db.collection("internalNotices").whereEqualTo("teamId", teamId)
            .whereEqualTo("scope", if (academyId == null) "EQUIPO" else "ACADEMIA")
            .whereArrayContains("audience", audience)
        if (audience != "ENTRENADOR") query = query.whereEqualTo("playerId", playerId)
        if (academyId != null) query = query.whereEqualTo("academyId", academyId)
        observe(query)
    }
    fun publish(teamId: String, academyId: String?, players: List<String>, title: String, content: String, audience: Set<String>) {
        publishRecipients(players.distinct().map { teamId to it }, academyId, title, content, audience)
    }
    fun publishAcademy(academyId: String, teamIds: List<String>, title: String, content: String, audience: Set<String>) {
        if (state.value.busy || state.value.canRetry) return
        state.update { it.copy(busy = true, message = null) }
        val recipients = mutableListOf<Pair<String, String>>()
        fun load(index: Int) {
            if (index == teamIds.size) {
                state.update { it.copy(busy = false) }
                publishRecipients(recipients, academyId, title, content, audience)
                return
            }
            val teamId = teamIds[index]
            db.collection("teamMemberships").whereEqualTo("teamId", teamId).get()
                .addOnSuccessListener { snapshot ->
                    snapshot.documents.mapNotNull { it.getString("userId") }.forEach { recipients += teamId to it }
                    load(index + 1)
                }.addOnFailureListener {
                    state.update { it.copy(busy = false, message = "No se pudo cargar la plantilla completa. Intenta nuevamente.") }
                }
        }
        load(0)
    }
    private fun publishRecipients(recipients: List<Pair<String, String>>, academyId: String?, title: String, content: String, audience: Set<String>) {
        if (state.value.busy || state.value.canRetry) return
        if (title.isBlank() || title.length > 100 || content.isBlank() || content.length > 2000 || audience.isEmpty() || recipients.isEmpty()) {
            state.update { it.copy(message = "Completa el aviso, selecciona destinatarios y comprueba la plantilla.") }
            return
        }
        val targets = JSONArray()
        recipients.distinct().forEach { (teamId, playerId) -> targets.put(JSONObject().put("teamId", teamId).put("playerId", playerId)) }
        val job = JSONObject().put("operationId", db.collection("internalNotices").document().id)
            .put("ownerId", uid).put("academyId", academyId.orEmpty()).put("title", title.trim())
            .put("content", content.trim()).put("audience", JSONArray(audience.toList())).put("targets", targets).put("next", 0)
        saved["noticeJob"] = job.toString()
        retryPublish()
    }
    fun retryPublish() {
        if (state.value.busy) return
        val raw = saved.get<String>("noticeJob") ?: return
        val job = JSONObject(raw)
        if (job.getString("ownerId") != uid) return
        state.update { it.copy(busy = true, canRetry = false, message = null) }
        val targets = job.getJSONArray("targets")
        fun send() {
            val index = job.getInt("next")
            if (index == targets.length()) {
                saved.remove<String>("noticeJob")
                state.update { it.copy(busy = false, canRetry = false, message = "Aviso enviado.") }
                return
            }
            val target = targets.getJSONObject(index)
            val teamId = target.getString("teamId")
            val playerId = target.getString("playerId")
            val ref = db.collection("internalNotices").document(job.getString("operationId") + "_" + teamId + "_" + playerId)
            db.runTransaction { transaction ->
                if (!transaction.get(ref).exists()) {
                    val groups = job.getJSONArray("audience")
                    transaction.set(ref, mapOf("teamId" to teamId, "playerId" to playerId, "authorId" to uid,
                        "scope" to if (job.getString("academyId").isEmpty()) "EQUIPO" else "ACADEMIA",
                        "academyId" to job.getString("academyId"), "title" to job.getString("title"),
                        "content" to job.getString("content"), "audience" to (0 until groups.length()).map { groups.getString(it) },
                        "createdAt" to FieldValue.serverTimestamp()))
                }
            }.addOnSuccessListener {
                job.put("next", index + 1)
                saved["noticeJob"] = job.toString()
                send()
            }.addOnFailureListener {
                state.update { it.copy(busy = false, canRetry = true,
                    message = "Se completaron $index de ${targets.length()} entregas. Puedes reintentar el resto.") }
            }
        }
        send()
    }
    override fun onCleared() {
        listener?.remove()
        guardianMemberships?.remove()
        guardianGeneration++
        guardianTeamListeners.values.forEach { it.remove() }
        guardianTeamListeners.clear()
        generation++
        super.onCleared()
    }
}

@Composable
fun NoticesScreen(
    isPlayer: Boolean,
    isCoach: Boolean,
    isGuardian: Boolean,
    coachApproved: Boolean,
    onBack: () -> Unit,
    notices: NoticesViewModel = viewModel(),
    teams: TeamsViewModel = viewModel(),
    academies: FeesViewModel = viewModel(),
    guardians: GuardianViewModel = viewModel()
) {
    LaunchedEffect(isGuardian) { guardians.loadLinks(isPlayer = false, isGuardian = isGuardian) }
    val state by notices.uiState.collectAsState()
    val teamState by teams.uiState.collectAsState()
    val academyState by academies.uiState.collectAsState()
    val guardianState by guardians.uiState.collectAsState()
    var guardianPlayerId by rememberSaveable { mutableStateOf("") }
    val linkedPlayers = guardianState.links.filter { isGuardian && it.guardianId == guardianState.uid }
    val activeGuardianPlayerId = guardianPlayerId.takeIf { id -> linkedPlayers.any { it.playerId == id } }.orEmpty()
    LaunchedEffect(activeGuardianPlayerId) { notices.watchGuardianTeams(activeGuardianPlayerId) }
    var selectedTeamId by rememberSaveable { mutableStateOf("") }
    var academyId by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var audience by rememberSaveable(stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(setOf("JUGADOR", "TUTOR")) }
    val uid = teamState.currentUserId
    val ownedAcademies = teamState.teams.filter { isCoach && it.type == "ACADEMIA" && it.createdBy == uid }
    val availableTeams = (teamState.teams.filter {
        it.type == "EQUIPO" && if (it.createdBy == uid) isCoach else isPlayer
    } + if (isCoach) academyState.academyTeams else emptyList()).distinctBy { it.id }
    val selected = availableTeams.firstOrNull { it.id == selectedTeamId }
    val ownsSelectedAcademy = ownedAcademies.any { it.id == academyId }
    val canPublish = coachApproved && selected != null &&
        (selected.createdBy == uid || (ownsSelectedAcademy && selected.academyId == academyId))
    LaunchedEffect(isCoach) { if (isCoach) notices.watchAuthored() }
    LaunchedEffect(academyId, ownsSelectedAcademy) { academies.watchAcademyTeams(academyId.takeIf { ownsSelectedAcademy }.orEmpty()) }
    LaunchedEffect(canPublish, selected) {
        if (canPublish && selected != null) teams.openTeam(selected) else teams.closeTeam()
    }
    DisposableEffect(Unit) { onDispose { teams.closeTeam() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Volver") }
        Text("Avisos internos", style = MaterialTheme.typography.headlineSmall)
        state.message?.let { Text(it) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (coachApproved && state.canRetry) Button(onClick = notices::retryPublish) { Text("Reintentar entregas pendientes") }
        if (isCoach) TextButton(onClick = notices::watchAuthored) { Text("Mis publicaciones") }
        linkedPlayers.forEach { link ->
            val label = guardianState.invites.firstOrNull { it.id == link.inviteId }?.playerName ?: "Jugador vinculado"
            FilterChip(selected = activeGuardianPlayerId == link.playerId, onClick = { guardianPlayerId = link.playerId }, label = { Text(label) })
        }
        if (activeGuardianPlayerId.isNotBlank()) state.guardianTeams.forEach { team ->
            TextButton(onClick = { notices.watchTeam(team.id, activeGuardianPlayerId, "TUTOR", null) }) { Text("Avisos de ${team.label}") }
            team.academyId?.let { id ->
                TextButton(onClick = { notices.watchTeam(team.id, activeGuardianPlayerId, "TUTOR", id) }) { Text("Avisos de academia · ${team.label}") }
            }
        }
        ownedAcademies.forEach { academy ->
            FilterChip(selected = academyId == academy.id, onClick = { academyId = academy.id; selectedTeamId = "" }, label = { Text(academy.name) })
        }
        availableTeams.forEach { team ->
            FilterChip(selected = selectedTeamId == team.id, onClick = { selectedTeamId = team.id }, label = { Text(team.name) })
        }
        if (selected != null) {
            Row {
                TextButton(onClick = { notices.watchTeam(selected.id, uid, if (selected.createdBy == uid) "ENTRENADOR" else "JUGADOR", null) }) { Text("Avisos del equipo") }
                selected.academyId?.let { id ->
                    TextButton(onClick = { notices.watchTeam(selected.id, uid, if (selected.createdBy == uid) "ENTRENADOR" else "JUGADOR", id) }) { Text("De la academia") }
                }
            }
        }
        if (canPublish || (coachApproved && ownsSelectedAcademy)) {
            Text("Publicar para ${selected?.name ?: ownedAcademies.firstOrNull { it.id == academyId }?.name.orEmpty()}", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(content, { content = it }, label = { Text("Mensaje") }, modifier = Modifier.fillMaxWidth())
            listOf("JUGADOR" to "Jugadores", "TUTOR" to "Tutores", "ENTRENADOR" to "Entrenadores").forEach { (key, label) ->
                FilterChip(selected = key in audience, onClick = { audience = if (key in audience) audience - key else audience + key }, label = { Text(label) })
            }
            if (selected != null) Text("Se enviará a ${teamState.members.size} jugadores y los grupos seleccionados.")
            if (ownedAcademies.any { it.id == academyId }) {
                Button(enabled = !state.busy && !state.canRetry && academyState.academyTeams.isNotEmpty(), onClick = {
                    notices.publishAcademy(academyId, academyState.academyTeams.map { it.id }, title, content, audience)
                }) { Text("Publicar en toda la academia") }
            }
            Button(enabled = canPublish && !state.busy && !state.canRetry && !teamState.membersLoading && teamState.members.isNotEmpty(), onClick = {
                if (selected == null) return@Button
                notices.publish(selected.id, academyId.takeIf { id -> ownedAcademies.any { it.id == id } && selected.academyId == id },
                    teamState.members.map { it.userId }, title, content, audience)
            }) { Text("Publicar aviso") }
        }
        state.rows.forEach { notice ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(notice.title, style = MaterialTheme.typography.titleMedium)
                Text(notice.content)
            } }
        }
        if (state.rows.isEmpty() && state.message == null) Text("No hay avisos en esta selección.")
    }
}
