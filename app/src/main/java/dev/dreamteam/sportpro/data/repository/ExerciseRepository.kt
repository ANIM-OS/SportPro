package dev.dreamteam.sportpro.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.Exercise

/** US-07: biblioteca de ejercicios. Cada entrenador administra sus propios ejercicios. */
class ExerciseRepository {

    private val auth = FirebaseAuth.getInstance()
    private val exercises = FirebaseFirestore.getInstance().collection(COLLECTION)

    fun listenMine(onChange: (List<Exercise>) -> Unit, onError: (String) -> Unit): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: run {
            onError("Debes iniciar sesión")
            return null
        }
        return exercises.whereEqualTo("createdBy", uid)
            .addSnapshotListener { snapshot, error ->
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
        val data = hashMapOf<String, Any>(
            "name" to name,
            "description" to description,
            "durationMinutes" to durationMinutes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (existingId == null) {
            data["createdBy"] = uid
            data["createdAt"] = FieldValue.serverTimestamp()
            exercises.add(data)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it.userMessage("No se pudo guardar el ejercicio")) }
        } else {
            exercises.document(existingId).update(data)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it.userMessage("No se pudo guardar el ejercicio")) }
        }
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
            name = name,
            description = doc.getString("description").orEmpty(),
            durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 0,
            createdAt = doc.getTimestamp("createdAt"),
            updatedAt = doc.getTimestamp("updatedAt")
        )
    }

    companion object {
        const val COLLECTION = "exercises"
    }
}
