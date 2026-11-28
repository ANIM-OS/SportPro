package dev.dreamteam.sportpro.data.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Transaction
import com.google.firebase.Timestamp
import dev.dreamteam.sportpro.data.model.CommunityPost
import dev.dreamteam.sportpro.data.model.CommunityRules
import dev.dreamteam.sportpro.data.model.CommunityTeamInfo
import dev.dreamteam.sportpro.data.model.CommunityTeamTarget
import dev.dreamteam.sportpro.domain.access.COACH_PENDING_MESSAGE

class CommunityRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    /** Equipos y academias del usuario (dueño o miembro), con la academia vinculada de cada equipo. */
    private fun loadMyTeams(uid: String, onResult: (List<CommunityTeamInfo>) -> Unit, onError: (String) -> Unit) {
        firestore.collection("teamMemberships").whereEqualTo("userId", uid).get()
            .addOnSuccessListener { memberships ->
                val memberTeamIds = memberships.documents.mapNotNull { it.getString("teamId") }
                firestore.collection("teams").whereEqualTo("createdBy", uid).get()
                    .addOnSuccessListener { ownedTeams ->
                        val owned = ownedTeams.documents.map(::teamInfo)
                        val missing = (memberTeamIds - owned.map { it.id }.toSet()).distinct()
                        val reads = missing.map { firestore.collection("teams").document(it).get() }
                        Tasks.whenAllComplete(reads).addOnCompleteListener {
                            val member = reads.mapNotNull { task -> task.result?.takeIf { task.isSuccessful && it.exists() }?.let(::teamInfo) }
                            onResult(owned + member)
                        }
                    }
                    .addOnFailureListener { onError("No se pudieron cargar tus equipos") }
            }
            .addOnFailureListener { onError("No se pudieron cargar tus membresías") }
    }

    private fun teamInfo(doc: DocumentSnapshot) = CommunityTeamInfo(doc.id, doc.getString("name") ?: "Equipo",
        doc.getString("type") ?: "EQUIPO", doc.getString("academyId")?.takeIf { it.isNotBlank() })

    fun loadPostTargets(onResult: (List<CommunityTeamTarget>) -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            onResult(emptyList())
            return
        }
        loadMyTeams(uid, { teams -> onResult(CommunityRules.targets(teams)) }, onError)
    }

    /** US-18: acceso a una academia por pertenecer a un equipo vinculado (las reglas lo revalidan). */
    private fun openAcademy(academyId: String, viaTeamId: String) =
        firestore.collection("academyAccess").document("${academyId}_${auth.currentUser?.uid}")
            .set(mapOf("academyId" to academyId, "teamId" to viaTeamId, "updatedAt" to FieldValue.serverTimestamp()))

    /**
     * Publica sin bloquear sin conexión: usa el perfil (caché si no hay red), escribe con add() y avisa
     * de inmediato; la publicación se ve como "Pendiente de sincronizar" hasta que el servidor la confirma.
     * Si el servidor la rechaza después, [onError] lo indica.
     */
    fun createPost(
        content: String,
        visibility: String,
        teamId: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        viaTeamId: String? = null
    ) {
        val user = auth.currentUser
        if (user == null) {
            onError("Debes iniciar sesión para publicar")
            return
        }
        CommunityRules.validateContent(content)?.let { onError(it); return }
        CommunityRules.validateTarget(visibility, teamId)?.let { onError(it); return }

        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { profile ->
                val roles = (profile.get("roles") as? List<*>)?.filterIsInstance<String>().orEmpty()
                firestore.collection("coachApprovals").document(user.uid).get()
                    .addOnCompleteListener { approval ->
                        val coachApproved = approval.isSuccessful && approval.result?.getBoolean("approved") == true
                        val authorRole = when {
                            "ENTRENADOR" in roles && coachApproved -> "ENTRENADOR"
                            "JUGADOR" in roles -> "JUGADOR"
                            else -> null
                        }
                        if (authorRole == null) {
                            onError(
                                if ("ENTRENADOR" in roles) COACH_PENDING_MESSAGE
                                else "Tu perfil no tiene un rol habilitado para publicar"
                            )
                            return@addOnCompleteListener
                        }

                        val post = hashMapOf<String, Any>(
                            "authorId" to user.uid,
                            // Nombre del perfil o "Usuario SportPro"; nunca del correo (las reglas lo comprueban).
                            "authorName" to CommunityRules.authorName(profile.getString("firstName"), profile.getString("lastName")),
                            "authorRole" to authorRole,
                            "content" to content.trim(),
                            "visibility" to visibility,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "active" to true,
                            "moderated" to false
                        )
                        if (teamId != null) post["teamId"] = teamId
                        // ACADEMIA por un equipo vinculado: primero el acceso (las escrituras se envían en orden).
                        if (visibility == "ACADEMIA" && teamId != null && viaTeamId != null) openAcademy(teamId, viaTeamId)
                        firestore.collection("communityPosts").add(post)
                            .addOnFailureListener { onError("No se pudo publicar: no tienes permiso o los datos no son válidos.") }
                        onSuccess()
                    }
            }
            .addOnFailureListener { onError("Conéctate una vez para cargar tu perfil y poder publicar.") }
    }

    private fun toPost(document: DocumentSnapshot) = CommunityPost(
        id = document.id,
        authorId = document.getString("authorId") ?: "",
        authorName = document.getString("authorName") ?: "",
        authorRole = document.getString("authorRole") ?: "",
        content = document.getString("content") ?: "",
        visibility = document.getString("visibility") ?: "TODOS",
        teamId = document.getString("teamId"),
        createdAt = document.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
        active = document.getBoolean("active") ?: true,
        moderated = document.getBoolean("moderated") ?: false,
        pending = document.metadata.hasPendingWrites()
    )

    /**
     * Publicaciones que el usuario puede ver (las consultas coinciden con las reglas):
     * - todas: visibility == TODOS (activas, no ocultadas);
     * - propias: authorId == uid (incluidas las ocultadas, con aviso);
     * - EQUIPO/ACADEMIA de sus equipos y academias (dueña o miembro);
     * - ACADEMIA de las academias de sus equipos vinculados (tras guardar academyAccess);
     * - EQUIPO de los equipos de sus jugadores vinculados si es tutor (tras guardar announcementGuardians).
     */
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
        val audienceRegistrations = mutableMapOf<String, ListenerRegistration>()
        val postsByQuery = mutableMapOf<String, List<CommunityPost>>()
        var removed = false

        fun emit() {
            val all = postsByQuery.values.flatten().distinctBy { it.id }
            onUpdate(CommunityRules.visible(all, uid).sortedByDescending { it.createdAt })
        }

        fun listen(key: String, query: Query) {
            registrations += query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    onError("No se pudieron cargar las publicaciones")
                    return@addSnapshotListener
                }
                postsByQuery[key] = snapshot?.documents.orEmpty().map(::toPost)
                emit()
            }
        }

        /** Escucha las publicaciones de un destino (equipo o academia) con la misma forma que exigen las reglas. */
        fun listenAudience(teamId: String, visibility: String) {
            val key = "$visibility:$teamId"
            if (removed || key in audienceRegistrations) return
            audienceRegistrations[key] = firestore.collection("communityPosts")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("visibility", visibility)
                .whereEqualTo("active", true)
                .whereEqualTo("moderated", false)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        onError("No se pudieron cargar las publicaciones del equipo")
                        return@addSnapshotListener
                    }
                    postsByQuery[key] = snapshot?.documents.orEmpty().map(::toPost)
                    emit()
                }
        }

        listen("public", firestore.collection("communityPosts")
            .whereEqualTo("visibility", "TODOS")
            .whereEqualTo("active", true)
            .whereEqualTo("moderated", false))
        // Propias sin filtro de moderación: el autor ve las suyas ocultas con "Ocultada por moderación".
        listen("authored", firestore.collection("communityPosts").whereEqualTo("authorId", uid))

        // Equipos y academias propios o de membresía, y las academias de los equipos vinculados.
        fun refreshTeams() {
            loadMyTeams(uid, { teams ->
                if (removed) return@loadMyTeams
                teams.forEach { listenAudience(it.id, it.type) }
                val ownIds = teams.map { it.id }.toSet()
                val academies = teams.filter { it.type == "EQUIPO" && it.academyId != null && it.academyId !in ownIds }
                academies.distinctBy { it.academyId }.forEach { team ->
                    openAcademy(team.academyId!!, team.id)
                        .addOnSuccessListener { listenAudience(team.academyId, "ACADEMIA") }
                        .addOnFailureListener { onError("No se pudo abrir la academia de ${team.name}") }
                }
            }, onError)
        }
        registrations += firestore.collection("teamMemberships").whereEqualTo("userId", uid)
            .addSnapshotListener { _, error -> if (error == null) refreshTeams() }
        registrations += firestore.collection("teams").whereEqualTo("createdBy", uid)
            .addSnapshotListener { _, error -> if (error == null) refreshTeams() }

        // Tutor: publicaciones EQUIPO de los equipos de sus jugadores vinculados (solo lectura).
        if (auth.currentUser?.isEmailVerified == true) {
            val childListeners = mutableMapOf<String, ListenerRegistration>()
            registrations += firestore.collection("guardianLinks").whereEqualTo("guardianId", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || removed) return@addSnapshotListener
                    val children = snapshot?.documents.orEmpty().filter { it.getString("status") == "ACTIVE" }
                        .mapNotNull { it.getString("playerId") }.toSet()
                    (childListeners.keys - children).forEach { childListeners.remove(it)?.remove() }
                    (children - childListeners.keys).forEach { child ->
                        childListeners[child] = firestore.collection("teamMemberships").whereEqualTo("userId", child)
                            .addSnapshotListener { memberships, failure ->
                                if (failure != null || removed) return@addSnapshotListener
                                memberships?.documents.orEmpty().mapNotNull { it.getString("teamId") }.distinct().forEach { teamId ->
                                    AnnouncementRepository(firestore).openAsGuardian(teamId, child)
                                        .addOnSuccessListener { listenAudience(teamId, "EQUIPO") }
                                }
                            }
                    }
                }
            registrations += ListenerRegistration { childListeners.values.forEach(ListenerRegistration::remove) }
        }

        return object : ListenerRegistration {
            override fun remove() {
                removed = true
                registrations.forEach(ListenerRegistration::remove)
                audienceRegistrations.values.forEach(ListenerRegistration::remove)
                audienceRegistrations.clear()
            }
        }
    }

    fun checkModerator(onResult: (Boolean) -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            onResult(false)
            return
        }
        val failure = { _: Exception -> onError("No se pudieron comprobar los permisos de moderación") }
        firestore.collection("moderators").document(uid).get()
            .addOnSuccessListener { moderator ->
                if (moderator.exists()) {
                    onResult(true)
                } else {
                    firestore.collection("admins").document(uid).get()
                        .addOnSuccessListener { onResult(it.exists()) }
                        .addOnFailureListener(failure)
                }
            }
            .addOnFailureListener(failure)
    }

    fun moderatePost(postId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        firestore.collection("communityPosts").document(postId)
            .update("moderated", true)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo ocultar la publicación") }
    }

    /** US-19: comentarios por publicación. Las reglas de Firestore deben validar visibilidad. */
    data class PostComment(
        val id: String,
        val authorId: String,
        val authorName: String,
        val text: String,
        val createdAt: Timestamp?,
        val moderated: Boolean = false
    )

    fun observeComments(postId: String, onUpdate: (List<PostComment>) -> Unit,
                        onError: (String) -> Unit): ListenerRegistration =
        firestore.collection("communityPosts").document(postId).collection("comments")
            .whereEqualTo("moderated", false)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    onError(error.localizedMessage ?: "No se pudieron cargar los comentarios")
                } else {
                    onUpdate(snap?.documents.orEmpty().map { doc ->
                        PostComment(
                            id = doc.id,
                            authorId = doc.getString("authorId").orEmpty(),
                            authorName = doc.getString("authorName").orEmpty(),
                            text = doc.getString("text").orEmpty(),
                            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                            moderated = doc.getBoolean("moderated") == true
                        )
                    }.sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE })
                }
            }

    /** Un documento por UID asegura como máximo un 'Me gusta' por usuario. */
    fun observeLikes(postId: String, onUpdate: (Int, Boolean) -> Unit,
                     onError: (String) -> Unit): ListenerRegistration {
        val uid = auth.currentUser?.uid
        return firestore.collection("communityPosts").document(postId).collection("likes")
            .addSnapshotListener { snap, error ->
                if (error != null) onError(error.localizedMessage ?: "No se pudieron cargar los Me gusta")
                else onUpdate(snap?.size() ?: 0, uid != null && snap?.documents.orEmpty().any { it.id == uid })
            }
    }

    /** El usuario debe estar autenticado y la publicación visible para él (reforzar con reglas). */
    fun addComment(postId: String, text: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser ?: run { onError("Inicia sesión para comentar"); return }
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > 300) {
            onError("Escribe entre 1 y 300 caracteres"); return
        }
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { profile ->
                val first = profile.getString("firstName").orEmpty().trim()
                val last = profile.getString("lastName").orEmpty().trim()
                val name = "$first $last".trim().ifBlank {
                    user.displayName?.takeIf { it.isNotBlank() }
                        ?: user.email?.substringBefore('@') ?: "Usuario SportPro"
                }
                val data = mapOf(
                    "authorId" to user.uid,
                    "authorName" to name,
                    "text" to clean,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "moderated" to false
                )
                firestore.collection("communityPosts").document(postId).collection("comments").add(data)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo comentar") }
            }
            .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo consultar el perfil") }
    }

    fun setLiked(postId: String, liked: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser ?: run { onError("Inicia sesión para reaccionar"); return }
        val doc = firestore.collection("communityPosts").document(postId)
            .collection("likes").document(user.uid)
        val task = if (liked) doc.set(mapOf("userId" to user.uid,
            "createdAt" to FieldValue.serverTimestamp())) else doc.delete()
        task.addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo guardar el Me gusta") }
    }

    fun reportComment(postId: String, commentId: String, reason: String,
                      onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { onError("Inicia sesión para reportar"); return }
        // Identidad determinista: el mismo usuario no genera múltiples reportes del mismo comentario.
        firestore.collection("communityPosts").document(postId).collection("comments")
            .document(commentId).collection("reports").document(uid)
            .set(mapOf("reporterId" to uid, "reason" to reason,
                "createdAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo enviar el reporte") }
    }

    fun moderateComment(postId: String, commentId: String,
                        onSuccess: () -> Unit, onError: (String) -> Unit) {
        checkModerator(onResult = { allowed ->
            if (!allowed) { onError("No tienes permisos para moderar"); return@checkModerator }
            firestore.collection("communityPosts").document(postId).collection("comments")
                .document(commentId).update("moderated", true)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it.localizedMessage ?: "No se pudo moderar") }
        }, onError = onError)
    }

}
