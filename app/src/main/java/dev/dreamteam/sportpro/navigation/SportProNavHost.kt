package dev.dreamteam.sportpro.navigation

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import dev.dreamteam.sportpro.R
import dev.dreamteam.sportpro.ui.auth.AuthState
import dev.dreamteam.sportpro.ui.auth.AuthViewModel
import dev.dreamteam.sportpro.ui.auth.LoginScreen
import dev.dreamteam.sportpro.ui.auth.RegisterScreen
import dev.dreamteam.sportpro.ui.auth.RoleSelectionScreen
import dev.dreamteam.sportpro.ui.principal.PrincipalScreen
import dev.dreamteam.sportpro.ui.theme.FondoSportPro
import dev.dreamteam.sportpro.ui.theme.NaranjaSportPro
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SportProNavHost() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSigningOut by remember { mutableStateOf(false) }
    val isLoading = authState is AuthState.Loading || isSigningOut
    val isGoogleLoading = (authState as? AuthState.Loading)?.isGoogle == true

    // Con sesión abierta se muestra una carga mientras se valida el perfil, en vez del login.
    val haySesion = rememberSaveable { FirebaseAuth.getInstance().currentUser != null }
    // Solo se valida la sesión una vez; al rotar la pantalla la navegación se restaura sola.
    var sesionVerificada by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!sesionVerificada) {
            sesionVerificada = true
            authViewModel.checkSession()
        }
    }

    // Al cambiar de pantalla se limpia el error de la pantalla anterior.
    val entradaActual by navController.currentBackStackEntryAsState()
    LaunchedEffect(entradaActual?.destination?.route) { errorMessage = null }

    // Handle AuthState changes
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Loading -> {
                errorMessage = null
            }
            is AuthState.Success -> {
                errorMessage = null
                navController.irLimpiando(if (state.needsRoleSelection) Rutas.SELECCION_ROL else Rutas.PRINCIPAL)
                authViewModel.resetState()
            }
            is AuthState.Error -> {
                errorMessage = state.message
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                authViewModel.resetState()
                // Si falló la validación de la sesión guardada, se vuelve al login.
                if (navController.currentDestination?.route == Rutas.CARGANDO) {
                    navController.irLimpiando(Rutas.LOGIN)
                }
            }
            is AuthState.Idle -> Unit
        }
    }

    val signOut: () -> Unit = {
        if (!isSigningOut && authViewModel.authState.value !is AuthState.Loading) {
            isSigningOut = true
            scope.launch {
                try {
                    FirebaseAuth.getInstance().signOut()
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: ClearCredentialException) {
                    Log.w("GoogleSignIn", "Could not clear credential state", e)
                    Toast.makeText(context, "Sesión cerrada. No se pudo limpiar el selector de cuentas de Google", Toast.LENGTH_LONG).show()
                } finally {
                    authViewModel.resetState()
                    errorMessage = null
                    navController.irLimpiando(Rutas.LOGIN)
                    isSigningOut = false
                }
            }
        }
    }

    val onGoogleLoginClick: () -> Unit = {
        if (!isSigningOut && authViewModel.beginGoogleSignIn()) {
            scope.launch {
                try {
                    val googleIdOption = GetSignInWithGoogleOption.Builder(
                        context.getString(R.string.default_web_client_id)
                    ).build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    val result = credentialManager.getCredential(context, request)
                    val credential = result.credential

                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val googleIdToken = googleIdTokenCredential.idToken
                        authViewModel.signInWithGoogle(googleIdToken)
                    } else {
                        authViewModel.googleSignInFailed("Credencial de Google no válida")
                    }
                } catch (e: GetCredentialCancellationException) {
                    authViewModel.cancelGoogleSignIn()
                } catch (e: CancellationException) {
                    authViewModel.cancelGoogleSignIn()
                    throw e
                } catch (e: GoogleIdTokenParsingException) {
                    authViewModel.googleSignInFailed("No se pudo validar la credencial de Google")
                } catch (e: GetCredentialException) {
                    authViewModel.googleSignInFailed("No se pudo iniciar sesión con Google. Inténtalo nuevamente")
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = FondoSportPro
    ) {
        NavHost(
            navController = navController,
            startDestination = if (haySesion) Rutas.CARGANDO else Rutas.LOGIN
        ) {
            composable(Rutas.CARGANDO) {
                PantallaCargando()
            }
            composable(Rutas.LOGIN) {
                LoginScreen(
                    onLoginClick = { email, pass -> authViewModel.login(email, pass) },
                    onGoogleLoginClick = onGoogleLoginClick,
                    onForgotPasswordClick = { email -> authViewModel.sendPasswordReset(email) },
                    onNavigateToRegister = {
                        navController.navigate(Rutas.REGISTRO) { launchSingleTop = true }
                    },
                    errorMessage = errorMessage,
                    isLoading = isLoading,
                    isGoogleLoading = isGoogleLoading
                )
            }
            composable(Rutas.REGISTRO) {
                RegisterScreen(
                    onRegisterClick = { email, pass -> authViewModel.register(email, pass) },
                    onNavigateToLogin = {
                        if (!navController.popBackStack()) navController.irLimpiando(Rutas.LOGIN)
                    },
                    errorMessage = errorMessage,
                    isLoading = isLoading
                )
            }
            composable(Rutas.SELECCION_ROL) {
                val email = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
                RoleSelectionScreen(
                    userEmail = email,
                    userName = email.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() },
                    onEnterClick = { roles -> authViewModel.saveRoles(roles) },
                    isLoading = isLoading,
                    onCancelClick = signOut
                )
            }
            composable(Rutas.PRINCIPAL) {
                PrincipalScreen(
                    userEmail = FirebaseAuth.getInstance().currentUser?.email.orEmpty(),
                    onLogout = signOut,
                    isSigningOut = isSigningOut
                )
            }
        }
    }
}

@Composable
private fun PantallaCargando() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = NaranjaSportPro)
    }
}

/** Navega a [ruta] vaciando la pila, para que "atrás" no regrese al login ni a la pantalla anterior. */
private fun NavHostController.irLimpiando(ruta: String) {
    navigate(ruta) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
