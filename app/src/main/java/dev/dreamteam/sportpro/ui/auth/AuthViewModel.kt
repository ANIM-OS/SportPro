package dev.dreamteam.sportpro.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthState {
    data object Idle : AuthState
    data class Loading(val isGoogle: Boolean = false) : AuthState
    data class Success(
        val userId: String,
        val isNewUser: Boolean = false,
        val needsRoleSelection: Boolean = false,
        val message: String? = null
    ) : AuthState
    data class Error(val message: String, val isEmailError: Boolean = false) : AuthState
}

private const val MAX_NAME_LENGTH = 100
private const val NO_CONNECTION_MESSAGE = "Sin conexión. Revisa tu internet e inténtalo de nuevo"

private data class ProfileName(val firstName: String, val lastName: String) {
    fun toFields(): Map<String, Any> = buildMap {
        firstName.trim().take(MAX_NAME_LENGTH).takeIf { it.isNotEmpty() }?.let { put("firstName", it) }
        lastName.trim().take(MAX_NAME_LENGTH).takeIf { it.isNotEmpty() }?.let { put("lastName", it) }
    }

    companion object {
        fun fromDisplayName(displayName: String?): ProfileName? {
            val parts = displayName?.trim()?.split(Regex("\\s+"), limit = 2) ?: return null
            return ProfileName(parts[0], parts.getOrElse(1) { "" }).takeIf { it.firstName.isNotEmpty() }
        }
    }
}

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    private val _sessionRoles = MutableStateFlow<Set<AppRole>>(emptySet())
    val sessionRoles: StateFlow<Set<AppRole>> = _sessionRoles.asStateFlow()
    private val _coachApproved = MutableStateFlow(false)
    val coachApproved: StateFlow<Boolean> = _coachApproved.asStateFlow()
    private var coachApprovalListener: ListenerRegistration? = null
    private var pendingName: ProfileName? = null

    private fun watchCoachApproval(userId: String) {
        coachApprovalListener?.remove()
        _coachApproved.value = false
        coachApprovalListener = firestore.collection("coachApprovals").document(userId)
            .addSnapshotListener { snapshot, error ->
                _coachApproved.value = error == null && snapshot?.getBoolean("approved") == true
            }
    }

    fun checkSession() {
        if (_authState.value is AuthState.Loading) return
        val user = auth.currentUser ?: return
        _authState.value = AuthState.Loading()
        resolveProfile(user.uid, false)
    }

    fun login(email: String, password: String) {
        if (_authState.value is AuthState.Loading) return
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Por favor completa todos los campos")
            return
        }
        _authState.value = AuthState.Loading()
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result -> resolveProfile(result.user?.uid, false) }
            .addOnFailureListener { error -> fail(error, "No se pudo iniciar sesión") }
    }

    fun register(firstName: String, lastName: String, email: String, password: String, roles: Set<String>) {
        if (_authState.value is AuthState.Loading) return
        val parsedRoles = roles.mapNotNull(AppRole::fromCode).toSet()
        if (firstName.isBlank() || lastName.isBlank() || email.isBlank() || password.isBlank() || parsedRoles.isEmpty()) {
            _authState.value = AuthState.Error("Por favor completa todos los campos")
            return
        }
        _authState.value = AuthState.Loading()
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) _authState.value = AuthState.Error("No se pudo crear la cuenta")
                else {
                    pendingName = ProfileName(firstName, lastName)
                    val displayName = UserProfileChangeRequest.Builder()
                        .setDisplayName("${firstName.trim()} ${lastName.trim()}")
                        .build()
                    // Comunidad usa displayName como autor; si falla, el registro continúa.
                    user.updateProfile(displayName).addOnCompleteListener {
                        saveProfile(user, parsedRoles)
                            .addOnSuccessListener { finishProfile(user.uid, parsedRoles, isNewUser = true) }
                            .addOnFailureListener { error -> recoverRegistration(user.uid, parsedRoles, error) }
                    }
                }
            }
            .addOnFailureListener { error -> fail(error, "No se pudo crear la cuenta") }
    }

    // La cuenta ya existe en Auth: "Elige tu rol" permite reintentar el guardado del perfil.
    private fun recoverRegistration(userId: String, roles: Set<AppRole>, error: Exception) {
        Log.w("AuthViewModel", "Could not save the new user profile", error)
        _sessionRoles.value = roles
        watchCoachApproval(userId)
        _authState.value = AuthState.Success(
            userId = userId,
            isNewUser = true,
            needsRoleSelection = true,
            message = "Tu cuenta se creó, pero no se pudo guardar tu perfil. Confirma tus roles para terminar"
        )
    }

    fun beginGoogleSignIn(): Boolean {
        if (_authState.value is AuthState.Loading) return false
        _authState.value = AuthState.Loading(isGoogle = true)
        return true
    }

    fun cancelGoogleSignIn() {
        if ((_authState.value as? AuthState.Loading)?.isGoogle == true) {
            _authState.value = AuthState.Idle
        }
    }

    fun googleSignInFailed(message: String) {
        _authState.value = AuthState.Error(message)
    }

    fun signInWithGoogle(idToken: String) {
        if ((_authState.value as? AuthState.Loading)?.isGoogle != true) return
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val googleProfile = result.additionalUserInfo?.profile
                pendingName = (googleProfile?.get("given_name") as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ProfileName(it, googleProfile["family_name"] as? String ?: "") }
                resolveProfile(result.user?.uid, result.additionalUserInfo?.isNewUser == true)
            }
            .addOnFailureListener { error -> fail(error, "No se pudo iniciar sesión con Google") }
    }

    // Estos roles describen el perfil elegido por el usuario. La autoridad
    // sobre un equipo se comprueba con el propietario de ese equipo.
    fun saveRoles(roles: Set<String>) {
        val user = auth.currentUser ?: run {
            _authState.value = AuthState.Error("Tu sesión expiró. Inicia sesión nuevamente")
            return
        }
        val parsedRoles = roles.mapNotNull(AppRole::fromCode).toSet()
        if (parsedRoles.isEmpty()) {
            _authState.value = AuthState.Error("Selecciona al menos un rol válido")
            return
        }
        _authState.value = AuthState.Loading()
        saveProfile(user, parsedRoles)
            .addOnSuccessListener { finishProfile(user.uid, parsedRoles) }
            .addOnFailureListener { error -> fail(error, "No se pudieron guardar tus roles") }
    }

    // Nombre y fecha solo se escriben al crear el documento, para que editar
    // roles no los modifique. La transacción falla sin conexión en lugar de
    // quedar en cola como un set().
    private fun saveProfile(user: FirebaseUser, roles: Set<AppRole>): Task<Unit> {
        val userRef = firestore.collection("users").document(user.uid)
        val profile = mapOf(
            "email" to user.email,
            "roles" to roles.map { it.code },
            "profileComplete" to true
        )
        val name = pendingName ?: ProfileName.fromDisplayName(user.displayName)
        return firestore.runTransaction { transaction ->
            val creationFields = if (transaction.get(userRef).exists()) {
                emptyMap()
            } else {
                name?.toFields().orEmpty() + ("createdAt" to FieldValue.serverTimestamp())
            }
            transaction.set(userRef, profile + creationFields, SetOptions.merge())
            Unit
        }
    }

    private fun finishProfile(userId: String, roles: Set<AppRole>, isNewUser: Boolean = false) {
        pendingName = null
        _sessionRoles.value = roles
        watchCoachApproval(userId)
        _authState.value = AuthState.Success(userId, isNewUser = isNewUser)
    }

    fun sendPasswordReset(email: String) {
        if (_authState.value is AuthState.Loading) return
        if (email.isBlank()) {
            _authState.value = AuthState.Error("Ingresa tu correo para recuperar la contraseña")
            return
        }
        _authState.value = AuthState.Loading()
        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener { _authState.value = AuthState.Error("Te enviamos un enlace para restablecer la contraseña") }
            .addOnFailureListener { error -> fail(error, "No se pudo enviar el enlace") }
    }

    private fun resolveProfile(userId: String?, isNewUser: Boolean) {
        if (userId == null) {
            _authState.value = AuthState.Error("No se pudo validar la sesión")
            return
        }
        watchCoachApproval(userId)
        if (isNewUser) {
            _sessionRoles.value = emptySet()
            _authState.value = AuthState.Success(userId, isNewUser = true, needsRoleSelection = true)
            return
        }
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { profile ->
                if (!profile.exists()) {
                    _sessionRoles.value = emptySet()
                    _authState.value = AuthState.Success(userId, isNewUser = true, needsRoleSelection = true)
                    return@addOnSuccessListener
                }
                val roles = profile.get("roles") as? List<*>
                val profileComplete = profile.getBoolean("profileComplete") == true
                val parsedRoles = roles?.filterIsInstance<String>()?.mapNotNull(AppRole::fromCode)?.toSet() ?: emptySet()
                val hasRoles = parsedRoles.isNotEmpty()
                _sessionRoles.value = if (profileComplete && hasRoles) parsedRoles else emptySet()
                _authState.value = AuthState.Success(
                    userId = userId,
                    needsRoleSelection = !(profileComplete && hasRoles)
                )
            }
            .addOnFailureListener { error -> fail(error, "No se pudo cargar tu perfil") }
    }

    private fun fail(error: Exception, fallback: String) {
        _authState.value = AuthState.Error(authErrorMessage(error, fallback), isEmailError(error))
    }

    private fun isEmailError(error: Exception): Boolean =
        error is FirebaseAuthUserCollisionException ||
            (error is FirebaseAuthInvalidCredentialsException && error.errorCode == "ERROR_INVALID_EMAIL")

    private fun authErrorMessage(error: Exception, fallback: String): String = when {
        error is FirebaseNetworkException -> NO_CONNECTION_MESSAGE
        error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.UNAVAILABLE -> NO_CONNECTION_MESSAGE
        error is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera unos minutos e inténtalo de nuevo"
        error is FirebaseAuthWeakPasswordException -> "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres"
        error is FirebaseAuthUserCollisionException ->
            if (error.errorCode == "ERROR_EMAIL_ALREADY_IN_USE") "Este correo ya está registrado"
            else "Este correo ya está registrado con otro método de inicio de sesión"
        error is FirebaseAuthInvalidCredentialsException ->
            if (error.errorCode == "ERROR_INVALID_EMAIL") "Ingresa un correo electrónico válido"
            else "Correo o contraseña incorrectos"
        error is FirebaseAuthInvalidUserException ->
            if (error.errorCode == "ERROR_USER_DISABLED") "Esta cuenta está deshabilitada"
            else "Correo o contraseña incorrectos"
        else -> fallback
    }

    fun resetState() { _authState.value = AuthState.Idle }

    fun clearSession() {
        coachApprovalListener?.remove()
        coachApprovalListener = null
        _coachApproved.value = false
        _sessionRoles.value = emptySet()
        pendingName = null
        resetState()
    }
}
