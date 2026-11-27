package dev.dreamteam.sportpro.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.Exercise

/**
 * US-07: biblioteca de ejercicios compartida. Todos la consultan; cada entrenadora aprobada
 * crea, edita y elimina solo los suyos (firestore.rules lo exige).
 */
class ExerciseRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val exercises = firestore.collection(COLLECTION)

    val currentUserId: String? get() = auth.currentUser?.uid

    /** Toda la biblioteca, ordenada por nombre. */
    fun listenAll(onChange: (List<Exercise>) -> Unit, onError: (String) -> Unit): ListenerRegistration? {
        if (auth.currentUser == null) {
            onError("Debes iniciar sesión")
            return null
        }
        return exercises.limit(LIBRARY_LIMIT).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.userMessage("No se pudo cargar la biblioteca"))
                return@addSnapshotListener
            }
            onChange(snapshot?.documents?.mapNotNull(::fromDocument)?.sortedBy { it.name.lowercase() }.orEmpty())
        }
    }

    fun save(
        existingId: String?,
        name: String,
        description: String,
        durationMinutes: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError("Debes iniciar sesión")
            return
        }
        // El nombre de la autora se toma de su propio users/{uid}, igual que lo comprueban las reglas.
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { profile ->
                val data = hashMapOf<String, Any>(
                    "name" to name,
                    "description" to description,
                    "durationMinutes" to durationMinutes,
                    "creatorName" to Exercise.displayName(profile.getString("firstName"), profile.getString("lastName")),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val task = if (existingId == null) {
                    data["createdBy"] = uid
                    data["createdAt"] = FieldValue.serverTimestamp()
                    exercises.add(data)
                } else {
                    exercises.document(existingId).update(data)
                }
                task.addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it.userMessage("No se pudo guardar el ejercicio")) }
            }
            .addOnFailureListener { onError(it.userMessage("No se pudo leer tu perfil")) }
    }

    /** Las sesiones guardan una copia de sus ejercicios, así que eliminar aquí no altera el historial. */
    fun delete(exerciseId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        exercises.document(exerciseId).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.userMessage("No se pudo eliminar el ejercicio")) }
    }

    private fun fromDocument(doc: DocumentSnapshot): Exercise? {
        val name = doc.getString("name") ?: return null
        return Exercise(
            id = doc.id,
            createdBy = doc.getString("createdBy").orEmpty(),
            creatorName = doc.getString("creatorName").orEmpty(),
            name = name,
            // Ejercicios antiguos pueden no tener descripción: se muestran igual.
            description = doc.getString("description").orEmpty(),
            durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 0,
            createdAt = doc.getTimestamp("createdAt"),
            updatedAt = doc.getTimestamp("updatedAt")
        )
    }

    companion object {
        const val COLLECTION = "exercises"
        private const val LIBRARY_LIMIT = 500L
    }
}
