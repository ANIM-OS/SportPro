package dev.dreamteam.sportpro.ui.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dreamteam.sportpro.ui.comun.CabeceraSeccion
import dev.dreamteam.sportpro.ui.theme.BordeSportPro
import dev.dreamteam.sportpro.ui.theme.SuperficieSportPro

@Composable
fun PerfilScreen(userEmail: String, onLogout: () -> Unit, isLoading: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        CabeceraSeccion(titulo = "MI PERFIL", subtitulo = "Datos de tu cuenta")
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SuperficieSportPro, RoundedCornerShape(12.dp))
                .border(1.dp, BordeSportPro, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(text = "Correo", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = userEmail, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onLogout,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BordeSportPro)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(text = "Cerrar sesión", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
