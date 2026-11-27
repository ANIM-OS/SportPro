package dev.dreamteam.sportpro.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import dev.dreamteam.sportpro.data.model.GuardianInvite
import dev.dreamteam.sportpro.data.model.GuardianLink
import dev.dreamteam.sportpro.data.model.PlayerProfile
import java.io.ByteArrayOutputStream
import java.util.Date

/**
 * Perfil del jugador y tutores confirmados mediante guardianInvites/guardianLinks.
 */
class PlayerProfileRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val profiles = firestore.collection(PROFILES)
    private val privates = firestore.collection(PRIVATE)
    private val coachLinks = firestore.collection(COACH_LINKS)
    private val guardians = firestore.collection(GUARDIANS)
    private val invites = firestore.collection(INVITES)

    /**
     * US-22: escucha el perfil deportivo (playerProfiles) y el sensible (playerPrivate) y los une.
     * Mientras un perfil antiguo no se migre, los datos sensibles se leen del documento deportivo.
     */
    fun listenProfile(playerId: String, onChange: (PlayerProfile?) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        var publicDoc: DocumentSnapshot? = null
        var privateDoc: DocumentSnapshot? = null
        var publicLoaded = false
        var privateLoaded = false
        fun emit() {
            if (publicLoaded && privateLoaded) onChange(merge(playerId, publicDoc, privateDoc))
        }
        val publicListener = profiles.document(playerId).addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener onError(error.userMessage("No se pudo cargar el perfil"))
            publicDoc = snapshot?.takeIf { it.exists() }
            publicLoaded = true
            emit()
        }
        val privateListener = privates.document(playerId).addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener onError(error.userMessage("No se pudieron cargar los datos privados del perfil"))
            privateDoc = snapshot?.takeIf { it.exists() }
            privateLoaded = true
            emit()
        }
        return ListenerRegistration {
            publicListener.remove()
            privateListener.remove()
        }
    }

    /**
     * La entrenadora registra por qué equipo accede al jugador (playerCoachLinks/{playerId}_{coachId}).
     * Las reglas vuelven a comprobar en cada lectura que el equipo es suyo y que el jugador es miembro.
     */
    fun ensureCoachLink(playerId: String, teamId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onError("Inicia sesión de nuevo")
        coachLinks.document("${playerId}_$uid")
            .set(mapOf("playerId" to playerId, "coachId" to uid, "teamId" to teamId))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.userMessage("No tienes acceso al perfil de este jugador")) }
    }

    /**
     * Guarda el perfil. Con [includePrivate] (el propio jugador) escribe también playerPrivate y,
     * en el mismo batch, borra los campos sensibles que aún queden en playerProfiles (migración US-22).
     * La entrenadora solo guarda los datos deportivos.
     */
    fun saveProfile(profile: PlayerProfile, includePrivate: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val sport = hashMapOf<String, Any?>(
            "fullName" to profile.fullName,
            "position" to profile.position,
            "dominantFoot" to profile.dominantFoot,
            "updatedAt" to FieldValue.serverTimestamp(),
            "updatedBy" to auth.currentUser?.uid
        )
        val batch = firestore.batch()
        if (includePrivate) {
            SENSITIVE_FIELDS.forEach { sport[it] = FieldValue.delete() }
            batch.set(privates.document(profile.playerId), mapOf(
                "heightCm" to profile.heightCm,
                "weightKg" to profile.weightKg,
                "phone" to profile.phone,
                "contactEmail" to profile.contactEmail,
                "emergencyName" to profile.emergencyName,
                "emergencyRelation" to profile.emergencyRelation,
                "emergencyPhone" to profile.emergencyPhone,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }
        batch.set(profiles.document(profile.playerId), sport, SetOptions.merge())
        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.userMessage("No se pudo guardar el perfil")) }
    }

    /**
     * Guarda la foto reducida en el propio perfil, igual que las fotos de equipo: el proyecto
     * usa el plan Spark sin Cloud Storage, y subir a un bucket inexistente o a un emulador
     * apagado deja la carga reintentando varios minutos.
     */
    fun uploadPhoto(context: Context, playerId: String, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val photo = try {
            Base64.encodeToString(compressPhoto(context, uri, maxSide = 512, quality = 80), Base64.NO_WRAP)
                .takeIf { it.length <= MAX_PHOTO_BASE64 }
                ?: Base64.encodeToString(compressPhoto(context, uri, maxSide = 256, quality = 70), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo procesar la foto del perfil", e)
            onError("No se pudo procesar la imagen. Elige otra foto.")
            return
        }
        // Las reglas rechazan más de 800000 caracteres; mejor avisar que enviarla.
        if (photo.length > MAX_PHOTO_BASE64) {
            Log.w(TAG, "Foto del perfil demasiado grande tras comprimir: ${photo.length} caracteres")
            onError("La imagen es demasiado grande. Elige otra foto.")
            return
        }
        // photoPath se borra para que no se muestre una foto anterior de Cloud Storage.
        updatePhotoFields(playerId, mapOf("photoBase64" to photo, "photoPath" to FieldValue.delete()), onSuccess, onError)
    }

    /** Carga la foto del perfil (miniatura guardada o archivo en Cloud Storage). */
    fun loadPhoto(profile: PlayerProfile, onLoaded: (Bitmap?) -> Unit) {
        when {
            profile.photoBase64.isNotBlank() -> onLoaded(
                runCatching {
                    val bytes = Base64.decode(profile.photoBase64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            )
            profile.photoPath.isNotBlank() -> storage.reference.child(profile.photoPath).getBytes(MAX_PHOTO_BYTES)
                .addOnSuccessListener { bytes -> onLoaded(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) }
                .addOnFailureListener { onLoaded(null) }
            else -> onLoaded(null)
        }
    }

    fun listenGuardians(playerId: String, onChange: (List<GuardianLink>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        var generation = 0
        return guardians.whereEqualTo("playerId", playerId).addSnapshotListener { snapshot, error ->
            val current = ++generation
            if (error != null) onError(error.userMessage("No se pudieron cargar los tutores"))
            else {
                val active = snapshot?.documents.orEmpty().mapNotNull(::guardianFromDocument).filter { it.status == "ACTIVE" }
                if (active.isEmpty()) onChange(emptyList())
                else Tasks.whenAllSuccess<DocumentSnapshot>(active.map { invites.document(it.inviteId).get() })
                    .addOnSuccessListener { docs ->
                        if (current == generation) onChange(active.mapIndexed { index, link ->
                            link.copy(guardianName = docs[index].getString("guardianName").orEmpty())
                        }.sortedBy { it.guardianName.lowercase() })
                    }.addOnFailureListener { if (current == generation) onError(it.userMessage("No se pudieron cargar los nombres de los tutores")) }
            }
        }
    }

    fun listenInvites(playerId: String, onChange: (List<GuardianInvite>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        invites.whereEqualTo("playerId", playerId).addSnapshotListener { snapshot, error ->
            if (error != null) onError(error.userMessage("No se pudieron cargar las invitaciones"))
            else onChange(snapshot?.documents.orEmpty().mapNotNull(::inviteFromDocument)
                .filter { it.status == "OPEN" || it.status == "CLAIMED" })
        }

    fun listenClaimedInvites(onChange: (List<GuardianInvite>) -> Unit, onError: (String) -> Unit): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: run { onError("Inicia sesión de nuevo"); return null }
        return invites.whereEqualTo("guardianId", uid).addSnapshotListener { snapshot, error ->
            if (error != null) onError(error.userMessage("No se pudieron cargar tus solicitudes pendientes"))
            else onChange(snapshot?.documents.orEmpty().mapNotNull(::inviteFromDocument).filter { it.status == "CLAIMED" })
        }
    }

    fun listenLegacyGuardians(playerId: String, onChange: (List<String>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        firestore.collection(LEGACY_GUARDIANS).whereEqualTo("playerId", playerId).addSnapshotListener { snapshot, error ->
            if (error != null) onError(error.userMessage("No se pudieron comprobar los vínculos anteriores"))
            else onChange(snapshot?.documents.orEmpty().mapNotNull { it.getString("guardianEmail") })
        }

    fun createInvite(playerId: String, playerName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser ?: return onError("Inicia sesión de nuevo")
        user.reload().addOnSuccessListener {
            user.getIdToken(true).addOnSuccessListener {
                if (!user.isEmailVerified) onError("Verifica tu correo antes de invitar a un tutor")
                else {
                    val expiry = Timestamp(Date(System.currentTimeMillis() + 30 * 60 * 1000L))
                    invites.document().set(mapOf("playerId" to playerId, "playerName" to playerName.trim(),
                        "status" to "OPEN", "createdAt" to FieldValue.serverTimestamp(), "expiresAt" to expiry))
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onError(it.userMessage("No se pudo crear la invitación")) }
                }
            }.addOnFailureListener { onError(it.userMessage("No se pudo actualizar la sesión")) }
        }.addOnFailureListener { onError(it.userMessage("No se pudo comprobar el correo")) }
    }

    fun claimInvite(code: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = auth.currentUser ?: return onError("Inicia sesión para aceptar la invitación")
        if (code.isBlank() || code.length > 100 || code.contains('/')) return onError("Código de invitación inválido")
        user.getIdToken(true).addOnSuccessListener { token ->
            val tokenName = token.claims["name"] as? String
            val label = tokenName?.takeIf { it.isNotBlank() && it.length <= 80 } ?: user.email.orEmpty()
            val ref = invites.document(code.trim())
            ref.get(Source.SERVER).addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener onError("Invitación inexistente o no disponible")
                ref.update(mapOf("status" to "CLAIMED", "guardianId" to user.uid,
                    "guardianName" to label, "claimedAt" to FieldValue.serverTimestamp()))
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it.userMessage("No se pudo aceptar la invitación")) }
            }.addOnFailureListener { onError(it.userMessage("No se pudo consultar la invitación")) }
        }.addOnFailureListener { onError(it.userMessage("No se pudo actualizar la sesión")) }
    }

    fun confirmInvite(invite: GuardianInvite, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val link = guardians.document("${invite.playerId}_${invite.guardianId}")
        val batch = firestore.batch()
        batch.update(invites.document(invite.id), mapOf("status" to "ACTIVE", "resolvedAt" to FieldValue.serverTimestamp()))
        batch.set(link, mapOf("playerId" to invite.playerId, "guardianId" to invite.guardianId,
            "inviteId" to invite.id, "status" to "ACTIVE", "activatedAt" to FieldValue.serverTimestamp()))
        batch.commit().addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.userMessage("No se pudo confirmar al tutor")) }
    }

    fun cancelInvite(invite: GuardianInvite, onSuccess: () -> Unit, onError: (String) -> Unit) {
        invites.document(invite.id).update(mapOf("status" to "CANCELLED", "resolvedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.userMessage("No se pudo cancelar la invitación")) }
    }

    fun removeGuardian(link: GuardianLink, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onError("Inicia sesión de nuevo")
        guardians.document(link.id).update(mapOf("status" to "REVOKED", "revokedAt" to FieldValue.serverTimestamp(), "revokedBy" to uid))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.userMessage("No se pudo revocar al tutor")) }
    }

    /** Jugadores con vínculo activo para el tutor autenticado. */
    fun listenLinkedPlayers(onChange: (List<GuardianLink>) -> Unit, onError: (String) -> Unit): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: run { onError("Inicia sesión de nuevo"); return null }
        var generation = 0
        return guardians.whereEqualTo("guardianId", uid).addSnapshotListener { snapshot, error ->
            val current = ++generation
            if (error != null) return@addSnapshotListener onError(error.userMessage("No se pudieron cargar los jugadores a tu cargo"))
            val active = snapshot?.documents.orEmpty().mapNotNull(::guardianFromDocument).filter { it.status == "ACTIVE" }
            if (active.isEmpty()) return@addSnapshotListener onChange(emptyList())
            val tasks = active.map { invites.document(it.inviteId).get() }
            Tasks.whenAllSuccess<DocumentSnapshot>(tasks).addOnSuccessListener { docs ->
                if (current == generation) onChange(active.mapIndexed { index, link ->
                    link.copy(playerName = docs[index].getString("playerName").orEmpty())
                }.sortedBy { it.playerName.lowercase() })
            }.addOnFailureListener { if (current == generation) onError(it.userMessage("No se pudieron cargar los nombres de los jugadores")) }
        }
    }

    private fun updatePhotoFields(playerId: String, fields: Map<String, Any>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        // Las reglas exigen updatedBy == quien escribe.
        profiles.document(playerId)
            .set(fields + mapOf("photoUpdatedAt" to FieldValue.serverTimestamp(), "updatedBy" to auth.currentUser?.uid), SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.userMessage("No se pudo guardar la foto")) }
    }

    private fun compressPhoto(context: Context, uri: Uri, maxSide: Int, quality: Int): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Con inJustDecodeBounds, decodeStream siempre devuelve null: se comprueba el stream,
        // no el resultado, y el tamaño se valida con outWidth/outHeight.
        val boundsStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Imagen no válida")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalArgumentException("Imagen no válida")

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSide) sampleSize *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: throw IllegalArgumentException("Imagen no válida")
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        return try {
            ByteArrayOutputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
                output.toByteArray()
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
    }

    /** Une el documento deportivo y el privado; sin privado, usa los campos sensibles antiguos. */
    private fun merge(playerId: String, sport: DocumentSnapshot?, sensitiveDoc: DocumentSnapshot?): PlayerProfile? {
        if (sport == null && sensitiveDoc == null) return null
        val sensitive = sensitiveDoc ?: sport
        return PlayerProfile(
            playerId = playerId,
            fullName = sport?.getString("fullName").orEmpty(),
            position = sport?.getString("position").orEmpty(),
            dominantFoot = sport?.getString("dominantFoot").orEmpty(),
            heightCm = sensitive?.getLong("heightCm")?.toInt(),
            weightKg = sensitive?.getDouble("weightKg"),
            phone = sensitive?.getString("phone").orEmpty(),
            contactEmail = sensitive?.getString("contactEmail").orEmpty(),
            emergencyName = sensitive?.getString("emergencyName").orEmpty(),
            emergencyRelation = sensitive?.getString("emergencyRelation").orEmpty(),
            emergencyPhone = sensitive?.getString("emergencyPhone").orEmpty(),
            photoPath = sport?.getString("photoPath").orEmpty(),
            photoBase64 = sport?.getString("photoBase64").orEmpty(),
            updatedAt = sport?.getTimestamp("updatedAt")
        )
    }

    private fun guardianFromDocument(doc: DocumentSnapshot): GuardianLink? = GuardianLink(
        id = doc.id, playerId = doc.getString("playerId") ?: return null,
        guardianId = doc.getString("guardianId") ?: return null,
        status = doc.getString("status").orEmpty(), inviteId = doc.getString("inviteId").orEmpty())

    private fun inviteFromDocument(doc: DocumentSnapshot): GuardianInvite? = GuardianInvite(
        id = doc.id, playerId = doc.getString("playerId") ?: return null,
        playerName = doc.getString("playerName").orEmpty(), status = doc.getString("status").orEmpty(),
        guardianId = doc.getString("guardianId").orEmpty(), guardianName = doc.getString("guardianName").orEmpty(),
        expiresAt = doc.getTimestamp("expiresAt"))

    companion object {
        private const val TAG = "PlayerProfile"
        const val PROFILES = "playerProfiles"
        const val PRIVATE = "playerPrivate"
        const val COACH_LINKS = "playerCoachLinks"
        // US-22: viven en playerPrivate; en perfiles antiguos aún pueden estar en playerProfiles.
        val SENSITIVE_FIELDS = listOf(
            "heightCm", "weightKg", "phone", "contactEmail", "emergencyName", "emergencyRelation", "emergencyPhone"
        )
        const val GUARDIANS = "guardianLinks"
        const val INVITES = "guardianInvites"
        const val LEGACY_GUARDIANS = "playerGuardians"
        private const val MAX_PHOTO_BYTES = 2L * 1024 * 1024
        // Por debajo del límite de 800000 caracteres de las reglas de Firestore.
        private const val MAX_PHOTO_BASE64 = 700_000
    }
}
