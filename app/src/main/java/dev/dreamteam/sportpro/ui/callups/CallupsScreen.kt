package dev.dreamteam.sportpro.ui.callups

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestoreException
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.data.repository.TeamDirectoryRepository
import dev.dreamteam.sportpro.ui.lineups.GuardianLineupView
import dev.dreamteam.sportpro.ui.lineups.MatchLineupScreen
import dev.dreamteam.sportpro.ui.lineups.showLineupButton
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * La transacción lee la membresía de cada alta y el equipo: si el jugador salió del equipo
 * o el equipo se borró mientras la pantalla estaba abierta, las reglas niegan esa lectura.
 */
internal fun saveCallupErrorMessage(failure: Exception): String {
    val denied = generateSequence<Throwable>(failure) { it.cause }.take(5)
        .any { (it as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED }
    return if (denied) "No se guardó la convocatoria: puede que un jugador ya no pertenezca al equipo o que el equipo ya no exista. Revisa la plantilla y vuelve a intentarlo."
    else "No se guardó la convocatoria. No se aplicó ningún cambio: ${failure.localizedMessage}"
}

internal fun dateLabel(millis: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.forLanguageTag("es-PE")).format(Date(millis))

@Composable
fun CallupsScreen(canManage: Boolean, isGuardian: Boolean = false, isPlayer: Boolean = false, onBack: () -> Unit) {
    var lineupMatch by remember { mutableStateOf<CallupMatch?>(null) }
    // US-11: al abrir desde la tarjeta del tutor se entra en modo tutor (sin nombres de otros menores).
    var lineupGuardian by remember { mutableStateOf<GuardianLineupView?>(null) }
    lineupMatch?.let {
        MatchLineupScreen(it, canManage && lineupGuardian == null && it.ownerId == com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid,
            lineupGuardian) { lineupMatch = null; lineupGuardian = null }
        return
    }
    val directory = remember { TeamDirectoryRepository() }
    val repository = remember { CallupRepository() }
    var teams by remember { mutableStateOf<List<Team>>(emptyList()) }
    var teamId by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<CallupMatch>>(emptyList()) }
    var matchId by remember { mutableStateOf("") }
    // Estado del panel del partido (convocatoria sin guardar o guardando): bloquea cambiar de equipo o partido.
    var panelDirty by remember { mutableStateOf(false) }
    var panelBusy by remember { mutableStateOf(false) }
    var loadingTeams by remember { mutableStateOf(true) }
    var loadingMatches by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var createMatch by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val leave = { if (!panelBusy) { if (panelDirty) confirmExit = true else onBack() } }
    BackHandler { leave() }
    val team = teams.find { it.id == teamId }
    val match = matches.find { it.id == matchId }
    DisposableEffect(canManage) {
        val listener = if (canManage) directory.listenOwnedTeams({
            teams = it.filter { item -> item.type == "EQUIPO" }; loadingTeams = false
        }, { error = it; loadingTeams = false }) else null
        onDispose { listener?.remove() }
    }
    DisposableEffect(teamId) {
        matches = emptyList(); matchId = ""; error = null
        loadingMatches = teamId.isNotBlank()
        val b = if (teamId.isBlank()) null else repository.matches(teamId, {
            matches = it; loadingMatches = false
        }, { matches = emptyList(); error = it; loadingMatches = false })
        onDispose { b?.remove() }
    }
    DisposableEffect(matchId) {
        panelDirty = false; panelBusy = false
        onDispose { }
    }
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false },
        title = { Text("¿Salir sin guardar?") }, text = { Text("Se perderán los cambios pendientes de la convocatoria.") },
        confirmButton = { TextButton(onClick = onBack) { Text("Salir") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Seguir editando") } })
    if (createMatch && team != null) {
        MatchEditor(team, null, repository,
            onDismiss = { createMatch = false },
            onSaved = { createMatch = false; message = "Partido guardado. Puedes seleccionarlo." })
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = leave, enabled = !panelBusy) { Text("← Volver") }
        Text("Convocatorias", style = MaterialTheme.typography.headlineSmall)
        if (canManage) {
            val locked = panelDirty || panelBusy
            Text("1. Equipo y partido", style = MaterialTheme.typography.titleMedium)
            if (loadingTeams) LinearProgressIndicator(Modifier.fillMaxWidth())
            Selector("Equipo", team?.name, teams.map { it.id to it.name }, !locked) { teamId = it; message = null }
            if (!loadingTeams && teams.isEmpty()) Text("No tienes equipos propios disponibles.")
            if (team != null) {
                OutlinedButton(enabled = !locked, onClick = { createMatch = true }) { Text("Registrar partido") }
                if (loadingMatches) LinearProgressIndicator(Modifier.fillMaxWidth())
                Selector("Partido", match?.let { "${it.opponent} · ${dateLabel(it.kickoff.toDate().time)}" },
                    matches.map { it.id to "${it.opponent} · ${it.category} · ${dateLabel(it.kickoff.toDate().time)}" }, !locked) { matchId = it; message = null }
                if (!loadingMatches && matches.isEmpty() && error == null) Text("No hay partidos registrados para este equipo.")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (match != null && team != null) key(match.id) {
                CoachMatchPanel(team, match, onOpenLineup = { lineupMatch = it },
                    onDirtyChange = { panelDirty = it }, onBusyChange = { panelBusy = it },
                    onCancelled = { text -> matchId = ""; message = text })
            }
        }
        if (isPlayer) MyCallups(repository) { lineupMatch = it }
        if (isGuardian) {
            if (isPlayer || canManage) HorizontalDivider()
            GuardianCallups(repository) { encounter, view -> lineupGuardian = view; lineupMatch = encounter }
        }
    }
}

/**
 * US-09: editar o cancelar UN partido de la entrenadora (mismas condiciones que antes en Convocatorias).
 * Lo usan el panel de Convocatorias y la pestaña Info del detalle del partido.
 */
@Composable
internal fun CoachMatchActions(team: Team, match: CallupMatch, enabled: Boolean, onMessage: (String) -> Unit, onCancelled: (String) -> Unit) {
    val repository = remember { CallupRepository() }
    val cleanup = remember { MatchCleanup() }
    val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    var editingMatch by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val future = match.kickoff.toDate().time > System.currentTimeMillis()
    val editableMatch = future && match.responseDeadline.toDate().time > System.currentTimeMillis()
    if (confirmCancel) AlertDialog(onDismissRequest = { if (!cancelling) confirmCancel = false },
        title = { Text("¿Cancelar el partido?") },
        text = { Text("Se eliminarán el partido vs ${match.opponent}, su convocatoria, las respuestas de disponibilidad y la alineación. No se puede deshacer.") },
        confirmButton = { TextButton(enabled = !cancelling, onClick = {
            cancelling = true; error = null
            cleanup.deleteMatch(com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("matchFixtures").document(match.id), {
                cancelling = false; confirmCancel = false; onCancelled("Partido cancelado: vs ${match.opponent}")
            }, {
                cancelling = false; confirmCancel = false; error = "No se pudo cancelar el partido. Puedes reintentar: ${it.localizedMessage}"
            })
        }) { Text(if (cancelling) "Cancelando…" else "Cancelar partido") } },
        dismissButton = { TextButton(enabled = !cancelling, onClick = { confirmCancel = false }) { Text("Volver") } })
    if (editingMatch) {
        MatchEditor(team, match, repository,
            onDismiss = { editingMatch = false },
            onSaved = { editingMatch = false; onMessage("Partido guardado.") })
    }
    OutlinedButton(enabled = enabled && !cancelling && editableMatch, onClick = { editingMatch = true }) { Text("Editar datos del partido") }
    if (future && match.ownerId == myUid) OutlinedButton(enabled = enabled && !cancelling, onClick = { confirmCancel = true },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancelar partido") }
    if (!editableMatch && future) Text("El plazo de respuesta terminó; los datos del partido ya no se pueden editar.")
    if (!future) Text("El partido ya comenzó: la convocatoria es de solo lectura.")
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

/**
 * US-09/10: convocatoria y disponibilidad de UN partido de la entrenadora. Lo usan Equipos →
 * Convocatorias y la pestaña Convocatoria del detalle del partido. No hace scroll propio.
 * [showMatchHeader]: datos del partido + Editar/Cancelar (en el detalle están en la pestaña Info).
 * [onOpenLineup]: null oculta el acceso a la alineación (en el detalle es otra pestaña).
 */
@Composable
internal fun CoachMatchPanel(
    team: Team, match: CallupMatch, showMatchHeader: Boolean = true,
    onOpenLineup: ((CallupMatch) -> Unit)? = null,
    onDirtyChange: (Boolean) -> Unit = {}, onBusyChange: (Boolean) -> Unit = {},
    onCancelled: (String) -> Unit = {}
) {
    val directory = remember { TeamDirectoryRepository() }
    val repository = remember { CallupRepository() }
    var roster by remember { mutableStateOf<List<TeamMember>>(emptyList()) }
    var records by remember { mutableStateOf<List<PlayerCallup>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dirty by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var loadingRoster by remember { mutableStateOf(true) }
    var loadingSelection by remember { mutableStateOf(true) }
    var selectionLoadFailed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dirty) { onDirtyChange(dirty) }
    LaunchedEffect(busy) { onBusyChange(busy) }
    DisposableEffect(team.id) {
        val listener = directory.listenRoster(team.id, { roster = it; loadingRoster = false }, { roster = emptyList(); error = it; loadingRoster = false })
        onDispose { listener.remove() }
    }
    DisposableEffect(match.id) {
        val listener = repository.coachSelections(match.id, {
            if (selectionLoadFailed) error = null
            records = it; loadingSelection = false; selectionLoadFailed = false
            if (!dirty && !busy) selected = it.filter { record -> record.selected }.map { record -> record.playerId }.toSet()
        }, { error = it; loadingSelection = false; selectionLoadFailed = true })
        onDispose { listener.remove(); onDirtyChange(false); onBusyChange(false) }
    }
    val eligible = roster.filter { it.category == match.category }.distinctBy { it.userId }
    val eligibleIds = eligible.map { it.userId }.toSet()
    val invalid = selected - eligibleIds
    val savedIds = records.filter { it.selected }.map { it.playerId }.toSet()
    val writeCost = callupWriteCost(selected, savedIds)
    val future = match.kickoff.toDate().time > System.currentTimeMillis()
    val editableMatch = future && match.responseDeadline.toDate().time > System.currentTimeMillis()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showMatchHeader) {
            Text("Rival: ${match.opponent}\nCategoría: ${match.category}\nFecha del partido: ${dateLabel(match.kickoff.toDate().time)} (hora local)")
            CoachMatchActions(team, match, enabled = !busy && !dirty, onMessage = { message = it }, onCancelled = onCancelled)
        } else if (!future) Text("El partido ya comenzó: la convocatoria es de solo lectura.")
        if (loadingRoster || loadingSelection) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (showMatchHeader) HorizontalDivider()
        Text(if (showMatchHeader) "2. Jugadores convocados" else "Jugadores convocados", style = MaterialTheme.typography.titleMedium)
        if (!loadingRoster && eligible.isEmpty()) {
            Text("No hay jugadores en ${match.category}.", style = MaterialTheme.typography.bodyLarge)
            Text("Añade jugadores a esta categoría desde Equipos → Plantilla y alineación general.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        eligible.forEach { player ->
            Row {
                Checkbox(modifier = Modifier.semantics { contentDescription = "Convocar a ${player.playerName}" }, checked = player.userId in selected, enabled = future && !busy && !loadingSelection,
                    onCheckedChange = { checked ->
                        val next = if (checked) selected + player.userId else selected - player.userId
                        if (callupWriteCost(next, savedIds) > MAX_CALLUP_WRITE_COST) message = CALLUP_WRITE_LIMIT_MESSAGE
                        else { selected = next; dirty = selected != savedIds; message = null }
                    })
                Text(player.playerName, Modifier.padding(top = 12.dp))
            }
        }
        if (invalid.isNotEmpty() && !loadingRoster) {
            Text("${invalid.size} selecciones ya no pertenecen a esta categoría.")
            TextButton(enabled = !busy, onClick = { selected = selected.intersect(eligibleIds); dirty = true }) { Text("Retirar selecciones no válidas") }
        }
        Text(if (selected.size == 1) "1 jugador seleccionado" else "${selected.size} jugadores seleccionados")
        if (future && !editableMatch && (selected - savedIds).isNotEmpty()) Text(
            "El cierre de respuestas ya pasó: este jugador no podrá confirmar su disponibilidad",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (dirty) Text("Guarda para seguir seleccionando si alcanzas el límite de cambios por guardado.", style = MaterialTheme.typography.bodySmall)
        if (!loadingSelection) {
            val saved = !dirty && !busy && error == null && records.any { it.selected }
            val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Surface(
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                shape = MaterialTheme.shapes.medium,
                color = if (saved) (if (dark) Color(0xFF173C29) else Color(0xFFE5F5EA)) else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (saved) (if (dark) Color(0xFF9BE5AF) else Color(0xFF1B5E32)) else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    when {
                        busy -> "Guardando convocatoria…"
                        error != null -> "No se pudo confirmar el estado de la convocatoria"
                        dirty -> "Cambios sin guardar"
                        saved -> "✓ Convocatoria guardada"
                        else -> "No hay jugadores convocados"
                    },
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Button(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = dirty && writeCost <= MAX_CALLUP_WRITE_COST && !busy && !loadingRoster && !loadingSelection && !selectionLoadFailed && invalid.isEmpty() && future,
            onClick = {
                busy = true; error = null; message = null
                repository.saveSelections(match, selected, records)
                    .addOnSuccessListener { dirty = false; message = null }
                    .addOnFailureListener { error = saveCallupErrorMessage(it) }
                    .addOnCompleteListener { busy = false }
            }) { Text(if (busy) "Guardando…" else "Guardar convocatoria") }
        if (dirty) TextButton(enabled = !busy, onClick = {
            selected = records.filter { it.selected }.map { it.playerId }.toSet(); dirty = false; message = null
        }) { Text("Descartar cambios") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (!dirty && !busy && !loadingSelection && records.any { it.selected }) {
            HorizontalDivider()
            Text("Disponibilidad para el partido", style = MaterialTheme.typography.titleLarge)
            Text("Fecha límite para responder: ${dateLabel(match.responseDeadline.toDate().time)} (hora local)")
            Text("Hasta ese momento, el jugador o su tutor autorizado pueden indicar o cambiar si asistirán.")
            CoachAvailability(match.id, match.teamId, records, roster)
            if (onOpenLineup != null) {
                HorizontalDivider()
                Text("Alineación", style = MaterialTheme.typography.titleLarge)
                Text("Organiza a los convocados como titulares o suplentes y asigna sus posiciones.")
                OutlinedButton(onClick = { onOpenLineup(match) }) { Text("Alineación del partido") }
            }
        }
    }
}

@Composable
private fun Selector(label: String, value: String?, options: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) { Text("$label: ${value ?: "Seleccionar"}") }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(id) }) }
        }
    }
}

@Composable
internal fun MatchEditor(team: Team, existing: CallupMatch?, repository: CallupRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var category by remember { mutableStateOf(existing?.category ?: "") }
    var opponent by remember { mutableStateOf(existing?.opponent ?: "") }
    var kickoff by remember { mutableStateOf(existing?.kickoff?.toDate()?.time ?: 0L) }
    var deadline by remember { mutableStateOf(existing?.responseDeadline?.toDate()?.time ?: 0L) }
    var automaticDeadline by remember { mutableStateOf(existing == null) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1000) }
    }
    fun suggestDeadline(start: Long): Long {
        val current = System.currentTimeMillis()
        return listOf(start - 24 * 60 * 60 * 1000L, start - 60 * 60 * 1000L, start)
            .firstOrNull { it > current } ?: start
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<String?>(null) }
    picker?.let { target ->
        MatchDateTimePicker(
            title = if (target == "partido") "Fecha del partido" else "Límite de respuesta",
            initial = if (target == "partido") kickoff else deadline,
            maximum = if (target == "partido") null else kickoff,
            onDismiss = { picker = null },
            onSelected = { chosen ->
                if (target == "partido") {
                    kickoff = chosen
                    if (automaticDeadline || deadline <= System.currentTimeMillis() || deadline > chosen) {
                        deadline = suggestDeadline(chosen); automaticDeadline = true
                    }
                } else { deadline = chosen; automaticDeadline = false }
                error = null; picker = null
            }
        )
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (existing == null) "Registrar partido" else "Editar partido") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(team.name, style = MaterialTheme.typography.titleMedium)
                Selector("Categoría", category.ifBlank { null }, team.categories.map { it to it }, existing == null && !busy) { category = it }
                OutlinedTextField(opponent, { opponent = it.take(100) }, label = { Text("Rival") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
                Text("Fecha y hora del partido (hora local)")
                OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy, onClick = { picker = "partido" }) {
                    Text(if (kickoff == 0L) "Seleccionar fecha y hora" else dateLabel(kickoff))
                }
                HorizontalDivider()
                Text("Fecha límite para responder")
                if (kickoff == 0L) Text("Se sugiere al elegir la fecha del partido.")
                if (deadline > 0) {
                    Text("${if (automaticDeadline) "Fecha sugerida" else "Fecha elegida"}: ${dateLabel(deadline)}")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy && kickoff > now, onClick = { picker = "limite" }) { Text("Cambiar fecha límite") }
                if (!automaticDeadline && kickoff > now) TextButton(enabled = !busy, onClick = {
                    deadline = suggestDeadline(kickoff); automaticDeadline = true; error = null
                }) { Text("Usar fecha sugerida") }
                if (deadline > 0 && deadline <= now) Text("La fecha límite ya pasó. Elige una nueva antes de guardar.", color = MaterialTheme.colorScheme.error)
                if (kickoff > 0 && deadline > kickoff) Text("La fecha límite no puede ser posterior al partido.", color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(enabled = !busy && category in team.categories && opponent.isNotBlank() && kickoff > now && deadline > now && deadline <= kickoff, onClick = {
            busy = true
            repository.saveMatch(existing, team.id, team.name, category, opponent, kickoff, deadline)
                .addOnSuccessListener { onSaved() }
                .addOnFailureListener { error = it.localizedMessage; busy = false }
        }) { Text(if (busy) "Guardando…" else "Guardar partido") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } })
}

@Composable
private fun MyCallups(repository: CallupRepository, hideWhenEmpty: Boolean = false, onOpenLineup: (CallupMatch) -> Unit) {
    var rows by remember { mutableStateOf<List<PlayerCallup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(repository) {
        val listener = repository.mySelections({ rows = it; loading = false; error = null }, { rows = emptyList(); loading = false; error = it })
        onDispose { listener.remove() }
    }
    if (hideWhenEmpty && rows.isEmpty() && error == null) return
    Text("Mis convocatorias como jugador", style = MaterialTheme.typography.titleLarge)
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (!loading && rows.isEmpty() && error == null) Text("Aún no estás convocado. Primero debes pertenecer a un equipo y el entrenador debe convocarte a un partido.")
    rows.forEach { row -> key(row.id) { PlayerMatchCard(row, repository, onOpenLineup) } }
}

/** US-09/10: tarjeta del jugador. [onOpenLineup] null oculta el botón (en el detalle la alineación es otra pestaña). */
@Composable
internal fun PlayerMatchCard(row: PlayerCallup, repository: CallupRepository, onOpenLineup: ((CallupMatch) -> Unit)?) {
    var match by remember { mutableStateOf<CallupMatch?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingMatch by remember(row.matchId) { mutableStateOf(true) }
    var cancelled by remember(row.matchId) { mutableStateOf(false) }
    DisposableEffect(row.matchId) {
        val listener = repository.match(row.matchId, { match = it; error = null; loadingMatch = false; cancelled = false },
            { match = null; error = it; loadingMatch = false },
            { match = null; error = null; loadingMatch = false; cancelled = true })
        onDispose { listener.remove() }
    }
    val now = rememberMinuteClock()
    val state = callupCardState(row.selected, if (cancelled) false else if (match != null) true else null,
        match?.kickoff?.toDate()?.time, now, match?.endedAtMillis)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (state == CallupCardState.CONVOCADO) "Estás convocado" else state.label, style = MaterialTheme.typography.titleSmall)
            // El partido en vivo no guarda el nombre del equipo: se toma de la copia de la convocatoria.
            (match?.copy(teamName = row.encounter?.teamName.orEmpty()) ?: row.encounter)?.let { Text(encounterLabel(it)) }
                ?: Text("El encuentro no está disponible")
            if (loadingMatch) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state == CallupCardState.CANCELADO) {
                Text("La entrenadora canceló este partido.")
                return@Column
            }
            if (!loadingMatch && match == null && row.encounter != null) Text(error ?: "Se muestran los últimos datos guardados; no se pudo confirmar el partido.")
            AvailabilityActions(row, match)
            // US-11 (P4c): sin botón con convocatoria retirada o partido cancelado.
            if (onOpenLineup != null) match?.takeIf { showLineupButton(state) }?.let { current -> OutlinedButton(onClick = { onOpenLineup(current) }) { Text("Ver alineación del partido") } }
        }
    }
}
