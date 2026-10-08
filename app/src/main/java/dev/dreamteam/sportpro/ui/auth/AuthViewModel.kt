package dev.dreamteam.sportpro.ui.auth

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthState {
    data object Idle : AuthState
    data class Loading(val isGoogle: Boolean = false) : AuthState
    data class Success(val userId: String, val isNewUser: Boolean = false, val needsRoleSelection: Boolean = false) : AuthState
    data class Error(val message: String) : AuthState
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

    fun register(email: String, password: String) {
        if (_authState.value is AuthState.Loading) return
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Por favor completa todos los campos")
            return
        }
        _authState.value = AuthState.Loading()
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) _authState.value = AuthState.Error("No se pudo crear la cuenta")
                else {
                    _sessionRoles.value = emptySet()
                    watchCoachApproval(user.uid)
                    _authState.value = AuthState.Success(user.uid, isNewUser = true, needsRoleSelection = true)
                }
            }
            .addOnFailureListener { error -> fail(error, "No se pudo crear la cuenta") }
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
            .addOnSuccessListener { result -> resolveProfile(result.user?.uid, result.additionalUserInfo?.isNewUser == true) }
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
        val normalizedRoleCodes = parsedRoles.map { it.code }.toList()
        _authState.value = AuthState.Loading()
        firestore.collection("users").document(user.uid)
            .set(mapOf("email" to user.email, "roles"  to normalizedRoleCodes, "profileComplete" to true))
            .addOnSuccessListener {
                _sessionRoles.value = parsedRoles
                watchCoachApproval(user.uid)
                _authState.value = AuthState.Success(user.uid)
            }
            .addOnFailureListener { error -> fail(error, "No se pudieron guardar tus roles") }
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
        _authState.value = AuthState.Error(error.localizedMessage ?: fallback)
    }

    fun resetState() { _authState.value = AuthState.Idle }

    fun clearSession() {
        coachApprovalListener?.remove()
        coachApprovalListener = null
        _coachApproved.value = false
        _sessionRoles.value = emptySet()
        resetState()
    }
}
