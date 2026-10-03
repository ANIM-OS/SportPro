package dev.dreamteam.sportpro

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import dev.dreamteam.sportpro.ui.auth.*
import dev.dreamteam.sportpro.ui.theme.SportProTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import dev.dreamteam.sportpro.navigation.SportProNavGraph

enum class Screen {
    LOGIN,
    REGISTER,
    ROLE_SELECTION,
    HOME
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SportProTheme {
                val authViewModel: AuthViewModel = viewModel()
                val authState by authViewModel.authState.collectAsState()
                val currentUser = FirebaseAuth.getInstance().currentUser
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val credentialManager = remember(context) { CredentialManager.create(context) }

                var currentScreen by remember {
                    mutableStateOf(Screen.LOGIN)
                }
                LaunchedEffect(Unit) { authViewModel.checkSession() }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                var isSigningOut by remember { mutableStateOf(false) }
                val isLoading = authState is AuthState.Loading || isSigningOut
                val isGoogleLoading = (authState as? AuthState.Loading)?.isGoogle == true
                var registeredEmail by remember { mutableStateOf(currentUser?.email ?: "") }

                // Handle AuthState changes
                LaunchedEffect(authState) {
                    when (val state = authState) {
                        is AuthState.Loading -> {
                            errorMessage = null
                        }
                        is AuthState.Success -> {
                            errorMessage = null
                            registeredEmail = FirebaseAuth.getInstance().currentUser?.email ?: "usuario@sportpro.dev"
                            currentScreen = if (state.needsRoleSelection) Screen.ROLE_SELECTION else Screen.HOME
                            authViewModel.resetState()
                        }
                        is AuthState.Error -> {
                            errorMessage = state.message
                            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                            authViewModel.resetState()
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
                                currentScreen = Screen.LOGIN
                                isSigningOut = false
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0D0F12)
                ) {
                    when (currentScreen) {
                        Screen.LOGIN -> LoginScreen(
                            onLoginClick = { email, pass ->
                                authViewModel.login(email, pass)
                            },
                            onGoogleLoginClick = {
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
                            },
                            onForgotPasswordClick = { email -> authViewModel.sendPasswordReset(email) },
                            onNavigateToRegister = {
                                errorMessage = null
                                currentScreen = Screen.REGISTER
                            },
                            errorMessage = errorMessage,
                            isLoading = isLoading,
                            isGoogleLoading = isGoogleLoading
                        )
                        Screen.REGISTER -> RegisterScreen(
                            onRegisterClick = { email, pass ->
                                authViewModel.register(email, pass)
                            },
                            onNavigateToLogin = {
                                errorMessage = null
                                currentScreen = Screen.LOGIN
                            },
                            errorMessage = errorMessage,
                            isLoading = isLoading
                        )
                        Screen.ROLE_SELECTION -> RoleSelectionScreen(
                            userEmail = registeredEmail,
                            userName = registeredEmail.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() },
                            onEnterClick = { roles -> authViewModel.saveRoles(roles) },
                            isLoading = isLoading,
                            onCancelClick = signOut
                        )
                        Screen.HOME -> SportProNavGraph(
                            userEmail = FirebaseAuth.getInstance().currentUser?.email ?: registeredEmail,
                            onLogout = signOut,
                            isLoading = isSigningOut
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(userEmail: String, onLogout: () -> Unit, isLoading: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F12))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Sport", color = Color(0xFFFF6600), fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Pro", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "¡Bienvenido de nuevo!", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = userEmail, color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onLogout,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2E35)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Cerrar sesión", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
