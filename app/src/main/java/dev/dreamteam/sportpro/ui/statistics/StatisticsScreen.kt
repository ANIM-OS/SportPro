package dev.dreamteam.sportpro.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import dev.dreamteam.sportpro.data.model.GuardianLink
import dev.dreamteam.sportpro.data.model.MatchEventRecord
import dev.dreamteam.sportpro.data.model.MatchStatsSummary
import dev.dreamteam.sportpro.data.model.MatchStatus
import dev.dreamteam.sportpro.data.model.MatchSummaryView
import dev.dreamteam.sportpro.data.model.MatchViewerRole
import dev.dreamteam.sportpro.data.model.PlayerLine
import dev.dreamteam.sportpro.data.model.StatsPrivacy
import dev.dreamteam.sportpro.data.model.StatsView
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.model.TeamTotals
import dev.dreamteam.sportpro.data.repository.FirestoreMatchEventStore
import dev.dreamteam.sportpro.data.repository.MatchStatsRepository
import dev.dreamteam.sportpro.data.repository.MonthlyFeeRepository
import dev.dreamteam.sportpro.data.repository.PlayerProfileRepository
import dev.dreamteam.sportpro.data.repository.StatsLoad
import dev.dreamteam.sportpro.data.repository.TeamDirectoryRepository
import dev.dreamteam.sportpro.ui.auth.AppRole
import dev.dreamteam.sportpro.ui.auth.RoleAccess
import dev.dreamteam.sportpro.ui.auth.RoleCapability
import dev.dreamteam.sportpro.ui.callups.dateLabel
import dev.dreamteam.sportpro.ui.callups.rememberMinuteClock

private val Background = Color(0xFF0D0F12)
private val Surface = Color(0xFF161920)
private val Accent = Color(0xFFFF6600)
private val Muted = Color(0xFF9A9CA2)
private val Danger = Color(0xFFFF6B6B)

private enum class StatsTab(val label: String) { TEAM("EQUIPO"), PLAYERS("JUGADORES"), MATCHES("PARTIDOS") }

/**
 * US-16: estadísticas reales calculadas a partir de los eventos válidos de los partidos. Cada rol ve
 * solo lo que le corresponde (US-22, ver [StatsPrivacy]). El histórico se lee una vez (get); el partido
 * en juego y el que está abierto se escuchan en tiempo real, así las correcciones y anulaciones se ven
 * al instante en ellos y en el resto al pulsar "Actualizar" o volver a entrar.
 */
@Composable
fun StatisticsScreen(roles: Set<AppRole>, isCoachApproved: Boolean) {
    var isAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { MonthlyFeeRepository().checkAdmin { isAdmin = it } }
    val available = buildList {
        if (RoleAccess.can(roles, RoleCapability.CREATE_TEAM, isCoachApproved)) add(MatchViewerRole.OWNER)
        if (AppRole.PLAYER in roles) add(MatchViewerRole.PLAYER)
        if (AppRole.PARENT in roles) add(MatchViewerRole.GUARDIAN)
        if (isAdmin) add(MatchViewerRole.ADMIN)
    }
    var chosen by remember { mutableStateOf<MatchViewerRole?>(null) }
    val role = chosen?.takeIf { it in available } ?: available.firstOrNull()

    Column(Modifier.fillMaxSize().background(Background).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("ESTADÍSTICAS", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Calculadas con los eventos válidos de cada partido", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        if (available.size > 1) ChipRow(available.map { roleLabel(it) }, available.indexOf(role)) { chosen = available[it] }
        when (role) {
            null -> Message("Selecciona un rol (entrenadora aprobada, jugador o tutor) para ver estadísticas.")
            MatchViewerRole.OWNER -> OwnerStats()
            MatchViewerRole.PLAYER -> RoleStats(MatchViewerRole.PLAYER, key = "player", childId = "") { it.loadPlayer() }
            MatchViewerRole.GUARDIAN -> GuardianStats()
            MatchViewerRole.ADMIN -> RoleStats(MatchViewerRole.ADMIN, key = "admin", childId = "") { it.loadAdmin() }
        }
    }
}

private fun roleLabel(role: MatchViewerRole) = when (role) {
    MatchViewerRole.OWNER -> "Entrenadora"
    MatchViewerRole.PLAYER -> "Jugador"
    MatchViewerRole.GUARDIAN -> "Tutor"
    MatchViewerRole.ADMIN -> "Administración"
}

@Composable
private fun OwnerStats() {
    var teams by remember { mutableStateOf<List<Team>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val listener = TeamDirectoryRepository().listenOwnedTeams({ list -> teams = list.filter { it.type == "EQUIPO" }; loading = false; error = null },
            { error = it; loading = false })
        onDispose { listener?.remove() }
    }
    var selected by remember { mutableIntStateOf(0) }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Message(it, Danger) }
    if (!loading && teams.isEmpty() && error == null) { Message("Aún no tienes equipos."); return }
    val team = teams.getOrNull(selected.coerceIn(0, (teams.size - 1).coerceAtLeast(0))) ?: return
    if (teams.size > 1) ChipRow(teams.map { it.name }, teams.indexOf(team)) { selected = it }
    RoleStats(MatchViewerRole.OWNER, key = "owner-${team.id}", childId = "") { it.loadOwner(team) }
}

@Composable
private fun GuardianStats() {
    var links by remember { mutableStateOf<List<GuardianLink>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val listener = PlayerProfileRepository().listenLinkedPlayers({ links = it; loading = false; error = null },
            { links = emptyList(); loading = false; error = it })
        onDispose { listener?.remove() }
    }
    var selected by remember { mutableIntStateOf(0) }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Message(it, Danger) }
    if (!loading && links.isEmpty() && error == null) { Message("Aún no tienes jugadores vinculados."); return }
    val link = links.getOrNull(selected.coerceIn(0, (links.size - 1).coerceAtLeast(0))) ?: return
    if (links.size > 1) ChipRow(links.map { it.playerName.ifBlank { "Jugador" } }, links.indexOf(link)) { selected = it }
    RoleStats(MatchViewerRole.GUARDIAN, key = "guardian-${link.playerId}", childId = link.playerId) { it.loadGuardian(link) }
}

/** Carga el histórico del rol, escucha en tiempo real los partidos en juego o abiertos y aplica la privacidad. */
@Composable
private fun RoleStats(role: MatchViewerRole, key: String, childId: String,
    load: (MatchStatsRepository) -> com.google.android.gms.tasks.Task<StatsLoad>) {
    val repository = remember { MatchStatsRepository() }
    var data by remember(key) { mutableStateOf<StatsLoad?>(null) }
    var loading by remember(key) { mutableStateOf(true) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var reload by remember(key) { mutableIntStateOf(0) }
    var openMatch by remember(key) { mutableStateOf<String?>(null) }
    var live by remember(key) { mutableStateOf<Map<String, List<MatchEventRecord>>>(emptyMap()) }
    LaunchedEffect(key, reload) {
        loading = true; error = null
        load(repository)
            .addOnSuccessListener { data = it; live = emptyMap(); loading = false }
            .addOnFailureListener { error = "No se pudieron cargar las estadísticas. Revisa tu conexión."; loading = false }
    }
    val now = rememberMinuteClock()
    // Tiempo real solo para el partido en juego y el que está abierto; el resto se leyó con get().
    val watched = data?.matches.orEmpty().filter { it.status(now) == MatchStatus.LIVE || it.matchId == openMatch }.map { it.matchId }.toSet()
    DisposableEffect(key, watched) {
        val store = FirestoreMatchEventStore()
        val subs = watched.map { id -> store.observe(id, { events -> live = live + (id to events) }, { }) }
        onDispose { subs.forEach { it.remove() } }
    }
    val matches = data?.matches.orEmpty().map { m -> live[m.matchId]?.let { m.copy(events = it) } ?: m }
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val view = StatsPrivacy.view(role, uid, childId, matches, data?.rosterNames.orEmpty(), now)

    val tabs = when (role) {
        MatchViewerRole.OWNER -> listOf(StatsTab.TEAM, StatsTab.PLAYERS, StatsTab.MATCHES)
        MatchViewerRole.PLAYER -> listOf(StatsTab.PLAYERS, StatsTab.TEAM, StatsTab.MATCHES)
        MatchViewerRole.GUARDIAN -> listOf(StatsTab.PLAYERS, StatsTab.MATCHES)
        MatchViewerRole.ADMIN -> listOf(StatsTab.TEAM, StatsTab.MATCHES)
    }
    var tab by remember(key) { mutableStateOf(tabs.first()) }
    ChipRow(tabs.map { tabLabel(it, role) }, tabs.indexOf(tab).coerceAtLeast(0)) { tab = tabs[it] }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(note(role), color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { reload++ }, enabled = !loading) { Text("Actualizar", color = Accent) }
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Message(it, Danger) }
    data?.failed?.takeIf { it > 0 }?.let { Message("No se pudieron leer $it partido(s); no se incluyen.", Danger) }
    if (data != null) when (tab) {
        StatsTab.TEAM -> TeamTab(view, role)
        StatsTab.PLAYERS -> PlayersTab(view, role)
        StatsTab.MATCHES -> MatchesTab(view, openMatch) { openMatch = if (openMatch == it) null else it }
    }
}

private fun tabLabel(tab: StatsTab, role: MatchViewerRole) = when {
    tab == StatsTab.PLAYERS && role == MatchViewerRole.PLAYER -> "MIS CIFRAS"
    tab == StatsTab.PLAYERS && role == MatchViewerRole.GUARDIAN -> "MI JUGADOR"
    else -> tab.label
}

private fun note(role: MatchViewerRole) = when (role) {
    MatchViewerRole.OWNER -> "Todos los partidos del equipo."
    MatchViewerRole.PLAYER -> "Solo partidos en los que fuiste convocado."
    MatchViewerRole.GUARDIAN -> "Solo las cifras de tu jugador vinculado."
    MatchViewerRole.ADMIN -> "Solo cifras, sin nombres (administración)."
}

@Composable
private fun TeamTab(view: StatsView, role: MatchViewerRole) {
    val team = view.team ?: return Message("Esta vista no incluye totales del equipo.")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TotalsCard(team, if (role == MatchViewerRole.ADMIN) "Todos los partidos registrados" else null) }
        item { Text("ÚLTIMOS PARTIDOS", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        if (view.matches.isEmpty()) item { Message("Aún no hay partidos.") }
        items(view.matches.take(5), key = { it.matchId }) { MatchRow(it) }
    }
}

@Composable
private fun TotalsCard(t: TeamTotals, subtitle: String?) {
    StatCard {
        Text("Partidos finalizados", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = Muted, fontSize = 11.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Mini("PJ", t.played); Mini("G", t.won); Mini("E", t.drawn); Mini("P", t.lost)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Mini("GF", t.goalsFor); Mini("GC", t.goalsAgainst); Mini("DG", t.goalDifference); Mini("En juego", t.live)
        }
    }
}

@Composable
private fun PlayersTab(view: StatsView, role: MatchViewerRole) {
    if (view.players.isEmpty()) return Message(when (role) {
        MatchViewerRole.PLAYER -> "Aún no tienes partidos convocados."
        MatchViewerRole.GUARDIAN -> "Tu jugador aún no tiene partidos convocados."
        else -> "Aún no hay datos de jugadores."
    })
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(view.players, key = { it.playerId }) { line ->
            PlayerCard(line, title = if (role == MatchViewerRole.PLAYER) "Tus estadísticas" else line.name)
        }
    }
}

@Composable
private fun PlayerCard(line: PlayerLine, title: String) {
    StatCard {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Mini("Conv.", line.called); Mini("Titular", line.starter); Mini("Suplente", line.bench); Mini("Goles", line.goals)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Mini("Amarillas", line.yellowCards); Mini("Rojas", line.redCards); Mini("Entra", line.subIn); Mini("Sale", line.subOut)
        }
    }
}

@Composable
private fun MatchesTab(view: StatsView, open: String?, onToggle: (String) -> Unit) {
    if (view.matches.isEmpty()) return Message("Aún no hay partidos.")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(view.matches, key = { it.matchId }) { m ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MatchRow(m) { onToggle(m.matchId) }
                if (open == m.matchId) MatchDetail(m)
            }
        }
    }
}

@Composable
private fun MatchRow(m: MatchSummaryView, onClick: (() -> Unit)? = null) {
    StatCard(onClick) {
        Text(listOf(m.teamName, m.category).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("vs ${m.opponent}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("${dateLabel(m.kickoffMillis)} · ${m.status.label}", color = Muted, fontSize = 12.sp)
            }
            Text("${m.score.own} - ${m.score.opponent}", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (m.score.goalsWithoutTeam > 0) Text("${m.score.goalsWithoutTeam} gol(es) sin equipo no cuentan en el marcador.", color = Danger, fontSize = 11.sp)
    }
}

/** US-16 #5: datos de un partido individual (totales por equipo y, para jugador/tutor, sus cifras). */
@Composable
private fun MatchDetail(m: MatchSummaryView) {
    StatCard {
        m.stats?.let { StatsTable(it) }
        m.ownLine?.let { line ->
            Text("Cifras del jugador en este partido", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Mini("Titular", line.starter); Mini("Goles", line.goals); Mini("Amarillas", line.yellowCards); Mini("Rojas", line.redCards)
            }
        }
        if (m.stats == null && m.ownLine == null) Text("Sin datos para mostrar.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun StatsTable(s: MatchStatsSummary) {
    Row(Modifier.fillMaxWidth()) {
        Text("", Modifier.weight(1f))
        Text("Nuestro", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(0.5f))
        Text("Rival", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(0.5f))
    }
    listOf(
        "Goles" to (s.own.goals to s.opponent.goals),
        "Amarillas" to (s.own.yellowCards to s.opponent.yellowCards),
        "Rojas" to (s.own.redCards to s.opponent.redCards),
        "Faltas" to (s.own.fouls to s.opponent.fouls),
        "Penales (goles)" to (s.own.penalties to s.opponent.penalties),
        "Cambios" to (s.own.substitutions to s.opponent.substitutions),
        "Tiros de esquina" to (s.own.corners to s.opponent.corners),
        "Fueras de juego" to (s.own.offsides to s.opponent.offsides)
    ).forEach { (label, values) ->
        val extra = if (label.startsWith("Penales")) " (${s.own.penaltyGoals})" to " (${s.opponent.penaltyGoals})" else "" to ""
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("${values.first}${extra.first}", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(0.5f))
            Text("${values.second}${extra.second}", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(0.5f))
        }
    }
}

@Composable
private fun StatCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Mini(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label -> FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(label) }) }
    }
}

@Composable
private fun Message(text: String, color: Color = Muted) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text(text, color = color, fontSize = 13.sp) }
}
