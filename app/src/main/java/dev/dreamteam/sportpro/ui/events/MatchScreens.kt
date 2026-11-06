package dev.dreamteam.sportpro.ui.events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import dev.dreamteam.sportpro.data.repository.LiveEventRepository
import dev.dreamteam.sportpro.data.repository.MatchEventRecord

/** Partidos de demostración: sustituir esta fuente por el repositorio real de partidos. */
data class MatchForUs13(
    val id: String,
    val opponent: String,
    val date: String,
    val place: String,
    val homeTeam: String = "FC Halcones",
    val awayTeam: String = opponent,
    val finished: Boolean = false
)

val demoMatchesUs13 = listOf(
    MatchForUs13("demo-monterrey", "FC Monterrey", "22 sep 2026 · 10:00", "Campo Norte"),
    MatchForUs13("demo-tigres", "Tigres Sub-17", "29 sep 2026 · 09:30", "Estadio Municipal"),
    MatchForUs13("demo-atletico", "Atlético Noreste", "6 oct 2026 · 11:00", "Campo Sur")
)

@Composable
fun MatchesForUs13Screen(onBack: () -> Unit, onOpenMatch: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Volver") }
        Text("PARTIDOS", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("PARTIDOS DE DEMOSTRACIÓN · aún sin conexión al listado real", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(demoMatchesUs13, key = { it.id }) { match ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenMatch(match.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("LOCAL · SUB-17", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("vs ${match.opponent}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${match.date} · ${match.place}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${match.homeTeam}     →", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun MatchDetailUs13Screen(
    matchId: String,
    canRegister: Boolean,
    onBack: () -> Unit,
    onRegister: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember(context) { LiveEventRepository(context) }
    var events by remember(matchId) { mutableStateOf(repo.forMatch(matchId)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, matchId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) events = repo.forMatch(matchId)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val match = demoMatchesUs13.firstOrNull { it.id == matchId }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← PARTIDOS") }
        Text("${match?.homeTeam ?: "Partido"} VS ${match?.opponent ?: matchId}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LIVE · DEMOSTRACIÓN", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("${match?.homeTeam ?: "Local"}   vs   ${match?.opponent ?: "Visitante"}", fontWeight = FontWeight.SemiBold)
                Text("Marcador pendiente de integración", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (canRegister) {
            Button(onClick = onRegister, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Text("REGISTRAR EVENTO", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }
        Text("CRONOLOGÍA DEL PARTIDO", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (events.isEmpty()) Text("Todavía no hay eventos registrados para este partido.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events, key = { it.id }) { item: MatchEventRecord ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${item.minute?.let { "$it'" } ?: "—"} · ${item.typeName.uppercase()}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text("LOCAL", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                        }
                        val details = listOfNotNull(item.team, item.player).joinToString(" · ")
                        if (details.isNotBlank()) Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        if (item.notes.isNotBlank()) Text(item.notes, fontSize = 12.sp)
                        Text("Pendiente de sincronización con servidor", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
