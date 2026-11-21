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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dreamteam.sportpro.data.model.GuardianLink
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.repository.PlayerProfileRepository
import dev.dreamteam.sportpro.data.repository.TeamDirectoryRepository
import dev.dreamteam.sportpro.ui.callups.CallupMatch
import dev.dreamteam.sportpro.ui.callups.CallupRepository
import dev.dreamteam.sportpro.ui.callups.PlayerCallup
import dev.dreamteam.sportpro.ui.callups.dateLabel

/** Displays the same persisted match identity that convocatorias use. */
@Composable
fun LiveMatchScreen(isCoach: Boolean, isPlayer: Boolean, isGuardian: Boolean) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Partidos", style = MaterialTheme.typography.headlineSmall)
        Text("Partidos registrados en SportPro", style = MaterialTheme.typography.bodyMedium)
        if (isCoach) CoachMatches()
        if (isPlayer) PlayerMatches()
        if (isGuardian) GuardianMatches()
        if (!isCoach && !isPlayer && !isGuardian) Text("Selecciona un rol para consultar partidos.")
        HorizontalDivider()
        Text("El marcador y los eventos en vivo aún no tienen datos persistidos.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CoachMatches() {
    val directory = remember { TeamDirectoryRepository() }
    val repository = remember { CallupRepository() }
    var teams by remember { mutableStateOf<List<Team>>(emptyList()) }
    var matchesByTeam by remember { mutableStateOf<Map<String, List<CallupMatch>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
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
    Text("Mis equipos", style = MaterialTheme.typography.titleMedium)
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (!loading && teams.isEmpty() && error == null) Text("Aún no tienes equipos registrados.")
    teams.forEach { team ->
        Text(team.name, style = MaterialTheme.typography.titleSmall)
        val matches = matchesByTeam[team.id]
        if (matches == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (matches?.isEmpty() == true) Text("Sin partidos registrados.")
        matches?.sortedByDescending { it.kickoff }?.forEach { match ->
            key(match.id) { MatchCard(match, team.name) }
        }
    }
}

@Composable
private fun PlayerMatches() {
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
    selected.forEach { row -> key(row.id) { PlayerMatch(row, repository) } }
}

@Composable
private fun PlayerMatch(row: PlayerCallup, repository: CallupRepository) {
    var match by remember(row.matchId) { mutableStateOf<CallupMatch?>(null) }
    var error by remember(row.matchId) { mutableStateOf<String?>(null) }
    var loading by remember(row.matchId) { mutableStateOf(true) }
    DisposableEffect(row.matchId) {
        val listener = repository.match(row.matchId, { match = it; error = null; loading = false },
            { match = null; error = it; loading = false })
        onDispose { listener.remove() }
    }
    val current = match ?: row.encounter
    if (current != null) MatchCard(current, null)
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (!loading && match == null) Text(error ?: "No se pudo confirmar el partido; se muestran los últimos datos guardados.",
        style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun GuardianMatches() {
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
    links.forEach { link -> key(link.id) { GuardianPlayerMatches(link, repository) } }
}

@Composable
private fun GuardianPlayerMatches(link: GuardianLink, repository: CallupRepository) {
    var rows by remember(link.playerId) { mutableStateOf<List<PlayerCallup>>(emptyList()) }
    var error by remember(link.playerId) { mutableStateOf<String?>(null) }
    DisposableEffect(link.playerId) {
        val listener = repository.playerSelections(link.playerId, { rows = it; error = null }, { rows = emptyList(); error = it })
        onDispose { listener.remove() }
    }
    Text(link.playerName, style = MaterialTheme.typography.titleSmall)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    rows.filter { it.selected }.distinctBy { it.matchId }.forEach { row ->
        row.encounter?.let { encounter -> key(row.id) { MatchCard(encounter, null) } }
    }
}

@Composable
private fun MatchCard(match: CallupMatch, teamName: String?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (teamName == null) "vs ${match.opponent}" else "$teamName vs ${match.opponent}",
                style = MaterialTheme.typography.titleMedium)
            Text("${match.category} · ${dateLabel(match.kickoff.toDate().time)}", style = MaterialTheme.typography.bodyMedium)
            Text(if (match.kickoff.toDate().time > System.currentTimeMillis()) "Programado" else "Iniciado o finalizado",
                style = MaterialTheme.typography.labelMedium)
        }
    }
}
