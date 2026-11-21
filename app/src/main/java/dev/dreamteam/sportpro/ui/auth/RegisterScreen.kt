package dev.dreamteam.sportpro.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composable screen for new user registration.
 * Collects name, email, password (with confirmation) and one or more roles, shows validation
 * errors under each field, offers Google sign-up, toggles light/dark theme and navigates back to login.
 */
@Composable
fun RegisterScreen(
    onRegisterClick: (String, String, String, String, Set<String>) -> Unit,
    onGoogleRegisterClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onClearError: () -> Unit = {},
    errorMessage: String? = null,
    isEmailError: Boolean = false,
    isLoading: Boolean = false,
    isGoogleLoading: Boolean = false,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedRoles by remember { mutableStateOf(emptySet<String>()) }
    var passwordVisible by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    val errors = if (submitted) {
        validateRegistration(firstName, lastName, email, password, confirmPassword, selectedRoles)
    } else {
        RegistrationErrors()
    }
    val passwordTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            // Header with Logo & Theme Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sport",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Pro",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    onClick = onToggleTheme,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDarkTheme) "🌙 MODO OSCURO" else "☀️ MODO CLARO",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "CREAR CUENTA",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Regístrate para comenzar",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            RegisterField(
                label = "NOMBRE",
                value = firstName,
                onValueChange = { firstName = it },
                error = errors.firstName,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )

            Spacer(modifier = Modifier.height(16.dp))

            RegisterField(
                label = "APELLIDO",
                value = lastName,
                onValueChange = { lastName = it },
                error = errors.lastName,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )

            Spacer(modifier = Modifier.height(16.dp))

            RegisterField(
                label = "CORREO ELECTRÓNICO",
                value = email,
                onValueChange = {
                    email = it
                    onClearError()
                },
                error = errors.email ?: errorMessage.takeIf { isEmailError },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(16.dp))

            RegisterField(
                label = "CONTRASEÑA",
                value = password,
                onValueChange = { password = it },
                error = errors.password,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = passwordTransformation
            )

            Spacer(modifier = Modifier.height(16.dp))

            RegisterField(
                label = "CONFIRMAR CONTRASEÑA",
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                error = errors.confirmPassword,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = passwordTransformation,
                trailingIcon = {
                    Text(
                        text = if (passwordVisible) "OCULTAR" else "MOSTRAR",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clickable { passwordVisible = !passwordVisible }
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Roles
            Text(
                text = "¿CUÁL ES TU ROL EN LA PLATAFORMA?",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            RoleOptions(
                selectedRoles = selectedRoles,
                onToggleRole = { role ->
                    selectedRoles = if (role in selectedRoles) selectedRoles - role else selectedRoles + role
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Puedes seleccionar más de uno.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            FieldError(errors.roles)

            if (errorMessage != null && !isEmailError) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Register Button
            Button(
                onClick = {
                    submitted = true
                    val currentErrors = validateRegistration(firstName, lastName, email, password, confirmPassword, selectedRoles)
                    if (!currentErrors.hasErrors) {
                        onRegisterClick(firstName, lastName, email, password, selectedRoles)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = !isLoading
            ) {
                if (isLoading && !isGoogleLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = "REGISTRARSE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            GoogleSignInSection(
                onClick = onGoogleRegisterClick,
                isLoading = isLoading,
                isGoogleLoading = isGoogleLoading
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Login Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "¿Ya tienes cuenta? ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Text(
                    text = "Inicia sesión",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }
        }
    }
}

@Composable
private fun RegisterField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(6.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        isError = error != null,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            errorBorderColor = Color.Red,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            errorContainerColor = MaterialTheme.colorScheme.surface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            errorTextColor = MaterialTheme.colorScheme.onSurface
        ),
        singleLine = true
    )
    FieldError(error)
}

@Composable
private fun FieldError(error: String?) {
    if (error != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = error,
            color = Color.Red,
            fontSize = 13.sp
        )
    }
}
