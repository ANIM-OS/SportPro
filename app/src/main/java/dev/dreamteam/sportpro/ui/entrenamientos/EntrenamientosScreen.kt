package dev.dreamteam.sportpro.ui.entrenamientos

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
fun EntrenamientosScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        CabeceraSeccion(titulo = "ENTRENAMIENTOS", subtitulo = "Planifica y registra el trabajo del equipo")
        Spacer(modifier = Modifier.height(24.dp))
        TarjetaModulo(
            titulo = "Sesiones de entrenamiento",
            descripcion = "Planifica sesiones con fecha, duración, objetivos y ejercicios.",
            historia = "US-06"
        )
        Spacer(modifier = Modifier.height(12.dp))
        TarjetaModulo(
            titulo = "Biblioteca de ejercicios",
            descripcion = "Guarda ejercicios reutilizables para planificar más rápido.",
            historia = "US-07"
        )
        Spacer(modifier = Modifier.height(12.dp))
        TarjetaModulo(
            titulo = "Asistencia",
            descripcion = "Marca la asistencia por sesión y consulta el historial de cada jugador.",
            historia = "US-08"
        )
    }
}
