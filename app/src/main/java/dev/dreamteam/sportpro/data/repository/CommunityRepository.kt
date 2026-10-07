package dev.dreamteam.sportpro.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.CommunityPost
import dev.dreamteam.sportpro.data.model.CommunityTeamTarget

class CommunityRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    fun loadPostTargets(onResult: (List<CommunityTeamTarget>) -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            onResult(emptyList())
            return
        }

        firestore.collection("teamMemberships").whereEqualTo("userId", uid).get()
            .addOnSuccessListener { memberships ->
                val memberTeamIds = memberships.documents.mapNotNull { it.getString("teamId") }
                firestore.collection("teams").whereEqualTo("createdBy", uid).get()
                    .addOnSuccessListener { ownedTeams ->
                        val teamIds = (memberTeamIds + ownedTeams.documents.map { it.id }).distinct()
                        loadTargets(teamIds, 0, mutableListOf(), onResult, onError)
                    }
                    .addOnFailureListener { onError(it.localizedMessage ?: "No se pudieron cargar tus equipos") }
            }
            .addOnFailureListener { onError(it.localizedMessage ?: "No se pudieron cargar tus membresías") }
    }

    private fun loadTargets(
        ids: List<String>,
        index: Int,
        targets: MutableList<CommunityTeamTarget>,
        onResult: (List<CommunityTeamTarget>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (index >= ids.size) {
            onResult(targets.sortedBy { it.name.lowercase() })
            return
        }
        firestore.collection("teams").document(ids[index]).get()
            .addOnSuccessListener { team ->
                if (team.exists()) {
                    targets += CommunityTeamTarget(
                        id = team.id,
                        name = team.getString("name") ?: "Equipo",
                        type = team.getString("type") ?: "EQUIPO"
                    )
                }
                loadTargets(ids, index + 1, targets, onResult, onError)
            }
            .addOnFailureListener { onError(it.localizedMessage ?: "No se pudieron cargar tus equipos") }
    }

    fun createPost(
        content: String,
        visibility: String,
        teamId: String?,
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
        if (visibility !in setOf("TODOS", "EQUIPO", "ACADEMIA") ||
            (visibility == "TODOS" && teamId != null) ||
            (visibility != "TODOS" && teamId.isNullOrBlank())
        ) {
            onError("Selecciona el equipo correspondiente a la visibilidad")
            return
        }

        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { profile ->
                val roles = (profile.get("roles") as? List<*>)?.filterIsInstance<String>().orEmpty()
                val authorRole = roles.firstOrNull { it == "JUGADOR" || it == "ENTRENADOR" }
                if (authorRole == null) {
                    onError("Tu perfil no tiene un rol habilitado para publicar")
                    return@addOnSuccessListener
                }

                val authorName = user.displayName
                    ?: user.email?.substringBefore("@")?.replace(".", " ")?.replaceFirstChar { it.uppercase() }
                    ?: "Usuario SportPro"
                val post = hashMapOf<String, Any>(
                    "authorId" to user.uid,
                    "authorName" to authorName,
                    "authorRole" to authorRole,
                    "content" to content.trim(),
                    "visibility" to visibility,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "active" to true,
                    "moderated" to false
                )
                if (teamId != null) post["teamId"] = teamId

                firestore.collection("communityPosts").add(post)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo publicar") }
            }
            .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo obtener la información del usuario") }
    }

    fun observePosts(
        onUpdate: (List<CommunityPost>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onError("Debes iniciar sesión")
            return ListenerRegistration { }
        }

        val registrations = mutableListOf<ListenerRegistration>()
        val teamRegistrations = mutableMapOf<String, ListenerRegistration>()
        val loadingTeamIds = mutableSetOf<String>()
        val postsByQuery = mutableMapOf<String, List<CommunityPost>>()
        var memberTeamIds: Set<String>? = null
        var ownedTeamIds: Set<String>? = null

        fun emit() {
            onUpdate(postsByQuery.values.flatten().distinctBy { it.id }.sortedByDescending { it.createdAt })
        }

        fun listen(key: String, query: com.google.firebase.firestore.Query) {
            registrations += query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.localizedMessage ?: "No se pudieron cargar las publicaciones")
                    return@addSnapshotListener
                }
                postsByQuery[key] = snapshot?.documents.orEmpty().map { document ->
                    CommunityPost(
                        id = document.id,
                        authorId = document.getString("authorId") ?: "",
                        authorName = document.getString("authorName") ?: "",
                        authorRole = document.getString("authorRole") ?: "",
                        content = document.getString("content") ?: "",
                        visibility = document.getString("visibility") ?: "TODOS",
                        teamId = document.getString("teamId"),
                        createdAt = document.getTimestamp("createdAt"),
                        active = document.getBoolean("active") ?: true,
                        moderated = document.getBoolean("moderated") ?: false
                    )
                }.filter { it.active && !it.moderated }
                emit()
            }
        }

        fun refreshTeamListeners() {
            val teamIds = (memberTeamIds.orEmpty() + ownedTeamIds.orEmpty()).toSet()
            (teamRegistrations.keys - teamIds).forEach { teamId ->
                teamRegistrations.remove(teamId)?.remove()
                postsByQuery.remove("team:$teamId")
            }
            (teamIds - teamRegistrations.keys - loadingTeamIds).forEach { teamId ->
                loadingTeamIds += teamId
                firestore.collection("teams").document(teamId).get()
                    .addOnSuccessListener { team ->
                        loadingTeamIds -= teamId
                        if (!team.exists() || teamId !in (memberTeamIds.orEmpty() + ownedTeamIds.orEmpty())) return@addOnSuccessListener
                        val audience = team.getString("type") ?: "EQUIPO"
                        val registration = firestore.collection("communityPosts")
                            .whereEqualTo("teamId", teamId)
                            .whereEqualTo("visibility", audience)
                            .whereEqualTo("active", true)
                            .whereEqualTo("moderated", false)
                            .addSnapshotListener { snapshot, error ->
                                if (error != null) {
                                    onError(error.localizedMessage ?: "No se pudieron cargar las publicaciones del equipo")
                                    return@addSnapshotListener
                                }
                                postsByQuery["team:$teamId"] = snapshot?.documents.orEmpty().map { document ->
                                    CommunityPost(
                                        id = document.id,
                                        authorId = document.getString("authorId") ?: "",
                                        authorName = document.getString("authorName") ?: "",
                                        authorRole = document.getString("authorRole") ?: "",
                                        content = document.getString("content") ?: "",
                                        visibility = document.getString("visibility") ?: "TODOS",
                                        teamId = document.getString("teamId"),
                                        createdAt = document.getTimestamp("createdAt"),
                                        active = document.getBoolean("active") ?: true,
                                        moderated = document.getBoolean("moderated") ?: false
                                    )
                                }
                                emit()
                            }
                        teamRegistrations[teamId] = registration
                    }
                    .addOnFailureListener {
                        loadingTeamIds -= teamId
                        onError(it.localizedMessage ?: "No se pudo cargar el equipo")
                    }
            }
            emit()
        }

        listen("public", firestore.collection("communityPosts")
            .whereEqualTo("visibility", "TODOS")
            .whereEqualTo("active", true)
            .whereEqualTo("moderated", false))
        listen("authored", firestore.collection("communityPosts")
            .whereEqualTo("authorId", uid)
            .whereEqualTo("active", true)
            .whereEqualTo("moderated", false))
        registrations += firestore.collection("teamMemberships").whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) onError(error.localizedMessage ?: "No se pudieron cargar tus equipos")
                else {
                    memberTeamIds = snapshot?.documents.orEmpty().mapNotNull { it.getString("teamId") }.toSet()
                    refreshTeamListeners()
                }
            }
        registrations += firestore.collection("teams").whereEqualTo("createdBy", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) onError(error.localizedMessage ?: "No se pudieron cargar tus equipos")
                else {
                    ownedTeamIds = snapshot?.documents.orEmpty().map { it.id }.toSet()
                    refreshTeamListeners()
                }
            }

        return object : ListenerRegistration {
            override fun remove() {
                registrations.forEach(ListenerRegistration::remove)
                teamRegistrations.values.forEach(ListenerRegistration::remove)
                teamRegistrations.clear()
            }
        }
    }

    fun checkModerator(onResult: (Boolean) -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            onResult(false)
            return
        }
        firestore.collection("moderators").document(uid).get()
            .addOnSuccessListener { onResult(it.exists()) }
            .addOnFailureListener { onError("No se pudieron comprobar los permisos de moderación") }
    }

    fun moderatePost(postId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        firestore.collection("communityPosts").document(postId)
            .update("moderated", true)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo ocultar la publicación") }
    }
}
