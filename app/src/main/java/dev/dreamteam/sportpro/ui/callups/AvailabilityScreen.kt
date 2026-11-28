package dev.dreamteam.sportpro.ui.callups

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import dev.dreamteam.sportpro.data.model.GuardianLink
import dev.dreamteam.sportpro.data.model.GuardianInvite
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
    var readError by remember(row.id) { mutableStateOf<String?>(null) }
    var saveError by remember(row.id) { mutableStateOf<String?>(null) }
    var message by remember(row.id) { mutableStateOf<String?>(null) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1000) }
    }
    DisposableEffect(row.id) {
        val listener = repository.response(row.id,
            { response = it; loading = false; readError = null },
            { readError = it; loading = false })
        onDispose { listener.remove() }
    }
    LaunchedEffect(busy) {
        if (busy) {
            delay(15_000)
            if (busy) message = "Esperando confirmación del servidor. Comprueba tu conexión."
        }
    }
    val open = row.selected && match != null && now < match.responseDeadline.toDate().time && now < match.kickoff.toDate().time
    Text("${if (row.selected) "Disponibilidad" else "Respuesta anterior"}: ${statusLabel(response?.status)}")
    if (!row.selected) Text("La convocatoria fue retirada; la respuesta anterior ya no cuenta.")
    if (response?.pending == true) Text("Cambio pendiente de confirmación del servidor.")
    response?.let { Text(if (it.respondedBy == row.playerId) "Respondió el jugador" else "Respondió un padre o tutor autorizado") }
    match?.let { Text("Fecha límite para responder: ${dateLabel(it.responseDeadline.toDate().time)} (hora local)") }
    if (row.selected && match != null && !open) Text("El plazo para responder está cerrado.")
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    val submit: (Boolean) -> Unit = { available ->
        busy = true; saveError = null; message = null
        repository.respond(row, available)
            .addOnSuccessListener { message = "Respuesta guardada" }
            .addOnFailureListener { saveError = "No se pudo guardar. Comprueba el plazo para responder y tu autorización e inténtalo de nuevo." }
            .addOnCompleteListener { busy = false }
    }
    val enabled = open && !busy && !loading && readError == null && response?.pending != true
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = response?.status == "AVAILABLE", enabled = enabled,
            onClick = { submit(true) }, label = { Text("Disponible") })
        FilterChip(selected = response?.status == "UNAVAILABLE", enabled = enabled,
            onClick = { submit(false) }, label = { Text("No disponible") })
    }
    if (busy) Text("Guardando respuesta…")
    readError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
    var pendingInvites by remember { mutableStateOf<List<GuardianInvite>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val user = FirebaseAuth.getInstance().currentUser
    var verified by remember(user?.uid) { mutableStateOf(user?.isEmailVerified == true) }
    var checking by remember { mutableStateOf(false) }
    var verificationMessage by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var claiming by remember { mutableStateOf(false) }
    var claimMessage by remember { mutableStateOf<String?>(null) }
    var claimError by remember { mutableStateOf(false) }
    Text("Como padre o tutor", style = MaterialTheme.typography.titleLarge)
    if (!verified) {
        Text("Verifica tu correo para consultar las convocatorias de tus jugadores vinculados.")
        TextButton(enabled = !checking && user != null, onClick = {
            checking = true; verificationMessage = null
            user?.sendEmailVerification()
                ?.addOnSuccessListener { verificationMessage = "Enlace enviado a ${user.email}. Revisa también el correo no deseado." }
                ?.addOnFailureListener { verificationMessage = "No se pudo enviar el enlace. Inténtalo de nuevo más tarde." }
                ?.addOnCompleteListener { checking = false }
        }) { Text("Enviar enlace de verificación") }
        OutlinedButton(enabled = !checking && user != null, onClick = {
            checking = true; verificationMessage = null
            user?.reload()?.addOnSuccessListener {
                user.getIdToken(true).addOnSuccessListener {
                    verified = user.isEmailVerified
                    if (!verified) verificationMessage = "El correo aún no está verificado. Abre el enlace recibido y vuelve a comprobar."
                    checking = false
                }.addOnFailureListener { checking = false; verificationMessage = "No se pudo actualizar la sesión." }
            }?.addOnFailureListener { checking = false; verificationMessage = "No se pudo comprobar la verificación." }
        }) { Text(if (checking) "Comprobando…" else "Ya verifiqué mi correo") }
        verificationMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        return
    }
    DisposableEffect(profiles) {
        val listener = profiles.listenLinkedPlayers({ links = it; loading = false; error = null }, { links = emptyList(); loading = false; error = it })
        val requests = profiles.listenClaimedInvites({ pendingInvites = it }, { claimError = true; claimMessage = it })
        onDispose { listener?.remove(); requests?.remove() }
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (!loading && links.isEmpty() && error == null) Text("Aún no tienes jugadores confirmados. Pide al jugador un código desde su perfil.")
    pendingInvites.forEach { invite ->
        Text("Solicitud para ${invite.playerName}: esperando confirmación del jugador.")
        TextButton(enabled = !claiming, onClick = {
            claiming = true; claimMessage = null; claimError = false
            profiles.cancelInvite(invite,
                { claiming = false; claimMessage = "Solicitud cancelada" },
                { claiming = false; claimError = true; claimMessage = it })
        }) { Text("Cancelar solicitud") }
    }
    OutlinedTextField(value = code, onValueChange = { code = it.trim().take(100) },
        label = { Text("Código de invitación del jugador") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(enabled = !claiming && code.isNotBlank(), onClick = {
        claiming = true; claimMessage = null; claimError = false
        profiles.claimInvite(code,
            { claiming = false; code = ""; claimMessage = "Solicitud enviada. El jugador debe confirmarte en su perfil." },
            { claiming = false; claimError = true; claimMessage = it })
    }) { Text(if (claiming) "Aceptando…" else "Solicitar vínculo") }
    claimMessage?.let { Text(it, color = if (claimError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
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
    rows.forEach { row -> key(row.id) { GuardianCallupCard(row, repository) } }
}

@Composable
private fun GuardianCallupCard(row: PlayerCallup, repository: CallupRepository) {
    // El tutor no puede leer el partido; solo se escucha para saber si ya no existe (cancelado).
    var cancelled by remember(row.matchId) { mutableStateOf(false) }
    DisposableEffect(row.matchId) {
        val listener = repository.match(row.matchId, { cancelled = false }, { }, { cancelled = true })
        onDispose { listener.remove() }
    }
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(30_000) }
    }
    val state = callupCardState(row.selected, if (cancelled) false else null, row.encounter?.kickoff?.toDate()?.time, now)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.label, style = MaterialTheme.typography.titleSmall)
            row.encounter?.let { Text(encounterLabel(it)) }
                ?: Text("El entrenador debe actualizar los datos de esta convocatoria anterior.")
            if (state == CallupCardState.CANCELADO) Text("La entrenadora canceló este partido.")
            else AvailabilityActions(row, row.encounter)
        }
    }
}
