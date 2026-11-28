package dev.dreamteam.sportpro.ui.matches

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import dev.dreamteam.sportpro.data.model.EventType
import dev.dreamteam.sportpro.data.model.MatchEventContext
import dev.dreamteam.sportpro.data.model.MatchEventRecord
import dev.dreamteam.sportpro.data.model.MatchEvents
import dev.dreamteam.sportpro.data.model.MatchTab
import dev.dreamteam.sportpro.data.model.MatchViewerRole
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.repository.EventCatalogFirestoreRepository
import dev.dreamteam.sportpro.data.repository.FirestoreMatchEventStore
import dev.dreamteam.sportpro.data.repository.MatchLineupRepository
import dev.dreamteam.sportpro.ui.callups.AvailabilityRepository
import dev.dreamteam.sportpro.ui.callups.AvailabilityResponse
import dev.dreamteam.sportpro.ui.callups.CallupMatch
import dev.dreamteam.sportpro.ui.callups.CallupRepository
import dev.dreamteam.sportpro.ui.callups.CoachMatchActions
import dev.dreamteam.sportpro.ui.callups.CoachMatchPanel
import dev.dreamteam.sportpro.ui.callups.GuardianCallupCard
import dev.dreamteam.sportpro.ui.callups.PlayerCallup
import dev.dreamteam.sportpro.ui.callups.PlayerMatchCard
import dev.dreamteam.sportpro.ui.callups.dateLabel
import dev.dreamteam.sportpro.ui.events.EventDetailScreen
import dev.dreamteam.sportpro.ui.events.EventEditScreen
import dev.dreamteam.sportpro.ui.events.EventHistoryScreen
import dev.dreamteam.sportpro.ui.events.EventLabels
import dev.dreamteam.sportpro.ui.events.LocalEventLabels
import dev.dreamteam.sportpro.ui.events.RegisterLiveEventScreen
import dev.dreamteam.sportpro.ui.events.rememberMatchPlayers
import dev.dreamteam.sportpro.ui.lineups.GUARDIAN_LINEUP_DENIED
import dev.dreamteam.sportpro.ui.lineups.GuardianLineupView
import dev.dreamteam.sportpro.ui.lineups.MatchLineupScreen

/** Quién abre el partido; decide pestañas y permisos (ver MatchEvents.access). */
sealed interface MatchViewer {
    data class Owner(val team: Team) : MatchViewer
    data class Player(val row: PlayerCallup) : MatchViewer
    data class Guardian(val row: PlayerCallup, val playerName: String) : MatchViewer
    data object Admin : MatchViewer
}

data class MatchEntry(val match: CallupMatch, val viewer: MatchViewer) {
    val role: MatchViewerRole get() = when (viewer) {
        is MatchViewer.Owner -> MatchViewerRole.OWNER
        is MatchViewer.Player -> MatchViewerRole.PLAYER
        is MatchViewer.Guardian -> MatchViewerRole.GUARDIAN
        MatchViewer.Admin -> MatchViewerRole.ADMIN
    }
    val callupSelected: Boolean get() = when (viewer) {
        is MatchViewer.Player -> viewer.row.selected
        is MatchViewer.Guardian -> viewer.row.selected
        else -> true
    }
}

private sealed interface LiveScreen {
    data object Register : LiveScreen
    data class Event(val id: String) : LiveScreen
    data class Edit(val id: String) : LiveScreen
    data class History(val id: String) : LiveScreen
}

/**
 * Detalle del partido real (matchFixtures, US-09) con pestañas Info · Convocatoria · Alineación · LIVE.
 * Cada pestaña reutiliza las pantallas de US-09/10/11 y solo muestra lo que el rol ya puede leer.
 */
@Composable
fun MatchDetailScreen(entry: MatchEntry, onBack: () -> Unit) {
    val callups = remember { CallupRepository() }
    var match by remember(entry.match.id) { mutableStateOf(entry.match) }
    var cancelled by remember(entry.match.id) { mutableStateOf(false) }
    var adminSelections by remember(entry.match.id) { mutableStateOf<List<PlayerCallup>>(emptyList()) }
    DisposableEffect(entry.match.id) {
        val listener = if (entry.viewer is MatchViewer.Guardian)
            // El tutor no puede leer el partido: solo se detecta si se canceló.
            callups.match(entry.match.id, { }, { }, { cancelled = true })
        else callups.match(entry.match.id, { live -> live?.let { match = it.copy(teamName = entry.match.teamName) } }, { }, { cancelled = true })
        val admin = if (entry.viewer == MatchViewer.Admin) callups.matchSelections(entry.match.id, { adminSelections = it }, { }) else null
        onDispose { listener.remove(); admin?.remove() }
    }
    val teamName = when (val v = entry.viewer) {
        is MatchViewer.Owner -> v.team.name
        is MatchViewer.Player -> v.row.encounter?.teamName.orEmpty()
        is MatchViewer.Guardian -> v.row.encounter?.teamName.orEmpty()
        MatchViewer.Admin -> adminSelections.firstNotNullOfOrNull { it.encounter?.teamName?.takeIf(String::isNotBlank) }.orEmpty()
    }.ifBlank { match.teamName }
    val access = MatchEvents.access(entry.role, entry.callupSelected, cancelled)
    var tab by rememberSaveable(entry.match.id) { mutableStateOf(MatchTab.INFO) }
    if (tab !in access.tabs) tab = MatchTab.INFO
    var callupDirty by remember { mutableStateOf(false) }
    var pendingTab by remember { mutableStateOf<MatchTab?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf<LiveScreen?>(null) }
    val ctx = MatchEventContext(match.id, match.teamId, match.category, match.kickoff.toDate().time, teamName.ifBlank { "Nuestro equipo" }, match.opponent)
    // Tutora: guarda una vez su acceso (US-11); sirve para la Alineación y para los eventos (LIVE).
    val guardianAccess = rememberGuardianAccess(entry, enabled = MatchTab.LIVE in access.tabs || MatchTab.LINEUP in access.tabs)
    val canReadEvents = MatchTab.LIVE in access.tabs && (entry.viewer !is MatchViewer.Guardian || guardianAccess == GuardianAccess.READY)
    val store = remember { FirestoreMatchEventStore() }
    var events by remember(entry.match.id) { mutableStateOf<List<MatchEventRecord>>(emptyList()) }
    var eventsError by remember(entry.match.id) { mutableStateOf<String?>(null) }
    var writeError by remember(entry.match.id) { mutableStateOf<String?>(null) }
    DisposableEffect(entry.match.id, canReadEvents) {
        val subscription = if (canReadEvents) store.observe(match.id, { events = it; eventsError = null }, { eventsError = it }) else null
        onDispose { subscription?.remove() }
    }
    val labels = rememberEventLabels(ctx, entry, events)
    val canCorrect = access.canEditEvents && MatchEvents.canCorrect(System.currentTimeMillis(), ctx.kickoffMillis)
    val reportError: (String) -> Unit = { writeError = it }
    val leave = { if (callupDirty) confirmLeave = true else onBack() }

    // Pantallas de eventos (US-13/14) dentro del partido.
    live?.let { screen ->
        BackHandler { live = if (screen is LiveScreen.Edit || screen is LiveScreen.History) LiveScreen.Event((screen as? LiveScreen.Edit)?.id ?: (screen as LiveScreen.History).id) else null }
        CompositionLocalProvider(LocalEventLabels provides labels) {
            when (screen) {
                LiveScreen.Register -> RegisterLiveEventScreen(ctx, onBack = { live = null }, onSaved = { writeError = null; live = null }, onError = reportError)
                is LiveScreen.Event -> EventDetailScreen(match.id, screen.id, canEdit = canCorrect, onBack = { live = null },
                    onCorrect = { live = LiveScreen.Edit(screen.id) }, onVoided = { writeError = null; live = null },
                    onShowHistory = { live = LiveScreen.History(screen.id) }, onError = reportError)
                is LiveScreen.Edit -> EventEditScreen(ctx, screen.id, onBack = { live = LiveScreen.Event(screen.id) },
                    onSaved = { writeError = null; live = LiveScreen.Event(screen.id) }, onError = reportError)
                is LiveScreen.History -> EventHistoryScreen(match.id, screen.id, onBack = { live = LiveScreen.Event(screen.id) })
            }
        }
        return
    }

    BackHandler { leave() }
    if (confirmLeave || pendingTab != null) AlertDialog(onDismissRequest = { confirmLeave = false; pendingTab = null },
        title = { Text("¿Salir sin guardar?") }, text = { Text("Se perderán los cambios pendientes de la convocatoria.") },
        confirmButton = { TextButton(onClick = {
            callupDirty = false
            val next = pendingTab
            confirmLeave = false; pendingTab = null
            if (next != null) tab = next else onBack()
        }) { Text("Salir") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false; pendingTab = null }) { Text("Seguir editando") } })

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            TextButton(onClick = leave) { Text("← Partidos") }
            Text("${teamName.ifBlank { "Partido" }} vs ${match.opponent}", style = MaterialTheme.typography.headlineSmall)
            Text("${match.category} · ${dateLabel(match.kickoff.toDate().time)}", style = MaterialTheme.typography.bodyMedium)
        }
        if (access.tabs.size > 1) TabRow(selectedTabIndex = access.tabs.indexOf(tab).coerceAtLeast(0)) {
            access.tabs.forEach { option ->
                Tab(selected = tab == option, text = { Text(option.label) }, onClick = {
                    if (tab == MatchTab.CALLUP && callupDirty && option != MatchTab.CALLUP) pendingTab = option else tab = option
                })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                MatchTab.INFO -> InfoTab(entry, match, teamName, cancelled, callupDirty, onCancelled = { onBack() })
                MatchTab.CALLUP -> CallupTab(entry, match, callups, adminSelections) { callupDirty = it }
                MatchTab.LINEUP -> LineupTab(entry, match, guardianAccess, onBack = leave)
                MatchTab.LIVE -> CompositionLocalProvider(LocalEventLabels provides labels) {
                    when {
                        entry.viewer is MatchViewer.Guardian && guardianAccess == GuardianAccess.FAILED ->
                            Text(GUARDIAN_LINEUP_DENIED, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                        !canReadEvents -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                        else -> MatchLiveTab(ctx, events, eventsError ?: writeError, canRegister = access.canEditEvents,
                            onRegister = { live = LiveScreen.Register }, onOpenEvent = { live = LiveScreen.Event(it) })
                    }
                }
            }
        }
    }
}

/**
 * Nombres de los eventos según el rol (D3, igual que la Alineación de US-11): la dueña ve la plantilla
 * y las copias; el jugador, las copias guardadas en el evento; la tutora, solo a su jugador (el resto
 * "Compañero"); el admin, ninguno ("Jugador").
 */
@Composable
private fun rememberEventLabels(ctx: MatchEventContext, entry: MatchEntry, events: List<MatchEventRecord>): EventLabels {
    val players = rememberMatchPlayers(ctx, enabled = entry.role == MatchViewerRole.OWNER)
    var types by remember(ctx.teamId) { mutableStateOf<List<EventType>>(emptyList()) }
    DisposableEffect(ctx.teamId) {
        val listener = EventCatalogFirestoreRepository().listenActive(ctx.teamId) { types = it }
        onDispose { listener.remove() }
    }
    val extraLabels = types.flatMap { it.extraFields }.associate { it.key to it.label }
    val guardian = entry.viewer as? MatchViewer.Guardian
    val (names, unknown) = MatchEvents.visibleNames(entry.role, events, players.names,
        guardian?.row?.playerId.orEmpty(), guardian?.playerName.orEmpty())
    return EventLabels(ctx.ownName, ctx.opponent, names, extraLabels, unknown, ownerId = entry.match.ownerId)
}

private enum class GuardianAccess { LOADING, READY, FAILED }

/** El tutor necesita su acceso (matchLineups/{m}/guardians/{uid}) para leer alineación y eventos. */
@Composable
private fun rememberGuardianAccess(entry: MatchEntry, enabled: Boolean): GuardianAccess {
    val guardian = entry.viewer as? MatchViewer.Guardian ?: return GuardianAccess.READY
    var state by remember(entry.match.id) { mutableStateOf(GuardianAccess.LOADING) }
    LaunchedEffect(entry.match.id, enabled) {
        if (enabled) MatchLineupRepository().openAsGuardian(entry.match.id, guardian.row.playerId)
            .addOnSuccessListener { state = GuardianAccess.READY }.addOnFailureListener { state = GuardianAccess.FAILED }
    }
    return state
}

@Composable
private fun InfoTab(entry: MatchEntry, match: CallupMatch, teamName: String, cancelled: Boolean, callupDirty: Boolean, onCancelled: () -> Unit) {
    var message by remember { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Estado: " + when {
            cancelled -> "Partido cancelado"
            !entry.callupSelected -> "Convocatoria retirada"
            match.kickoff.toDate().time <= now -> "Comenzó"
            else -> "Programado"
        }, fontWeight = FontWeight.Bold)
        if (teamName.isNotBlank()) Text("Equipo: $teamName")
        Text("Rival: ${match.opponent}")
        Text("Categoría: ${match.category}")
        Text("Fecha del partido: ${dateLabel(match.kickoff.toDate().time)} (hora local)")
        Text("Fecha límite para responder: ${dateLabel(match.responseDeadline.toDate().time)} (hora local)")
        if (cancelled) Text("La entrenadora canceló este partido.")
        else if (!entry.callupSelected) Text("La convocatoria fue retirada: la alineación y los eventos ya no se muestran.")
        val viewer = entry.viewer
        if (!cancelled && viewer is MatchViewer.Owner) {
            HorizontalDivider()
            CoachMatchActions(viewer.team, match, enabled = !callupDirty, onMessage = { message = it }, onCancelled = { onCancelled() })
            if (callupDirty) Text("Guarda o descarta la convocatoria antes de editar el partido.", style = MaterialTheme.typography.bodySmall)
        }
        if (viewer == MatchViewer.Admin) Text("Vista de administración (solo lectura).", style = MaterialTheme.typography.bodySmall)
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun CallupTab(entry: MatchEntry, match: CallupMatch, callups: CallupRepository, adminSelections: List<PlayerCallup>, onDirtyChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val viewer = entry.viewer) {
            is MatchViewer.Owner -> CoachMatchPanel(viewer.team, match, showMatchHeader = false, onOpenLineup = null, onDirtyChange = onDirtyChange)
            is MatchViewer.Player -> PlayerMatchCard(viewer.row, callups, onOpenLineup = null)
            is MatchViewer.Guardian -> GuardianCallupCard(viewer.row, viewer.playerName, callups, onOpenLineup = null)
            MatchViewer.Admin -> AdminCallupSummary(match.id, adminSelections)
        }
    }
}

/** Admin: solo conteos (no puede leer nombres de la plantilla; US-22). */
@Composable
private fun AdminCallupSummary(matchId: String, selections: List<PlayerCallup>) {
    var responses by remember(matchId) { mutableStateOf<List<AvailabilityResponse>>(emptyList()) }
    var error by remember(matchId) { mutableStateOf<String?>(null) }
    DisposableEffect(matchId) {
        val listener = AvailabilityRepository().matchResponses(matchId, { responses = it; error = null }, { error = it })
        onDispose { listener.remove() }
    }
    val called = selections.filter { it.selected }.map { it.playerId }.toSet()
    val answers = responses.filter { it.playerId in called }
    val available = answers.count { it.status == "AVAILABLE" }
    val unavailable = answers.count { it.status == "UNAVAILABLE" }
    Text("Convocatoria (solo lectura)", style = MaterialTheme.typography.titleMedium)
    Text("${called.size} convocados · $available disponibles · $unavailable no disponibles · ${called.size - available - unavailable} sin respuesta")
    Text("Los nombres de los jugadores no se muestran en la vista de administración.", style = MaterialTheme.typography.bodySmall)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun LineupTab(entry: MatchEntry, match: CallupMatch, guardianAccess: GuardianAccess, onBack: () -> Unit) {
    val myUid = FirebaseAuth.getInstance().currentUser?.uid
    when (val viewer = entry.viewer) {
        is MatchViewer.Owner -> MatchLineupScreen(match, canManage = match.ownerId == myUid, showHeader = false, onBack = onBack)
        is MatchViewer.Player -> MatchLineupScreen(match, canManage = false, showHeader = false, onBack = onBack)
        MatchViewer.Admin -> MatchLineupScreen(match, canManage = false, showHeader = false, anonymous = true, onBack = onBack)
        is MatchViewer.Guardian -> when (guardianAccess) {
            GuardianAccess.READY -> MatchLineupScreen(viewer.row.encounter ?: match, canManage = false,
                guardian = GuardianLineupView(viewer.row.playerId, viewer.playerName), showHeader = false, onBack = onBack)
            GuardianAccess.FAILED -> Text(GUARDIAN_LINEUP_DENIED, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            GuardianAccess.LOADING -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        }
    }
}

/**
 * Pestaña LIVE: marcador y cronología en tiempo real (Firestore). Solo la dueña aprobada registra,
 * desde el inicio del partido hasta 24 h después. Lo escrito sin conexión aparece como pendiente.
 */
@Composable
private fun MatchLiveTab(ctx: MatchEventContext, events: List<MatchEventRecord>, error: String?, canRegister: Boolean,
    onRegister: () -> Unit, onOpenEvent: (String) -> Unit) {
    val labels = LocalEventLabels.current
    val score = MatchEvents.score(events)
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Marcador", style = MaterialTheme.typography.labelLarge)
                Text("${ctx.ownName}  ${score.own} - ${score.opponent}  ${ctx.opponent}", style = MaterialTheme.typography.headlineSmall)
                if (score.goalsWithoutTeam > 0) Text("${score.goalsWithoutTeam} ${if (score.goalsWithoutTeam == 1) "gol sin equipo no cuenta" else "goles sin equipo no cuentan"} en el marcador.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (canRegister) {
            val open = MatchEvents.canRegister(now, ctx.kickoffMillis)
            Button(modifier = Modifier.fillMaxWidth(), enabled = open, onClick = onRegister) { Text("Registrar evento") }
            if (!open) Text(if (now < ctx.kickoffMillis) "Podrás registrar eventos cuando empiece el partido (${dateLabel(ctx.kickoffMillis)})."
                else "El plazo para registrar eventos terminó (24 h después del inicio).", style = MaterialTheme.typography.bodySmall)
        }
        Text("Cronología", style = MaterialTheme.typography.titleMedium)
        if (events.isEmpty()) Text("Todavía no hay eventos registrados para este partido.")
        events.forEach { event ->
            Card(onClick = { onOpenEvent(event.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${event.minute?.let { "$it'" } ?: "—"} · ${event.typeName}", fontWeight = FontWeight.Bold,
                        textDecoration = if (event.voided) TextDecoration.LineThrough else null)
                    val details = listOfNotNull(event.team?.let { labels.team(it) }, event.playerId?.let { labels.player(it) }) +
                        event.extraFields.map { (key, value) -> "${labels.extraLabel(key)}: ${labels.extraValue(value, isPlayer = key in event.extraNames)}" }
                    if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    if (event.notes.isNotBlank()) Text(event.notes, style = MaterialTheme.typography.bodySmall)
                    if (event.voided) Text("Anulado: no cuenta en el marcador", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (event.pending) Text("Pendiente de sincronizar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
