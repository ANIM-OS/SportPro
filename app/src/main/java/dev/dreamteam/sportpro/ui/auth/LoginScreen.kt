package dev.dreamteam.sportpro.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit,
    onGoogleLoginClick: () -> Unit,
    onForgotPasswordClick: (String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onDemoSelected: (String, String) -> Unit,
    errorMessage: String? = null,
    isLoading: Boolean = false,
    isGoogleLoading: Boolean = false
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var expandedDemo by remember { mutableStateOf(false) }

    val demoAccounts = listOf(
        "demo.jugador@sportpro.dev" to "password123",
        "demo.entrenador@sportpro.dev" to "password123",
        "demo.padre@sportpro.dev" to "password123"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F12))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            // Logo / Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SPORT",
                    color = Color(0xFFFF6600),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PRO",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "BIENVENIDO",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Inicia sesión para continuar",
                color = Color.Gray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Email
            Text(
                text = "CORREO ELECTRÓNICO",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6600),
                    unfocusedBorderColor = Color(0xFF2A2E35),
                    focusedContainerColor = Color(0xFF161920),
                    unfocusedContainerColor = Color(0xFF161920),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password
            Text(
                text = "CONTRASEÑA",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Text(
                        text = if (passwordVisible) "OCULTAR" else "MOSTRAR",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clickable { passwordVisible = !passwordVisible }
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6600),
                    unfocusedBorderColor = Color(0xFF2A2E35),
                    focusedContainerColor = Color(0xFF161920),
                    unfocusedContainerColor = Color(0xFF161920),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = "¿Olvidaste tu contraseña?",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(enabled = !isLoading) { onForgotPasswordClick(email) }
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Login Button
            Button(
                onClick = { onLoginClick(email, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6600)),
                enabled = !isLoading
            ) {
                if (isLoading && !isGoogleLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = "INICIAR SESIÓN",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Divider "o continúa con"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2A2E35))
                Text(
                    text = "  o continúa con  ",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2A2E35))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Google Button
            OutlinedButton(
                onClick = onGoogleLoginClick,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF2A2E35)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF161920))
            ) {
                if (isGoogleLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = if (isGoogleLoading) "Conectando con Google…" else "Continuar con Google",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Divider "o prueba con una cuenta demo"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2A2E35))
                Text(
                    text = "  o prueba con una cuenta demo  ",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2A2E35))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Demo Accounts Dropdown Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(Color(0xFF161920), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF2A2E35), RoundedCornerShape(12.dp))
                    .clickable(enabled = !isLoading) { expandedDemo = true }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cuentas de prueba (prototipo)",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "▼",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                DropdownMenu(
                    expanded = expandedDemo,
                    onDismissRequest = { expandedDemo = false },
                    modifier = Modifier
                        .fillMaxWidth(0.855f)
                        .background(Color(0xFF161920))
                ) {
                    demoAccounts.forEach { (demoEmail, demoPass) ->
                        DropdownMenuItem(
                            enabled = !isLoading,
                            text = { Text(text = demoEmail, color = Color.White) },
                            onClick = {
                                expandedDemo = false
                                email = demoEmail
                                password = demoPass
                                onDemoSelected(demoEmail, demoPass)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Register Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "¿No tienes cuenta? ",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Text(
                    text = "Regístrate",
                    color = Color(0xFFFF6600),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(enabled = !isLoading) { onNavigateToRegister() }
                )
            }
        }
    }
}
