package dev.dreamteam.sportpro.data.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import dev.dreamteam.sportpro.data.model.Announcement
import dev.dreamteam.sportpro.data.model.AnnouncementType

/**
 * US-17: anuncios internos por equipo.
 * - `announcements/{id}`: siempre se consulta con `whereEqualTo("teamId", …)`, igual que exigen las reglas.
 * - `users/{uid}/announcementReads/{teamId}`: cuándo vio el usuario los anuncios de ese equipo.
 * - `announcementGuardians/{teamId}_{uid}`: acceso del tutor (de qué jugador vinculado viene).
 * Las escrituras son set/delete simples: funcionan sin conexión y se envían al reconectar.
 */
class AnnouncementRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val announcements = db.collection("announcements")
    private val uid get() = requireNotNull(FirebaseAuth.getInstance().currentUser?.uid) { "Inicia sesión" }
    private fun reads(userId: String) = db.collection("users").document(userId).collection("announcementReads")

    /** Anuncios de un equipo en tiempo real (incluye los pendientes de sincronizar de este celular). */
    fun listenTeam(teamId: String, onChange: (List<Announcement>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        announcements.whereEqualTo("teamId", teamId).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) onError(ANNOUNCEMENTS_LOAD_ERROR)
            else onChange(snapshot?.documents.orEmpty().mapNotNull(::fromDoc))
        }

    /** Marcas de visto del usuario: teamId → hora (null si aún no la confirmó el servidor). */
    fun listenReads(onChange: (Map<String, Long?>) -> Unit): ListenerRegistration =
        reads(uid).addSnapshotListener { snapshot, _ ->
            onChange(snapshot?.documents.orEmpty().associate { doc ->
                doc.id to doc.getTimestamp("lastSeenAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            })
        }

    /** Al abrir la pestaña Anuncios: marca como vistos los equipos que se están mirando. */
    fun markSeen(teamIds: Collection<String>) {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (teamIds.isEmpty()) return
        val batch = db.batch()
        teamIds.distinct().forEach { batch.set(reads(me).document(it), mapOf("lastSeenAt" to FieldValue.serverTimestamp())) }
        batch.commit()
    }

    fun create(teamId: String, type: AnnouncementType, title: String, body: String): Task<Void> =
        announcements.document().set(mapOf("teamId" to teamId, "type" to type.name, "title" to title.trim(),
            "body" to body.trim(), "authorId" to uid, "createdAt" to FieldValue.serverTimestamp()))

    fun delete(id: String): Task<Void> = announcements.document(id).delete()

    /** Tutor: guarda de qué jugador vinculado viene antes de leer los anuncios del equipo. */
    fun openAsGuardian(teamId: String, playerId: String): Task<Void> =
        db.collection("announcementGuardians").document("${teamId}_$uid")
            .set(mapOf("teamId" to teamId, "playerId" to playerId, "updatedAt" to FieldValue.serverTimestamp()))

    /** Equipos y categoría donde [userId] es miembro (el propio jugador, o su tutor verificado). */
    fun listenMemberTeams(userId: String, onChange: (List<Pair<String, String>>) -> Unit): ListenerRegistration =
        db.collection("teamMemberships").whereEqualTo("userId", userId).addSnapshotListener { snapshot, _ ->
            onChange(snapshot?.documents.orEmpty().mapNotNull { doc ->
                (doc.getString("teamId") ?: return@mapNotNull null) to doc.getString("category").orEmpty()
            }.distinctBy { it.first })
        }

    /** Nombre del equipo (la dueña y los miembros pueden leerlo; si no, null). */
    fun teamName(teamId: String): Task<String?> =
        db.collection("teams").document(teamId).get().continueWith { task -> if (task.isSuccessful) task.result.getString("name") else null }

    /**
     * Admin: equipos con partidos registrados (matchFixtures, que el admin ya puede leer). No se leen
     * nombres de equipo (US-22): se muestran como "Equipo · categoría".
     */
    fun listenAdminTeams(onChange: (List<Pair<String, String>>) -> Unit): ListenerRegistration =
        db.collection("matchFixtures").addSnapshotListener { snapshot, _ ->
            onChange(snapshot?.documents.orEmpty().mapNotNull { doc ->
                (doc.getString("teamId") ?: return@mapNotNull null) to doc.getString("category").orEmpty()
            }.distinctBy { it.first })
        }

    private fun fromDoc(doc: DocumentSnapshot): Announcement? = Announcement(
        id = doc.id,
        teamId = doc.getString("teamId") ?: return null,
        type = AnnouncementType.fromCode(doc.getString("type")) ?: return null,
        title = doc.getString("title").orEmpty(),
        body = doc.getString("body").orEmpty(),
        authorId = doc.getString("authorId").orEmpty(),
        createdAtMillis = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
        pending = doc.metadata.hasPendingWrites()
    )
}

const val ANNOUNCEMENTS_LOAD_ERROR = "No se pudieron cargar los anuncios. Revisa tu conexión."

/** Error al publicar o borrar, en español y sin texto técnico. */
fun announcementErrorMessage(error: Throwable): String {
    val code = generateSequence(error) { it.cause }.take(6).firstNotNullOfOrNull { (it as? FirebaseFirestoreException)?.code }
    return when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "No tienes permiso para esta acción o los datos no son válidos."
        FirebaseFirestoreException.Code.UNAVAILABLE -> "Sin conexión. Se enviará al reconectar."
        else -> "No se pudo completar la acción. Inténtalo de nuevo."
    }
}
