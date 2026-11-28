package dev.dreamteam.sportpro.ui.callups

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

data class AvailabilityResponse(val selectionId: String, val playerId: String, val status: String,
    val respondedBy: String, val pending: Boolean = false, val updatedAtMillis: Long? = null)

internal const val AVAILABILITY_CLOSED_MESSAGE = "El plazo para responder ya cerró."
internal const val AVAILABILITY_DENIED_MESSAGE = "No se pudo guardar: puede que ya no pertenezcas a esta categoría o que la convocatoria haya cambiado."
internal const val AVAILABILITY_GENERIC_MESSAGE = "No se pudo guardar. Comprueba el plazo para responder y tu autorización e inténtalo de nuevo."
/** Margen para relojes de celular desajustados: cerca del cierre, un rechazo se atribuye al plazo. */
internal const val AVAILABILITY_CLOCK_MARGIN_MS = 5 * 60 * 1000L

/**
 * El servidor no dice por qué rechazó la respuesta. Si fue por permisos y, según la hora del celular
 * (con 5 min de margen), el cierre o el inicio ya pasaron, se atribuye al plazo.
 */
internal fun availabilityErrorMessage(permissionDenied: Boolean, deadlineMillis: Long?, kickoffMillis: Long?, nowMillis: Long): String {
    if (!permissionDenied) return AVAILABILITY_GENERIC_MESSAGE
    val closesAt = listOfNotNull(deadlineMillis, kickoffMillis).minOrNull()
    return if (closesAt != null && nowMillis >= closesAt - AVAILABILITY_CLOCK_MARGIN_MS) AVAILABILITY_CLOSED_MESSAGE
    else AVAILABILITY_DENIED_MESSAGE
}

/** "respondió el jugador" / "respondió el tutor" según quién firmó la respuesta. */
internal fun responderLabel(response: AvailabilityResponse): String =
    if (response.respondedBy == response.playerId) "respondió el jugador" else "respondió el tutor"

class AvailabilityRepository {
    private val db = FirebaseFirestore.getInstance()
    private val responses = db.collection("matchAvailability")
    fun response(selectionId: String, change: (AvailabilityResponse?) -> Unit, error: (String) -> Unit) =
        responses.document(selectionId).addSnapshotListener(MetadataChanges.INCLUDE) { doc, failure ->
            if (failure != null) error("No se pudo consultar la disponibilidad")
            else change(doc?.takeIf { it.exists() }?.let(::parse))
        }
    fun coachResponses(matchId: String, teamId: String, change: (List<AvailabilityResponse>) -> Unit, error: (String) -> Unit): ListenerRegistration {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        return responses.whereEqualTo("ownerId", uid).whereEqualTo("matchId", matchId).whereEqualTo("teamId", teamId).addSnapshotListener { snapshot, failure ->
            if (failure != null) error("No se pudieron cargar las respuestas")
            else change(snapshot?.documents.orEmpty().map(::parse))
        }
    }
    fun respond(row: PlayerCallup, available: Boolean): Task<Void> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Tasks.forException(IllegalStateException("Inicia sesión"))
        // Server rules re-read the active selection, current membership and authoritative deadline.
        return responses.document(row.id).set(mapOf(
            "matchId" to row.matchId, "teamId" to row.teamId, "playerId" to row.playerId,
            "ownerId" to row.ownerId, "status" to if (available) "AVAILABLE" else "UNAVAILABLE",
            "respondedBy" to uid, "updatedAt" to FieldValue.serverTimestamp()
        ))
    }
    private fun parse(doc: DocumentSnapshot) = AvailabilityResponse(doc.id, doc.getString("playerId").orEmpty(),
        doc.getString("status").orEmpty(), doc.getString("respondedBy").orEmpty(), doc.metadata.hasPendingWrites(),
        doc.getTimestamp("updatedAt")?.toDate()?.time)
}
