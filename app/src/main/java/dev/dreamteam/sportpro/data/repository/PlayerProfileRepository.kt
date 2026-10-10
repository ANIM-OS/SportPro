package dev.dreamteam.sportpro.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import dev.dreamteam.sportpro.data.model.GuardianLink
import dev.dreamteam.sportpro.data.model.PlayerProfile
import java.io.ByteArrayOutputStream

/**
 * US-04: perfil del jugador (playerProfiles/{playerId}), su fotografía y los correos de
 * padres o tutores vinculados (playerGuardians), que habilitan la consulta de US-04, US-05 y US-08.
 */
class PlayerProfileRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val profiles = firestore.collection(PROFILES)
    private val guardians = firestore.collection(GUARDIANS)

    fun listenProfile(playerId: String, onChange: (PlayerProfile?) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        profiles.document(playerId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.userMessage("No se pudo cargar el perfil"))
                return@addSnapshotListener
            }
            onChange(snapshot?.takeIf { it.exists() }?.let(::fromDocument))
        }

    /** Guarda solo los campos del formulario; la foto se guarda aparte. */
    fun saveProfile(profile: PlayerProfile, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val data = hashMapOf<String, Any?>(
            "fullName" to profile.fullName,
            "position" to profile.position,
            "dominantFoot" to profile.dominantFoot,
            "heightCm" to profile.heightCm,
            "weightKg" to profile.weightKg,
            "phone" to profile.phone,
            "contactEmail" to profile.contactEmail,
            "emergencyName" to profile.emergencyName,
            "emergencyRelation" to profile.emergencyRelation,
            "emergencyPhone" to profile.emergencyPhone,
            "updatedAt" to FieldValue.serverTimestamp(),
            "updatedBy" to auth.currentUser?.uid
        )
        profiles.document(profile.playerId).set(data, SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.userMessage("No se pudo guardar el perfil")) }
    }

    /**
     * Sube la foto a Cloud Storage (playerProfiles/{playerId}/photo.jpg). Si Storage no está
     * disponible, guarda una miniatura comprimida en el perfil para que la foto no se pierda.
     */
    fun uploadPhoto(context: Context, playerId: String, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val bytes = try {
            compressPhoto(context, uri, maxSide = 512, quality = 85)
        } catch (_: Exception) {
            onError("No se pudo procesar la imagen. Elige otra foto.")
            return
        }
        val path = "$PROFILES/$playerId/photo.jpg"
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        storage.reference.child(path).putBytes(bytes, metadata)
            .addOnSuccessListener {
                updatePhotoFields(playerId, mapOf("photoPath" to path, "photoBase64" to FieldValue.delete()), onSuccess, onError)
            }
            .addOnFailureListener {
                val thumbnail = try {
                    Base64.encodeToString(compressPhoto(context, uri, maxSide = 256, quality = 75), Base64.NO_WRAP)
                } catch (_: Exception) {
                    onError("No se pudo procesar la imagen. Elige otra foto.")
                    return@addOnFailureListener
                }
                updatePhotoFields(playerId, mapOf("photoBase64" to thumbnail), onSuccess, onError)
            }
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

    fun listenGuardians(playerId: String, onChange: (List<GuardianLink>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        guardians.whereEqualTo("playerId", playerId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.userMessage("No se pudieron cargar los apoderados"))
                return@addSnapshotListener
            }
            onChange(snapshot?.documents?.mapNotNull(::guardianFromDocument)?.sortedBy { it.guardianEmail }.orEmpty())
        }

    fun addGuardian(playerId: String, playerName: String, email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val normalized = email.trim().lowercase()
        guardians.document(GuardianLink.documentId(playerId, normalized)).set(
            mapOf(
                "playerId" to playerId,
                "playerName" to playerName,
                "guardianEmail" to normalized,
                "createdBy" to auth.currentUser?.uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.userMessage("No se pudo vincular al padre o tutor")) }
    }

    fun removeGuardian(link: GuardianLink, onSuccess: () -> Unit, onError: (String) -> Unit) {
        guardians.document(link.id).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.userMessage("No se pudo quitar al padre o tutor")) }
    }

    /** Jugadores vinculados al correo del padre o tutor que inició sesión. */
    fun listenLinkedPlayers(onChange: (List<GuardianLink>) -> Unit, onError: (String) -> Unit): ListenerRegistration? {
        val email = auth.currentUser?.email?.trim()?.lowercase() ?: run {
            onError("Tu cuenta no tiene un correo asociado")
            return null
        }
        return guardians.whereEqualTo("guardianEmail", email).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.userMessage("No se pudieron cargar los jugadores a tu cargo"))
                return@addSnapshotListener
            }
            onChange(snapshot?.documents?.mapNotNull(::guardianFromDocument)?.sortedBy { it.playerName.lowercase() }.orEmpty())
        }
    }

    private fun updatePhotoFields(playerId: String, fields: Map<String, Any>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        profiles.document(playerId)
            .set(fields + mapOf("photoUpdatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.userMessage("No se pudo guardar la foto")) }
    }

    private fun compressPhoto(context: Context, uri: Uri, maxSide: Int, quality: Int): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IllegalArgumentException("Imagen no válida")
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

    private fun fromDocument(doc: DocumentSnapshot): PlayerProfile = PlayerProfile(
        playerId = doc.id,
        fullName = doc.getString("fullName").orEmpty(),
        position = doc.getString("position").orEmpty(),
        dominantFoot = doc.getString("dominantFoot").orEmpty(),
        heightCm = doc.getLong("heightCm")?.toInt(),
        weightKg = doc.getDouble("weightKg"),
        phone = doc.getString("phone").orEmpty(),
        contactEmail = doc.getString("contactEmail").orEmpty(),
        emergencyName = doc.getString("emergencyName").orEmpty(),
        emergencyRelation = doc.getString("emergencyRelation").orEmpty(),
        emergencyPhone = doc.getString("emergencyPhone").orEmpty(),
        photoPath = doc.getString("photoPath").orEmpty(),
        photoBase64 = doc.getString("photoBase64").orEmpty(),
        updatedAt = doc.getTimestamp("updatedAt")
    )

    private fun guardianFromDocument(doc: DocumentSnapshot): GuardianLink? {
        val playerId = doc.getString("playerId") ?: return null
        return GuardianLink(
            id = doc.id,
            playerId = playerId,
            playerName = doc.getString("playerName").orEmpty(),
            guardianEmail = doc.getString("guardianEmail").orEmpty()
        )
    }

    companion object {
        const val PROFILES = "playerProfiles"
        const val GUARDIANS = "playerGuardians"
        private const val MAX_PHOTO_BYTES = 2L * 1024 * 1024
    }
}
