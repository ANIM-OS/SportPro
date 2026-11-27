
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

            Text(
                text = "Equipo: ${record.team ?: "Sin especificar"}",
                color = Muted14,
                fontSize = 12.sp
            )

            Text(
                text = "Jugador: ${record.player ?: "Sin especificar"}",
                color = Muted14,
                fontSize = 12.sp
            )

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditScreen(
    matchId: String,
    eventId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current

    val repo = remember(context) {
        LiveEventRepository(context)
    }

    val catalog = remember(context) {
        EventCatalogRepository(context)
    }

    val original = remember(matchId, eventId) {
        repo.find(matchId, eventId)
    }

    val allTypes = remember {
        catalog.load()
    }

    var typeId by remember {
        mutableStateOf(original?.typeId.orEmpty())
    }

    var minute by remember {
        mutableStateOf(original?.minute?.toString().orEmpty())
    }

    var team by remember {
        mutableStateOf(original?.team.orEmpty())
    }

    var player by remember {
        mutableStateOf(original?.player.orEmpty())
    }

    var notes by remember {
        mutableStateOf(original?.notes.orEmpty())
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    val match = demoMatchesUs13.firstOrNull {
        it.id == matchId
    }

    Us14Container(
        title = "CORREGIR EVENTO",
        onBack = onBack
    ) {
        if (original == null) {
            Text(
                text = "El evento no existe.",
                color = MaterialTheme.colorScheme.error
            )
        } else if (original.voided) {
            Text(
                text = "Los eventos anulados no se pueden corregir.",
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                text = "Valor original (se conservará en el historial)",
                color = Orange14,
                fontWeight = FontWeight.Bold
            )

            RecordSummary14(original)

            Text(
                text = "TIPO DE EVENTO",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                allTypes
                    .filter {
                        it.active || it.id == original.typeId
                    }
                    .forEach { type ->
                        FilterChip(
                            selected = type.id == typeId,
                            onClick = {
                                typeId = type.id
                            },
                            label = {
                                Text(
                                    text = type.name,
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Orange14,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
            }

            OutlinedTextField(
                value = minute,
                onValueChange = {
                    if (
                        it.length <= 3 &&
                        it.all(Char::isDigit)
                    ) {
                        minute = it
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Minuto (opcional)")
                },
                singleLine = true
            )

            Text(
                text = "EQUIPO",
                color = Color.White,
                fontSize = 12.sp
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    match?.homeTeam ?: "Local",
                    match?.awayTeam ?: "Visitante",
                    ""
                ).forEach { value ->
                    FilterChip(
                        selected = team == value,
                        onClick = {
                            team = value
                        },
                        label = {
                            Text(
                                value.ifBlank {
                                    "Sin especificar"
                                }
                            )
                        }
                    )
                }
            }

            OutlinedTextField(
                value = player,
                onValueChange = {
                    player = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Jugador (opcional)")
                }
            )

            OutlinedTextField(
                value = notes,
                onValueChange = {
                    notes = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Observaciones")
                },
                minLines = 3
            )

            Text(
                text = "Los demás campos personalizados se conservan sin cambios.",
                fontSize = 11.sp,
                color = Muted14
            )

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val parsedMinute = minute.toIntOrNull()

                    val selected = allTypes.firstOrNull {
                        it.id == typeId
                    }

                    error = when {
                        selected == null ->
                            "Selecciona un tipo de evento."

                        minute.isNotBlank() &&
                                (
                                        parsedMinute == null ||
                                                parsedMinute !in 0..130
                                        ) ->
                            "Minuto inválido (0 a 130)."

                        else -> null
                    }

                    if (error == null && selected != null) {
                        try {
                            val actor = currentActor14()
                                ?: throw IllegalStateException(
                                    "Debes iniciar sesión"
                                )

                            repo.correct(
                                matchId,
                                eventId,
                                original.copy(
                                    typeId = selected.id,
                                    typeName = selected.name,
                                    minute = parsedMinute,
                                    team = team.ifBlank { null },
                                    player = player.trim().ifBlank { null },
                                    notes = notes.trim()
                                ),
                                actor
                            )

                            onSaved()

                        } catch (ex: Exception) {
                            error = ex.message
                                ?: "No se pudo guardar"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Orange14
                )
            ) {
                Text(
                    text = "GUARDAR CORRECCIÓN",
                    fontWeight = FontWeight.Bold
                )
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
                val names = listOf(
                    "typeName" to "Tipo de evento",
                    "minute" to "Minuto",
                    "team" to "Equipo",
                    "player" to "Jugador",
                    "notes" to "Observaciones"
                )

                names.forEach { (key, label) ->
                    val current = snapshotField14(
                        obj,
                        key
                    )

                    val old = if (previous != null) {
                        snapshotField14(previous, key)
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
                            label = key,
                            value = extras
                                .optString(key)
                                .ifBlank {
                                    "Sin especificar"
                                }
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
