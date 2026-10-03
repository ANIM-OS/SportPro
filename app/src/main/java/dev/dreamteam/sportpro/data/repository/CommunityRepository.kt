package dev.dreamteam.sportpro.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dev.dreamteam.sportpro.data.model.CommunityPost

class CommunityRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    fun createPost(
        content: String,
        visibility: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = auth.currentUser

        if (user == null) {
            onError("Debes iniciar sesión para publicar")
            return
        }

        if (content.isBlank()) {
            onError("Escribe algo antes de publicar")
            return
        }

        // Primero obtenemos la información del usuario.
        firestore.collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->

                val roles = document.get("roles")
                        as? List<*>
                    ?: emptyList<Any>()

                val authorRole = roles
                    .map { it.toString() }
                    .firstOrNull {
                        it == "JUGADOR" ||
                                it == "ENTRENADOR" ||
                                it == "ACADEMIA"
                    }

                if (authorRole == null) {
                    onError("Tu rol no tiene permiso para publicar en la comunidad")
                    return@addOnSuccessListener
                }

                val authorName =
                    user.displayName
                        ?: user.email
                            ?.substringBefore("@")
                            ?.replace(".", " ")
                            ?.replaceFirstChar { it.uppercase() }
                        ?: "Usuario SportPro"

                val post = hashMapOf(
                    "authorId" to user.uid,
                    "authorName" to authorName,
                    "authorRole" to authorRole,
                    "content" to content.trim(),
                    "visibility" to visibility,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "active" to true,
                    "moderated" to false
                )

                firestore.collection("communityPosts")
                    .add(post)
                    .addOnSuccessListener {
                        onSuccess()
                    }
                    .addOnFailureListener { error ->
                        onError(
                            error.localizedMessage
                                ?: "No se pudo publicar"
                        )
                    }
            }
            .addOnFailureListener {
                onError("No se pudo obtener la información del usuario")
            }
    }

    fun observePosts(
        onUpdate: (List<CommunityPost>) -> Unit,
        onError: (String) -> Unit
    ) = firestore.collection("communityPosts")
        .whereEqualTo("active", true)
        .addSnapshotListener { snapshot, error ->

            if (error != null) {
                onError(
                    error.localizedMessage
                        ?: "No se pudieron cargar las publicaciones"
                )
                return@addSnapshotListener
            }

            val posts = snapshot
                ?.documents
                ?.map { document ->

                    CommunityPost(
                        id = document.id,
                        authorId = document.getString("authorId") ?: "",
                        authorName = document.getString("authorName") ?: "",
                        authorRole = document.getString("authorRole") ?: "",
                        content = document.getString("content") ?: "",
                        visibility = document.getString("visibility") ?: "TODOS",
                        createdAt = document.getTimestamp("createdAt"),
                        active = document.getBoolean("active") ?: true,
                        moderated = document.getBoolean("moderated") ?: false
                    )
                }
                ?.filter { !it.moderated }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()

            onUpdate(posts)
        }
}