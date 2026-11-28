package dev.dreamteam.sportpro.data.model

import dev.dreamteam.sportpro.data.repository.CommunityRepository

/** Un equipo del usuario (dueño o miembro) con su academia vinculada, si la tiene. */
data class CommunityTeamInfo(val id: String, val name: String, val type: String, val academyId: String? = null)

/** US-18: reglas puras de la comunidad (mismo criterio que firestore.rules). */
object CommunityRules {
    const val MAX_CONTENT = 1000
    const val MAX_AUTHOR = 80
    const val DEFAULT_AUTHOR = "Usuario SportPro"
    const val HIDDEN_LABEL = "Ocultada por moderación"
    val VISIBILITIES = listOf("TODOS", "EQUIPO", "ACADEMIA")

    /** Nombre del perfil (nombre + apellido) o "Usuario SportPro"; nunca se deriva del correo. */
    fun authorName(firstName: String?, lastName: String?): String {
        val name = listOf(firstName.orEmpty(), lastName.orEmpty()).joinToString(" ").trim()
        return if (name.isNotEmpty() && name.length <= MAX_AUTHOR) name else DEFAULT_AUTHOR
    }

    fun roleLabel(code: String): String = when (code) {
        "JUGADOR" -> "Jugador"
        "ENTRENADOR" -> "Entrenador"
        else -> code.lowercase().replaceFirstChar { it.uppercase() }
    }

    /** TODOS sin equipo; EQUIPO/ACADEMIA con su equipo o academia. Mensaje de error o null. */
    fun validateTarget(visibility: String, teamId: String?): String? = when {
        visibility !in VISIBILITIES -> "Elige la visibilidad."
        visibility == "TODOS" && teamId != null -> "Las publicaciones para todos no llevan equipo."
        visibility != "TODOS" && teamId.isNullOrBlank() -> "Selecciona el equipo correspondiente a la visibilidad"
        else -> null
    }

    fun validateContent(content: String): String? = when {
        content.isBlank() -> "Escribe algo antes de publicar"
        content.trim().length > MAX_CONTENT -> "La publicación admite hasta $MAX_CONTENT caracteres."
        else -> null
    }

    /**
     * Destinos de publicación: los equipos y academias del usuario, más las academias de sus equipos
     * vinculados (para esas hace falta guardar academyAccess con [CommunityTeamTarget.viaTeamId]).
     */
    fun targets(teams: List<CommunityTeamInfo>): List<CommunityTeamTarget> {
        val own = teams.map { CommunityTeamTarget(it.id, it.name, it.type) }
        val ownIds = teams.map { it.id }.toSet()
        val linked = teams.filter { it.type == "EQUIPO" && !it.academyId.isNullOrBlank() && it.academyId !in ownIds }
            .distinctBy { it.academyId }
            .map { CommunityTeamTarget(it.academyId!!, "Academia de ${it.name}", "ACADEMIA", viaTeamId = it.id) }
        return (own + linked).sortedWith(compareBy<CommunityTeamTarget> { it.type }.thenBy { it.name.lowercase() })
    }

    /** Lo que se muestra: activas y no ocultadas; las propias ocultas también (con aviso). */
    fun visible(posts: List<CommunityPost>, viewerId: String): List<CommunityPost> =
        posts.filter { (it.active && !it.moderated) || it.authorId == viewerId }

    // ── US-19: comentarios, reacciones y reportes ──
    const val MAX_COMMENT = 300
    const val MAX_REASON = 500
    const val COMMENT_HIDDEN_LABEL = "Ocultado por moderación"
    val REPORT_REASONS = listOf("Contenido inapropiado", "Acoso o insulto", "Datos personales", "Spam", "Otro")

    fun validateComment(text: String): String? =
        if (text.trim().isEmpty() || text.trim().length > MAX_COMMENT) "Escribe entre 1 y $MAX_COMMENT caracteres" else null

    fun validateReason(reason: String): String? =
        if (reason.trim().isEmpty() || reason.trim().length > MAX_REASON) "Elige un motivo para el reporte." else null

    /** Une visibles y propios sin repetir, en orden de fecha (los pendientes sin hora al final). */
    fun mergeComments(list: List<CommunityRepository.PostComment>): List<CommunityRepository.PostComment> =
        list.groupBy { it.id }.values.map { same -> same.firstOrNull { it.pending } ?: same.first() }
            .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }

    /** El autor ve sus comentarios ocultos con aviso; los demás no los reciben. */
    fun commentNotice(comment: CommunityRepository.PostComment, viewerId: String): String? =
        if (comment.moderated && comment.authorId == viewerId) COMMENT_HIDDEN_LABEL else null

    /** REPORTAR: no en los propios ni en los ya ocultos. */
    fun canReport(comment: CommunityRepository.PostComment, viewerId: String, canEngage: Boolean): Boolean =
        canEngage && viewerId.isNotBlank() && comment.authorId != viewerId && !comment.moderated && !comment.pending

    /** Comentar o reaccionar: rol habilitado y publicación activa y no ocultada. */
    fun canInteract(post: CommunityPost?, canEngage: Boolean): Boolean =
        canEngage && post != null && post.active && !post.moderated

    fun hiddenNotice(post: CommunityPost, viewerId: String): String? =
        if ((post.moderated || !post.active) && post.authorId == viewerId) HIDDEN_LABEL else null
}
