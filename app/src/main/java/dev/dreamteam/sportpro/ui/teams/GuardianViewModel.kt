package dev.dreamteam.sportpro.ui.teams

import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class GuardianInvite(
    val id: String, val playerId: String, val playerName: String,
    val status: String, val guardianId: String = "", val guardianName: String = "",
    val expiresAt: Timestamp? = null
)
data class GuardianLink(val id: String, val playerId: String, val guardianId: String, val inviteId: String)
data class GuardianState(
    val uid: String = "", val invites: List<GuardianInvite> = emptyList(),
    val links: List<GuardianLink> = emptyList(), val scanned: GuardianInvite? = null,
    val busy: Boolean = false, val message: String? = null,
    val loadError: String? = null, val createdInviteId: String? = null
)

class GuardianViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val state = MutableStateFlow(GuardianState(uid = auth.currentUser?.uid.orEmpty()))
    val uiState = state.asStateFlow()
    private val listeners = mutableListOf<ListenerRegistration>()
    private val inviteGroups = mutableMapOf<String, List<GuardianInvite>>()
    private val linkGroups = mutableMapOf<String, List<GuardianLink>>()

    fun loadLinks(isPlayer: Boolean, isGuardian: Boolean) {
        listeners.forEach { it.remove() }
        listeners.clear()
        inviteGroups.clear()
        linkGroups.clear()
        state.update { it.copy(loadError = null, invites = emptyList(), links = emptyList()) }
        val uid = auth.currentUser?.uid
        if (uid != null) {
            val fields = listOfNotNull("playerId".takeIf { isPlayer }, "guardianId".takeIf { isGuardian })
            for (field in fields) {
                listeners += db.collection("guardianInvites").whereEqualTo(field, uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            inviteGroups.remove(field)
                            state.update { it.copy(invites = inviteGroups.values.flatten().distinctBy { invite -> invite.id },
                                loadError = errorMessage(error, "No se pudieron cargar tus vínculos.")) }
                            return@addSnapshotListener
                        }
                        inviteGroups[field] = snapshot?.documents.orEmpty().mapNotNull { doc ->
                            GuardianInvite(doc.id, doc.getString("playerId") ?: return@mapNotNull null,
                                doc.getString("playerName").orEmpty(), doc.getString("status").orEmpty(),
                                doc.getString("guardianId").orEmpty(), doc.getString("guardianName").orEmpty(),
                                doc.getTimestamp("expiresAt"))
                        }
                        state.update { it.copy(invites = inviteGroups.values.flatten().distinctBy { invite -> invite.id }) }
                    }
                listeners += db.collection("guardianLinks").whereEqualTo(field, uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            linkGroups.remove(field)
                            state.update { it.copy(links = linkGroups.values.flatten().distinctBy { link -> link.id },
                                loadError = errorMessage(error, "No se pudieron cargar tus vínculos.")) }
                            return@addSnapshotListener
                        }
                        linkGroups[field] = snapshot?.documents.orEmpty().mapNotNull { doc ->
                            if (doc.getString("status") != "ACTIVE") return@mapNotNull null
                            GuardianLink(doc.id, doc.getString("playerId") ?: return@mapNotNull null,
                                doc.getString("guardianId") ?: return@mapNotNull null,
                                doc.getString("inviteId") ?: return@mapNotNull null)
                        }
                        state.update { it.copy(links = linkGroups.values.flatten().distinctBy { link -> link.id }) }
                    }
            }
        }
    }

    private fun errorMessage(error: Exception, fallback: String): String = when ((error as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "$fallback El servidor no autorizó la solicitud."
        FirebaseFirestoreException.Code.UNAVAILABLE -> "No hay conexión. Inténtalo de nuevo."
        else -> fallback
    }

    private fun error(e: Exception) {
        state.update { it.copy(busy = false, message = errorMessage(e, "No se pudo completar la operación.")) }
    }

    fun consumeCreatedInvite() { state.update { it.copy(createdInviteId = null) } }

    fun reportScannerError() {
        state.update { it.copy(message = "No se pudo abrir el escáner. Puedes ingresar el código de invitación.") }
    }

    fun createInvite() {
        val user = auth.currentUser ?: return
        val name = user.displayName?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
            ?: user.email?.substringBefore('@')?.take(80) ?: "Jugador"
        state.update { it.copy(busy = true, message = null) }
        val invitation = db.collection("guardianInvites").document()
        invitation.set(mapOf(
            "playerId" to user.uid, "playerName" to name, "status" to "OPEN",
            "createdAt" to FieldValue.serverTimestamp(),
            "expiresAt" to Timestamp(Date(System.currentTimeMillis() + 55 * 60 * 1000L))
        )).addOnSuccessListener { state.update { it.copy(busy = false, createdInviteId = invitation.id) } }
            .addOnFailureListener(::error)
    }

    fun scan(raw: String) {
        val id = raw.trim().removePrefix("sportpro://guardian/")
        if (!id.matches(Regex("[A-Za-z0-9]{20}"))) {
            state.update { it.copy(message = "El QR no es una invitación de tutor.") }
            return
        }
        state.update { it.copy(busy = true, message = null) }
        db.collection("guardianInvites").document(id).get(Source.SERVER)
            .addOnSuccessListener { doc ->
                val playerId = doc.getString("playerId")
                val expiry = doc.getTimestamp("expiresAt")
                if (!doc.exists() || playerId == null || doc.getString("status") != "OPEN" ||
                    expiry == null || expiry.toDate().time <= System.currentTimeMillis() ||
                    playerId == auth.currentUser?.uid) {
                    state.update { it.copy(busy = false, message = "La invitación ya no está disponible.") }
                } else state.update { it.copy(busy = false, scanned = GuardianInvite(doc.id, playerId,
                    doc.getString("playerName").orEmpty(), "OPEN", expiresAt = expiry)) }
            }.addOnFailureListener(::error)
    }

    fun dismissScanned() { state.update { it.copy(scanned = null) } }

    fun accept(invite: GuardianInvite) {
        val user = auth.currentUser ?: return
        state.update { it.copy(busy = true, message = null) }
        user.getIdToken(false).addOnSuccessListener { token ->
            val label = (token.claims["name"] as? String)?.takeIf { it.isNotEmpty() && it.length <= 80 }
                ?: user.email
            if (label.isNullOrBlank()) {
                state.update { it.copy(busy = false, message = "La cuenta no tiene un nombre ni un correo disponibles.") }
                return@addOnSuccessListener
            }
            db.collection("guardianInvites").document(invite.id).update(mapOf(
                "status" to "CLAIMED", "guardianId" to user.uid, "guardianName" to label,
                "claimedAt" to FieldValue.serverTimestamp()
            )).addOnSuccessListener {
                state.update { it.copy(busy = false, scanned = null, message = "Aceptaste la invitación. Espera la confirmación del jugador.") }
            }.addOnFailureListener(::error)
        }.addOnFailureListener(::error)
    }

    fun confirm(invite: GuardianInvite) {
        if (invite.playerId != auth.currentUser?.uid || invite.guardianId.isBlank()) return
        state.update { it.copy(busy = true, message = null) }
        val batch = db.batch()
        batch.update(db.collection("guardianInvites").document(invite.id),
            mapOf("status" to "ACTIVE", "resolvedAt" to FieldValue.serverTimestamp()))
        batch.set(db.collection("guardianLinks").document("${invite.playerId}_${invite.guardianId}"), mapOf(
            "playerId" to invite.playerId, "guardianId" to invite.guardianId, "inviteId" to invite.id,
            "status" to "ACTIVE", "activatedAt" to FieldValue.serverTimestamp()
        ))
        batch.commit().addOnSuccessListener { state.update { it.copy(busy = false, message = "Vínculo confirmado.") } }
            .addOnFailureListener(::error)
    }

    fun cancel(invite: GuardianInvite) {
        state.update { it.copy(busy = true, message = null) }
        db.collection("guardianInvites").document(invite.id)
            .update(mapOf("status" to "CANCELLED", "resolvedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { state.update { it.copy(busy = false, message = "Invitación cancelada.") } }
            .addOnFailureListener(::error)
    }

    fun revoke(link: GuardianLink) {
        val uid = auth.currentUser?.uid ?: return
        state.update { it.copy(busy = true, message = null) }
        db.collection("guardianLinks").document(link.id)
            .update(mapOf("status" to "REVOKED", "revokedBy" to uid, "revokedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { state.update { it.copy(busy = false, message = "Vínculo finalizado.") } }
            .addOnFailureListener(::error)
    }

    override fun onCleared() {
        listeners.forEach { it.remove() }
        super.onCleared()
    }
}
