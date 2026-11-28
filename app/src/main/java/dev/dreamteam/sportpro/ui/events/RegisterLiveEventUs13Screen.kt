package dev.dreamteam.sportpro.ui.events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import dev.dreamteam.sportpro.data.model.CoreFields
import dev.dreamteam.sportpro.data.model.EventType
import dev.dreamteam.sportpro.data.model.ExtraKinds
import dev.dreamteam.sportpro.data.repository.EventCatalogFirestoreRepository
import dev.dreamteam.sportpro.data.repository.LiveEventRepository
import dev.dreamteam.sportpro.data.repository.MatchEventRecord

/**
 * US-13: ofrece los tipos ACTIVOS del catálogo de US-12 (Firestore, en vivo) del equipo del partido o,
 * si el partido no tiene equipo, los globales. Los eventos aún se guardan en el celular (parte 3).
 * Los obligatorios del tipo se marcan con * pero todavía no se exigen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterLiveEventUs13Screen(matchId: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val catalogRepo = remember { EventCatalogFirestoreRepository() }
    val eventRepo = remember(context) { LiveEventRepository(context) }
    // Los partidos de esta pantalla aún no tienen equipo (parte 2): se usan los tipos globales.
    val matchTeamId: String? = null
    var activeTypes by remember(matchId) { mutableStateOf<List<EventType>>(emptyList()) }
    DisposableEffect(matchId, matchTeamId) {
        val listener = catalogRepo.listenActive(matchTeamId) { activeTypes = it }
        onDispose { listener.remove() }
    }
    var chosenTypeId by remember { mutableStateOf("") }
    var minute by remember { mutableStateOf("") }
    var selectedTeam by remember { mutableStateOf("") }
    var player by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val extraFields = remember { mutableStateMapOf<String, String>() }
    val selectedType = activeTypes.firstOrNull { it.id == chosenTypeId }
    val match = demoMatchesUs13.firstOrNull { it.id == matchId }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Volver") }
        Text("REGISTRO EN VIVO", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("${match?.homeTeam ?: "Partido"} vs ${match?.opponent ?: matchId}",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text("Por ahora los eventos se guardan solo en este celular.",
            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Nuevo evento", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("TIPO DE EVENTO *", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (activeTypes.isEmpty()) {
                    Text("No hay tipos de evento activos. Actívalos en el catálogo de eventos del equipo.", color = MaterialTheme.colorScheme.error)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeTypes.forEach { type ->
                            FilterChip(
                                selected = chosenTypeId == type.id,
                                onClick = { chosenTypeId = type.id; message = ""; extraFields.clear() },
                                label = { Text(type.name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
                fun fieldLabel(field: String) = CoreFields.label(field).uppercase() +
                    if (selectedType?.requiredCore?.contains(field) == true) " *" else " (OPCIONAL)"
                Text(fieldLabel(CoreFields.MINUTE), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = minute,
                    onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) minute = it },
                    placeholder = { Text("Ej. 45") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text(fieldLabel(CoreFields.TEAM), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(match?.homeTeam ?: "Local", match?.awayTeam ?: "Visitante", "Sin especificar").forEachIndexed { index, name ->
                        val value = if (index == 2) "" else name
                        FilterChip(selected = selectedTeam == value, onClick = { selectedTeam = value },
                            label = { Text(if (index == 0) "LOCAL" else if (index == 1) "VISITANTE" else "N/A", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White))
                    }
                }
                OutlinedTextField(
                    value = player, onValueChange = { player = it },
                    label = { Text("Jugador involucrado" + if (selectedType?.requiredCore?.contains(CoreFields.PLAYER) == true) " *" else " (opcional)") },
                    placeholder = { Text("Nombre o identificador") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                // Datos adicionales del tipo (US-12), con su etiqueta y su clase; la clave no se muestra.
                selectedType?.extraFields?.forEach { field ->
                    val label = field.label + if (field.required) " *" else " (opcional)"
                    if (field.kind == ExtraKinds.CHOICE) {
                        Text(label.uppercase(), fontSize = 11.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            field.options.forEach { option ->
                                FilterChip(selected = extraFields[field.key] == option,
                                    onClick = { extraFields[field.key] = option }, label = { Text(option) })
                            }
                        }
                    } else OutlinedTextField(
                        value = extraFields[field.key].orEmpty(),
                        onValueChange = { v -> if (field.kind != ExtraKinds.NUMBER || v.all { it.isDigit() || it == '.' }) extraFields[field.key] = v.take(100) },
                        label = { Text(label) },
                        keyboardOptions = KeyboardOptions(keyboardType = if (field.kind == ExtraKinds.NUMBER) KeyboardType.Number else KeyboardType.Text),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Observaciones (opcional)") },
                    placeholder = { Text("Notas adicionales...") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3
                )
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Button(
                    onClick = {
                        val validMinute = minute.toIntOrNull()
                        message = when {
                            chosenTypeId.isBlank() -> "Selecciona un tipo de evento."
                            minute.isNotBlank() && (validMinute == null || validMinute !in 0..130) -> "Minuto inválido (0 a 130)."
                            else -> ""
                        }
                        if (message.isBlank()) {
                            try {
                                eventRepo.save(MatchEventRecord(
                                    matchId = matchId,
                                    typeId = chosenTypeId,
                                    typeName = requireNotNull(selectedType).name,
                                    minute = validMinute,
                                    team = selectedTeam.ifBlank { null },
                                    player = player.trim().ifBlank { null },
                                    notes = notes.trim(),
                                    extraFields = extraFields.toMap(),
                                    authorId = FirebaseAuth.getInstance().currentUser?.uid ?: "sin_sesion"
                                ))
                                onSaved()
                            } catch (ex: Exception) { message = ex.message ?: "No se pudo guardar el evento" }
                        }
                    },
                    enabled = activeTypes.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("GUARDAR EVENTO", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
