package dev.dreamteam.sportpro.ui.events

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import dev.dreamteam.sportpro.data.model.EventType
import dev.dreamteam.sportpro.data.model.MatchEventContext
import dev.dreamteam.sportpro.data.model.MatchEvents
import dev.dreamteam.sportpro.data.repository.EventCatalogFirestoreRepository
import dev.dreamteam.sportpro.data.repository.LiveEventRepository

/**
 * US-13: registrar un evento del partido REAL. Ofrece los tipos activos del catálogo DEL EQUIPO del
 * partido (US-12: un tipo desactivado en el equipo no aparece) y los convocados como jugadores.
 * Los eventos aún se guardan en el celular; la parte 3 los pasará a Firestore.
 */
@Composable
fun RegisterLiveEventScreen(ctx: MatchEventContext, onBack: () -> Unit, onSaved: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val store = remember(context) { LiveEventRepository(context) }
    val catalog = remember { EventCatalogFirestoreRepository() }
    var types by remember(ctx.teamId) { mutableStateOf<List<EventType>>(emptyList()) }
    DisposableEffect(ctx.teamId) {
        val listener = catalog.listenActive(ctx.teamId) { types = it }
        onDispose { listener.remove() }
    }
    val players = rememberMatchPlayers(ctx)
    val state = remember(ctx.matchId) { EventFormState() }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Volver al partido") }
        Text("Registrar evento", style = MaterialTheme.typography.headlineSmall)
        Text("${ctx.ownName} vs ${ctx.opponent} · ${ctx.category}")
        Text("Por ahora los eventos se guardan solo en este celular.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary)
        EventFormFields(state, types, ctx, players)
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(modifier = Modifier.fillMaxWidth(), enabled = types.isNotEmpty(), onClick = {
            message = MatchEvents.validate(state.typeId, state.minute)
            val type = types.firstOrNull { it.id == state.typeId }
            val actor = FirebaseAuth.getInstance().currentUser?.uid
            if (message == null && type != null) {
                if (actor == null) message = "Inicia sesión para registrar eventos."
                else try {
                    store.add(state.toRecord(ctx, type, actor, base = null))
                    onSaved()
                } catch (ex: Exception) { message = "No se pudo guardar el evento en el celular." }
            }
        }) { Text("Guardar evento") }
    }
}
