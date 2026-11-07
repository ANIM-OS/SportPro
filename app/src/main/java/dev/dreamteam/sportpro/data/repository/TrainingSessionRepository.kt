package dev.dreamteam.sportpro.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.SessionExercise
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.model.TrainingSession

/** US-06: sesiones de entrenamiento. Solo el entrenador dueño del equipo las crea o modifica. */
class TrainingSessionRepository {

    private val auth = FirebaseAuth.getInstance()
    private val sessions = FirebaseFirestore.getInstance().collection(COLLECTION)

    /** Sesiones creadas por el entrenador actual. */
    fun listenCreatedByMe(onChange: (List<TrainingSession>) -> Unit, onError: (String) -> Unit): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: run {
            onError("Debes iniciar sesión")
            return null
        }
        return sessions.whereEqualTo("createdBy", uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.userMessage("No se pudieron cargar las sesiones"))
                return@addSnapshotListener
            }
            onChange(snapshot?.documents?.mapNotNull(::fromDocument).orEmpty())
        }
    }

    /** Sesiones de un equipo, para los jugadores que pertenecen a él. */
    fun listenTeam(teamId: String, onChange: (List<TrainingSession>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        sessions.whereEqualTo("teamId", teamId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.userMessage("No se pudieron cargar las sesiones"))
                return@addSnapshotListener
            }
            onChange(snapshot?.documents?.mapNotNull(::fromDocument).orEmpty())
        }

    fun listenSession(sessionId: String, onChange: (TrainingSession?) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        sessions.document(sessionId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.userMessage("No se pudo cargar la sesión"))
                return@addSnapshotListener
            }
            onChange(snapshot?.let(::fromDocument))
        }

    fun save(
        existingId: String?,
        team: Team,
        category: String,
        date: Timestamp,
        durationMinutes: Int,
        objectives: String,
        exercises: List<SessionExercise>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError("Debes iniciar sesión")
            return
        }
        val data = hashMapOf<String, Any>(
            "teamId" to team.id,
            "teamName" to team.name,
            "category" to category,
            "date" to date,
            "durationMinutes" to durationMinutes,
            "objectives" to objectives,
            "exercises" to exercises.map {
                mapOf(
                    "exerciseId" to it.exerciseId,
                    "name" to it.name,
                    "description" to it.description,
                    "durationMinutes" to it.durationMinutes
                )
            },
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (existingId == null) {
            data["createdBy"] = uid
            data["createdAt"] = FieldValue.serverTimestamp()
            sessions.add(data)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it.userMessage("No se pudo guardar la sesión")) }
        } else {
            sessions.document(existingId).update(data)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it.userMessage("No se pudo guardar la sesión")) }
        }
    }

    fun delete(sessionId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        sessions.document(sessionId).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.userMessage("No se pudo eliminar la sesión")) }
    }

    private fun fromDocument(doc: DocumentSnapshot): TrainingSession? {
        if (!doc.exists()) return null
        val teamId = doc.getString("teamId") ?: return null
        val exercises = (doc.get("exercises") as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            SessionExercise(
                exerciseId = map["exerciseId"] as? String ?: "",
                name = map["name"] as? String ?: "Ejercicio",
                description = map["description"] as? String ?: "",
                durationMinutes = (map["durationMinutes"] as? Number)?.toInt() ?: 0
            )
        }.orEmpty()
        return TrainingSession(
            id = doc.id,
            teamId = teamId,
            teamName = doc.getString("teamName").orEmpty(),
            category = doc.getString("category") ?: TrainingSession.ALL_CATEGORIES,
            createdBy = doc.getString("createdBy").orEmpty(),
            date = doc.getTimestamp("date"),
            durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 0,
            objectives = doc.getString("objectives").orEmpty(),
            exercises = exercises,
            createdAt = doc.getTimestamp("createdAt")
        )
    }

    companion object {
        const val COLLECTION = "trainingSessions"
    }
}
