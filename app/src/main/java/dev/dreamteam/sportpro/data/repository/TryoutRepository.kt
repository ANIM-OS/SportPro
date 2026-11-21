package dev.dreamteam.sportpro.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.TRYOUT_ACTIVE
import dev.dreamteam.sportpro.data.model.TRYOUT_CANCELLED
import dev.dreamteam.sportpro.data.model.TryoutNotice
import java.util.Date

/** US-20. Single-field queries only, so no composite indexes are needed; filtering happens on the device. */
class TryoutRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val notices get() = firestore.collection("tryoutNotices")

    val currentUserId: String? get() = auth.currentUser?.uid

    fun observeActive(onUpdate: (List<TryoutNotice>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        notices.whereEqualTo("status", TRYOUT_ACTIVE).addSnapshotListener { snapshot, error ->
            if (error != null) onError(readError(error, "No se pudieron cargar las convocatorias"))
            else onUpdate(snapshot?.documents.orEmpty().map(::toNotice))
        }

    fun observeMine(onUpdate: (List<TryoutNotice>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        val uid = currentUserId ?: return ListenerRegistration { }
        return notices.whereEqualTo("authorId", uid).addSnapshotListener { snapshot, error ->
            if (error != null) onError(readError(error, "No se pudieron cargar tus avisos"))
            else onUpdate(snapshot?.documents.orEmpty().map(::toNotice))
        }
    }

    fun create(
        title: String,
        description: String,
        category: String?,
        position: String?,
        tryoutDateMillis: Long?,
        location: String?,
        deadlineMillis: Long?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = auth.currentUser ?: run {
            onError("Debes iniciar sesión para publicar")
            return
        }
        val authorName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")?.replace(".", " ")?.replaceFirstChar { it.uppercase() }
            ?: "Entrenador SportPro"
        val data = hashMapOf<String, Any>(
            "authorId" to user.uid,
            "authorName" to authorName.take(100),
            "title" to title.trim(),
            "description" to description.trim(),
            "status" to TRYOUT_ACTIVE,
            "createdAt" to FieldValue.serverTimestamp()
        )
        category?.let { data["category"] = it }
        position?.let { data["position"] = it }
        tryoutDateMillis?.let { data["tryoutDate"] = Timestamp(Date(it)) }
        location?.trim()?.takeIf { it.isNotEmpty() }?.let { data["location"] = it }
        deadlineMillis?.let { data["deadline"] = Timestamp(Date(it)) }

        notices.add(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(writeError(it, "No se pudo publicar el aviso")) }
    }

    fun cancel(noticeId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        notices.document(noticeId)
            .update(mapOf("status" to TRYOUT_CANCELLED, "cancelledAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(writeError(it, "No se pudo cancelar el aviso")) }
    }

    fun checkAdmin(onResult: (Boolean) -> Unit) {
        val uid = currentUserId ?: run {
            onResult(false)
            return
        }
        firestore.collection("admins").document(uid).get()
            .addOnSuccessListener { onResult(it.exists()) }
            .addOnFailureListener { onResult(false) }
    }

    private fun toNotice(document: DocumentSnapshot) = TryoutNotice(
        id = document.id,
        authorId = document.getString("authorId").orEmpty(),
        authorName = document.getString("authorName").orEmpty(),
        title = document.getString("title").orEmpty(),
        description = document.getString("description").orEmpty(),
        category = document.getString("category"),
        position = document.getString("position"),
        tryoutDateMillis = document.getTimestamp("tryoutDate")?.toDate()?.time,
        location = document.getString("location"),
        deadlineMillis = document.getTimestamp("deadline")?.toDate()?.time,
        status = document.getString("status") ?: TRYOUT_ACTIVE,
        createdAtMillis = document.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
    )

    private fun readError(error: FirebaseFirestoreException, fallback: String): String =
        if (error.code == FirebaseFirestoreException.Code.UNAVAILABLE) NO_CONNECTION else fallback

    private fun writeError(error: Exception, fallback: String): String =
        when ((error as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "No tienes permiso para esta acción. Para publicar avisos tu rol de entrenador debe estar aprobado"
            FirebaseFirestoreException.Code.UNAVAILABLE -> NO_CONNECTION
            else -> fallback
        }

    private companion object {
        const val NO_CONNECTION = "Sin conexión. Revisa tu internet e inténtalo de nuevo"
    }
}
