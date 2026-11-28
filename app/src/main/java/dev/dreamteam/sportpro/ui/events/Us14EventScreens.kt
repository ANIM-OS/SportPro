
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
import org.json.JSONObject
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
                    text = "${labels.extraLabel(key)}: ${labels.extraValue(value)}",
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

@Composable
fun EventDetailScreen(
    matchId: String,
    eventId: String,
    canEdit: Boolean,
    onBack: () -> Unit,
    onCorrect: () -> Unit,
    onVoided: () -> Unit,
    onShowHistory: () -> Unit
) {
    val context = LocalContext.current

    val repo = remember(context) {
        LiveEventRepository(context)
    }

    var record by remember(matchId, eventId) {
        mutableStateOf(repo.find(matchId, eventId))
    }

    var confirm by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    val owner = androidx.compose.ui.platform
        .LocalLifecycleOwner.current

    DisposableEffect(owner, matchId, eventId) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (
                    event == androidx.lifecycle.Lifecycle.Event.ON_RESUME
                ) {
                    record = repo.find(matchId, eventId)
                }
            }

        owner.lifecycle.addObserver(observer)

        onDispose {
            owner.lifecycle.removeObserver(observer)
        }
    }

    Us14Container(
        title = "DETALLE DEL EVENTO",
        onBack = onBack
    ) {
        if (record == null) {
            Text(
                text = "No se encontró el evento en el almacenamiento local.",
                color = MaterialTheme.colorScheme.error
            )
        } else {
            RecordSummary14(record!!)

            OutlinedButton(
                onClick = onShowHistory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("VER HISTORIAL DE CAMBIOS")
            }

            if (canEdit && !record!!.voided) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCorrect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CORREGIR")
                    }

                    Button(
                        onClick = { confirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("ANULAR")
                    }
                }
            }

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = {
                confirm = false
            },
            title = {
                Text("Confirmar anulación")
            },
            text = {
                Text(
                    "El evento no se eliminará. " +
                            "Se conservarán su historial, " +
                            "autor y datos anteriores. " +
                            "El marcador ignorará los goles anulados."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val actor = currentActor14()
                                ?: throw IllegalStateException(
                                    "Debes iniciar sesión"
                                )

                            repo.void(
                                matchId,
                                eventId,
                                actor
                            )

                            record = repo.find(
                                matchId,
                                eventId
                            )

                            confirm = false
                            onVoided()

                        } catch (ex: Exception) {
                            error = ex.message
                            confirm = false
                        }
                    }
                ) {
                    Text(
                        text = "ANULAR",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirm = false
                    }
                ) {
                    Text("CANCELAR")
                }
            }
        )
    }
}

/**
 * US-14: corregir un evento del partido real. Usa el mismo formulario que el registro (tipos activos
 * del catálogo del equipo, equipo nuestro/rival y convocados). El valor anterior queda en el historial.
 */
@Composable
fun EventEditScreen(
    ctx: MatchEventContext,
    eventId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember(context) { LiveEventRepository(context) }
    val catalog = remember { EventCatalogFirestoreRepository() }
    val original = remember(ctx.matchId, eventId) { repo.find(ctx.matchId, eventId) }
    var activeCatalog by remember { mutableStateOf<List<EventType>>(emptyList()) }
    DisposableEffect(ctx.teamId) {
        val listener = catalog.listenActive(ctx.teamId) { activeCatalog = it }
        onDispose { listener.remove() }
    }
    // El tipo original se sigue ofreciendo aunque se haya desactivado en el catálogo.
    val allTypes = activeCatalog + listOfNotNull(original?.takeIf { o -> activeCatalog.none { it.id == o.typeId } }
        ?.let { EventType(it.typeId, it.typeName, active = false) })
    val players = rememberMatchPlayers(ctx)
    val state = remember(eventId) { EventFormState(original) }
    var error by remember { mutableStateOf<String?>(null) }

    Us14Container(title = "CORREGIR EVENTO", onBack = onBack) {
        if (original == null) {
            Text(text = "El evento no existe.", color = MaterialTheme.colorScheme.error)
        } else if (original.voided) {
            Text(text = "Los eventos anulados no se pueden corregir.", color = MaterialTheme.colorScheme.error)
        } else {
            Text(text = "Valor original (se conservará en el historial)", color = Orange14, fontWeight = FontWeight.Bold)
            RecordSummary14(original)
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) { EventFormFields(state, allTypes, ctx, players) }
            }
            error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    error = MatchEvents.validate(state.typeId, state.minute)
                    val selected = allTypes.firstOrNull { it.id == state.typeId }
                    if (error == null && selected != null) {
                        try {
                            val actor = currentActor14() ?: throw IllegalStateException("Debes iniciar sesión")
                            repo.correct(ctx.matchId, eventId, state.toRecord(ctx, selected, actor, original), actor)
                            onSaved()
                        } catch (ex: Exception) {
                            error = "No se pudo guardar la corrección en el celular."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange14)
            ) {
                Text(text = "GUARDAR CORRECCIÓN", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun snapshotValue14(
    raw: String
): JSONObject? {
    return try {
        JSONObject(raw)
    } catch (_: Exception) {
        null
    }
}

private fun snapshotField14(
    obj: JSONObject?,
    key: String
): String {
    if (obj == null || obj.isNull(key)) {
        return "Sin especificar"
    }

    val value = obj.optString(key).trim()

    return value.ifBlank {
        "Sin especificar"
    }
}

@Composable
private fun HistoryField14(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label.uppercase(Locale("es", "PE")),
            fontSize = 10.sp,
            color = if (highlight) Orange14 else Muted14,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = value,
            fontSize = 13.sp,
            color = Color.White
        )
    }
}

@Composable
private fun HistorySnapshot14(
    title: String,
    raw: String,
    previous: JSONObject? = null
) {
    val obj = snapshotValue14(raw)

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
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Text(
                text = title,
                color = Orange14,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            if (obj == null) {
                Text(
                    text = "No se pudieron interpretar los datos de esta versión.",
                    color = Muted14,
                    fontSize = 12.sp
                )
            } else {
                val labels = LocalEventLabels.current
                val names = listOf(
                    "typeName" to "Tipo de evento",
                    "minute" to "Minuto",
                    "team" to "Equipo",
                    "playerId" to "Jugador",
                    "notes" to "Observaciones"
                )
                // Equipo (OWN/OPPONENT) y jugador (uid) se muestran con su nombre.
                fun shown(source: JSONObject, key: String): String {
                    val raw = snapshotField14(source, key)
                    return when {
                        raw == "Sin especificar" -> raw
                        key == "team" -> labels.team(raw)
                        key == "playerId" -> labels.player(raw)
                        else -> raw
                    }
                }
                names.forEach { (key, label) ->
                    val current = shown(obj, key)
                    val old = if (previous != null) {
                        shown(previous, key)
                    } else {
                        null
                    }

                    val visible = if (
                        key == "minute" &&
                        current != "Sin especificar"
                    ) {
                        "$current'"
                    } else {
                        current
                    }

                    HistoryField14(
                        label = label,
                        value = visible,
                        highlight = previous != null &&
                                current != old
                    )
                }

                val voided = obj.optBoolean(
                    "voided",
                    false
                )

                HistoryField14(
                    label = "Estado",
                    value = if (voided) {
                        "ANULADO"
                    } else {
                        "VÁLIDO"
                    },
                    highlight = previous != null &&
                            voided != previous.optBoolean(
                        "voided",
                        false
                    )
                )

                val extras = obj.optJSONObject(
                    "extraFields"
                )

                if (extras != null) {
                    val keys = extras.keys()

                    while (keys.hasNext()) {
                        val key = keys.next()

                        HistoryField14(
                            label = labels.extraLabel(key),
                            value = extras
                                .optString(key)
                                .ifBlank {
                                    "Sin especificar"
                                }
                                .let { labels.extraValue(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EventHistoryScreen(
    matchId: String,
    eventId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val repo = remember(context) {
        LiveEventRepository(context)
    }

    val record = remember(matchId, eventId) {
        repo.find(matchId, eventId)
    }

    val entries = remember(matchId, eventId) {
        repo.history(matchId, eventId)
    }

    Us14Container(
        title = "HISTORIAL DEL EVENTO",
        onBack = onBack
    ) {
        if (record != null) {
            RecordSummary14(record)
        }

        if (entries.isEmpty()) {
            Text(
                text = "Este evento aún no tiene correcciones o anulaciones.",
                color = Muted14
            )
        }

        entries.forEachIndexed { index, entry ->
            val before = snapshotValue14(
                entry.previous
            )

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
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        text = "${index + 1}. ${
                            if (entry.action == "ANULACION") {
                                "EVENTO ANULADO"
                            } else {
                                "EVENTO CORREGIDO"
                            }
                        }",
                        color = if (
                            entry.action == "ANULACION"
                        ) {
                            Color(0xFFFF5353)
                        } else {
                            Orange14
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Text(
                        text = "Realizado por",
                        color = Muted14,
                        fontSize = 11.sp
                    )

                    ActorName14(
                        actorId = entry.actorId,
                        textSize = 13
                    )

                    Text(
                        text = "Fecha y hora: ${
                            date14(entry.changedAtMillis)
                        }",
                        color = Muted14,
                        fontSize = 12.sp
                    )
                }
            }

            HistorySnapshot14(
                title = "DATO ANTERIOR · REFERENCIA HISTÓRICA",
                raw = entry.previous
            )

            HistorySnapshot14(
                title = "DATO NUEVO · DESPUÉS DE LA MODIFICACIÓN",
                raw = entry.current,
                previous = before
            )
        }

        Text(
            text = "Se conservan las versiones anteriores y el identificador del responsable de cada cambio.",
            color = Muted14,
            fontSize = 11.sp
        )
    }
}
