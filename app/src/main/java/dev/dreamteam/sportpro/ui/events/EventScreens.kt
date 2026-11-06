package dev.dreamteam.sportpro.ui.events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
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
// US-12 · Catálogo configurable de eventos (colores originales SportPro)
// ─────────────────────────────────────────────────────────────
@Composable
fun EventCatalogScreen(
    onBack: () -> Unit,
    onNewType: () -> Unit,
    onEditType: (typeId: String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { dev.dreamteam.sportpro.data.repository.EventCatalogRepository(context) }
    var types by remember { mutableStateOf(repository.load()) }
    var error by remember { mutableStateOf<String?>(null) }
    val base = types.filter { it.base }
    val custom = types.filterNot { it.base }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Volver", color = MaterialTheme.colorScheme.primary) }
        Text("CATÁLOGO DE EVENTOS", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text("Configuración local — PENDIENTE: sincronización con servidor no implementada.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error.orEmpty(), color = androidx.compose.ui.graphics.Color(0xFFFF5252), fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
        ) {
            item { Text("TIPOS BASE (${base.size})", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            items(base, key = { it.id }) { type ->
                CatalogTypeCard(type = type, onEdit = { onEditType(type.id) }, onToggle = { checked ->
                    try {
                        repository.setActive(type.id, checked)
                        types = repository.load()
                        error = null
                    } catch (ex: Exception) { error = ex.message ?: "No se pudo guardar" }
                })
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("PERSONALIZADOS (${custom.size})", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            items(custom, key = { it.id }) { type ->
                CatalogTypeCard(type = type, onEdit = { onEditType(type.id) }, onToggle = { checked ->
                    try {
                        repository.setActive(type.id, checked)
                        types = repository.load()
                        error = null
                    } catch (ex: Exception) { error = ex.message ?: "No se pudo guardar" }
                })
            }
            item {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onNewType,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("+ AGREGAR AL CATÁLOGO", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun CatalogTypeCard(
    type: dev.dreamteam.sportpro.data.repository.CatalogEventType,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).clickable(onClick = onEdit)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(type.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text(if (type.base) "BASE" else "NUEVO", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp)
                }
                Text(type.requiredFields.joinToString(", "), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Editar campos →", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            }
            androidx.compose.material3.Switch(
                checked = type.active,
                onCheckedChange = onToggle,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                    checkedTrackColor = androidx.compose.ui.graphics.Color(0xFF18845B),
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            Text(if (type.active) "ACTIVO" else "INACTIVO",
                color = if (type.active) androidx.compose.ui.graphics.Color(0xFF32D296) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EventCatalogEditScreen(typeId: String?, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { dev.dreamteam.sportpro.data.repository.EventCatalogRepository(context) }
    val original = remember(typeId) { repository.load().firstOrNull { it.id == typeId } }
    var name by remember(typeId) { mutableStateOf(original?.name ?: "") }
    var key by remember(typeId) { mutableStateOf(original?.id ?: "") }
    var description by remember(typeId) { mutableStateOf(original?.description ?: "") }
    var fields by remember(typeId) { mutableStateOf(original?.requiredFields?.joinToString(", ") ?: "jugadorId, minuto") }
    var active by remember(typeId) { mutableStateOf(original?.active ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TextButton(onClick = onBack) { Text("← Volver", color = MaterialTheme.colorScheme.primary) }
        Text(if (original == null) "NUEVO TIPO DE EVENTO" else "EDITAR TIPO DE EVENTO",
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text("Define los datos que serán obligatorios al registrar este evento.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        androidx.compose.foundation.rememberScrollState().let { scroll ->
            Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nombre *") }, placeholder = { Text("Ej. Penalti ejecutado") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = key, onValueChange = { if (original == null) key = it },
                    label = { Text("Clave / tipo *") },
                    placeholder = { Text("Ej. penalti_ejecutado") },
                    enabled = original == null,
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2
                )
                androidx.compose.material3.OutlinedTextField(
                    value = fields, onValueChange = { fields = it },
                    label = { Text("Campos obligatorios, separados por coma *") },
                    placeholder = { Text("jugadorId, minuto, favor") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Evento activo", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    androidx.compose.material3.Switch(checked = active, onCheckedChange = { active = it })
                }
                error?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFFFF5252), fontSize = 12.sp) }
                Button(onClick = {
                    val normalizedKey = dev.dreamteam.sportpro.data.repository.EventCatalogRepository.slug(key.ifBlank { name })
                    val required = fields.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    val existing = repository.load()
                    error = when {
                        name.isBlank() -> "Escribe un nombre para el evento."
                        normalizedKey.isBlank() -> "Escribe una clave válida."
                        required.isEmpty() -> "Añade al menos un campo obligatorio."
                        original == null && existing.any { it.id == normalizedKey } -> "Ya existe un evento con esa clave."
                        else -> null
                    }
                    if (error == null) {
                        try {
                            repository.upsert(dev.dreamteam.sportpro.data.repository.CatalogEventType(
                                id = original?.id ?: normalizedKey,
                                name = name.trim(), description = description.trim(),
                                requiredFields = required, active = active,
                                base = original?.base ?: false
                            ))
                            onBack()
                        } catch (ex: Exception) { error = ex.message ?: "Error al guardar el evento" }
                    }
                }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
                    Text("GUARDAR EN EL CATÁLOGO", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
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
