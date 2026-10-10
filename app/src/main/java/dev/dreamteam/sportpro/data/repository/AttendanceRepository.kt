package dev.dreamteam.sportpro.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import dev.dreamteam.sportpro.data.model.AttendanceRecord
import dev.dreamteam.sportpro.data.model.AttendanceStatus
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.data.model.TrainingSession

/** US-08: asistencia por sesión e historial por jugador. */
class AttendanceRepository {

    private val auth = FirebaseAuth.getInstance()
    private val attendance = FirebaseFirestore.getInstance().collection(COLLECTION)

    /** Asistencia de una sesión, vista por el entrenador que la registra. */
    fun listenSession(sessionId: String, onChange: (List<AttendanceRecord>) -> Unit, onError: (String) -> Unit): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: run {
            onError("Debes iniciar sesión")
            return null
        }
        return listen(
            attendance.whereEqualTo("sessionId", sessionId).whereEqualTo("coachId", uid),
            onChange, onError
        )
    }

    /**
     * Historial de un jugador. Si [asCoach] es true solo se piden los registros del entrenador actual,
     * que es lo que las reglas le permiten leer; el jugador y su padre o tutor ven todo su historial.
     */
    fun listenPlayer(
        playerId: String,
        asCoach: Boolean,
        onChange: (List<AttendanceRecord>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: run {
            onError("Debes iniciar sesión")
            return null
        }
        var query: Query = attendance.whereEqualTo("playerId", playerId)
        if (asCoach) query = query.whereEqualTo("coachId", uid)
        return listen(query, onChange, onError)
    }

    /** Marca o corrige la asistencia; el id fijo evita duplicados del mismo jugador en la sesión. */
    fun mark(
        session: TrainingSession,
        member: TeamMember,
        status: AttendanceStatus,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError("Debes iniciar sesión")
            return
        }
        val data = hashMapOf<String, Any>(
            "sessionId" to session.id,
            "teamId" to session.teamId,
            "teamName" to session.teamName,
            "coachId" to uid,
            "playerId" to member.userId,
            "playerName" to member.playerName,
            "status" to status.name,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        session.date?.let { data["sessionDate"] = it }
        attendance.document(AttendanceRecord.documentId(session.id, member.userId)).set(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.userMessage("No se pudo registrar la asistencia")) }
    }

    private fun listen(
        query: Query,
        onChange: (List<AttendanceRecord>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration = query.addSnapshotListener { snapshot, error ->
        if (error != null) {
            onError(error.userMessage("No se pudo cargar la asistencia"))
            return@addSnapshotListener
        }
        onChange(
            snapshot?.documents?.mapNotNull(::fromDocument)
                ?.sortedByDescending { it.sessionDate?.seconds ?: 0L }.orEmpty()
        )
    }

    private fun fromDocument(doc: DocumentSnapshot): AttendanceRecord? {
        val status = AttendanceStatus.fromCode(doc.getString("status")) ?: return null
        return AttendanceRecord(
            id = doc.id,
            sessionId = doc.getString("sessionId").orEmpty(),
            teamId = doc.getString("teamId").orEmpty(),
            teamName = doc.getString("teamName").orEmpty(),
            coachId = doc.getString("coachId").orEmpty(),
            playerId = doc.getString("playerId").orEmpty(),
            playerName = doc.getString("playerName").orEmpty(),
            status = status,
            sessionDate = doc.getTimestamp("sessionDate"),
            updatedAt = doc.getTimestamp("updatedAt")
        )
    }

    companion object {
        const val COLLECTION = "attendance"
    }
}
