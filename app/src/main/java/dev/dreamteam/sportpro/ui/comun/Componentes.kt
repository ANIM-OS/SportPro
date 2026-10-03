package dev.dreamteam.sportpro.ui.comun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dreamteam.sportpro.ui.theme.BordeSportPro
import dev.dreamteam.sportpro.ui.theme.NaranjaSportPro
import dev.dreamteam.sportpro.ui.theme.SuperficieSportPro

/** Logo de texto "SportPro" usado en las cabeceras. */
@Composable
fun LogoSportPro(fontSize: Int = 32) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Sport", color = NaranjaSportPro, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "Pro", color = Color.White, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
    }
}

/** Título y subtítulo de una pestaña. */
@Composable
fun CabeceraSeccion(titulo: String, subtitulo: String) {
    Text(text = titulo, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = subtitulo, color = Color.Gray, fontSize = 14.sp)
}

/** Tarjeta de un módulo que todavía no está implementado, con la historia de usuario que lo cubre. */
@Composable
fun TarjetaModulo(titulo: String, descripcion: String, historia: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SuperficieSportPro, RoundedCornerShape(12.dp))
            .border(1.dp, BordeSportPro, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = titulo,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(text = historia, color = NaranjaSportPro, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = descripcion, color = Color.Gray, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "PRÓXIMAMENTE", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}
