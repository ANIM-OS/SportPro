package dev.dreamteam.sportpro.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import dev.dreamteam.sportpro.data.model.CommunityRules
import dev.dreamteam.sportpro.data.model.ModeratedContent
import dev.dreamteam.sportpro.data.model.ModerationReport
import dev.dreamteam.sportpro.data.model.ModerationRules

/**
 * US-21: reportes, ocultar/restaurar y "Marcar como revisado". Las escrituras no bloquean sin conexión:
 * [onSaved] se llama al guardarse en el celular ("Pendiente de sincronizar") y [onSynced] cuando el
 * servidor lo confirma; si las reglas lo rechazan, [onError].
 */
class ModerationRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun post(postId: String) = firestore.collection("communityPosts").document(postId)
    private fun target(postId: String, commentId: String?): DocumentReference =
        if (commentId == null) post(postId) else post(postId).collection("comments").document(commentId)

    /** Reporta una publicación ([commentId] null) o un comentario; un documento por usuario (ID = uid). */
    fun report(postId: String, commentId: String?, reason: String, onSaved: () -> Unit,
               onSynced: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { onError("Inicia sesión para reportar"); return }
        CommunityRules.validateReason(reason)?.let { onError(it); return }
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { profile ->
                val name = CommunityRules.authorName(profile.getString("firstName"), profile.getString("lastName"))
                val data = ModerationRules.reportFields(postId, commentId, uid, name, reason) +
                    ("createdAt" to FieldValue.serverTimestamp())
                target(postId, commentId).collection("reports").document(uid).set(data)
                    .addOnSuccessListener { onSynced() }
                    .addOnFailureListener { onError("No se pudo enviar el reporte (puede que ya lo hayas reportado).") }
                onSaved()
            }
            .addOnFailureListener { onError("Conéctate una vez para cargar tu perfil y poder reportar.") }
    }

    /** Ocultar ([hidden] = true) o restaurar, guardando quién y cuándo. Solo moderador o admin (reglas). */
    fun setHidden(postId: String, commentId: String?, hidden: Boolean, onSaved: () -> Unit,
                  onSynced: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { onError("Inicia sesión para moderar"); return }
        target(postId, commentId).update(mapOf(
            "moderated" to hidden,
            "moderatedBy" to uid,
            "moderatedAt" to FieldValue.serverTimestamp()
        ))
            .addOnSuccessListener { onSynced() }
            .addOnFailureListener {
                onError(if (hidden) "No se pudo ocultar: no tienes permisos de moderación o ya estaba oculto."
                else "No se pudo restaurar: no tienes permisos de moderación o ya estaba visible.")
            }
        onSaved()
    }

    /** "Marcar como revisado": PENDING → REVIEWED con reviewedBy y reviewedAt. */
    fun markReviewed(reportPath: String, onSaved: () -> Unit, onSynced: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { onError("Inicia sesión para moderar"); return }
        firestore.document(reportPath).update(mapOf(
            "status" to ModerationRules.REVIEWED,
            "reviewedBy" to uid,
            "reviewedAt" to FieldValue.serverTimestamp()
        ))
            .addOnSuccessListener { onSynced() }
            .addOnFailureListener { onError("No se pudo marcar como revisado.") }
        onSaved()
    }

    private fun toReport(doc: DocumentSnapshot) = ModerationReport(
        path = doc.reference.path,
        targetType = doc.getString("targetType") ?: ModerationRules.COMMENT,
        postId = doc.getString("postId").orEmpty(),
        commentId = doc.getString("commentId"),
        reporterId = doc.getString("reporterId").orEmpty(),
        reporterName = doc.getString("reporterName")?.takeIf { it.isNotBlank() } ?: CommunityRules.DEFAULT_AUTHOR,
        reason = doc.getString("reason").orEmpty(),
        status = doc.getString("status") ?: ModerationRules.PENDING,
        createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
        pending = doc.metadata.hasPendingWrites()
    )

    /** Reportes pendientes de publicaciones y comentarios (collectionGroup; solo moderador o admin). */
    fun observePendingReports(onUpdate: (List<ModerationReport>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        firestore.collectionGroup("reports").whereEqualTo("status", ModerationRules.PENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null) onError("No se pudieron cargar los reportes")
                else onUpdate(ModerationRules.pendingNewestFirst(snap?.documents.orEmpty().map(::toReport)))
            }

    private fun toContent(doc: DocumentSnapshot): ModeratedContent {
        val isComment = doc.reference.parent.id == "comments"
        return ModeratedContent(
            postId = if (isComment) doc.reference.parent.parent?.id.orEmpty() else doc.id,
            commentId = if (isComment) doc.id else null,
            authorName = doc.getString("authorName")?.takeIf { it.isNotBlank() } ?: CommunityRules.DEFAULT_AUTHOR,
            text = (if (isComment) doc.getString("text") else doc.getString("content")).orEmpty(),
            hidden = doc.getBoolean("moderated") == true,
            moderatedAt = doc.getTimestamp("moderatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            pending = doc.metadata.hasPendingWrites()
        )
    }

    /** Contenido de un reporte, en vivo (para mostrar su texto y si está oculto). null si no existe. */
    fun observeContent(postId: String, commentId: String?, onUpdate: (ModeratedContent?) -> Unit): ListenerRegistration =
        target(postId, commentId).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            onUpdate(if (error == null && snap != null && snap.exists()) toContent(snap) else null)
        }

    /** Publicaciones y comentarios ocultos (sección "Ocultos"), para poder restaurarlos. */
    fun observeHidden(onUpdate: (List<ModeratedContent>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        val byQuery = mutableMapOf<String, List<ModeratedContent>>()
        fun emit() = onUpdate(ModerationRules.hiddenNewestFirst(byQuery.values.flatten()))
        val posts = firestore.collection("communityPosts").whereEqualTo("moderated", true)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null) onError("No se pudieron cargar las publicaciones ocultas")
                else { byQuery["posts"] = snap?.documents.orEmpty().map(::toContent); emit() }
            }
        val comments = firestore.collectionGroup("comments").whereEqualTo("moderated", true)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null) onError("No se pudieron cargar los comentarios ocultos")
                else { byQuery["comments"] = snap?.documents.orEmpty().map(::toContent); emit() }
            }
        return ListenerRegistration { posts.remove(); comments.remove() }
    }
}
