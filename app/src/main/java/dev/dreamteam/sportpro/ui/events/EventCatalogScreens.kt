package dev.dreamteam.sportpro.ui.events

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Task
import dev.dreamteam.sportpro.data.model.CoreFields
import dev.dreamteam.sportpro.data.model.EventCatalogRules
import dev.dreamteam.sportpro.data.model.EventType
import dev.dreamteam.sportpro.data.model.ExtraField
import dev.dreamteam.sportpro.data.model.ExtraKinds
import dev.dreamteam.sportpro.data.repository.EventCatalogFirestoreRepository
import dev.dreamteam.sportpro.data.repository.EventCatalogRepository
import dev.dreamteam.sportpro.data.repository.TeamCatalog
import dev.dreamteam.sportpro.data.repository.catalogErrorMessage

/** Qué catálogo se gestiona: el global (admin) o el de un equipo (entrenadora dueña aprobada). */
sealed interface CatalogScope {
    data object Global : CatalogScope
    data class Team(val teamId: String, val teamName: String) : CatalogScope
}

/**
 * US-12. Lista en vivo del catálogo y formulario de tipo. La entrenadora activa o desactiva para su
 * equipo los tipos generales y gestiona los propios; el admin gestiona los generales.
 */
@Composable
fun EventCatalogManagerScreen(scope: CatalogScope, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { EventCatalogFirestoreRepository() }
    val legacy = remember(context) { EventCatalogRepository(context) }
    val teamId = (scope as? CatalogScope.Team)?.teamId
    var globals by remember { mutableStateOf<List<EventType>>(emptyList()) }
    var teamCatalog by remember { mutableStateOf(TeamCatalog()) }
    var loading by remember(scope) { mutableStateOf(true) }
    var loadError by remember(scope) { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<EventType?>(null) }
    var confirmOff by remember { mutableStateOf<Pair<EventType, () -> Task<Void>>?>(null) }
    var legacyTypes by remember(teamId) { mutableStateOf(if (teamId != null) legacy.legacyCustomTypes() else emptyList()) }
    var seeding by remember { mutableStateOf(false) }

    DisposableEffect(scope) {
        val a = repository.listenGlobal({ globals = it; loading = false; loadError = null }, { loading = false; loadError = it })
        val b = teamId?.let { id -> repository.listenTeam(id, { teamCatalog = it; loadError = null }, { loadError = it }) }
        onDispose { a.remove(); b?.remove() }
    }

    /** Las escrituras se aplican al instante en la caché; el resultado del servidor llega después. */
    fun run(task: Task<*>, done: String) {
        message = "Guardando…"
        task.addOnSuccessListener { message = done }.addOnFailureListener { message = catalogErrorMessage(it) }
    }

    editing?.let { type ->
        val existing = (globals.map { it.id } + teamCatalog.customs.map { it.id } + EventCatalogRules.BASE_IDS).toSet()
        EventTypeForm(type, existing - type.id, prefix = if (scope is CatalogScope.Team) EventCatalogRules.CUSTOM_PREFIX else "",
            onCancel = { editing = null },
            onSave = { saved ->
                editing = null
                run(if (teamId != null) repository.saveCustom(teamId, saved) else repository.saveGlobal(saved), "Tipo guardado")
            })
        return
    }

    confirmOff?.let { (type, action) ->
        AlertDialog(onDismissRequest = { confirmOff = null },
            title = { Text("¿Desactivar «${type.name}»?") },
            text = { Text(if (scope is CatalogScope.Team) "No se ofrecerá al registrar eventos de este equipo. Los eventos ya registrados no cambian."
                else "No se ofrecerá al registrar eventos en ningún equipo. Los eventos ya registrados no cambian.") },
            confirmButton = { TextButton(onClick = { confirmOff = null; run(action(), "Tipo desactivado") }) { Text("Desactivar") } },
            dismissButton = { TextButton(onClick = { confirmOff = null }) { Text("Cancelar") } })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Volver") }
        Text(if (scope is CatalogScope.Team) "Catálogo de eventos" else "Catálogo global de eventos", style = MaterialTheme.typography.headlineSmall)
        Text(when (scope) {
            is CatalogScope.Team -> "${scope.teamName}: elige qué tipos generales usa tu equipo y crea los tuyos."
            CatalogScope.Global -> "Tipos de evento disponibles para todos los equipos."
        }, style = MaterialTheme.typography.bodyMedium)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        if (teamId != null && legacyTypes.isNotEmpty()) LegacyImportCard(legacyTypes.size,
            onImport = {
                var taken = (teamCatalog.customs.map { it.id } + globals.map { it.id }).toSet()
                val converted = legacyTypes.map { old ->
                    EventCatalogRules.fromLegacy(old.id, old.name, old.description, old.requiredFields, old.active, taken).also { taken = taken + it.id }
                }
                run(repository.importCustoms(teamId, converted), "Tipos importados a este equipo")
                legacy.clearLegacy(); legacyTypes = emptyList()
            },
            onDiscard = { legacy.clearLegacy(); legacyTypes = emptyList() })

        Text("Tipos generales", style = MaterialTheme.typography.titleMedium)
        val usingFallback = !loading && globals.isEmpty()
        if (usingFallback) Text(if (scope is CatalogScope.Team) "El administrador aún no ha creado el catálogo general; se usan los tipos base."
            else "Aún no hay tipos en el catálogo global.")
        if (scope is CatalogScope.Global && !loading) {
            val missing = EventCatalogRules.BASE_IDS - globals.map { it.id }.toSet()
            if (missing.isNotEmpty()) Button(enabled = !seeding, onClick = {
                seeding = true; message = "Creando tipos base…"
                repository.seedMissingBase()
                    .addOnSuccessListener { n -> message = if (n == 0) "Los tipos base ya existían" else "Se crearon $n tipos base" }
                    .addOnFailureListener { message = catalogErrorMessage(it) }
                    .addOnCompleteListener { seeding = false }
            }) { Text("Crear tipos base que faltan (${missing.size})") }
        }
        EventCatalogRules.effectiveGlobals(globals).sortedWith(compareBy<EventType> { it.order }.thenBy { it.name }).forEach { type ->
            when (scope) {
                CatalogScope.Global -> TypeRow(type, checked = type.active, switchLabel = "Activo", enabled = !usingFallback,
                    note = null, onEdit = if (usingFallback) null else ({ editing = type })) { on ->
                    if (on) run(repository.setGlobalActive(type.id, true), "Tipo activado")
                    else confirmOff = type to { repository.setGlobalActive(type.id, false) }
                }
                is CatalogScope.Team -> {
                    val used = teamCatalog.overrides[type.id] != false
                    TypeRow(type, checked = type.active && used, switchLabel = "Usar en este equipo", enabled = type.active && !usingFallback,
                        note = if (!type.active) "Desactivado por el administrador" else null, onEdit = null) { on ->
                        if (on) run(repository.setTeamUsesGlobal(scope.teamId, type.id, true), "Tipo activado en el equipo")
                        else confirmOff = type to { repository.setTeamUsesGlobal(scope.teamId, type.id, false) }
                    }
                }
            }
        }
        if (scope is CatalogScope.Team) {
            HorizontalDivider()
            Text("Tipos propios del equipo", style = MaterialTheme.typography.titleMedium)
            if (teamCatalog.customs.isEmpty()) Text("Aún no has creado tipos propios.")
            teamCatalog.customs.sortedBy { it.name.lowercase() }.forEach { type ->
                TypeRow(type, checked = type.active, switchLabel = "Activo", enabled = true, note = null, onEdit = { editing = type }) { on ->
                    if (on) run(repository.setCustomActive(scope.teamId, type.id, true), "Tipo activado")
                    else confirmOff = type to { repository.setCustomActive(scope.teamId, type.id, false) }
                }
            }
        }
        Button(modifier = Modifier.fillMaxWidth(), enabled = !loading, onClick = { editing = EventType(id = "", name = "", custom = scope is CatalogScope.Team) }) {
            Text("+ Agregar tipo")
        }
    }
}

@Composable
private fun LegacyImportCard(count: Int, onImport: () -> Unit, onDiscard: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Hay $count ${if (count == 1) "tipo creado" else "tipos creados"} antes en este celular.", fontWeight = FontWeight.Bold)
            Text("Puedes pasarlos a este equipo (se verán en todos los celulares) o descartarlos.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImport) { Text("Importar a este equipo") }
                OutlinedButton(onClick = onDiscard) { Text("Descartar") }
            }
        }
    }
}

@Composable
private fun TypeRow(type: EventType, checked: Boolean, switchLabel: String, enabled: Boolean, note: String?,
    onEdit: (() -> Unit)?, onToggle: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(type.name + if (type.custom) " · Propio" else " · General", fontWeight = FontWeight.Bold)
                if (type.description.isNotBlank()) Text(type.description, style = MaterialTheme.typography.bodySmall)
                Text(EventCatalogRules.requirementsLabel(type), style = MaterialTheme.typography.bodySmall)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (type.pending) Text("Pendiente de sincronizar", style = MaterialTheme.typography.bodySmall)
                onEdit?.let { TextButton(onClick = it, contentPadding = PaddingValues(0.dp)) { Text("Editar") } }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = checked, enabled = enabled, onCheckedChange = onToggle)
                Text(if (checked) switchLabel else "Desactivado", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Formulario de tipo: casillas para los datos comunes y "+ Dato adicional". La clave no se muestra. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventTypeForm(initial: EventType, existingIds: Set<String>, prefix: String, onCancel: () -> Unit, onSave: (EventType) -> Unit) {
    BackHandler(onBack = onCancel)
    val isNew = initial.id.isBlank()
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var core by remember { mutableStateOf(initial.requiredCore.toSet()) }
    var extras by remember { mutableStateOf(initial.extraFields.map { ExtraDraft(it.key, it.label, it.kind, it.options.joinToString(", "), it.required) }) }
    var active by remember { mutableStateOf(initial.active) }
    var countsAsGoal by remember { mutableStateOf(initial.countsAsGoal) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onCancel) { Text("← Cancelar") }
        Text(if (isNew) "Nuevo tipo de evento" else "Editar tipo de evento", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { name = it.take(EventCatalogRules.MAX_NAME) }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it.take(EventCatalogRules.MAX_DESCRIPTION) }, label = { Text("Descripción (opcional)") }, modifier = Modifier.fillMaxWidth())
        Text("Datos obligatorios al registrar", style = MaterialTheme.typography.titleMedium)
        Text("El tipo siempre es obligatorio. Marca qué más debe completarse.", style = MaterialTheme.typography.bodySmall)
        CoreFields.ALL.forEach { field ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = field in core, onCheckedChange = { core = if (it) core + field else core - field })
                Text(CoreFields.label(field))
            }
        }
        Text("Datos adicionales", style = MaterialTheme.typography.titleMedium)
        extras.forEachIndexed { index, draft ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(draft.label, { v -> extras = extras.toMutableList().also { it[index] = draft.copy(label = v.take(EventCatalogRules.MAX_LABEL)) } },
                        label = { Text("Etiqueta (ej. Color)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    // 4 clases (Texto, Número, Opciones, Jugador convocado): FlowRow para pantallas estrechas.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExtraKinds.ALL.forEach { kind ->
                            FilterChip(selected = draft.kind == kind, onClick = { extras = extras.toMutableList().also { it[index] = draft.copy(kind = kind) } },
                                label = { Text(ExtraKinds.label(kind)) })
                        }
                    }
                    if (draft.kind == ExtraKinds.CHOICE) OutlinedTextField(draft.options,
                        { v -> extras = extras.toMutableList().also { it[index] = draft.copy(options = v) } },
                        label = { Text("Opciones, separadas por coma") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = draft.required, onCheckedChange = { v -> extras = extras.toMutableList().also { it[index] = draft.copy(required = v) } })
                        Text("Obligatorio", Modifier.weight(1f))
                        TextButton(onClick = { extras = extras.toMutableList().also { it.removeAt(index) } }) { Text("Quitar") }
                    }
                }
            }
        }
        if (extras.size < EventCatalogRules.MAX_EXTRAS) OutlinedButton(onClick = { extras = extras + ExtraDraft() }) { Text("+ Dato adicional") }
        // US-15: solo los tipos propios pueden sumar al marcador ("gol" y "penal" con resultado Gol ya cuentan).
        if (initial.custom) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = countsAsGoal, onCheckedChange = { countsAsGoal = it })
                Column {
                    Text("Cuenta como gol")
                    Text("Cada evento de este tipo suma un gol al equipo indicado en el marcador.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Activo", Modifier.weight(1f))
            Switch(checked = active, onCheckedChange = { active = it })
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            val built = buildExtras(extras)
            error = when {
                name.isBlank() -> "Escribe un nombre para el tipo."
                built == null -> "Cada dato adicional necesita etiqueta, y los de opciones al menos dos opciones."
                else -> null
            }
            if (error == null) {
                val id = if (isNew) EventCatalogRules.uniqueKey(name, existingIds, prefix) else initial.id
                onSave(initial.copy(id = id, name = name.trim(), description = description.trim(),
                    requiredCore = CoreFields.ALL.filter { it in core }, extraFields = built!!, active = active,
                    countsAsGoal = initial.custom && countsAsGoal))
            }
        }) { Text("Guardar tipo") }
    }
}

private data class ExtraDraft(val key: String = "", val label: String = "", val kind: String = ExtraKinds.TEXT,
    val options: String = "", val required: Boolean = false)

/** Convierte los borradores en datos adicionales con clave generada; null si alguno no es válido. */
private fun buildExtras(drafts: List<ExtraDraft>): List<ExtraField>? {
    val keys = drafts.map { it.key }.filter { it.isNotBlank() }.toMutableSet()
    return drafts.map { draft ->
        val options = draft.options.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (draft.label.isBlank() || (draft.kind == ExtraKinds.CHOICE && options.size < 2)) return null
        val key = draft.key.ifBlank { EventCatalogRules.uniqueKey(draft.label, keys, max = EventCatalogRules.MAX_EXTRA_KEY, fallback = "dato") }
        keys += key
        ExtraField(key, draft.label.trim(), draft.kind, if (draft.kind == ExtraKinds.CHOICE) options else emptyList(), draft.required)
    }
}
