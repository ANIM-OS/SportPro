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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RoleSelectionScreen(
    userEmail: String,
    userName: String = "Usuario SportPro",
    onEnterClick: (Set<String>) -> Unit,
    isLoading: Boolean = false,
    onCancelClick: () -> Unit
) {
    var selectedRoles by remember { mutableStateOf(setOf("JUGADOR")) }

    val roles = listOf(
        "JUGADOR" to Color(0xFFFF6600),
        "ENTRENADOR" to Color(0xFFFFCC00),
        "PADRE / TUTOR" to Color(0xFF00C853)
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
                    text = "Sport",
                    color = Color(0xFFFF6600),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Pro",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "ELIGE TU ROL",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Último paso para completar tu cuenta",
                color = Color.Gray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // User Info Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161920), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF2A2E35), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = " G ", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Datos recibidos de Google",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Nombre", color = Color.Gray, fontSize = 13.sp)
                        Text(text = userName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Correo", color = Color.Gray, fontSize = 13.sp)
                        Text(text = userEmail, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "¿CUÁL ES TU ROL EN LA PLATAFORMA?",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Role Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                roles.forEach { (roleName, roleColor) ->
                    val isSelected = selectedRoles.contains(roleName)
                    OutlinedButton(
                        onClick = {
                            selectedRoles = if (isSelected) {
                                if (selectedRoles.size > 1) selectedRoles - roleName else selectedRoles
                            } else {
                                selectedRoles + roleName
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) roleColor else Color(0xFF2A2E35)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) roleColor.copy(alpha = 0.15f) else Color(0xFF161920)
                        )
                    ) {
                        Text(
                            text = roleName,
                            color = if (isSelected) roleColor else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Puedes seleccionar más de uno. Podrás cambiar tu rol activo dentro de la app.",
                color = Color.Gray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Enter Button
            Button(
                onClick = { onEnterClick(selectedRoles) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6600)),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = "ENTRAR A SPORT PRO",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Cancelar y volver al inicio de sesión",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(enabled = !isLoading) { onCancelClick() }
                )
            }
        }
    }
}
