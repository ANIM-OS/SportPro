
package dev.dreamteam.sportpro.ui.events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dev.dreamteam.sportpro.data.model.EventAuditEntry
import dev.dreamteam.sportpro.data.model.EventType
import dev.dreamteam.sportpro.data.model.MatchEventContext
import dev.dreamteam.sportpro.data.model.MatchEventRecord
import dev.dreamteam.sportpro.data.model.MatchEvents
import dev.dreamteam.sportpro.data.repository.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Orange14 = Color(0xFFFF6600)
private val Bg14 = Color(0xFF0D0F12)
private val Panel14 = Color(0xFF161920)
private val Muted14 = Color(0xFF9A9CA2)

private fun date14(ms: Long): String =
    SimpleDateFormat(
        "dd/MM/yyyy · HH:mm:ss",
        Locale("es", "PE")
    ).format(Date(ms))

private fun currentActor14(): String? =
    FirebaseAuth.getInstance().currentUser?.uid

private fun profileName14(
    data: Map<String, Any>?
): String? {
    if (data == null) return null

    val keys = listOf(
        "displayName",
        "fullName",
        "nombreCompleto",
        "name",
        "nombre",
        "username"
    )

    for (key in keys) {
        val name = (data[key] as? String)
            ?.trim()
            .orEmpty()

        if (name.isNotBlank()) return name
    }

    val first = (data["firstName"] as? String)
        ?.trim()
        .orEmpty()

    val last = (data["lastName"] as? String)
        ?.trim()
        .orEmpty()

    return "$first $last"
        .trim()
        .takeIf { it.isNotBlank() }
}

@Composable
private fun ActorName14(
    actorId: String?,
    textSize: Int = 12
) {
    val signedIn = FirebaseAuth
        .getInstance()
        .currentUser

    var display by remember(actorId) {
        mutableStateOf("Buscando nombre...")
    }

    DisposableEffect(actorId) {
        val uid = actorId?.takeIf {
            it.isNotBlank()
        }

        if (uid == null) {
            display = "No registrado"
            onDispose { }
        } else {
            val current = signedIn?.takeIf {
                it.uid == uid
            }

            val authName = current
                ?.displayName
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            val fallback = authName
                ?: current?.email?.let {
                    "Cuenta: $it"
                }
                ?: "Nombre no disponible"

            display = fallback

            var active = true

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { document ->
                    if (active) {
                        display = profileName14(
                            document.data
                        ) ?: fallback
                    }
                }
                .addOnFailureListener {
                    if (active) {
                        display = fallback
                    }
                }

            onDispose {
                active = false
            }
        }
    }

    Text(
        text = display,
        fontSize = textSize.sp,
        color = Color.White
    )
}

@Composable
private fun Us14Container(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg14)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(
                text = "← Volver",
                color = Orange14
            )
        }

        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        content()
    }
}

@Composable
private fun RecordSummary14(
    record: MatchEventRecord
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Panel14
        ),
        border = BorderStroke(
            1.dp,
            Color(0xFF2A2E35)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = "${record.minute?.let { "$it'" } ?: "—"} ${record.typeName}",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            val labels = LocalEventLabels.current

            Text(
                text = "Equipo: ${labels.team(record.team)}",
                color = Muted14,
                fontSize = 12.sp
            )

            Text(
                text = "Jugador: ${labels.player(record.playerId)}",
                color = Muted14,
                fontSize = 12.sp
            )

            record.extraFields.forEach { (key, value) ->
                Text(
                    text = "${labels.extraLabel(key)}: ${labels.extraValue(value, isPlayer = key in record.extraNames)}",
                    color = Muted14,
                    fontSize = 12.sp
                )
            }

            if (record.notes.isNotBlank()) {
                Text(
                    text = "Observaciones: ${record.notes}",
                    color = Muted14,
                    fontSize = 12.sp
                )
            }

            Text(
                text = "Creado por",
                color = Orange14,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            ActorName14(record.authorId)

            Text(
                text = "Fecha de creación: ${date14(record.createdAtMillis)}",
                color = Muted14,
                fontSize = 11.sp
            )

            record.lastModifiedBy?.let { actor ->
                Text(
                    text = "Modificado por",
                    color = Orange14,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                ActorName14(actor)

                record.lastModifiedAtMillis?.let { ms ->
                    Text(
                        text = "Fecha de modificación: ${date14(ms)}",
                        color = Muted14,
                        fontSize = 11.sp
                    )
                }
            }

            if (record.pending) {
                Text(
                    text = "Pendiente de sincronizar",
                    color = Orange14,
                    fontSize = 11.sp
                )
            }

            if (record.voided) {
                Text(
                    text = "ANULADO · permanece en el historial",
                    color = Color(0xFFFF5353),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * US-14: detalle de un evento (Firestore, en vivo). [canEdit]: dueña aprobada y dentro del plazo de
 * corrección (7 días). Anular no borra nada: el evento queda visible como anulado.
 */
@Composable
fun EventDetailScreen(
    matchId: String,
    eventId: String,
    canEdit: Boolean,
    onBack: () -> Unit,
    onCorrect: () -> Unit,
    onVoided: () -> Unit,
    onShowHistory: () -> Unit,
    onError: (String) -> Unit = {}
) {
    val store = remember { FirestoreMatchEventStore() }
    var record by remember(matchId, eventId) { mutableStateOf<MatchEventRecord?>(null) }
    var loading by remember(matchId, eventId) { mutableStateOf(true) }
    var loadError by remember(matchId, eventId) { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    DisposableEffect(matchId, eventId) {
        val subscription = store.observeEvent(matchId, eventId, { record = it; loading = false; loadError = null },
            { loadError = it; loading = false })
        onDispose { subscription.remove() }
    }

    Us14Container(title = "DETALLE DEL EVENTO", onBack = onBack) {
        val current = record
        when {
            loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            loadError != null -> Text(text = loadError!!, color = MaterialTheme.colorScheme.error)
            current == null -> Text(text = "Este evento ya no está disponible.", color = MaterialTheme.colorScheme.error)
            else -> {
                RecordSummary14(current)
                OutlinedButton(onClick = onShowHistory, modifier = Modifier.fillMaxWidth()) { Text("VER HISTORIAL DE CAMBIOS") }
                if (canEdit && !current.voided) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCorrect, modifier = Modifier.weight(1f)) { Text("CORREGIR") }
                        Button(onClick = { confirm = true }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("ANULAR") }
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Confirmar anulación") },
            text = { Text("El evento no se eliminará. Se conservarán su historial, autor y datos anteriores. El marcador ignorará los goles anulados.") },
            confirmButton = {
                TextButton(onClick = {
                    val actor = currentActor14()
                    val before = record
                    confirm = false
                    if (actor == null || before == null) onError("Inicia sesión para anular eventos.")
                    else {
                        // Se aplica al instante en la caché (también sin conexión); el servidor confirma después.
                        store.void(before, actor).addOnFailureListener { onError(eventWriteErrorMessage(it)) }
                        onVoided()
                    }
                }) { Text(text = "ANULAR", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("CANCELAR") } }
        )
    }
}

/**
 * US-14: corregir un evento. Mismo formulario que el registro (tipos activos del catálogo del equipo,
 * nuestro/rival y convocados). El valor anterior queda en revisions/ y se ve en el historial.
 */
@Composable
fun EventEditScreen(
    ctx: MatchEventContext,
    eventId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onError: (String) -> Unit = {}
) {
    val store = remember { FirestoreMatchEventStore() }
    val catalog = remember { EventCatalogFirestoreRepository() }
    var original by remember(ctx.matchId, eventId) { mutableStateOf<MatchEventRecord?>(null) }
    var loading by remember(ctx.matchId, eventId) { mutableStateOf(true) }
    val state = remember(eventId) { EventFormState() }
    var loaded by remember(eventId) { mutableStateOf(false) }
    DisposableEffect(ctx.matchId, eventId) {
        val subscription = store.observeEvent(ctx.matchId, eventId, { event ->
            original = event; loading = false
            // El formulario se precarga una sola vez; los cambios posteriores no pisan lo que se está editando.
            if (event != null && !loaded) { state.load(event); loaded = true }
        }, { loading = false })
        onDispose { subscription.remove() }
    }
    var activeCatalog by remember { mutableStateOf<List<EventType>>(emptyList()) }
    DisposableEffect(ctx.teamId) {
        val listener = catalog.listenActive(ctx.teamId) { activeCatalog = it }
        onDispose { listener.remove() }
    }
    val current = original
    // El tipo original se sigue ofreciendo aunque se haya desactivado en el catálogo.
    val allTypes = activeCatalog + listOfNotNull(current?.takeIf { o -> activeCatalog.none { it.id == o.typeId } }
        ?.let { EventType(it.typeId, it.typeName, active = false) })
    val players = rememberMatchPlayers(ctx)
    var error by remember { mutableStateOf<String?>(null) }

    Us14Container(title = "CORREGIR EVENTO", onBack = onBack) {
        when {
            loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            current == null -> Text(text = "El evento no existe.", color = MaterialTheme.colorScheme.error)
            current.voided -> Text(text = "Los eventos anulados no se pueden corregir.", color = MaterialTheme.colorScheme.error)
            !MatchEvents.canCorrect(System.currentTimeMillis(), ctx.kickoffMillis) ->
                Text(text = "El plazo para corregir terminó (7 días después del inicio).", color = MaterialTheme.colorScheme.error)
            else -> {
                Text(text = "Valor original (se conservará en el historial)", color = Orange14, fontWeight = FontWeight.Bold)
                RecordSummary14(current)
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) { EventFormFields(state, allTypes, ctx, players) }
                }
                error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        error = MatchEvents.validate(state.typeId, state.minute, state.extras)
                        val selected = allTypes.firstOrNull { it.id == state.typeId }
                        val actor = currentActor14()
                        if (error == null && selected != null) {
                            if (actor == null) error = "Debes iniciar sesión"
                            else {
                                store.correct(current, state.toRecord(ctx, selected, actor, current, players.names), actor)
                                    .addOnFailureListener { onError(eventWriteErrorMessage(it)) }
                                onSaved()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange14)
                ) { Text(text = "GUARDAR CORRECCIÓN", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private const val UNSET14 = "Sin especificar"

/** Valor de un campo de una versión (mapa con las claves de Firestore). */
private fun snapshotField14(data: Map<String, Any?>?, key: String): String {
    val value = data?.get(key) ?: return UNSET14
    return value.toString().trim().ifBlank { UNSET14 }
}

@Composable
private fun HistoryField14(label: String, value: String, highlight: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(text = label.uppercase(Locale("es", "PE")), fontSize = 10.sp, color = if (highlight) Orange14 else Muted14, fontWeight = FontWeight.SemiBold)
        Text(text = value, fontSize = 13.sp, color = Color.White)
    }
}

/** Una versión del evento; resalta lo que cambió respecto de [previous]. Nombres según el rol (LocalEventLabels). */
@Composable
private fun HistorySnapshot14(title: String, data: Map<String, Any?>, previous: Map<String, Any?>? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel14), border = BorderStroke(1.dp, Color(0xFF2A2E35)), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text(text = title, color = Orange14, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val labels = LocalEventLabels.current
            // Equipo (OWN/OPPONENT) y jugador (uid) se muestran con su nombre visible para el rol.
            fun shown(source: Map<String, Any?>, key: String): String {
                val raw = snapshotField14(source, key)
                return when {
                    raw == UNSET14 -> raw
                    key == "team" -> labels.team(raw)
                    key == "playerId" -> labels.player(raw)
                    key == "minute" -> "$raw'"
                    else -> raw
                }
            }
            listOf("typeName" to "Tipo de evento", "minute" to "Minuto", "team" to "Equipo", "playerId" to "Jugador", "notes" to "Observaciones")
                .forEach { (key, label) ->
                    val current = shown(data, key)
                    HistoryField14(label = label, value = current, highlight = previous != null && current != shown(previous, key))
                }
            val voided = data["voided"] == true
            HistoryField14(label = "Estado", value = if (voided) "ANULADO" else "VÁLIDO",
                highlight = previous != null && voided != (previous["voided"] == true))
            val extras = (data["extras"] as? Map<*, *>).orEmpty()
            val extraNames = (data["extraNames"] as? Map<*, *>).orEmpty()
            extras.forEach { (key, value) ->
                HistoryField14(label = labels.extraLabel(key.toString()),
                    value = labels.extraValue(value?.toString().orEmpty().ifBlank { UNSET14 }, isPlayer = extraNames.containsKey(key)))
            }
        }
    }
}

/** US-14: historial de un evento desde revisions/ (quién, cuándo, valor anterior y nuevo), en vivo. */
@Composable
fun EventHistoryScreen(matchId: String, eventId: String, onBack: () -> Unit) {
    val store = remember { FirestoreMatchEventStore() }
    var record by remember(matchId, eventId) { mutableStateOf<MatchEventRecord?>(null) }
    var entries by remember(matchId, eventId) { mutableStateOf<List<EventAuditEntry>>(emptyList()) }
    var error by remember(matchId, eventId) { mutableStateOf<String?>(null) }
    DisposableEffect(matchId, eventId) {
        val a = store.observeEvent(matchId, eventId, { record = it }, { error = it })
        val b = store.observeHistory(matchId, eventId, { entries = it }, { error = it })
        onDispose { a.remove(); b.remove() }
    }
    Us14Container(title = "HISTORIAL DEL EVENTO", onBack = onBack) {
        record?.let { RecordSummary14(it) }
        error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
        if (entries.isEmpty()) Text(text = "Este evento aún no tiene correcciones o anulaciones.", color = Muted14)
        entries.forEachIndexed { index, entry ->
            val voidAction = entry.action == "VOID"
            Card(colors = CardDefaults.cardColors(containerColor = Panel14), border = BorderStroke(1.dp, Color(0xFF2A2E35)), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(text = "${index + 1}. ${if (voidAction) "EVENTO ANULADO" else "EVENTO CORREGIDO"} · versión ${entry.version}",
                        color = if (voidAction) Color(0xFFFF5353) else Orange14, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(text = "Realizado por", color = Muted14, fontSize = 11.sp)
                    ActorName14(actorId = entry.actorId, textSize = 13)
                    Text(text = "Fecha y hora: ${if (entry.changedAtMillis > 0) date14(entry.changedAtMillis) else "pendiente de sincronizar"}",
                        color = Muted14, fontSize = 12.sp)
                }
            }
            HistorySnapshot14(title = "DATO ANTERIOR · REFERENCIA HISTÓRICA", data = entry.previous)
            HistorySnapshot14(title = "DATO NUEVO · DESPUÉS DE LA MODIFICACIÓN", data = entry.current, previous = entry.previous)
        }
        Text(text = "Se conservan las versiones anteriores y el responsable de cada cambio.", color = Muted14, fontSize = 11.sp)
    }
}
