package dev.dreamteam.sportpro.data.model

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

    fun hiddenNotice(post: CommunityPost, viewerId: String): String? =
        if ((post.moderated || !post.active) && post.authorId == viewerId) HIDDEN_LABEL else null
}
