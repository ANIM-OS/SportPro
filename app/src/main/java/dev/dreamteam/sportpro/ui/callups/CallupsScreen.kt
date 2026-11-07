package dev.dreamteam.sportpro.ui.callups

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dreamteam.sportpro.BuildConfig
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.data.repository.TeamDirectoryRepository
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

internal fun dateLabel(millis: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

@Composable
fun CallupsScreen(canManage: Boolean, isGuardian: Boolean = false, onBack: () -> Unit) {
    if (!BuildConfig.LOCAL_FIREBASE) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("Convocatorias", style = MaterialTheme.typography.headlineSmall)
            Text("Esta función está en pruebas y todavía no está habilitada en la app compartida.")
        }
        BackHandler(onBack = onBack)
        return
    }
    var lineupMatch by remember { mutableStateOf<CallupMatch?>(null) }
    lineupMatch?.let {
        dev.dreamteam.sportpro.ui.lineups.MatchLineupScreen(it, canManage && it.ownerId == com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid) { lineupMatch = null }
        return
    }
    val directory = remember { TeamDirectoryRepository() }
    val repository = remember { CallupRepository() }
    var teams by remember { mutableStateOf<List<Team>>(emptyList()) }
    var teamId by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<CallupMatch>>(emptyList()) }
    var matchId by remember { mutableStateOf("") }
    var roster by remember { mutableStateOf<List<TeamMember>>(emptyList()) }
    var records by remember { mutableStateOf<List<PlayerCallup>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dirty by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var loadingTeams by remember { mutableStateOf(true) }
    var loadingRoster by remember { mutableStateOf(false) }
    var loadingMatches by remember { mutableStateOf(false) }
    var loadingSelection by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var editingMatch by remember { mutableStateOf(false) }
    var createMatch by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val leave = { if (!busy) { if (dirty) confirmExit = true else onBack() } }
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
        matches = emptyList(); roster = emptyList(); records = emptyList()
        matchId = ""; selected = emptySet(); dirty = false; error = null
        loadingRoster = teamId.isNotBlank(); loadingMatches = teamId.isNotBlank()
        val a = if (teamId.isBlank()) null else directory.listenRoster(teamId, {
            roster = it; loadingRoster = false
        }, { roster = emptyList(); error = it; loadingRoster = false })
        val b = if (teamId.isBlank()) null else repository.matches(teamId, {
            matches = it; loadingMatches = false
        }, { matches = emptyList(); error = it; loadingMatches = false })
        onDispose { a?.remove(); b?.remove() }
    }
    DisposableEffect(matchId) {
        records = emptyList(); selected = emptySet(); dirty = false
        loadingSelection = matchId.isNotBlank()
        val listener = if (matchId.isBlank()) null else repository.coachSelections(matchId, {
            records = it; loadingSelection = false
            if (!dirty && !busy) selected = it.filter { record -> record.selected }.map { record -> record.playerId }.toSet()
        }, { error = it; loadingSelection = true })
        onDispose { listener?.remove() }
    }
    val eligible = roster.filter { it.category == match?.category }.distinctBy { it.userId }
    val eligibleIds = eligible.map { it.userId }.toSet()
    val invalid = selected - eligibleIds
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false },
        title = { Text("¿Salir sin guardar?") }, text = { Text("Se perderán los cambios pendientes de la convocatoria.") },
        confirmButton = { TextButton(onClick = onBack) { Text("Salir") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Seguir editando") } })
    if ((editingMatch || createMatch) && team != null) {
        MatchEditor(team, if (createMatch) null else match, repository,
            onDismiss = { editingMatch = false; createMatch = false },
            onSaved = { editingMatch = false; createMatch = false; message = "Partido guardado. Puedes seleccionarlo." })
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = leave, enabled = !busy) { Text("← Volver") }
        Text("Convocatorias", style = MaterialTheme.typography.headlineSmall)
        if (canManage) {
            if (loadingTeams) LinearProgressIndicator(Modifier.fillMaxWidth())
            Selector("Equipo", team?.name, teams.map { it.id to it.name }, !dirty && !busy) { teamId = it; message = null }
            if (!loadingTeams && teams.isEmpty()) Text("No tienes equipos propios disponibles.")
            if (team != null) {
                OutlinedButton(enabled = !busy && !dirty, onClick = { createMatch = true }) { Text("Registrar partido") }
                if (loadingMatches) LinearProgressIndicator(Modifier.fillMaxWidth())
                Selector("Partido", match?.let { "${it.opponent} · ${dateLabel(it.kickoff.toDate().time)}" },
                    matches.map { it.id to "${it.opponent} · ${it.category} · ${dateLabel(it.kickoff.toDate().time)}" }, !busy && !dirty) { matchId = it; message = null }
                if (!loadingMatches && matches.isEmpty()) Text("No hay partidos registrados para este equipo.")
            }
            if (match != null) {
                Text("Rival: ${match.opponent}\nCategoría: ${match.category}\n${dateLabel(match.kickoff.toDate().time)} (hora local)")
                OutlinedButton(enabled = !busy && !dirty, onClick = { editingMatch = true }) { Text("Editar datos del partido") }
                OutlinedButton(enabled = !busy && !dirty && !loadingSelection, onClick = { lineupMatch = match }) { Text("Alineación del partido") }
                val future = match.kickoff.toDate().time > System.currentTimeMillis()
                if (!future) Text("El partido ya comenzó: la convocatoria es de solo lectura.")
                if (loadingRoster || loadingSelection) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!loadingRoster && eligible.isEmpty()) Text("No hay jugadores de esta categoría en la plantilla.")
                eligible.forEach { player ->
                    Row {
                        Checkbox(modifier = Modifier.semantics { contentDescription = "Convocar a ${player.playerName}" }, checked = player.userId in selected, enabled = future && !busy && !loadingSelection,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + player.userId else selected - player.userId
                                dirty = true; message = null
                            })
                        Text(player.playerName, Modifier.padding(top = 12.dp))
                    }
                }
                if (invalid.isNotEmpty() && !loadingRoster) {
                    Text("${invalid.size} selecciones ya no pertenecen a esta categoría.")
                    TextButton(enabled = !busy, onClick = { selected = selected.intersect(eligibleIds); dirty = true }) { Text("Retirar selecciones no válidas") }
                }
                Text("${selected.size} jugadores seleccionados")
                Text("Cierre de respuestas: ${dateLabel(match.responseDeadline.toDate().time)}")
                CoachAvailability(match.id, match.teamId, records, roster)
                if (dirty) Text("Guarda o descarta los cambios antes de cambiar de equipo o partido.")
                Button(enabled = dirty && !busy && !loadingRoster && !loadingSelection && invalid.isEmpty() && future && team != null,
                    onClick = {
                        busy = true; error = null; message = null
                        repository.saveSelections(match, selected, records)
                            .addOnSuccessListener { dirty = false; message = "Convocatoria guardada. Los jugadores pueden consultar su estado." }
                            .addOnFailureListener { error = "No se completó el guardado. Algunos cambios pueden haberse aplicado; vuelve a guardar para completar. ${it.localizedMessage}" }
                            .addOnCompleteListener { busy = false }
                    }) { Text(if (busy) "Guardando…" else "Guardar convocatoria") }
                if (dirty) TextButton(enabled = !busy, onClick = {
                    selected = records.filter { it.selected }.map { it.playerId }.toSet(); dirty = false; message = null
                }) { Text("Descartar cambios") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            message?.let { Text(it) }
            HorizontalDivider()
        }
        MyCallups(repository) { lineupMatch = it }
        if (isGuardian) GuardianCallups(repository)
    }
}

@Composable
private fun Selector(label: String, value: String?, options: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("$label: ${value ?: "Seleccionar"}") }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(id) }) }
        }
    }
}

@Composable
private fun MatchEditor(team: Team, existing: CallupMatch?, repository: CallupRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var category by remember { mutableStateOf(existing?.category ?: "") }
    var opponent by remember { mutableStateOf(existing?.opponent ?: "") }
    var kickoff by remember { mutableStateOf(existing?.kickoff?.toDate()?.time ?: 0L) }
    var deadline by remember { mutableStateOf(existing?.responseDeadline?.toDate()?.time ?: 0L) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (existing == null) "Registrar partido" else "Editar partido") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(team.name)
                Selector("Categoría", category.ifBlank { null }, team.categories.map { it to it }, existing == null && !busy) { category = it }
                Text("Los datos se registran aquí una vez y se muestran automáticamente en la convocatoria.")
                OutlinedTextField(opponent, { opponent = it.take(100) }, label = { Text("Rival") }, enabled = !busy, singleLine = true)
                OutlinedButton(enabled = !busy, onClick = {
                    val calendar = Calendar.getInstance().apply { if (kickoff > 0) timeInMillis = kickoff }
                    DatePickerDialog(context, { _, year, month, day ->
                        TimePickerDialog(context, { _, hour, minute ->
                            calendar.set(year, month, day, hour, minute, 0); calendar.set(Calendar.MILLISECOND, 0)
                            kickoff = calendar.timeInMillis
                            if (deadline == 0L) deadline = kickoff
                        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
                    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                }) { Text(if (kickoff == 0L) "Elegir fecha y hora" else dateLabel(kickoff)) }
                Text("Cierre para responder disponibilidad")
                OutlinedButton(enabled = !busy, onClick = {
                    val calendar = Calendar.getInstance().apply { if (deadline > 0) timeInMillis = deadline }
                    DatePickerDialog(context, { _, year, month, day ->
                        TimePickerDialog(context, { _, hour, minute ->
                            calendar.set(year, month, day, hour, minute, 0); calendar.set(Calendar.MILLISECOND, 0)
                            deadline = calendar.timeInMillis
                        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
                    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                }) { Text(if (deadline == 0L) "Elegir cierre" else dateLabel(deadline)) }
                Text("El cierre debe ser futuro y no posterior al inicio del partido.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !busy && category in team.categories && opponent.isNotBlank() && kickoff > System.currentTimeMillis() && deadline > System.currentTimeMillis() && deadline <= kickoff, onClick = {
            busy = true
            repository.saveMatch(existing, team.id, category, opponent, kickoff, deadline)
                .addOnSuccessListener { onSaved() }
                .addOnFailureListener { error = it.localizedMessage; busy = false }
        }) { Text(if (busy) "Guardando…" else "Guardar partido") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } })
}

@Composable
private fun MyCallups(repository: CallupRepository, onOpenLineup: (CallupMatch) -> Unit) {
    var rows by remember { mutableStateOf<List<PlayerCallup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(repository) {
        val listener = repository.mySelections({ rows = it; loading = false; error = null }, { rows = emptyList(); loading = false; error = it })
        onDispose { listener.remove() }
    }
    Text("Mis convocatorias", style = MaterialTheme.typography.titleLarge)
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (!loading && rows.isEmpty() && error == null) Text("Todavía no tienes convocatorias.")
    rows.forEach { row -> key(row.id) { PlayerMatchCard(row, repository, onOpenLineup) } }
}

@Composable
private fun PlayerMatchCard(row: PlayerCallup, repository: CallupRepository, onOpenLineup: (CallupMatch) -> Unit) {
    var match by remember { mutableStateOf<CallupMatch?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(row.matchId) {
        val listener = repository.match(row.matchId, { match = it; error = null }, { match = null; error = it })
        onDispose { listener.remove() }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (!row.selected) "Convocatoria retirada" else if (match != null) "Estás convocado" else "Convocatoria pendiente de comprobar")
            match?.let { Text("vs ${it.opponent}\n${it.category}\n${dateLabel(it.kickoff.toDate().time)} (hora local)") }
                ?: Text(error ?: "El encuentro no está disponible")
            AvailabilityActions(row, match)
            match?.let { current -> OutlinedButton(onClick = { onOpenLineup(current) }) { Text("Ver alineación del partido") } }
        }
    }
}
