package dev.dreamteam.sportpro.ui.jugadores

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dreamteam.sportpro.ui.comun.CabeceraSeccion
import dev.dreamteam.sportpro.ui.comun.TarjetaModulo

@Composable
fun JugadoresScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        CabeceraSeccion(titulo = "JUGADORES", subtitulo = "Perfiles y seguimiento de tus jugadores")
        Spacer(modifier = Modifier.height(24.dp))
        TarjetaModulo(
            titulo = "Perfil del jugador",
            descripcion = "Posición, datos físicos, fotografía y contacto de emergencia.",
            historia = "US-04"
        )
        Spacer(modifier = Modifier.height(12.dp))
        TarjetaModulo(
            titulo = "Mensualidades",
            descripcion = "Estado simulado de pagos por jugador y periodo.",
            historia = "US-05"
        )
    }
}
