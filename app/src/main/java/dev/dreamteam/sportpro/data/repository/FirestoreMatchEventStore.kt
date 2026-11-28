package dev.dreamteam.sportpro.data.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import dev.dreamteam.sportpro.data.model.EventAuditEntry
import dev.dreamteam.sportpro.data.model.EventRevision
import dev.dreamteam.sportpro.data.model.MatchEventContext
import dev.dreamteam.sportpro.data.model.MatchEventRecord
import dev.dreamteam.sportpro.data.model.MatchEvents
import java.util.Date

/** Suscripción a cambios; se cancela con [remove]. */
fun interface EventSubscription { fun remove() }

/**
 * US-13/14: eventos de un partido real. Todo con listeners (tiempo real) y escrituras en batch
 * (evento + revisión), que funcionan sin conexión: se aplican en la caché y se envían al reconectar.
 */
interface MatchEventStore {
    fun observe(matchId: String, onChange: (List<MatchEventRecord>) -> Unit, onError: (String) -> Unit): EventSubscription
    fun observeEvent(matchId: String, eventId: String, onChange: (MatchEventRecord?) -> Unit, onError: (String) -> Unit): EventSubscription
    fun observeHistory(matchId: String, eventId: String, onChange: (List<EventAuditEntry>) -> Unit, onError: (String) -> Unit): EventSubscription
    fun add(ctx: MatchEventContext, event: MatchEventRecord): Task<Void>
    fun correct(before: MatchEventRecord, after: MatchEventRecord, actorId: String): Task<Void>
    fun void(before: MatchEventRecord, actorId: String): Task<Void>
}

/** matchFixtures/{matchId}/events/{eventId} + events/{eventId}/revisions/{version}. */
class FirestoreMatchEventStore(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) : MatchEventStore {
    private fun events(matchId: String) = db.collection("matchFixtures").document(matchId).collection("events")

    override fun observe(matchId: String, onChange: (List<MatchEventRecord>) -> Unit, onError: (String) -> Unit): EventSubscription {
        val registration = events(matchId).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) onError(EVENTS_LOAD_ERROR)
            else onChange(MatchEvents.chronology(snapshot?.documents.orEmpty().mapNotNull { fromDoc(matchId, it) }))
        }
        return EventSubscription { registration.remove() }
    }

    override fun observeEvent(matchId: String, eventId: String, onChange: (MatchEventRecord?) -> Unit, onError: (String) -> Unit): EventSubscription {
        val registration = events(matchId).document(eventId).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) onError(EVENTS_LOAD_ERROR) else onChange(snapshot?.let { fromDoc(matchId, it) })
        }
        return EventSubscription { registration.remove() }
    }

    override fun observeHistory(matchId: String, eventId: String, onChange: (List<EventAuditEntry>) -> Unit, onError: (String) -> Unit): EventSubscription {
        var current: MatchEventRecord? = null
        var revisions = emptyList<EventRevision>()
        val emit = { current?.let { onChange(MatchEvents.historyEntries(matchId, eventId, revisions, MatchEvents.editableSnapshot(it))) } ?: onChange(emptyList()) }
        val a = events(matchId).document(eventId).addSnapshotListener { snapshot, error ->
            if (error != null) onError(EVENTS_LOAD_ERROR) else { current = snapshot?.let { fromDoc(matchId, it) }; emit() }
        }
        val b = events(matchId).document(eventId).collection("revisions").addSnapshotListener { snapshot, error ->
            if (error != null) onError(EVENTS_LOAD_ERROR)
            else {
                revisions = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    EventRevision(doc.id.toIntOrNull() ?: return@mapNotNull null, doc.getString("action").orEmpty(),
                        (doc.get("previous") as? Map<*, *>).orEmpty().entries.associate { (k, v) -> k.toString() to v },
                        doc.getString("by").orEmpty(), doc.getTimestamp("at")?.toDate()?.time ?: 0L)
                }
                emit()
            }
        }
        return EventSubscription { a.remove(); b.remove() }
    }

    override fun add(ctx: MatchEventContext, event: MatchEventRecord): Task<Void> {
        val ref = events(ctx.matchId).document(event.id)
        val data = MatchEvents.editableSnapshot(event.copy(voided = false)) + mapOf(
            "teamId" to ctx.teamId, "category" to ctx.category, "version" to 1L,
            "createdBy" to event.authorId, "createdAt" to FieldValue.serverTimestamp(),
            "clientCreatedAt" to Timestamp(Date(event.createdAtMillis)),
            "updatedBy" to event.authorId, "updatedAt" to FieldValue.serverTimestamp())
        return db.batch()
            .set(ref, data)
            .set(ref.collection("revisions").document("1"), revision("CREATE", emptyMap(), event.authorId))
            .commit()
    }

    override fun correct(before: MatchEventRecord, after: MatchEventRecord, actorId: String): Task<Void> {
        val ref = events(before.matchId).document(before.id)
        val version = before.version + 1
        val next = MatchEvents.editableSnapshot(after.copy(voided = false))
        // Los campos opcionales que se vaciaron se borran del documento.
        val removed = listOf("minute", "team", "playerId", "playerName", "notes").filter { it !in next }.associateWith { FieldValue.delete() }
        val update: Map<String, Any> = (next + removed + mapOf("version" to version.toLong(), "updatedBy" to actorId,
            "updatedAt" to FieldValue.serverTimestamp())).filterValues { it != null }.mapValues { it.value!! }
        return db.batch()
            .update(ref, update)
            .set(ref.collection("revisions").document(version.toString()), revision("CORRECTION", MatchEvents.editableSnapshot(before), actorId))
            .commit()
    }

    override fun void(before: MatchEventRecord, actorId: String): Task<Void> {
        val ref = events(before.matchId).document(before.id)
        val version = before.version + 1
        return db.batch()
            .update(ref, mapOf("voided" to true, "voidedBy" to actorId, "voidedAt" to FieldValue.serverTimestamp(),
                "version" to version.toLong(), "updatedBy" to actorId, "updatedAt" to FieldValue.serverTimestamp()))
            .set(ref.collection("revisions").document(version.toString()), revision("VOID", MatchEvents.editableSnapshot(before), actorId))
            .commit()
    }

    private fun revision(action: String, previous: Map<String, Any?>, actorId: String) =
        mapOf("action" to action, "previous" to previous, "by" to actorId, "at" to FieldValue.serverTimestamp())

    private fun fromDoc(matchId: String, doc: DocumentSnapshot): MatchEventRecord? {
        if (!doc.exists()) return null
        fun stringMap(key: String) = (doc.get(key) as? Map<*, *>).orEmpty().entries
            .mapNotNull { (k, v) -> (v as? String)?.let { k.toString() to it } }.toMap()
        return MatchEventRecord(
            id = doc.id, matchId = matchId, teamId = doc.getString("teamId").orEmpty(),
            typeId = doc.getString("typeId") ?: return null, typeName = doc.getString("typeName").orEmpty(),
            minute = doc.getLong("minute")?.toInt(), team = doc.getString("team"), playerId = doc.getString("playerId"),
            notes = doc.getString("notes").orEmpty(), extraFields = stringMap("extras"), authorId = doc.getString("createdBy").orEmpty(),
            createdAtMillis = doc.getTimestamp("clientCreatedAt")?.toDate()?.time ?: doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
            voided = doc.getBoolean("voided") ?: false,
            lastModifiedBy = doc.getString("updatedBy").takeIf { (doc.getLong("version") ?: 1L) > 1L },
            lastModifiedAtMillis = doc.getTimestamp("updatedAt")?.toDate()?.time.takeIf { (doc.getLong("version") ?: 1L) > 1L },
            version = (doc.getLong("version") ?: 1L).toInt(), playerName = doc.getString("playerName"),
            extraNames = stringMap("extraNames"), voidedBy = doc.getString("voidedBy"),
            voidedAtMillis = doc.getTimestamp("voidedAt")?.toDate()?.time, pending = doc.metadata.hasPendingWrites())
    }

    companion object {
        /** Borra eventos y revisiones de un partido. SOLO la usa la cascada al eliminar el equipo (US-03, D2). */
        fun deleteMatchEvents(db: FirebaseFirestore, matchId: String, done: () -> Unit, failed: (Exception) -> Unit) {
            val events = db.collection("matchFixtures").document(matchId).collection("events")
            events.limit(5).get(com.google.firebase.firestore.Source.SERVER).addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) { done(); return@addOnSuccessListener }
                val revisionReads = snapshot.documents.map { it.reference.collection("revisions").get(com.google.firebase.firestore.Source.SERVER) }
                com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(revisionReads)
                    .addOnSuccessListener { revisionSnapshots ->
                        val batch = db.batch()
                        revisionSnapshots.forEach { revs -> revs.documents.forEach { batch.delete(it.reference) } }
                        snapshot.documents.forEach { batch.delete(it.reference) }
                        batch.commit().addOnSuccessListener { deleteMatchEvents(db, matchId, done, failed) }.addOnFailureListener(failed)
                    }.addOnFailureListener(failed)
            }.addOnFailureListener(failed)
        }
    }
}

const val EVENTS_LOAD_ERROR = "No se pudieron cargar los eventos del partido."

/** Mensaje en español, sin texto técnico, cuando el servidor rechaza un evento. */
fun eventWriteErrorMessage(error: Throwable): String {
    val code = generateSequence(error) { it.cause }.take(6).firstNotNullOfOrNull { (it as? FirebaseFirestoreException)?.code }
    return when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "No se guardó el evento: puede que el plazo haya terminado, que el tipo se haya desactivado o que la convocatoria haya cambiado."
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "Sin conexión. El evento se enviará al reconectar."
        else -> "No se pudo guardar el evento. Inténtalo de nuevo."
    }
}
