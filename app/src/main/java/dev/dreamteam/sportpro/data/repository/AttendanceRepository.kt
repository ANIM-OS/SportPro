package dev.dreamteam.sportpro.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import dev.dreamteam.sportpro.data.model.AttendanceChange
import dev.dreamteam.sportpro.data.model.AttendanceRecord
import dev.dreamteam.sportpro.data.model.AttendanceStatus
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.data.model.TrainingSession

/** US-08: asistencia por sesión, historial por jugador y registro de correcciones. */
class AttendanceRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val attendance = firestore.collection(COLLECTION)

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

    /** Admin (solo lectura): toda la asistencia de una sesión. Las reglas lo exigen con isAdmin(). */
    fun listenSessionAsAdmin(sessionId: String, onChange: (List<AttendanceRecord>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        listen(attendance.whereEqualTo("sessionId", sessionId), onChange, onError)

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

    /**
     * Marca o corrige la asistencia. El id fijo evita duplicados del mismo jugador en la sesión.
     * - Primera marca ([existing] null): crea el registro con createdAt.
     * - Corrección: actualiza el estado y, en el mismo batch, crea attendance/{id}/changes/{nuevo}
     *   con el estado anterior y el nuevo (firestore.rules exige ambas escrituras juntas).
     * El nombre del equipo se lee al momento porque las reglas lo comparan con el actual.
     */
    fun mark(
        session: TrainingSession,
        member: TeamMember,
        status: AttendanceStatus,
        existing: AttendanceRecord?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError("Debes iniciar sesión")
            return
        }
        val sessionDate = session.date ?: run {
            onError("La sesión no tiene fecha")
            return
        }
        if (existing?.status == status) {
            onSuccess()
            return
        }
        firestore.collection("teams").document(session.teamId).get()
            .addOnSuccessListener { team ->
                val teamName = team.getString("name") ?: session.teamName
                val ref = attendance.document(AttendanceRecord.documentId(session.id, member.userId))
                val task = if (existing == null) {
                    ref.set(
                        mapOf(
                            "sessionId" to session.id,
                            "teamId" to session.teamId,
                            "teamName" to teamName,
                            "coachId" to uid,
                            "playerId" to member.userId,
                            "playerName" to member.playerName,
                            "status" to status.name,
                            "sessionDate" to sessionDate,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    val change = ref.collection(CHANGES).document()
                    firestore.batch()
                        .update(
                            ref,
                            mapOf(
                                "status" to status.name,
                                "teamName" to teamName,
                                "playerName" to member.playerName,
                                "sessionDate" to sessionDate,
                                "lastChangeId" to change.id,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        .set(
                            change,
                            mapOf(
                                "from" to existing.status.name,
                                "to" to status.name,
                                "changedBy" to uid,
                                "changedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        .commit()
                }
                task.addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it.userMessage("No se pudo registrar la asistencia")) }
            }
            .addOnFailureListener { onError(it.userMessage("No se pudo leer el equipo de la sesión")) }
    }

    /** Última corrección de un registro (para mostrar "Corregido: A → B"). */
    fun loadChange(recordId: String, changeId: String, onResult: (AttendanceChange?) -> Unit) {
        attendance.document(recordId).collection(CHANGES).document(changeId).get()
            .addOnSuccessListener { doc ->
                val from = AttendanceStatus.fromCode(doc.getString("from"))
                val to = AttendanceStatus.fromCode(doc.getString("to"))
                onResult(
                    if (from == null || to == null) null
                    else AttendanceChange(doc.id, from, to, doc.getString("changedBy").orEmpty(), doc.getTimestamp("changedAt"))
                )
            }
            .addOnFailureListener { onResult(null) }
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
            createdAt = doc.getTimestamp("createdAt"),
            updatedAt = doc.getTimestamp("updatedAt"),
            lastChangeId = doc.getString("lastChangeId")
        )
    }

    companion object {
        const val COLLECTION = "attendance"
        const val CHANGES = "changes"
    }
}
