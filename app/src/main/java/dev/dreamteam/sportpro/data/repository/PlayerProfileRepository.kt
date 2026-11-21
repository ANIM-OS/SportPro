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
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import dev.dreamteam.sportpro.data.model.GuardianLink
import dev.dreamteam.sportpro.data.model.PlayerProfile
import java.io.ByteArrayOutputStream

/**
 * Perfil deportivo del jugador, fotografía y consulta de sus vínculos activos de tutoría.
 */
class PlayerProfileRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val profiles = firestore.collection(PROFILES)

    fun listenProfile(playerId: String, onChange: (PlayerProfile?) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        profiles.document(playerId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.userMessage("No se pudo cargar el perfil"))
                return@addSnapshotListener
            }
            val profile = snapshot?.takeIf { it.exists() }?.let(::fromDocument)
            onChange(profile)
            if (profile != null && auth.currentUser?.uid == playerId && snapshot?.metadata?.isFromCache == false) {
                migrateLegacyContact(profile)
            }
        }

    private fun migrateLegacyContact(profile: PlayerProfile) {
        val contactRef = firestore.collection("playerContacts").document(profile.playerId)
        val profileRef = profiles.document(profile.playerId)
        firestore.document("deploymentCapabilities/us22").get(Source.SERVER).addOnSuccessListener {
            firestore.runTransaction { transaction ->
                if (!transaction.get(contactRef).exists()) {
                    val current = transaction.get(profileRef)
                    if (current.exists()) {
                        transaction.set(contactRef, mapOf(
                            "phone" to current.getString("phone").orEmpty(),
                            "emergencyName" to current.getString("emergencyName").orEmpty(),
                            "emergencyRelation" to current.getString("emergencyRelation").orEmpty(),
                            "emergencyPhone" to current.getString("emergencyPhone").orEmpty()
                        ))
                    }
                }
            }
        }
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
        val contact = mapOf(
            "phone" to profile.phone,
            "emergencyName" to profile.emergencyName,
            "emergencyRelation" to profile.emergencyRelation,
            "emergencyPhone" to profile.emergencyPhone
        )
        val profileRef = profiles.document(profile.playerId)
        firestore.document("deploymentCapabilities/us22").get(Source.SERVER).continueWithTask { capability ->
            if (capability.isSuccessful) {
                firestore.batch()
                    .set(profileRef, data, SetOptions.merge())
                    .set(firestore.collection("playerContacts").document(profile.playerId), contact)
                    .commit()
            } else if ((capability.exception as? com.google.firebase.firestore.FirebaseFirestoreException)?.code ==
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
            ) {
                profileRef.set(data, SetOptions.merge())
            } else {
                throw capability.exception ?: IllegalStateException("No se pudo verificar el servidor")
            }
        }
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
        } catch (_: Exception) {
            onError("No se pudo procesar la imagen. Elige otra foto.")
            return
        }
        // photoPath se borra para que no se muestre una foto anterior de Cloud Storage.
        updatePhotoFields(playerId, mapOf("photoBase64" to photo, "photoPath" to FieldValue.delete()), onSuccess, onError)
    }

    fun loadContact(
        playerId: String,
        teamId: String?,
        onContact: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        val contact = firestore.collection("playerContacts").document(playerId)
        val read = {
            contact.get(Source.SERVER).continueWith { task ->
                val doc = task.result
                listOfNotNull(
                    doc.getString("phone")?.takeIf { it.isNotBlank() }?.let { "Teléfono: $it" },
                    listOf("emergencyName", "emergencyRelation", "emergencyPhone")
                        .mapNotNull { field -> doc.getString(field)?.takeIf { it.isNotBlank() } }
                        .joinToString(" · ").takeIf { it.isNotBlank() }?.let { "Emergencia: $it" }
                )
            }
        }
        val uid = auth.currentUser?.uid
        val request = if (teamId != null && uid != null) {
            firestore.document("playerContactAccess/$uid/players/$playerId")
                .set(mapOf("teamId" to teamId)).onSuccessTask { read() }
        } else read()
        request.addOnSuccessListener(onContact).addOnFailureListener {
            onError("No se pudo consultar el contacto autorizado.")
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

    fun listenLinkedPlayers(onChange: (List<GuardianLink>) -> Unit, onError: (String) -> Unit): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: run {
            onError("Tu sesión expiró.")
            return null
        }
        return firestore.collection("guardianLinks")
            .whereEqualTo("guardianId", uid)
            .whereEqualTo("status", "ACTIVE")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.userMessage("No se pudieron cargar los jugadores a tu cargo"))
                    return@addSnapshotListener
                }
                onChange(snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val playerId = doc.getString("playerId") ?: return@mapNotNull null
                    GuardianLink(id = doc.id, playerId = playerId, playerName = "Jugador vinculado")
                })
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

    companion object {
        const val PROFILES = "playerProfiles"
        private const val MAX_PHOTO_BYTES = 2L * 1024 * 1024
        // Por debajo del límite de 800000 caracteres de las reglas de Firestore.
        private const val MAX_PHOTO_BASE64 = 700_000
    }
}
