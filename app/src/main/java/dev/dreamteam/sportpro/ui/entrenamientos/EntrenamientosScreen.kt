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
import dev.dreamteam.sportpro.ui.comun.EtiquetaGrupo
import dev.dreamteam.sportpro.ui.comun.TarjetaModulo

/**
 * Pestaña "Entrenar": módulos de entrenamientos (US-06, US-07, US-08) y de jugadores (US-04, US-05).
 * [canPlanTraining] es true solo para entrenadores; el resto de roles consulta la información.
 */
@Composable
fun EntrenamientosScreen(canPlanTraining: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        CabeceraSeccion(
            titulo = "ENTRENAMIENTOS",
            subtitulo = if (canPlanTraining) {
                "Planifica y registra el trabajo de tus equipos"
            } else {
                "Consulta los entrenamientos y tu asistencia"
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        EtiquetaGrupo("ENTRENAMIENTOS")
        Spacer(modifier = Modifier.height(12.dp))
        TarjetaModulo(
            titulo = "Sesiones de entrenamiento",
            descripcion = "Sesiones con fecha, duración, objetivos y ejercicios por equipo o categoría.",
            historia = "US-06"
        )
        if (canPlanTraining) {
            Spacer(modifier = Modifier.height(12.dp))
            TarjetaModulo(
                titulo = "Biblioteca de ejercicios",
                descripcion = "Ejercicios reutilizables para planificar sesiones más rápido.",
                historia = "US-07"
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        TarjetaModulo(
            titulo = "Asistencia",
            descripcion = "Asistencia por sesión e historial de participación de cada jugador.",
            historia = "US-08"
        )

        Spacer(modifier = Modifier.height(24.dp))
        EtiquetaGrupo("JUGADORES")
        Spacer(modifier = Modifier.height(12.dp))
        TarjetaModulo(
            titulo = "Perfil del jugador",
            descripcion = "Posición, datos físicos, fotografía y contacto de emergencia.",
            historia = "US-04"
        )
        Spacer(modifier = Modifier.height(12.dp))
        TarjetaModulo(
            titulo = "Mensualidades",
            descripcion = "Estado simulado de pagos por jugador y periodo, sin dinero real.",
            historia = "US-05"
        )
    }
}
