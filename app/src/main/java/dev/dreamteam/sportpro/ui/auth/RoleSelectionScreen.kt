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
    onCancelClick: () -> Unit,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
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
            .background(MaterialTheme.colorScheme.background)
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

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "ELIGE TU ROL",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Último paso para completar tu cuenta",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // User Info Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = " G ", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Datos recibidos de Google",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Nombre", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text(text = userName, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Correo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text(text = userEmail, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "¿CUÁL ES TU ROL EN LA PLATAFORMA?",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) roleColor else MaterialTheme.colorScheme.surfaceVariant),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) roleColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            text = roleName,
                            color = if (isSelected) roleColor else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Puedes seleccionar más de uno. Podrás cambiar tu rol activo dentro de la app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(enabled = !isLoading) { onCancelClick() }
                )
            }
        }
    }
}
