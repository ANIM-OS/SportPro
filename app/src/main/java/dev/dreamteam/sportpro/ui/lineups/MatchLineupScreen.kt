package dev.dreamteam.sportpro.ui.lineups

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Task
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.data.model.MatchLineupEntry
import dev.dreamteam.sportpro.data.repository.MatchLineupRepository
import dev.dreamteam.sportpro.data.repository.TeamDirectoryRepository
import dev.dreamteam.sportpro.ui.callups.*
import dev.dreamteam.sportpro.ui.teams.LineupPitch
import dev.dreamteam.sportpro.ui.teams.formationSlots
import kotlinx.coroutines.delay

private fun responseLabel(status: String?) = when (status) {
    "AVAILABLE" -> "Disponible"
    "UNAVAILABLE" -> "No disponible"
    else -> "Sin respuesta"
}
private val benchPositions = listOf("POR" to "Portero", "DEF" to "Defensa", "MED" to "Mediocampo", "DEL" to "Delantera")

@Composable
fun MatchLineupScreen(encounter: CallupMatch, canManage: Boolean, onBack: () -> Unit) {
    val repository = remember { MatchLineupRepository() }
    val callups = remember { CallupRepository() }
    val directory = remember { TeamDirectoryRepository() }
    val availability = remember { AvailabilityRepository() }
    var match by remember(encounter.id) { mutableStateOf<CallupMatch?>(encounter) }
    var formation by remember(encounter.id) { mutableStateOf("4-3-3") }
    var entries by remember(encounter.id) { mutableStateOf<List<MatchLineupEntry>>(emptyList()) }
    var roster by remember(encounter.id) { mutableStateOf<List<TeamMember>>(emptyList()) }
    var selected by remember(encounter.id) { mutableStateOf<Set<String>>(emptySet()) }
    var responses by remember(encounter.id) { mutableStateOf<List<AvailabilityResponse>>(emptyList()) }
    var pending by remember(encounter.id, canManage) { mutableStateOf(setOf("match", "formation", "players", "roster") + if (canManage) setOf("callups", "responses") else emptySet()) }
    var failures by remember(encounter.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<MatchLineupEntry?>(null) }
    var pendingFormation by remember { mutableStateOf<String?>(null) }
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }
    fun loaded(key: String) { pending = pending - key; failures = failures - key }
    fun failed(key: String, text: String) { pending = pending - key; failures = failures + (key to text) }
    fun perform(task: Task<Void>, success: String) {
        busy = true; message = null
        task.addOnSuccessListener { message = success; editing = null; pendingFormation = null }
            .addOnFailureListener { message = it.localizedMessage ?: "No se pudo guardar. Revisa tus permisos y la convocatoria." }
            .addOnCompleteListener { busy = false }
    }
    DisposableEffect(encounter.id, canManage) {
        val listeners = mutableListOf(
            callups.match(encounter.id, { match = it; loaded("match") }, { match = null; failed("match", it) }),
            repository.observeFormation(encounter.id, { formation = it; loaded("formation") }, { failed("formation", it) }),
            repository.observePlayers(encounter.id, { entries = it; loaded("players") }, { entries = emptyList(); failed("players", it) }),
            directory.listenRoster(encounter.teamId, { roster = it; loaded("roster") }, { roster = emptyList(); failed("roster", it) })
        )
        if (canManage) {
            listeners += callups.coachSelections(encounter.id, { selected = it.filter { row -> row.selected }.map { row -> row.playerId }.toSet(); loaded("callups") }, { selected = emptySet(); failed("callups", it) })
            listeners += availability.coachResponses(encounter.id, encounter.teamId, { responses = it; loaded("responses") }, { failed("responses", it) })
        }
        onDispose { listeners.forEach { it.remove() } }
    }
    BackHandler(enabled = busy) { }
    BackHandler(enabled = !busy, onBack = onBack)
    val current = match
    val eligible = roster.filter { it.category == current?.category && it.userId in selected }
    val editable = canManage && current != null && now < current.kickoff.toDate().time && pending.isEmpty() && failures.isEmpty() && !busy
    val slots = formationSlots(formation)
    val starters = entries.filter { it.kind == "STARTER" }.sortedBy { it.position.removePrefix("P").toIntOrNull() ?: 99 }
    val reserves = entries.filter { it.kind == "BENCH" }.sortedBy { entry -> roster.find { it.userId == entry.playerId }?.playerName }
    val invalid = if (canManage) entries.filter { entry -> eligible.none { it.userId == entry.playerId } } else emptyList()
    if (editing != null && current != null) {
        AssignmentDialog(editing!!, formation, eligible, entries, responses, editable,
            onDismiss = { if (!busy) editing = null },
            onSave = { playerId, kind, position -> perform(repository.assign(current, formation, playerId, kind, position), "Asignación guardada para este partido") })
    }
    if (pendingFormation != null && current != null) AlertDialog(
        onDismissRequest = { if (!busy) pendingFormation = null },
        title = { Text("Cambiar formación") },
        text = { Text("Los titulares conservarán sus casillas y sus posiciones se adaptarán a ${pendingFormation}. Revisa la cancha después del cambio.") },
        confirmButton = { TextButton(enabled = editable, onClick = { perform(repository.formation(current, pendingFormation!!), "Formación guardada") }) { Text("Cambiar") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { pendingFormation = null }) { Text("Cancelar") } })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text("← Volver a convocatorias") }
        Text("Alineación del partido", style = MaterialTheme.typography.headlineSmall)
        current?.let { Text("vs ${it.opponent} · ${it.category}\n${dateLabel(it.kickoff.toDate().time)}") }
            ?: Text("El partido ya no está disponible")
        if (pending.isNotEmpty() || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        failures.values.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        if (current != null && now >= current.kickoff.toDate().time) Text("El partido comenzó. La alineación es de solo lectura.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("4-3-3", "4-4-2", "3-5-2").forEach { item ->
                FilterChip(selected = formation == item, enabled = editable, onClick = { if (item != formation) pendingFormation = item }, label = { Text(item) })
            }
        }
        Text("Titulares (${starters.size}/11)", style = MaterialTheme.typography.titleMedium)
        LineupPitch(formation, starters.filter { it !in invalid }.associate { it.position to it.playerId }, roster, editable) { index ->
            editing = starters.find { it.position == "P$index" } ?: MatchLineupEntry("", "STARTER", "P$index")
        }
        starters.forEach { row ->
            val label = slots.getOrNull(row.position.removePrefix("P").toIntOrNull() ?: -1)?.third ?: row.position
            EntryRow(row, label, roster, responses, editable, { editing = row }) { perform(repository.remove(encounter.id, row.playerId), "Jugador retirado de esta alineación") }
        }
        Text("Suplentes (${reserves.size})", style = MaterialTheme.typography.titleMedium)
        reserves.forEach { row ->
            EntryRow(row, benchPositions.find { it.first == row.position }?.second ?: row.position, roster, responses, editable, { editing = row }) {
                perform(repository.remove(encounter.id, row.playerId), "Jugador retirado de esta alineación")
            }
        }
        if (canManage) {
            OutlinedButton(enabled = editable && eligible.isNotEmpty(), onClick = { editing = MatchLineupEntry("", "BENCH", "DEF") }) { Text("Agregar suplente") }
            if (pending.isEmpty() && eligible.isEmpty()) Text("No hay jugadores convocados de esta categoría. Guarda primero la convocatoria.")
            if (invalid.isNotEmpty()) Text("Hay jugadores que ya no pertenecen a la convocatoria o categoría. Retíralos de la alineación.", color = MaterialTheme.colorScheme.error)
            Text("Solo se ofrecen jugadores convocados. Su disponibilidad se muestra como referencia; sin respuesta no equivale a no disponible.")
            Text("Cada asignación se guarda al confirmar. Un jugador solo puede ocupar un lugar en esta alineación.")
        }
        message?.let { Text(it) }
    }
}

@Composable
private fun EntryRow(entry: MatchLineupEntry, position: String, roster: List<TeamMember>, responses: List<AvailabilityResponse>, editable: Boolean, edit: () -> Unit, remove: () -> Unit) {
    val player = roster.find { it.userId == entry.playerId }
    Column {
        Text("${player?.playerName ?: "Jugador fuera de plantilla"} · $position")
        if (editable) {
            Text(responseLabel(responses.find { it.playerId == entry.playerId }?.status))
            Row {
                TextButton(onClick = edit) { Text("Editar") }
                TextButton(onClick = remove) { Text("Retirar") }
            }
        }
    }
}

@Composable
private fun AssignmentDialog(initial: MatchLineupEntry, formation: String, eligible: List<TeamMember>, entries: List<MatchLineupEntry>, responses: List<AvailabilityResponse>, enabled: Boolean, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var player by remember { mutableStateOf(initial.playerId) }
    var kind by remember { mutableStateOf(initial.kind) }
    var position by remember { mutableStateOf(initial.position) }
    val options = if (kind == "BENCH") benchPositions else formationSlots(formation).mapIndexedNotNull { index, slot ->
        val id = "P$index"
        if (entries.any { it.kind == "STARTER" && it.position == id && it.playerId != player }) null else id to "${slot.third} · ${index + 1}"
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Asignar jugador") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LineupSelector("Jugador", player, eligible.map { it.userId to "${it.playerName} · ${responseLabel(responses.find { answer -> answer.playerId == it.userId }?.status)}" }, enabled && initial.playerId.isEmpty()) { player = it }
            LineupSelector("Condición", kind, listOf("STARTER" to "Titular", "BENCH" to "Suplente"), enabled) { kind = it; position = "" }
            LineupSelector("Posición", position, options, enabled) { position = it }
            if (entries.any { it.playerId == player }) Text("Esta asignación moverá al jugador de su lugar actual; no lo duplicará.")
        } },
        confirmButton = { TextButton(enabled = enabled && eligible.any { it.userId == player } && options.any { it.first == position }, onClick = { onSave(player, kind, position) }) { Text("Guardar asignación") } },
        dismissButton = { TextButton(enabled = enabled, onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun LineupSelector(label: String, value: String, options: List<Pair<String, String>>, enabled: Boolean, select: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(enabled = enabled && options.isNotEmpty(), onClick = { open = true }) { Text("$label: ${options.find { it.first == value }?.second ?: "Seleccionar"}") }
        DropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { select(id); open = false }) }
        }
    }
}
