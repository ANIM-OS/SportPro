package dev.dreamteam.sportpro.ui.events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import dev.dreamteam.sportpro.data.repository.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun date14(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(ms))
private fun currentActor14(): String? = FirebaseAuth.getInstance().currentUser?.uid

@Composable
private fun Us14Container(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Volver") }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        content()
    }
}

@Composable
private fun RecordSummary14(record: MatchEventRecord) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${record.minute?.let { "$it'" } ?: "—"} ${record.typeName}", fontWeight = FontWeight.Bold)
            Text("Equipo: ${record.team ?: "Sin especificar"}", fontSize = 12.sp)
            Text("Jugador: ${record.player ?: "Sin especificar"}", fontSize = 12.sp)
            if (record.notes.isNotBlank()) Text("Observaciones: ${record.notes}", fontSize = 12.sp)
            Text("Registrado por: ${record.authorId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Creado: ${date14(record.createdAtMillis)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            record.lastModifiedBy?.let { Text("Último cambio: $it · ${date14(record.lastModifiedAtMillis ?: record.createdAtMillis)}", fontSize = 11.sp) }
            if (record.voided) Text("ANULADO · permanece en el historial", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EventDetailScreen(matchId: String, eventId: String, canEdit: Boolean,
    onBack: () -> Unit, onCorrect: () -> Unit, onVoided: () -> Unit, onShowHistory: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { LiveEventRepository(context) }
    var record by remember(matchId, eventId) { mutableStateOf(repo.find(matchId, eventId)) }
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner, matchId, eventId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) record = repo.find(matchId, eventId)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Us14Container("DETALLE DEL EVENTO", onBack) {
        if (record == null) {
            Text("No se encontró el evento en el almacenamiento local.", color = MaterialTheme.colorScheme.error)
        } else {
            RecordSummary14(record!!)
            OutlinedButton(onClick = onShowHistory, modifier = Modifier.fillMaxWidth()) { Text("VER HISTORIAL DE CAMBIOS") }
            if (canEdit && !record!!.voided) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCorrect, modifier = Modifier.weight(1f)) { Text("CORREGIR") }
                    Button(onClick = { confirm = true }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("ANULAR") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Confirmar anulación") },
        text = { Text("El evento no se eliminará: conservará su historial, autor y datos anteriores. El marcador ignorará los goles anulados.") },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val actor = currentActor14() ?: throw IllegalStateException("Debes iniciar sesión")
                    repo.void(matchId, eventId, actor)
                    record = repo.find(matchId, eventId)
                    confirm = false
                    onVoided()
                } catch (ex: Exception) { error = ex.message; confirm = false }
            }) { Text("ANULAR", color = MaterialTheme.colorScheme.error) }
        }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("CANCELAR") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditScreen(matchId: String, eventId: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { LiveEventRepository(context) }
    val catalog = remember(context) { EventCatalogRepository(context) }
    val original = remember(matchId, eventId) { repo.find(matchId, eventId) }
    val allTypes = remember { catalog.load() }
    var typeId by remember { mutableStateOf(original?.typeId.orEmpty()) }
    var minute by remember { mutableStateOf(original?.minute?.toString().orEmpty()) }
    var team by remember { mutableStateOf(original?.team.orEmpty()) }
    var player by remember { mutableStateOf(original?.player.orEmpty()) }
    var notes by remember { mutableStateOf(original?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val match = demoMatchesUs13.firstOrNull { it.id == matchId }
    Us14Container("CORREGIR EVENTO", onBack) {
        if (original == null) {
            Text("El evento no existe.", color = MaterialTheme.colorScheme.error)
        } else if (original.voided) {
            Text("Los eventos anulados no se pueden corregir.", color = MaterialTheme.colorScheme.error)
        } else {
            Text("Valor original (se conservará en el historial)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            RecordSummary14(original)
            Text("TIPO DE EVENTO", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                allTypes.filter { it.active || it.id == original.typeId }.forEach { type ->
                    FilterChip(selected = type.id == typeId, onClick = { typeId = type.id }, label = { Text(type.name, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White))
                }
            }
            OutlinedTextField(value = minute, onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) minute = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("Minuto (opcional)") }, singleLine = true)
            Text("EQUIPO", fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(match?.homeTeam ?: "Local", match?.awayTeam ?: "Visitante", "").forEach { value ->
                    FilterChip(selected = team == value, onClick = { team = value },
                        label = { Text(value.ifBlank { "Sin especificar" }) })
                }
            }
            OutlinedTextField(value = player, onValueChange = { player = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Jugador (opcional)") })
            OutlinedTextField(value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Observaciones") }, minLines = 3)
            Text("Los demás campos personalizados se conservan sin cambios.", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val parsedMinute = minute.toIntOrNull()
                val selected = allTypes.firstOrNull { it.id == typeId }
                error = when {
                    selected == null -> "Selecciona un tipo de evento."
                    minute.isNotBlank() && (parsedMinute == null || parsedMinute !in 0..130) -> "Minuto inválido (0 a 130)."
                    else -> null
                }
                if (error == null && selected != null) {
                    try {
                        val actor = currentActor14() ?: throw IllegalStateException("Debes iniciar sesión")
                        repo.correct(matchId, eventId, original.copy(
                            typeId = selected.id, typeName = selected.name, minute = parsedMinute,
                            team = team.ifBlank { null }, player = player.trim().ifBlank { null }, notes = notes.trim()
                        ), actor)
                        onSaved()
                    } catch (ex: Exception) { error = ex.message ?: "No se pudo guardar" }
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("GUARDAR CORRECCIÓN", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun EventHistoryScreen(matchId: String, eventId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { LiveEventRepository(context) }
    val entries = remember(matchId, eventId) { repo.history(matchId, eventId) }
    Us14Container("HISTORIAL DEL EVENTO", onBack) {
        if (entries.isEmpty()) Text("Todavía no hay modificaciones. El registro original permanece intacto.")
        entries.forEach { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(entry.action, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Responsable: ${entry.actorId}", fontSize = 12.sp)
                    Text("Fecha: ${date14(entry.changedAtMillis)}", fontSize = 12.sp)
                    Text("DATOS ANTERIORES", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(entry.previous, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("DATOS NUEVOS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(entry.current, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
