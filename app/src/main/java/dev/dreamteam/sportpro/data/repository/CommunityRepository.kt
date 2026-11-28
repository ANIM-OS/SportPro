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

    // US-21: ocultar, restaurar y reportar están en ModerationRepository.

    /** US-19: comentario de una publicación. */
    data class PostComment(
        val id: String,
        val authorId: String,
        val authorName: String,
        val text: String,
        val createdAt: Timestamp?,
        val moderated: Boolean = false,
        /** Escrito en este celular y aún no confirmado por el servidor. */
        val pending: Boolean = false
    )

    private fun toComment(doc: DocumentSnapshot) = PostComment(
        id = doc.id,
        authorId = doc.getString("authorId").orEmpty(),
        authorName = doc.getString("authorName").orEmpty(),
        text = doc.getString("text").orEmpty(),
        createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
        moderated = doc.getBoolean("moderated") == true,
        pending = doc.metadata.hasPendingWrites()
    )

    /**
     * Comentarios visibles (moderated == false) más los propios (authorId == uid), que incluyen los
     * ocultados por moderación: el autor los ve con "Ocultado por moderación". Ambas consultas coinciden
     * con las reglas. Con MetadataChanges.INCLUDE se ve "Pendiente de sincronizar".
     */
    fun observeComments(postId: String, onUpdate: (List<PostComment>) -> Unit,
                        onError: (String) -> Unit): ListenerRegistration {
        val comments = firestore.collection("communityPosts").document(postId).collection("comments")
        val uid = auth.currentUser?.uid
        val byQuery = mutableMapOf<String, List<PostComment>>()
        fun emit() = onUpdate(CommunityRules.mergeComments(byQuery.values.flatten()))
        val visible = comments.whereEqualTo("moderated", false)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null) onError("No se pudieron cargar los comentarios")
                else { byQuery["visible"] = snap?.documents.orEmpty().map(::toComment); emit() }
            }
        val own = uid?.let {
            comments.whereEqualTo("authorId", it).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error == null) { byQuery["own"] = snap?.documents.orEmpty().map(::toComment); emit() }
            }
        }
        return ListenerRegistration { visible.remove(); own?.remove() }
    }

    /**
     * Un documento por UID asegura como máximo un "Me gusta" por usuario. [onUpdate]: total, si el propio
     * existe y si está pendiente de sincronizar.
     */
    fun observeLikes(postId: String, onUpdate: (Int, Boolean, Boolean) -> Unit,
                     onError: (String) -> Unit): ListenerRegistration {
        val uid = auth.currentUser?.uid
        return firestore.collection("communityPosts").document(postId).collection("likes")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null) onError("No se pudieron cargar los Me gusta")
                else {
                    val mine = snap?.documents.orEmpty().firstOrNull { it.id == uid }
                    onUpdate(snap?.size() ?: 0, mine != null, snap?.metadata?.hasPendingWrites() == true)
                }
            }
    }

    /**
     * Comenta sin bloquear sin conexión: nombre del perfil (caché si no hay red) o "Usuario SportPro",
     * nunca displayName ni correo. [onSuccess] se llama al guardar en el celular; si el servidor lo
     * rechaza después, [onError].
     */
    fun addComment(postId: String, text: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser ?: run { onError("Inicia sesión para comentar"); return }
        CommunityRules.validateComment(text)?.let { onError(it); return }
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { profile ->
                val data = mapOf(
                    "authorId" to user.uid,
                    "authorName" to CommunityRules.authorName(profile.getString("firstName"), profile.getString("lastName")),
                    "text" to text.trim(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "moderated" to false
                )
                firestore.collection("communityPosts").document(postId).collection("comments").add(data)
                    .addOnFailureListener { onError("No se pudo comentar: no tienes permiso o la publicación ya no está disponible.") }
                onSuccess()
            }
            .addOnFailureListener { onError("Conéctate una vez para cargar tu perfil y poder comentar.") }
    }

    /** Dar o quitar el "Me gusta" propio; se aplica al instante (también sin conexión). */
    fun setLiked(postId: String, liked: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser ?: run { onError("Inicia sesión para reaccionar"); return }
        val doc = firestore.collection("communityPosts").document(postId)
            .collection("likes").document(user.uid)
        val task = if (liked) doc.set(mapOf("userId" to user.uid,
            "createdAt" to FieldValue.serverTimestamp())) else doc.delete()
        task.addOnFailureListener { onError("No se pudo guardar el Me gusta.") }
        onSuccess()
    }

}
