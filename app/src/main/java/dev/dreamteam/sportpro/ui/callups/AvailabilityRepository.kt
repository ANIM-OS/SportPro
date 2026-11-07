package dev.dreamteam.sportpro.ui.callups

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

data class AvailabilityResponse(val selectionId: String, val playerId: String, val status: String, val respondedBy: String)

class AvailabilityRepository {
    private val db = FirebaseFirestore.getInstance()
    private val responses = db.collection("matchAvailability")
    fun response(selectionId: String, change: (AvailabilityResponse?) -> Unit, error: (String) -> Unit) =
        responses.document(selectionId).addSnapshotListener { doc, failure ->
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
        doc.getString("status").orEmpty(), doc.getString("respondedBy").orEmpty())
}
