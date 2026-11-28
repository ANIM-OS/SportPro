package dev.dreamteam.sportpro.ui.events

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.dreamteam.sportpro.data.model.CoreFields
import dev.dreamteam.sportpro.data.model.EventCatalogRules
import dev.dreamteam.sportpro.data.model.EventTeams
import dev.dreamteam.sportpro.data.model.EventType
import dev.dreamteam.sportpro.data.model.ExtraKinds
import dev.dreamteam.sportpro.data.model.MatchEventContext
import dev.dreamteam.sportpro.data.model.MatchEventRecord
import dev.dreamteam.sportpro.data.model.MatchEvents
import dev.dreamteam.sportpro.data.model.MatchLineupEntry
import dev.dreamteam.sportpro.data.model.PlayerOption
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.data.repository.MatchLineupRepository
import dev.dreamteam.sportpro.data.repository.TeamDirectoryRepository
import dev.dreamteam.sportpro.ui.callups.CallupRepository

/**
 * Cómo se muestran equipo, jugador y datos extra de un evento (nombres, no códigos ni uid).
 * [playerNames] son los nombres que el rol puede ver (MatchEvents.visibleNames); los demás jugadores
 * se muestran como [unknownPlayer] ("Jugador" para el admin, "Compañero" para el tutor).
 */
data class EventLabels(
    val ownName: String = "",
    val opponent: String = "",
    val playerNames: Map<String, String> = emptyMap(),
    val extraLabels: Map<String, String> = emptyMap(),
    val unknownPlayer: String = "Jugador"
) {
    fun team(code: String?) = MatchEvents.teamLabel(code, ownName, opponent)
    fun player(id: String?) = if (id.isNullOrBlank()) "Sin especificar" else playerNames[id] ?: unknownPlayer
    fun extraLabel(key: String) = extraLabels[key] ?: EventCatalogRules.humanize(key)
    /** [isPlayer]: el dato es un jugador (tiene copia en extraNames); si no, se muestra tal cual. */
    fun extraValue(value: String, isPlayer: Boolean) = if (isPlayer) player(value) else value
}

val LocalEventLabels = staticCompositionLocalOf { EventLabels() }

/** Convocados vigentes del partido (dueña): nombres de la plantilla y orden titulares → suplentes → resto. */
data class MatchPlayers(val options: List<PlayerOption> = emptyList(), val names: Map<String, String> = emptyMap(), val loading: Boolean = true)

@Composable
fun rememberMatchPlayers(ctx: MatchEventContext, enabled: Boolean = true): MatchPlayers {
    var selected by remember(ctx.matchId) { mutableStateOf<Set<String>>(emptySet()) }
    var roster by remember(ctx.matchId) { mutableStateOf<List<TeamMember>>(emptyList()) }
    var lineup by remember(ctx.matchId) { mutableStateOf<List<MatchLineupEntry>>(emptyList()) }
    var loading by remember(ctx.matchId) { mutableStateOf(enabled) }
    DisposableEffect(ctx.matchId, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val callups = CallupRepository().coachSelections(ctx.matchId, { rows ->
            selected = rows.filter { it.selected }.map { it.playerId }.toSet(); loading = false
        }, { loading = false })
        val team = TeamDirectoryRepository().listenRoster(ctx.teamId, { roster = it }, { })
        val lineupListener = MatchLineupRepository().observePlayers(ctx.matchId, { lineup = it }, { })
        onDispose { callups.remove(); team.remove(); lineupListener.remove() }
    }
    val names = roster.associate { it.userId to it.playerName }
    return MatchPlayers(MatchEvents.playerOptions(selected, lineup, names), names, loading)
}

/** Estado del formulario de evento; se precarga con el evento al corregir. */
class EventFormState(initial: MatchEventRecord? = null) {
    var typeId by mutableStateOf(initial?.typeId.orEmpty())
    var minute by mutableStateOf(initial?.minute?.toString().orEmpty())
    var team by mutableStateOf(initial?.team.orEmpty())
    var playerId by mutableStateOf(initial?.playerId.orEmpty())
    var notes by mutableStateOf(initial?.notes.orEmpty())
    val extras = mutableStateMapOf<String, String>().apply { initial?.extraFields?.let { putAll(it) } }

    /** Precarga el formulario con un evento que llegó del servidor (corregir). */
    fun load(record: MatchEventRecord) {
        typeId = record.typeId; minute = record.minute?.toString().orEmpty(); team = record.team.orEmpty()
        playerId = record.playerId.orEmpty(); notes = record.notes
        extras.clear(); extras.putAll(record.extraFields)
    }

    /**
     * Evento listo para guardar (nuevo si [base] es null; si no, la corrección de [base]). Copia los
     * nombres del jugador y de los datos extra de clase "player" ([names] = plantilla de la dueña).
     */
    fun toRecord(ctx: MatchEventContext, type: EventType, actorId: String, base: MatchEventRecord?, names: Map<String, String> = emptyMap()): MatchEventRecord {
        val validKeys = type.extraFields.map { it.key }.toSet()
        val fields = extras.filterKeys { it in validKeys }.filterValues { it.isNotBlank() }
        val player = playerId.takeIf { it.isNotBlank() && team == EventTeams.OWN }
        val playerFields = type.extraFields.filter { MatchEvents.isPlayerField(type.id, it) }.map { it.key }.toSet()
        val extraNames = fields.filterKeys { it in playerFields }.mapNotNull { (key, id) ->
            (names[id] ?: base?.extraNames?.get(key)?.takeIf { base.extraFields[key] == id })?.let { key to it.take(80) }
        }.toMap()
        val record = MatchEventRecord(matchId = ctx.matchId, teamId = ctx.teamId, typeId = type.id, typeName = type.name,
            minute = minute.toIntOrNull(), team = team.ifBlank { null }, playerId = player,
            playerName = player?.let { id -> (names[id] ?: base?.playerName?.takeIf { base.playerId == id })?.take(80) },
            notes = notes.trim(), extraFields = fields, extraNames = extraNames, authorId = actorId)
        return base?.let { record.copy(id = it.id, authorId = it.authorId, createdAtMillis = it.createdAtMillis, version = it.version) } ?: record
    }
}

/**
 * Campos del evento: tipo (de los activos del catálogo del equipo), minuto, equipo (nuestro / rival),
 * jugador convocado, datos extra del tipo y observaciones. Los obligatorios del tipo llevan "*";
 * todavía no se exigen (parte 3).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventFormFields(state: EventFormState, types: List<EventType>, ctx: MatchEventContext, players: MatchPlayers) {
    val type = types.firstOrNull { it.id == state.typeId }
    fun marked(field: String) = CoreFields.label(field) + if (type?.requiredCore?.contains(field) == true) " *" else " (opcional)"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tipo de evento *", fontWeight = FontWeight.Bold)
        if (types.isEmpty()) Text("No hay tipos de evento activos en el catálogo de este equipo.", color = MaterialTheme.colorScheme.error)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            types.forEach { option ->
                FilterChip(selected = state.typeId == option.id, label = { Text(option.name) }, onClick = {
                    if (state.typeId != option.id) { state.typeId = option.id; state.extras.clear() }
                })
            }
        }
        OutlinedTextField(state.minute, { if (it.length <= 3 && it.all(Char::isDigit)) state.minute = it },
            label = { Text(marked(CoreFields.MINUTE)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Text(marked(CoreFields.TEAM))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(EventTeams.OWN to ctx.ownName.ifBlank { "Nuestro equipo" }, EventTeams.OPPONENT to ctx.opponent.ifBlank { "Rival" }, "" to "Sin especificar")
                .forEach { (code, label) ->
                    FilterChip(selected = state.team == code, label = { Text(label) }, onClick = {
                        state.team = code
                        if (code != EventTeams.OWN) state.playerId = ""
                    })
                }
        }
        if (state.typeId == MatchEvents.GOAL_TYPE && state.team.isBlank())
            Text("Elige el equipo: un gol sin equipo no cuenta en el marcador.", color = MaterialTheme.colorScheme.error)
        PlayerPicker(marked(CoreFields.PLAYER), state.playerId, players, enabled = state.team == EventTeams.OWN) { state.playerId = it }
        if (state.team != EventTeams.OWN) Text("El jugador se elige entre los convocados de ${ctx.ownName.ifBlank { "nuestro equipo" }}.",
            style = MaterialTheme.typography.bodySmall)
        type?.extraFields?.forEach { field ->
            val label = field.label + if (field.required) " *" else " (opcional)"
            when {
                MatchEvents.isPlayerField(type.id, field) -> {
                    // Cambio: quien sale no puede ser quien entra (también lo exigen las reglas).
                    val other = when (field.key) { "sale" -> state.extras["entra"]; "entra" -> state.extras["sale"]; else -> null }
                    PlayerPicker(label, state.extras[field.key].orEmpty(), players.copy(options = players.options.filter { it.id != other }),
                        enabled = true) { state.extras[field.key] = it }
                }
                field.kind == ExtraKinds.CHOICE -> {
                    Text(label)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        field.options.forEach { option ->
                            FilterChip(selected = state.extras[field.key] == option, label = { Text(option) },
                                onClick = { state.extras[field.key] = option })
                        }
                    }
                }
                else -> OutlinedTextField(state.extras[field.key].orEmpty(),
                    { v -> if (field.kind != ExtraKinds.NUMBER || v.all { it.isDigit() || it == '.' }) state.extras[field.key] = v.take(100) },
                    label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = if (field.kind == ExtraKinds.NUMBER) KeyboardType.Number else KeyboardType.Text))
            }
        }
        OutlinedTextField(state.notes, { state.notes = it.take(500) }, label = { Text(marked(CoreFields.NOTES)) },
            modifier = Modifier.fillMaxWidth(), minLines = 2)
    }
}

@Composable
private fun PlayerPicker(label: String, value: String, players: MatchPlayers, enabled: Boolean, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = players.options.firstOrNull { it.id == value }?.label ?: players.names[value] ?: if (value.isBlank()) "Seleccionar" else "Jugador"
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled && players.options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("$label: $current")
        }
        DropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Sin especificar") }, onClick = { onSelect(""); open = false })
            players.options.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelect(option.id); open = false })
            }
        }
    }
    if (enabled && !players.loading && players.options.isEmpty()) Text("No hay convocados guardados para este partido.", style = MaterialTheme.typography.bodySmall)
}
