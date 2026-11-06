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
import dev.dreamteam.sportpro.data.repository.EventCatalogRepository
import dev.dreamteam.sportpro.data.repository.LiveEventRepository
import dev.dreamteam.sportpro.data.repository.MatchEventRecord

/** US-13: usa los tipos ACTIVOS de US-12. El registro se guarda localmente, sin enviar a Firebase todavía. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterLiveEventUs13Screen(matchId: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val catalogRepo = remember(context) { EventCatalogRepository(context) }
    val eventRepo = remember(context) { LiveEventRepository(context) }
    val activeTypes = remember(matchId) { catalogRepo.activeTypes() }
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
        Text("Registro local: los eventos no se sincronizan todavía con Firebase.",
            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Nuevo evento", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("TIPO DE EVENTO *", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (activeTypes.isEmpty()) {
                    Text("No hay eventos activos. Activa algún tipo en Catálogo de eventos.", color = MaterialTheme.colorScheme.error)
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
                Text("MINUTO (OPCIONAL)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = minute,
                    onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) minute = it },
                    placeholder = { Text("Ej. 45") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text("EQUIPO (OPCIONAL)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    label = { Text("Jugador involucrado (opcional)") },
                    placeholder = { Text("Nombre o identificador") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                // Tipos personalizados y campos configurados en US-12 se muestran sin rehacer este formulario.
                val standardFields = setOf("jugadorid", "minuto", "favor", "color", "jugadorsaleid", "jugadorentraid")
                selectedType?.requiredFields?.filterNot { it.lowercase() in standardFields }?.forEach { field ->
                    OutlinedTextField(
                        value = extraFields[field].orEmpty(),
                        onValueChange = { extraFields[field] = it },
                        label = { Text("$field (opcional en US-13)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (selectedType?.requiredFields?.contains("color") == true) {
                    Text("COLOR DE TARJETA (OPCIONAL)", fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Amarilla", "Roja").forEach { color ->
                            FilterChip(selected = extraFields["color"] == color,
                                onClick = { extraFields["color"] = color }, label = { Text(color) })
                        }
                    }
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
