package dev.dreamteam.sportpro.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import dev.dreamteam.sportpro.data.model.GuardianLink
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.repository.LiveEventRepository
import dev.dreamteam.sportpro.data.repository.MonthlyFeeRepository
import dev.dreamteam.sportpro.data.repository.PlayerProfileRepository
import dev.dreamteam.sportpro.data.repository.TeamDirectoryRepository
import dev.dreamteam.sportpro.ui.callups.CallupMatch
import dev.dreamteam.sportpro.ui.callups.CallupRepository
import dev.dreamteam.sportpro.ui.callups.MatchEditor
import dev.dreamteam.sportpro.ui.callups.PlayerCallup
import dev.dreamteam.sportpro.ui.callups.dateLabel
import dev.dreamteam.sportpro.ui.matches.MatchDetailScreen
import dev.dreamteam.sportpro.ui.matches.MatchEntry
import dev.dreamteam.sportpro.ui.matches.MatchViewer

/**
 * Pestaña Partidos: los partidos REALES (matchFixtures, US-09) de cada rol. Al tocar uno se abre su
 * detalle con Info · Convocatoria · Alineación · LIVE (MatchDetailScreen).
 */
@Composable
fun LiveMatchScreen(isCoach: Boolean, isPlayer: Boolean, isGuardian: Boolean) {
    var opened by remember { mutableStateOf<MatchEntry?>(null) }
    var isAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { MonthlyFeeRepository().checkAdmin { isAdmin = it } }
    // Los eventos de los antiguos partidos de demostración no pertenecen a ningún partido real: se borran una vez.
    val context = LocalContext.current
    val removedDemo = remember { LiveEventRepository(context).removeDemoEvents() }
    opened?.let { entry ->
        MatchDetailScreen(entry, onBack = { opened = null })
        return
    }
    val open: (MatchEntry) -> Unit = { opened = it }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Partidos", style = MaterialTheme.typography.headlineSmall)
        Text("Toca un partido para ver su convocatoria, alineación y eventos.", style = MaterialTheme.typography.bodyMedium)
        if (removedDemo > 0) Text("Se eliminaron $removedDemo eventos de prueba guardados en este celular.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        if (isCoach) CoachMatches(open)
        if (isPlayer) PlayerMatches(open)
        if (isGuardian) GuardianMatches(open)
        if (isAdmin) AdminMatches(open)
        if (!isCoach && !isPlayer && !isGuardian && !isAdmin) Text("Selecciona un rol para consultar partidos.")
    }
}

@Composable
private fun CoachMatches(open: (MatchEntry) -> Unit) {
    val directory = remember { TeamDirectoryRepository() }
    val repository = remember { CallupRepository() }
    var teams by remember { mutableStateOf<List<Team>>(emptyList()) }
    var matchesByTeam by remember { mutableStateOf<Map<String, List<CallupMatch>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var creatingFor by remember { mutableStateOf<Team?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    DisposableEffect(directory) {
        val listener = directory.listenOwnedTeams({ teams = it.filter { team -> team.type == "EQUIPO" }; loading = false; error = null },
            { error = it; loading = false })
        onDispose { listener?.remove() }
    }
    val teamIds = teams.map { it.id }
    DisposableEffect(teamIds) {
        matchesByTeam = emptyMap()
        val listeners = teams.map { team ->
            repository.matches(team.id, { matches -> matchesByTeam = matchesByTeam + (team.id to matches); error = null },
                { error = it })
        }
        onDispose { listeners.forEach { it.remove() } }
    }
    creatingFor?.let { team ->
        MatchEditor(team, null, repository, onDismiss = { creatingFor = null },
            onSaved = { creatingFor = null; message = "Partido registrado en ${team.name}." })
    }
    Text("Mis equipos", style = MaterialTheme.typography.titleMedium)
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    if (!loading && teams.isEmpty() && error == null) Text("Aún no tienes equipos registrados.")
    teams.forEach { team ->
        Text(team.name, style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { creatingFor = team }) { Text("Registrar partido") }
        val matches = matchesByTeam[team.id]
        if (matches == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (matches?.isEmpty() == true) Text("Sin partidos registrados.")
        matches?.sortedByDescending { it.kickoff }?.forEach { match ->
            key(match.id) { MatchCard(match, team.name) { open(MatchEntry(match.copy(teamName = team.name), MatchViewer.Owner(team))) } }
        }
    }
}

@Composable
private fun PlayerMatches(open: (MatchEntry) -> Unit) {
    val repository = remember { CallupRepository() }
    var rows by remember { mutableStateOf<List<PlayerCallup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(repository) {
        val listener = repository.mySelections({ rows = it; loading = false; error = null },
            { rows = emptyList(); loading = false; error = it })
        onDispose { listener.remove() }
    }
    Text("Mis partidos como jugador", style = MaterialTheme.typography.titleMedium)
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    val selected = rows.filter { it.selected }.distinctBy { it.matchId }
    if (!loading && selected.isEmpty() && error == null) Text("Aún no tienes partidos convocados.")
    selected.forEach { row -> key(row.id) { PlayerMatch(row, repository, open) } }
}

@Composable
private fun PlayerMatch(row: PlayerCallup, repository: CallupRepository, open: (MatchEntry) -> Unit) {
    var match by remember(row.matchId) { mutableStateOf<CallupMatch?>(null) }
    var error by remember(row.matchId) { mutableStateOf<String?>(null) }
    var loading by remember(row.matchId) { mutableStateOf(true) }
    var cancelled by remember(row.matchId) { mutableStateOf(false) }
    DisposableEffect(row.matchId) {
        val listener = repository.match(row.matchId, { match = it; error = null; loading = false; cancelled = false },
            { match = null; error = it; loading = false },
            { match = null; error = null; loading = false; cancelled = true })
        onDispose { listener.remove() }
    }
    val teamName = row.encounter?.teamName.orEmpty()
    val current = (match ?: row.encounter)?.copy(teamName = teamName)
    if (current != null) MatchCard(current, teamName.takeIf { it.isNotBlank() }, cancelled) { open(MatchEntry(current, MatchViewer.Player(row))) }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (!loading && match == null && !cancelled) Text(error ?: "No se pudo confirmar el partido; se muestran los últimos datos guardados.",
        style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun GuardianMatches(open: (MatchEntry) -> Unit) {
    if (FirebaseAuth.getInstance().currentUser?.isEmailVerified != true) {
        Text("Verifica tu correo en Equipos → Convocatorias por partido para consultar a tus jugadores.")
        return
    }
    val profiles = remember { PlayerProfileRepository() }
    val repository = remember { CallupRepository() }
    var links by remember { mutableStateOf<List<GuardianLink>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(profiles) {
        val listener = profiles.listenLinkedPlayers({ links = it; loading = false; error = null },
            { links = emptyList(); loading = false; error = it })
        onDispose { listener?.remove() }
    }
    Text("Partidos de mis jugadores vinculados", style = MaterialTheme.typography.titleMedium)
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (!loading && links.isEmpty() && error == null) Text("Aún no tienes jugadores vinculados.")
    links.forEach { link -> key(link.id) { GuardianPlayerMatches(link, repository, open) } }
}

@Composable
private fun GuardianPlayerMatches(link: GuardianLink, repository: CallupRepository, open: (MatchEntry) -> Unit) {
    var rows by remember(link.playerId) { mutableStateOf<List<PlayerCallup>>(emptyList()) }
    var error by remember(link.playerId) { mutableStateOf<String?>(null) }
    DisposableEffect(link.playerId) {
        val listener = repository.playerSelections(link.playerId, { rows = it; error = null }, { rows = emptyList(); error = it })
        onDispose { listener.remove() }
    }
    Text(link.playerName, style = MaterialTheme.typography.titleSmall)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    rows.filter { it.selected }.distinctBy { it.matchId }.forEach { row ->
        row.encounter?.let { encounter -> key(row.id) { GuardianMatch(row, encounter, link, repository, open) } }
    }
}

@Composable
private fun GuardianMatch(row: PlayerCallup, encounter: CallupMatch, link: GuardianLink, repository: CallupRepository, open: (MatchEntry) -> Unit) {
    // El tutor no lee el partido; el listener solo indica si ya no existe (cancelado).
    var cancelled by remember(row.matchId) { mutableStateOf(false) }
    DisposableEffect(row.matchId) {
        val listener = repository.match(row.matchId, { cancelled = false }, { }, { cancelled = true })
        onDispose { listener.remove() }
    }
    MatchCard(encounter, encounter.teamName.takeIf { it.isNotBlank() }, cancelled) {
        open(MatchEntry(encounter, MatchViewer.Guardian(row, link.playerName)))
    }
}

/** Admin (solo lectura): todos los partidos. Los nombres de equipo y jugadores no se leen (US-22). */
@Composable
private fun AdminMatches(open: (MatchEntry) -> Unit) {
    val repository = remember { CallupRepository() }
    var matches by remember { mutableStateOf<List<CallupMatch>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(repository) {
        val listener = repository.allMatches({ matches = it; loading = false; error = null }, { error = it; loading = false })
        onDispose { listener.remove() }
    }
    HorizontalDivider()
    Text("Todos los partidos (administración, solo lectura)", style = MaterialTheme.typography.titleMedium)
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (!loading && matches.isEmpty() && error == null) Text("No hay partidos registrados.")
    matches.forEach { match -> key(match.id) { MatchCard(match, null) { open(MatchEntry(match, MatchViewer.Admin)) } } }
}

@Composable
private fun MatchCard(match: CallupMatch, teamName: String?, cancelled: Boolean = false, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (teamName == null) "vs ${match.opponent}" else "$teamName vs ${match.opponent}",
                style = MaterialTheme.typography.titleMedium)
            Text("${match.category} · ${dateLabel(match.kickoff.toDate().time)} (hora local)", style = MaterialTheme.typography.bodyMedium)
            Text(when {
                cancelled -> "Partido cancelado"
                match.kickoff.toDate().time > System.currentTimeMillis() -> "Programado"
                else -> "Comenzó"
            }, style = MaterialTheme.typography.labelMedium)
        }
    }
}
