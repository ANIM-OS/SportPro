package dev.dreamteam.sportpro.ui.events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Pantallas de navegación de US-12, US-13 y US-14.
 * Son andamios (scaffolds): la navegación entre ellas ya funciona con argumentos,
 * y cada TODO marca dónde conectar Firestore / ViewModel en la siguiente tarea.
 */

/** Marco común: botón volver + título + subtítulo + contenido. */
@Composable
private fun EventScreenShell(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Volver") }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

// ─────────────────────────────────────────────────────────────
// Punto de entrada desde la pestaña "En vivo" (temporal hasta US-15)
// ─────────────────────────────────────────────────────────────
@Composable
fun LiveEventsEntryScreen(
    canManageCatalog: Boolean,
    canRegisterEvents: Boolean,
    onOpenCatalog: () -> Unit,
    onRegisterEvent: (matchId: String) -> Unit,
    onOpenEvent: (matchId: String, eventId: String) -> Unit
) {
    // TODO(US-15): reemplazar por el partido y la cronología reales.
    val demoMatchId = "demo-match"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "EN VIVO",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "US-15 · Marcador y cronología",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp
        )

        if (canRegisterEvents) {
            Button(
                onClick = { onRegisterEvent(demoMatchId) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Registrar evento (US-13)") }
        }
        if (canManageCatalog) {
            OutlinedButton(
                onClick = onOpenCatalog,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Catálogo de eventos (US-12)") }
        }

        Text(
            text = "Cronología (demo)",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        listOf("evt-1" to "11' · GOL · FC Halcones", "evt-2" to "35' · TARJETA · FC Monterrey")
            .forEach { (eventId, label) ->
                EventRowCard(label = label, onClick = { onOpenEvent(demoMatchId, eventId) })
            }
    }
}

@Composable
private fun EventRowCard(label: String, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            trailing()
        }
    }
}

// ─────────────────────────────────────────────────────────────
// US-12 · Catálogo configurable de eventos
// ─────────────────────────────────────────────────────────────
private data class EventTypeUi(val id: String, val name: String, val active: Boolean)

@Composable
fun EventCatalogScreen(
    onBack: () -> Unit,
    onNewType: () -> Unit,
    onEditType: (typeId: String) -> Unit
) {
    // TODO(US-12): leer el catálogo desde Firestore (sincronizado para usuarios autorizados).
    var types by remember {
        mutableStateOf(
            listOf(
                EventTypeUi("gol", "Gol", true),
                EventTypeUi("falta", "Falta", true),
                EventTypeUi("penal", "Penal", true),
                EventTypeUi("tarjeta", "Tarjeta", true),
                EventTypeUi("fuera_de_juego", "Fuera de juego", true),
                EventTypeUi("cambio", "Cambio", true),
                EventTypeUi("saque_lateral", "Saque lateral", true),
                EventTypeUi("saque_de_meta", "Saque de meta", true),
                EventTypeUi("tiro_de_esquina", "Tiro de esquina", true)
            )
        )
    }

    EventScreenShell(
        title = "CATÁLOGO DE EVENTOS",
        subtitle = "US-12 · Los eventos desactivados no aparecen al registrar",
        onBack = onBack
    ) {
        Button(onClick = onNewType, modifier = Modifier.fillMaxWidth()) {
            Text("Nuevo tipo de evento")
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(types, key = { it.id }) { type ->
                EventRowCard(
                    label = type.name,
                    onClick = { onEditType(type.id) },
                    trailing = {
                        Switch(
                            checked = type.active,
                            onCheckedChange = { checked ->
                                types = types.map { if (it.id == type.id) it.copy(active = checked) else it }
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun EventCatalogEditScreen(typeId: String?, onBack: () -> Unit) {
    // TODO(US-12): formulario real (nombre, activo, datos obligatorios por tipo: minuto/equipo/jugador/observaciones).
    EventScreenShell(
        title = if (typeId == null) "NUEVO TIPO DE EVENTO" else "EDITAR TIPO DE EVENTO",
        subtitle = "US-12 · ${typeId ?: "sin id"} · define qué datos son obligatorios",
        onBack = onBack
    ) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
    }
}

// ─────────────────────────────────────────────────────────────
// US-13 · Registro dinámico de eventos en vivo
// ─────────────────────────────────────────────────────────────
@Composable
fun RegisterEventScreen(matchId: String, onBack: () -> Unit, onSaved: () -> Unit) {
    // TODO(US-13): tipos activos del catálogo, formulario (minuto, equipo, jugador, observaciones),
    // guardar autor + fecha de registro, y cola offline que sincroniza al recuperar conexión.
    EventScreenShell(
        title = "REGISTRAR EVENTO",
        subtitle = "US-13 · Partido: $matchId",
        onBack = onBack
    ) {
        Button(onClick = onSaved, modifier = Modifier.fillMaxWidth()) { Text("Guardar evento") }
    }
}

// ─────────────────────────────────────────────────────────────
// US-14 · Corrección y anulación con trazabilidad
// ─────────────────────────────────────────────────────────────
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
    var confirmVoid by remember { mutableStateOf(false) }

    EventScreenShell(
        title = "DETALLE DEL EVENTO",
        subtitle = "US-14 · Partido: $matchId · Evento: $eventId",
        onBack = onBack
    ) {
        // TODO(US-14): mostrar los datos reales del evento (tipo, minuto, equipo, jugador, estado).
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onShowHistory, modifier = Modifier.fillMaxWidth()) {
                Text("Ver historial de cambios")
            }
            if (canEdit) {
                Button(onClick = onCorrect, modifier = Modifier.fillMaxWidth()) { Text("Corregir") }
                OutlinedButton(onClick = { confirmVoid = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Anular")
                }
            }
        }
    }

    if (confirmVoid) {
        AlertDialog(
            onDismissRequest = { confirmVoid = false },
            title = { Text("Anular evento") },
            text = { Text("El evento se marcará como anulado, sin borrar su historial.") },
            confirmButton = {
                TextButton(onClick = {
                    // TODO(US-14): marcar como anulado en Firestore guardando quién y cuándo (+ motivo).
                    confirmVoid = false
                    onVoided()
                }) { Text("Anular") }
            },
            dismissButton = {
                TextButton(onClick = { confirmVoid = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun EventEditScreen(matchId: String, eventId: String, onBack: () -> Unit, onSaved: () -> Unit) {
    // TODO(US-14): reutilizar el formulario de US-13 precargado; guardar referencia al dato anterior.
    EventScreenShell(
        title = "CORREGIR EVENTO",
        subtitle = "US-14 · Partido: $matchId · Evento: $eventId",
        onBack = onBack
    ) {
        Button(onClick = onSaved, modifier = Modifier.fillMaxWidth()) { Text("Guardar corrección") }
    }
}

@Composable
fun EventHistoryScreen(matchId: String, eventId: String, onBack: () -> Unit) {
    // TODO(US-14): lista de versiones (quién modificó/anuló, cuándo y valor anterior).
    EventScreenShell(
        title = "HISTORIAL DEL EVENTO",
        subtitle = "US-14 · Partido: $matchId · Evento: $eventId",
        onBack = onBack
    ) {
        Text(
            text = "Aún no hay cambios registrados.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
