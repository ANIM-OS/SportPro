package dev.dreamteam.sportpro.ui.events

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import dev.dreamteam.sportpro.data.model.EventType
import dev.dreamteam.sportpro.data.model.MatchEventContext
import dev.dreamteam.sportpro.data.model.MatchEvents
import dev.dreamteam.sportpro.data.repository.EventCatalogFirestoreRepository
import dev.dreamteam.sportpro.data.repository.FirestoreMatchEventStore
import dev.dreamteam.sportpro.ui.matches.registerClosedMessage
import dev.dreamteam.sportpro.data.repository.eventWriteErrorMessage
import kotlinx.coroutines.delay

/**
 * US-13: registrar un evento del partido real en Firestore. Ofrece los tipos activos del catálogo DEL
 * EQUIPO (US-12) y los convocados. Se puede registrar desde el inicio del partido hasta 24 h después.
 * Sin conexión, el evento se guarda en la caché y se envía al reconectar; si el servidor lo rechaza,
 * [onError] avisa al partido.
 */
@Composable
fun RegisterLiveEventScreen(ctx: MatchEventContext, onBack: () -> Unit, onSaved: () -> Unit, onError: (String) -> Unit = {}) {
    BackHandler(onBack = onBack)
    val store = remember { FirestoreMatchEventStore() }
    val catalog = remember { EventCatalogFirestoreRepository() }
    var types by remember(ctx.teamId) { mutableStateOf<List<EventType>>(emptyList()) }
    DisposableEffect(ctx.teamId) {
        val listener = catalog.listenActive(ctx.teamId) { types = it }
        onDispose { listener.remove() }
    }
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }
    // US-15: también se cierra si la entrenadora finaliza el partido (aunque sea desde otro celular).
    val open = MatchEvents.canRegister(now, ctx.kickoffMillis, ctx.endedAtMillis)
    val players = rememberMatchPlayers(ctx)
    val state = remember(ctx.matchId) { EventFormState() }
    var message by remember { mutableStateOf<String?>(null) }
    // El mensaje de error se borra en cuanto se cambia cualquier campo.
    ClearOnFormChange(state) { message = null }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Volver al partido") }
        Text("Registrar evento", style = MaterialTheme.typography.headlineSmall)
        Text("${ctx.ownName} vs ${ctx.opponent} · ${ctx.category}")
        if (!open) Text(registerClosedMessage(now, ctx), color = MaterialTheme.colorScheme.error)
        EventFormFields(state, types, ctx, players)
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(modifier = Modifier.fillMaxWidth(), enabled = open && types.isNotEmpty(), onClick = {
            val type = types.firstOrNull { it.id == state.typeId }
            // Incluye los datos obligatorios del tipo (catálogo del equipo): el error se ve antes de guardar.
            message = MatchEvents.validateEvent(type, state.typeId, state.minute, state.team, state.playerId, state.notes, state.extras)
            val actor = FirebaseAuth.getInstance().currentUser?.uid
            if (message == null && type != null) {
                if (actor == null) message = "Inicia sesión para registrar eventos."
                else {
                    // El batch se aplica al instante en la caché (también sin conexión); el servidor confirma después.
                    store.add(ctx, state.toRecord(ctx, type, actor, base = null, names = players.names))
                        .addOnFailureListener { onError(eventWriteErrorMessage(it)) }
                    onSaved()
                }
            }
        }) { Text("Guardar evento") }
    }
}
