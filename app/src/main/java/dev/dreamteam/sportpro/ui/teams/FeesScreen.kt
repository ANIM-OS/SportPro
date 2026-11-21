package dev.dreamteam.sportpro.ui.teams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.Team
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FeeRow(val id: String, val academyId: String, val playerId: String, val period: String,
    val status: String, val amount: String? = null, val dueDate: String = "")
data class FeesState(val fees: List<FeeRow> = emptyList(), val academyTeams: List<Team> = emptyList(), val busy: Boolean = false, val message: String? = null)

class FeesViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val state = MutableStateFlow(FeesState())
    val uiState = state.asStateFlow()
    private var listener: ListenerRegistration? = null
    private var generation = 0
    private var academyTeamsListener: ListenerRegistration? = null
    private var academyGeneration = 0
    fun watchAcademyTeams(academyId: String) {
        academyTeamsListener?.remove()
        val currentAcademy = ++academyGeneration
        state.update { it.copy(academyTeams = emptyList()) }
        if (academyId.isBlank()) return
        academyTeamsListener = db.collection("teams").whereEqualTo("academyId", academyId)
            .addSnapshotListener { snapshot, error ->
                if (currentAcademy != academyGeneration) return@addSnapshotListener
                if (error != null) { fail(error); return@addSnapshotListener }
                val teams = snapshot?.documents.orEmpty().map { doc ->
                    Team(id = doc.id, name = doc.getString("name").orEmpty(), createdBy = doc.getString("createdBy").orEmpty(),
                        type = "EQUIPO", academyId = academyId)
                }
                state.update { it.copy(academyTeams = teams) }
            }
    }
    fun watch(field: String, value: String, showAmounts: Boolean) {
        listener?.remove()
        val current = ++generation
        state.update { it.copy(fees = emptyList(), busy = value.isNotBlank(), message = null) }
        if (value.isBlank()) return
        listener = db.collection("academyFees").whereEqualTo(field, value).addSnapshotListener { snapshot, error ->
            if (generation != current) return@addSnapshotListener
            if (error != null) { fail(error); return@addSnapshotListener }
            val fees = snapshot?.documents.orEmpty().map { doc ->
                FeeRow(doc.id, doc.getString("academyId").orEmpty(), doc.getString("playerId").orEmpty(),
                    doc.getString("period").orEmpty(), doc.getString("status").orEmpty(),
                    dueDate = doc.getTimestamp("dueAt")?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()?.toString().orEmpty())
            }.sortedByDescending { it.period }
            state.update { it.copy(fees = fees, busy = false) }
            if (showAmounts) fees.forEach { fee ->
                db.collection("academyFeeAmounts").document(fee.id).get().addOnSuccessListener { amount ->
                    if (generation != current) return@addOnSuccessListener
                    val minor = amount.getLong("amountMinor") ?: return@addOnSuccessListener
                    val label = BigDecimal.valueOf(minor, 2).toPlainString() + " " + amount.getString("currency").orEmpty()
                    state.update { it.copy(fees = it.fees.map { row -> if (row.id == fee.id) row.copy(amount = label) else row }) }
                }.addOnFailureListener { if (generation == current) fail(it) }
            }
        }
    }
    private fun fail(error: Exception) {
        val message = when ((error as? com.google.firebase.firestore.FirebaseFirestoreException)?.code) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "El servidor no autorizó la operación de mensualidades."
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                "No hay conexión. Vuelve a intentarlo."
            else -> "No se pudo completar la operación de mensualidades."
        }
        state.update { it.copy(fees = emptyList(), busy = false, message = message) }
    }
    fun create(academyId: String, teamId: String, coachId: String, playerId: String,
               period: String, amount: String, currency: String, due: String) {
        val uid = auth.currentUser?.uid ?: return
        val minor = try { BigDecimal(amount.replace(',', '.')).movePointRight(2).longValueExact() } catch (_: Exception) { null }
        val dueAt = try { Timestamp(java.util.Date.from(LocalDate.parse(due).atStartOfDay(ZoneId.systemDefault()).toInstant())) } catch (_: Exception) { null }
        if (!period.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])")) || minor == null || minor !in 0..100000000 ||
            dueAt == null || !currency.matches(Regex("[A-Z]{3}")) || playerId.isBlank()) {
            state.update { it.copy(message = "Revisa el período, importe, moneda y vencimiento.") }
            return
        }
        state.update { it.copy(busy = true, message = null) }
        val id = "${academyId}_${playerId}_${period}"
        val batch = db.batch()
        batch.set(db.collection("academyFees").document(id), mapOf(
            "academyId" to academyId, "teamId" to teamId, "coachId" to coachId, "playerId" to playerId,
            "period" to period, "status" to "PENDIENTE", "dueAt" to dueAt,
            "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid))
        batch.set(db.collection("academyFeeAmounts").document(id), mapOf(
            "feeId" to id, "academyId" to academyId, "playerId" to playerId, "amountMinor" to minor, "currency" to currency))
        batch.commit().addOnSuccessListener { state.update { it.copy(busy = false, message = "Mensualidad simulada registrada.") } }.addOnFailureListener(::fail)
    }
    fun changeStatus(fee: FeeRow) {
        val uid = auth.currentUser?.uid ?: return
        state.update { it.copy(busy = true, message = null) }
        db.collection("academyFees").document(fee.id).update(mapOf(
            "status" to if (fee.status == "PAGADA") "PENDIENTE" else "PAGADA",
            "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { state.update { it.copy(busy = false) } }.addOnFailureListener(::fail)
    }
    override fun onCleared() {
        listener?.remove()
        academyTeamsListener?.remove()
        generation++
        academyGeneration++
        super.onCleared()
    }
}

@Composable
fun FeesScreen(
    isPlayer: Boolean,
    isCoach: Boolean,
    isGuardian: Boolean,
    onBack: () -> Unit,
    fees: FeesViewModel = viewModel(),
    teams: TeamsViewModel = viewModel(),
    guardians: GuardianViewModel = viewModel()
) {
    LaunchedEffect(isGuardian) { guardians.loadLinks(isPlayer = false, isGuardian = isGuardian) }
    val state by fees.uiState.collectAsState()
    val teamState by teams.uiState.collectAsState()
    val guardianState by guardians.uiState.collectAsState()
    var linkedPlayerId by rememberSaveable { mutableStateOf("") }
    val linkedPlayers = guardianState.links.filter { isGuardian && it.guardianId == guardianState.uid }
    val subjectId = linkedPlayerId.takeIf { id -> linkedPlayers.any { it.playerId == id } }.orEmpty()
    var requestedAcademyId by rememberSaveable { mutableStateOf("") }
    var selectedTeamId by rememberSaveable { mutableStateOf("") }
    var playerId by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("PEN") }
    var due by rememberSaveable { mutableStateOf("") }
    var coachView by rememberSaveable { mutableStateOf(isCoach && !isPlayer && !isGuardian) }
    val academies = teamState.teams.filter { isCoach && it.type == "ACADEMIA" && it.createdBy == teamState.currentUserId }
    val academyId = requestedAcademyId.takeIf { id -> academies.any { it.id == id } }.orEmpty()
    LaunchedEffect(academyId) { fees.watchAcademyTeams(academyId) }
    DisposableEffect(Unit) { onDispose { teams.closeTeam() } }
    val activeCoachView = isCoach && coachView
    val field = when {
        academyId.isNotBlank() -> "academyId"
        activeCoachView -> "coachId"
        else -> "playerId"
    }
    val target = when {
        academyId.isNotBlank() -> academyId
        activeCoachView -> teamState.currentUserId
        subjectId.isNotBlank() -> subjectId
        isPlayer && linkedPlayerId.isBlank() -> teamState.currentUserId
        else -> ""
    }
    LaunchedEffect(field, target) { fees.watch(field, target, showAmounts = field != "coachId") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Volver") }
        Text("Mensualidades", style = MaterialTheme.typography.headlineSmall)
        Text("Registro de pagos simulados")
        if (isPlayer && (isCoach || isGuardian)) {
            TextButton(onClick = { requestedAcademyId = ""; coachView = false; linkedPlayerId = "" }) { Text("Mis registros") }
        }
        if (isCoach && (isPlayer || isGuardian || academies.isNotEmpty())) {
            TextButton(onClick = { requestedAcademyId = ""; coachView = true; linkedPlayerId = "" }) { Text("Como entrenador") }
        }
        if (isGuardian) {
            guardianState.loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!isCoach && !isPlayer && linkedPlayers.isEmpty() && guardianState.loadError == null) {
                Text("Vincula un jugador para consultar sus mensualidades.")
            }
        }
        linkedPlayers.forEach { link ->
            val name = guardianState.invites.firstOrNull { it.id == link.inviteId }?.playerName ?: "Jugador vinculado"
            FilterChip(selected = subjectId == link.playerId && academyId.isBlank() && !activeCoachView,
                onClick = { requestedAcademyId = ""; coachView = false; linkedPlayerId = link.playerId }, label = { Text(name) })
        }
        academies.forEach { academy -> FilterChip(selected = academyId == academy.id, onClick = { requestedAcademyId = academy.id; selectedTeamId = ""; playerId = "" }, label = { Text(academy.name) }) }
        state.message?.let { Text(it) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (academyId.isNotBlank()) {
            state.academyTeams.forEach { team ->
                FilterChip(selected = selectedTeamId == team.id, onClick = { selectedTeamId = team.id; playerId = ""; teams.openTeam(team) }, label = { Text(team.name) })
            }
            if (selectedTeamId.isNotBlank()) {
                teamState.members.forEach { member -> FilterChip(selected = playerId == member.userId, onClick = { playerId = member.userId }, label = { Text(member.playerName) }) }
                OutlinedTextField(period, { period = it }, label = { Text("Período AAAA-MM") })
                OutlinedTextField(amount, { amount = it }, label = { Text("Importe") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(currency, { currency = it.uppercase() }, label = { Text("Moneda") })
                OutlinedTextField(due, { due = it }, label = { Text("Vencimiento AAAA-MM-DD") })
                Button(enabled = !state.busy && playerId.isNotBlank(), onClick = {
                    val team = state.academyTeams.firstOrNull { it.id == selectedTeamId }
                    if (team != null) fees.create(academyId, team.id, team.createdBy, playerId, period, amount, currency, due)
                }) { Text("Registrar mensualidad") }
            }
        }
        if (target.isNotBlank() && !state.busy && state.message == null && state.fees.isEmpty()) Text("No hay mensualidades en esta selección.")
        state.fees.filter { fee -> target.isNotBlank() && (field != "playerId" || fee.playerId == target) }.forEach { fee ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(fee.period, style = MaterialTheme.typography.titleMedium)
                    val playerName = teamState.members.firstOrNull { it.userId == fee.playerId }?.playerName
                        ?: guardianState.invites.firstOrNull { it.playerId == fee.playerId }?.playerName
                    Text(playerName ?: "Jugador · ${fee.playerId.take(8)}")
                    Text(if (fee.status == "PAGADA") "Pagada (simulada)" else "Pendiente")
                    if (fee.dueDate.isNotBlank()) Text("Vence el ${fee.dueDate}")
                    fee.amount?.let { Text(it) }
                    if (academyId.isNotBlank() && academyId == fee.academyId) TextButton(enabled = !state.busy, onClick = { fees.changeStatus(fee) }) {
                        Text(if (fee.status == "PAGADA") "Marcar pendiente" else "Marcar pagada")
                    }
                }
            }
        }
    }
}
