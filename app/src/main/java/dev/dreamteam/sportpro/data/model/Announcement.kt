package dev.dreamteam.sportpro.data.model

/** US-17: tipo de anuncio interno (lista cerrada, igual que en las reglas). */
enum class AnnouncementType(val label: String) {
    CONVOCATORIA("Convocatoria"),
    ENTRENAMIENTO("Entrenamiento"),
    CAMBIO_HORARIO("Cambio de horario");

    companion object {
        fun fromCode(code: String?): AnnouncementType? = entries.firstOrNull { it.name == code }
    }
}

/** announcements/{id}: anuncio dirigido a un equipo. No se edita; lo borra el autor o el admin. */
data class Announcement(
    val id: String,
    val teamId: String,
    val type: AnnouncementType,
    val title: String,
    val body: String,
    val authorId: String,
    /** Hora del servidor (estimada mientras está pendiente); null si aún no se conoce. */
    val createdAtMillis: Long?,
    /** Creado en este celular y aún no confirmado por el servidor. */
    val pending: Boolean = false
)

/** Por qué el usuario ve un equipo en Anuncios. */
enum class AnnouncementAccess { OWNER, MEMBER, GUARDIAN, ADMIN }

/**
 * Equipo visible en Anuncios. [childId] es el jugador vinculado por el que lo ve un tutor.
 * Los equipos ADMIN solo sirven para consultar o publicar: sin tema FCM ni contador.
 */
data class AnnouncementTeam(val teamId: String, val name: String, val access: AnnouncementAccess, val childId: String = "") {
    val notifies: Boolean get() = access != AnnouncementAccess.ADMIN
}

object Announcements {
    const val MAX_TITLE = 80
    const val MAX_BODY = 500
    const val TOPIC_PREFIX = "team_"
    /** Texto fijo de los push (docs/fcm-envio-manual.md): nunca datos del anuncio. */
    const val PUSH_TITLE = "SportPro"
    const val PUSH_TEXT = "Nuevo anuncio en tu equipo. Abre SportPro"

    /** Mismas comprobaciones que las reglas; mensaje de error en español o null. */
    fun validate(type: AnnouncementType?, title: String, body: String, teamId: String): String? = when {
        teamId.isBlank() -> "Elige un equipo."
        type == null -> "Elige el tipo de anuncio."
        title.trim().isEmpty() -> "Escribe un título."
        title.trim().length > MAX_TITLE -> "El título admite hasta $MAX_TITLE caracteres."
        body.trim().isEmpty() -> "Escribe el mensaje."
        body.trim().length > MAX_BODY -> "El mensaje admite hasta $MAX_BODY caracteres."
        else -> null
    }

    /**
     * No leído: creado después de la última vez que el usuario abrió Anuncios para ese equipo (o nunca lo
     * abrió) y no lo escribió él mismo. Sin hora conocida (pendiente) no cuenta.
     */
    fun isUnread(a: Announcement, viewerId: String, lastSeenMillis: Long?): Boolean =
        a.authorId != viewerId && a.createdAtMillis != null && (lastSeenMillis == null || a.createdAtMillis > lastSeenMillis)

    /** Total de no leídos de los equipos que notifican (el admin no tiene contador). */
    fun unreadCount(teams: List<AnnouncementTeam>, byTeam: Map<String, List<Announcement>>, lastSeen: Map<String, Long?>, viewerId: String): Int =
        teams.filter { it.notifies }.distinctBy { it.teamId }.sumOf { team ->
            byTeam[team.teamId].orEmpty().count { isUnread(it, viewerId, lastSeen[team.teamId]) }
        }

    /** Más recientes primero; los pendientes (sin hora) arriba. */
    fun sorted(list: List<Announcement>): List<Announcement> =
        list.sortedByDescending { it.createdAtMillis ?: Long.MAX_VALUE }

    /** Grupos por equipo en el orden de [teams]; [filter] = un teamId o null para todos. */
    fun grouped(teams: List<AnnouncementTeam>, byTeam: Map<String, List<Announcement>>, filter: String?): List<Pair<AnnouncementTeam, List<Announcement>>> =
        teams.distinctBy { it.teamId }.filter { filter == null || it.teamId == filter }.map { it to sorted(byTeam[it.teamId].orEmpty()) }

    fun topic(teamId: String): String = TOPIC_PREFIX + teamId

    /** Temas a dar de alta y de baja para que las suscripciones coincidan con [wanted]. */
    fun topicDiff(current: Set<String>, wanted: Set<String>): Pair<Set<String>, Set<String>> = (wanted - current) to (current - wanted)

    /** Temas que deben tener suscripción: equipos que notifican (no los de solo admin). */
    fun wantedTopics(teams: List<AnnouncementTeam>): Set<String> = teams.filter { it.notifies }.map { topic(it.teamId) }.toSet()

    /** Equipos donde puede publicar: los suyos como dueña aprobada; el admin, cualquiera de la lista. */
    fun canPublish(team: AnnouncementTeam, coachApproved: Boolean, isAdmin: Boolean): Boolean =
        isAdmin || (team.access == AnnouncementAccess.OWNER && coachApproved)

    /** Puede borrar: el autor o el admin (igual que las reglas). */
    fun canDelete(a: Announcement, viewerId: String, isAdmin: Boolean): Boolean = isAdmin || a.authorId == viewerId

    /**
     * Une los equipos de cada origen sin repetir: si un equipo llega por varias vías se queda la de más
     * acceso (dueña > miembro > tutor > admin).
     */
    fun merge(vararg sources: List<AnnouncementTeam>): List<AnnouncementTeam> =
        sources.toList().flatten().groupBy { it.teamId }.values
            .map { same -> same.minBy { it.access.ordinal } }
            .sortedWith(compareBy<AnnouncementTeam> { it.access.ordinal }.thenBy { it.name.lowercase() })
}
