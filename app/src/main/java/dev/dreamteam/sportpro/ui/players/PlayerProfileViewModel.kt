package dev.dreamteam.sportpro.ui.players

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.GuardianLink
import dev.dreamteam.sportpro.data.model.PlayerProfile
import dev.dreamteam.sportpro.data.repository.PlayerProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Valores del formulario del perfil, tal como los escribe el usuario. */
data class PlayerProfileForm(
    val fullName: String = "",
    val position: String = "",
    val dominantFoot: String = "",
    val heightCm: String = "",
    val weightKg: String = "",
    val phone: String = "",
    val contactEmail: String = "",
    val emergencyName: String = "",
    val emergencyRelation: String = "",
    val emergencyPhone: String = ""
)

data class PlayerProfileUiState(
    val profile: PlayerProfile? = null,
    val isLoading: Boolean = true,
    val photo: Bitmap? = null,
    val isSaving: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val guardians: List<GuardianLink> = emptyList(),
    val error: String? = null,
    val formError: String? = null,
    val message: String? = null
)

/** US-04: consulta y mantenimiento del perfil del jugador. */
class PlayerProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val repository = PlayerProfileRepository()
    private val _uiState = MutableStateFlow(PlayerProfileUiState())
    val uiState: StateFlow<PlayerProfileUiState> = _uiState.asStateFlow()

    private var profileListener: ListenerRegistration? = null
    private var guardiansListener: ListenerRegistration? = null
    private var playerId: String = ""
    private var loadedPhotoKey: String? = null

    /** [manageGuardians] solo es true cuando el jugador ve su propio perfil. */
    fun start(playerId: String, manageGuardians: Boolean) {
        if (this.playerId == playerId) return
        this.playerId = playerId
        profileListener = repository.listenProfile(
            playerId = playerId,
            onChange = { profile ->
                _uiState.update { it.copy(profile = profile, isLoading = false, error = null) }
                if (profile != null) loadPhoto(profile)
            },
            onError = { message -> _uiState.update { it.copy(isLoading = false, error = message) } }
        )
        if (manageGuardians) {
            guardiansListener = repository.listenGuardians(
                playerId = playerId,
                onChange = { links -> _uiState.update { it.copy(guardians = links) } },
                onError = { message -> _uiState.update { it.copy(message = message) } }
            )
        }
    }

    private fun loadPhoto(profile: PlayerProfile) {
        val key = "${profile.photoPath}|${profile.photoBase64.length}|${profile.updatedAt?.seconds}"
        if (key == loadedPhotoKey) return
        loadedPhotoKey = key
        repository.loadPhoto(profile) { bitmap -> _uiState.update { it.copy(photo = bitmap) } }
    }

    fun save(form: PlayerProfileForm) {
        val height = form.heightCm.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        val weight = form.weightKg.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        val phonePattern = Regex("^[+]?[0-9 ]{6,20}$")
        val error = when {
            form.fullName.trim().length > 80 -> "El nombre admite hasta 80 caracteres"
            form.heightCm.isNotBlank() && (height == null || height !in 50..250) -> "La estatura debe estar entre 50 y 250 cm"
            form.weightKg.isNotBlank() && (weight == null || weight !in 15.0..200.0) -> "El peso debe estar entre 15 y 200 kg"
            form.phone.isNotBlank() && !phonePattern.matches(form.phone.trim()) -> "Ingresa un teléfono válido"
            form.contactEmail.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(form.contactEmail.trim()).matches() ->
                "Ingresa un correo de contacto válido"
            form.emergencyPhone.isNotBlank() && !phonePattern.matches(form.emergencyPhone.trim()) ->
                "Ingresa un teléfono de emergencia válido"
            form.emergencyName.trim().length > 80 -> "El nombre del contacto de emergencia admite hasta 80 caracteres"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(formError = error) }
            return
        }
        _uiState.update { it.copy(isSaving = true, formError = null) }
        repository.saveProfile(
            profile = PlayerProfile(
                playerId = playerId,
                fullName = form.fullName.trim(),
                position = form.position,
                dominantFoot = form.dominantFoot,
                heightCm = height,
                weightKg = weight,
                phone = form.phone.trim(),
                contactEmail = form.contactEmail.trim(),
                emergencyName = form.emergencyName.trim(),
                emergencyRelation = form.emergencyRelation.trim(),
                emergencyPhone = form.emergencyPhone.trim()
            ),
            onSuccess = { _uiState.update { it.copy(isSaving = false, message = "Perfil guardado") } },
            onError = { message -> _uiState.update { it.copy(isSaving = false, formError = message) } }
        )
    }

    fun uploadPhoto(context: Context, uri: Uri) {
        _uiState.update { it.copy(isUploadingPhoto = true) }
        repository.uploadPhoto(
            context = context,
            playerId = playerId,
            uri = uri,
            onSuccess = { _uiState.update { it.copy(isUploadingPhoto = false, message = "Foto actualizada") } },
            onError = { message -> _uiState.update { it.copy(isUploadingPhoto = false, message = message) } }
        )
    }

    fun addGuardian(email: String, playerName: String, onAdded: () -> Unit) {
        val clean = email.trim().lowercase()
        val error = when {
            !Patterns.EMAIL_ADDRESS.matcher(clean).matches() -> "Ingresa un correo válido"
            clean == auth.currentUser?.email?.lowercase() ->
                "Ingresa el correo de tu padre o tutor, no el tuyo"
            _uiState.value.guardians.any { it.guardianEmail == clean } -> "Ese correo ya está vinculado"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(message = error) }
            return
        }
        repository.addGuardian(
            playerId = playerId,
            playerName = playerName,
            email = clean,
            onSuccess = {
                _uiState.update { it.copy(message = "Padre o tutor vinculado") }
                onAdded()
            },
            onError = { message -> _uiState.update { it.copy(message = message) } }
        )
    }

    fun removeGuardian(link: GuardianLink) {
        repository.removeGuardian(
            link = link,
            onSuccess = { _uiState.update { it.copy(message = "Vínculo eliminado") } },
            onError = { message -> _uiState.update { it.copy(message = message) } }
        )
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    override fun onCleared() {
        profileListener?.remove()
        guardiansListener?.remove()
    }
}
