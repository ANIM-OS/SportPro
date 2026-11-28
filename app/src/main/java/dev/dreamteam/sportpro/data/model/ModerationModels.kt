package dev.dreamteam.sportpro.data.model

import com.google.firebase.Timestamp

/**
 * US-21: reporte de una publicación (communityPosts/{post}/reports/{uid}) o de un comentario
 * (communityPosts/{post}/comments/{comentario}/reports/{uid}). [path] es la ruta completa del documento.
 */
data class ModerationReport(
    val path: String,
    val targetType: String,
    val postId: String,
    val commentId: String?,
    val reporterId: String,
    val reporterName: String,
    val reason: String,
    val status: String,
    val createdAt: Timestamp?,
    /** Escrito en este celular y aún no confirmado por el servidor. */
    val pending: Boolean = false
)

/** US-21: publicación o comentario visto desde la pantalla de Moderación. */
data class ModeratedContent(
    val postId: String,
    val commentId: String?,
    val authorName: String,
    val text: String,
    val hidden: Boolean,
    val moderatedAt: Timestamp?,
    val pending: Boolean = false
) {
    val targetType: String get() = if (commentId == null) ModerationRules.POST else ModerationRules.COMMENT
    val key: String get() = ModerationRules.contentKey(postId, commentId)
}

/** US-21: reglas puras de moderación (mismo criterio que firestore.rules). */
object ModerationRules {
    const val POST = "POST"
    const val COMMENT = "COMMENT"
    const val PENDING = "PENDING"
    const val REVIEWED = "REVIEWED"

    fun contentKey(postId: String, commentId: String?): String =
        if (commentId == null) "post:$postId" else "comment:$postId/$commentId"

    fun typeLabel(targetType: String): String = if (targetType == COMMENT) "Comentario" else "Publicación"

    /** Pendientes, los más nuevos primero (los aún sin hora del servidor arriba). */
    fun pendingNewestFirst(reports: List<ModerationReport>): List<ModerationReport> =
        reports.filter { it.status == PENDING }.distinctBy { it.path }
            .sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }

    /** Ocultos, los ocultados más recientemente primero. */
    fun hiddenNewestFirst(items: List<ModeratedContent>): List<ModeratedContent> =
        items.filter { it.hidden }.distinctBy { it.key }
            .sortedByDescending { it.moderatedAt?.toDate()?.time ?: Long.MAX_VALUE }

    /**
     * Campos del reporte (sin createdAt, que pone el servidor). Las reglas exigen exactamente estos:
     * commentId solo en los de comentario y reporterName igual al nombre del perfil.
     */
    fun reportFields(postId: String, commentId: String?, reporterId: String, reporterName: String,
                     reason: String): Map<String, Any> = buildMap {
        put("targetType", if (commentId == null) POST else COMMENT)
        put("postId", postId)
        if (commentId != null) put("commentId", commentId)
        put("reporterId", reporterId)
        put("reporterName", reporterName)
        put("reason", reason.trim())
        put("status", PENDING)
    }

    /** REPORTAR una publicación: rol habilitado, ajena, activa, no ocultada y ya sincronizada. */
    fun canReportPost(post: CommunityPost, viewerId: String, canEngage: Boolean): Boolean =
        canEngage && viewerId.isNotBlank() && post.authorId != viewerId && post.active && !post.moderated && !post.pending

    /** Texto del botón según el estado actual del contenido. */
    fun actionLabel(hidden: Boolean): String = if (hidden) "Restaurar" else "Ocultar"
}
