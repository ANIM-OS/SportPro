package dev.dreamteam.sportpro.ui.callups

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import dev.dreamteam.sportpro.data.model.GuardianLink
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.data.repository.PlayerProfileRepository
import kotlinx.coroutines.delay

private fun statusLabel(status: String?) = when (status) {
    "AVAILABLE" -> "Disponible"
    "UNAVAILABLE" -> "No disponible"
    else -> "Sin respuesta"
}

@Composable
internal fun AvailabilityActions(row: PlayerCallup, match: CallupMatch?) {
    val repository = remember { AvailabilityRepository() }
    var response by remember(row.id) { mutableStateOf<AvailabilityResponse?>(null) }
    var loading by remember(row.id) { mutableStateOf(true) }
    var busy by remember(row.id) { mutableStateOf(false) }
    var error by remember(row.id) { mutableStateOf<String?>(null) }
    var message by remember(row.id) { mutableStateOf<String?>(null) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1000) }
    }
    DisposableEffect(row.id) {
        val listener = repository.response(row.id, { response = it; loading = false; error = null }, { error = it; loading = false })
        onDispose { listener.remove() }
    }
    val open = row.selected && match != null && now < match.responseDeadline.toDate().time && now < match.kickoff.toDate().time
    Text("Disponibilidad: ${statusLabel(response?.status)}")
    response?.let { Text(if (it.respondedBy == row.playerId) "Respondió el jugador" else "Respondió un padre o tutor autorizado") }
    match?.let { Text("Cierre: ${dateLabel(it.responseDeadline.toDate().time)}") }
    if (row.selected && match != null && !open) Text("El plazo para responder está cerrado.")
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    val submit: (Boolean) -> Unit = { available ->
        busy = true; error = null; message = null
        repository.respond(row, available)
            .addOnSuccessListener { message = "Respuesta guardada" }
            .addOnFailureListener { error = "No se pudo guardar. Comprueba el cierre y tu autorización e inténtalo de nuevo." }
            .addOnCompleteListener { busy = false }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = open && !busy && !loading, onClick = { submit(true) }) { Text("Disponible") }
        OutlinedButton(enabled = open && !busy && !loading, onClick = { submit(false) }) { Text("No disponible") }
    }
    if (busy) Text("Guardando respuesta…")
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    message?.let { Text(it) }
}

@Composable
internal fun CoachAvailability(matchId: String, teamId: String, players: List<PlayerCallup>, roster: List<TeamMember>) {
    val repository = remember { AvailabilityRepository() }
    var responses by remember(matchId) { mutableStateOf<List<AvailabilityResponse>>(emptyList()) }
    var error by remember(matchId) { mutableStateOf<String?>(null) }
    var loading by remember(matchId) { mutableStateOf(true) }
    DisposableEffect(matchId) {
        val listener = repository.coachResponses(matchId, teamId, { responses = it; error = null; loading = false }, { error = it; loading = false })
        onDispose { listener.remove() }
    }
    Text("Respuestas de los convocados", style = MaterialTheme.typography.titleMedium)
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (!loading && error == null) players.filter { it.selected }.forEach { player ->
        val response = responses.find { it.playerId == player.playerId }
        val name = roster.find { it.userId == player.playerId }?.playerName ?: "Jugador fuera de la plantilla actual"
        Text("$name: ${statusLabel(response?.status)}")
    }
}

@Composable
internal fun GuardianCallups(repository: CallupRepository) {
    val profiles = remember { PlayerProfileRepository() }
    var links by remember { mutableStateOf<List<GuardianLink>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    Text("Convocatorias de mis jugadores", style = MaterialTheme.typography.titleLarge)
    if (FirebaseAuth.getInstance().currentUser?.isEmailVerified != true) {
        Text("Verifica tu correo y vuelve a iniciar sesión para consultar a los jugadores vinculados.")
        return
    }
    DisposableEffect(profiles) {
        val listener = profiles.listenLinkedPlayers({ links = it; loading = false; error = null }, { links = emptyList(); loading = false; error = it })
        onDispose { listener?.remove() }
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (!loading && links.isEmpty() && error == null) Text("No tienes jugadores vinculados. El jugador debe vincular tu correo desde su perfil.")
    links.forEach { link -> key(link.id) { GuardianPlayerCallups(link, repository) } }
}

@Composable
private fun GuardianPlayerCallups(link: GuardianLink, repository: CallupRepository) {
    var rows by remember { mutableStateOf<List<PlayerCallup>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    DisposableEffect(link.playerId) {
        val listener = repository.playerSelections(link.playerId, { rows = it; error = null; loading = false }, { rows = emptyList(); error = it; loading = false })
        onDispose { listener.remove() }
    }
    Text(link.playerName, style = MaterialTheme.typography.titleMedium)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (!loading && rows.isEmpty() && error == null) Text("Sin convocatorias")
    rows.forEach { row -> key(row.id) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (row.selected) "Convocado" else "Convocatoria retirada")
                row.encounter?.let { Text("vs ${it.opponent}\n${it.category}\n${dateLabel(it.kickoff.toDate().time)}") }
                    ?: Text("El entrenador debe actualizar los datos de esta convocatoria anterior.")
                AvailabilityActions(row, row.encounter)
            }
        }
    } }
}
