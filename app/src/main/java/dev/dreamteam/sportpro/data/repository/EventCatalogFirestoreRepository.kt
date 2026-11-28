package dev.dreamteam.sportpro.data.repository

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import dev.dreamteam.sportpro.data.model.EventCatalogRules
import dev.dreamteam.sportpro.data.model.EventType
import dev.dreamteam.sportpro.data.model.ExtraField
import dev.dreamteam.sportpro.data.model.ExtraKinds

/** Lo que el equipo guarda en teams/{id}/eventTypes: tipos propios y globales desactivados. */
data class TeamCatalog(val customs: List<EventType> = emptyList(), val overrides: Map<String, Boolean> = emptyMap())

/**
 * US-12. Catálogo global `eventCatalog/{typeId}` (solo admin) y del equipo
 * `teams/{teamId}/eventTypes/{typeId}` (entrenadora dueña aprobada). Las escrituras son set/update
 * (no transacciones) para que funcionen sin conexión; la caché de Firestore las envía al reconectar.
 */
class EventCatalogFirestoreRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val global = db.collection("eventCatalog")
    private fun team(teamId: String) = db.collection("teams").document(teamId).collection("eventTypes")
    private val uid get() = requireNotNull(FirebaseAuth.getInstance().currentUser?.uid) { "Inicia sesión" }

    fun listenGlobal(onChange: (List<EventType>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        global.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) onError(CATALOG_LOAD_ERROR)
            else onChange(snapshot?.documents.orEmpty().mapNotNull { toType(it, custom = false) })
        }

    fun listenTeam(teamId: String, onChange: (TeamCatalog) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        team(teamId).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) onError(CATALOG_LOAD_ERROR)
            else {
                val docs = snapshot?.documents.orEmpty()
                onChange(TeamCatalog(
                    customs = docs.filter { it.getString("kind") == "CUSTOM" }.mapNotNull { toType(it, custom = true) },
                    overrides = docs.filter { it.getString("kind") == "OVERRIDE" }
                        .associate { it.id to (it.getBoolean("active") ?: true) }))
            }
        }

    /**
     * Tipos que se ofrecen al registrar: los del equipo del partido o, si no hay equipo, los globales.
     * Si el catálogo global aún está vacío (o no hay caché ni conexión) se usan los 9 base del código.
     */
    fun listenActive(teamId: String?, onChange: (List<EventType>) -> Unit): ListenerRegistration {
        var globals = emptyList<EventType>()
        var teamCatalog = TeamCatalog()
        val emit = { onChange(EventCatalogRules.activeTypes(globals, teamCatalog.overrides, teamCatalog.customs)) }
        emit()
        val a = listenGlobal({ globals = it; emit() }, { emit() })
        val b = teamId?.takeIf { it.isNotBlank() }?.let { id -> listenTeam(id, { teamCatalog = it; emit() }, { emit() }) }
        return ListenerRegistration { a.remove(); b?.remove() }
    }

    fun saveGlobal(type: EventType): Task<Void> =
        global.document(type.id).set(fields(type) + mapOf("base" to (type.id in EventCatalogRules.BASE_IDS && type.base)))

    fun setGlobalActive(typeId: String, active: Boolean): Task<Void> =
        global.document(typeId).update(mapOf("active" to active, "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()))

    fun saveCustom(teamId: String, type: EventType): Task<Void> {
        require(type.id.startsWith(EventCatalogRules.CUSTOM_PREFIX)) { "Clave de tipo propio inválida" }
        return team(teamId).document(type.id).set(customFields(type))
    }

    fun setCustomActive(teamId: String, typeId: String, active: Boolean): Task<Void> =
        team(teamId).document(typeId).update(mapOf("active" to active, "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()))

    /** Usar o no en el equipo un tipo global. No se borra nada: queda un OVERRIDE con active. */
    fun setTeamUsesGlobal(teamId: String, typeId: String, active: Boolean): Task<Void> =
        team(teamId).document(typeId).set(mapOf("kind" to "OVERRIDE", "active" to active,
            "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()))

    /** Admin: crea SOLO los tipos base que faltan (la regla de creación impide pisar los existentes). */
    fun seedMissingBase(): Task<Int> = global.get(Source.SERVER).continueWithTask { task ->
        val existing = task.result.documents.map { it.id }.toSet()
        val missing = EventCatalogRules.BASE_TYPES.filter { it.id !in existing }
        if (missing.isEmpty()) return@continueWithTask Tasks.forResult(0)
        val batch = db.batch()
        missing.forEach { batch.set(global.document(it.id), fields(it) + mapOf("base" to true)) }
        batch.commit().continueWith { missing.size }
    }

    /** Importa al equipo los tipos propios antiguos del celular (ya convertidos con EventCatalogRules.fromLegacy). */
    fun importCustoms(teamId: String, types: List<EventType>): Task<Void> {
        if (types.isEmpty()) return Tasks.forResult(null)
        val batch = db.batch()
        types.forEach { batch.set(team(teamId).document(it.id), customFields(it)) }
        return batch.commit()
    }

    private fun fields(type: EventType): Map<String, Any> = mapOf(
        "name" to type.name.trim(),
        "description" to type.description.trim(),
        "requiredCore" to type.requiredCore,
        "extraKeys" to type.extraFields.map { it.key },
        "extraFields" to type.extraFields.map { mapOf("key" to it.key, "label" to it.label.trim(), "kind" to it.kind,
            "options" to it.options, "required" to it.required) },
        "requiredExtra" to type.requiredExtra,
        "active" to type.active,
        "order" to type.order,
        "updatedBy" to uid,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    /** Tipo propio: además de los campos comunes, la casilla "Cuenta como gol" (US-15; el global no la tiene). */
    private fun customFields(type: EventType): Map<String, Any> =
        fields(type) + mapOf("kind" to "CUSTOM", "countsAsGoal" to type.countsAsGoal)

    private fun toType(doc: DocumentSnapshot, custom: Boolean): EventType? {
        val name = doc.getString("name") ?: return null
        @Suppress("UNCHECKED_CAST")
        val extras = (doc.get("extraFields") as? List<Map<String, Any?>>).orEmpty().mapNotNull { item ->
            val key = item["key"] as? String ?: return@mapNotNull null
            ExtraField(key, item["label"] as? String ?: key, (item["kind"] as? String)?.takeIf { it in ExtraKinds.ALL } ?: ExtraKinds.TEXT,
                (item["options"] as? List<*>).orEmpty().filterIsInstance<String>(), item["required"] as? Boolean ?: false)
        }
        return EventType(doc.id, name, doc.getString("description").orEmpty(),
            (doc.get("requiredCore") as? List<*>).orEmpty().filterIsInstance<String>(), extras,
            doc.getBoolean("active") ?: true, doc.getBoolean("base") ?: false, (doc.getLong("order") ?: 1000L).toInt(),
            custom, doc.metadata.hasPendingWrites(), countsAsGoal = custom && doc.getBoolean("countsAsGoal") == true)
    }

    companion object {
        /** Borra el catálogo de un equipo (cascada al eliminar el equipo, US-03), de 10 en 10. */
        fun deleteTeamCatalog(db: FirebaseFirestore, teamId: String, done: () -> Unit, failed: (Exception) -> Unit) {
            val docs: CollectionReference = db.collection("teams").document(teamId).collection("eventTypes")
            docs.limit(10).get(Source.SERVER).addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) done()
                else {
                    val batch = db.batch()
                    snapshot.documents.forEach { batch.delete(it.reference) }
                    batch.commit().addOnSuccessListener { deleteTeamCatalog(db, teamId, done, failed) }.addOnFailureListener(failed)
                }
            }.addOnFailureListener(failed)
        }
    }
}

/** Mensajes en español, sin texto técnico. */
fun catalogErrorMessage(error: Throwable): String {
    val code = generateSequence(error) { it.cause }.take(6).firstNotNullOfOrNull { (it as? FirebaseFirestoreException)?.code }
    return when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "No se pudo guardar el catálogo: no tienes permiso o los datos no son válidos."
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "Sin conexión. Los cambios se enviarán al reconectar."
        else -> "No se pudo completar la operación del catálogo. Inténtalo de nuevo."
    }
}

const val CATALOG_LOAD_ERROR = "No se pudo cargar el catálogo de eventos. Revisa tu conexión."
