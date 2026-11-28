package dev.dreamteam.sportpro.ui.callups

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/** US-15: hora del celular que se actualiza cada minuto para el estado del partido (sin recargar). */
@Composable
fun rememberMinuteClock(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            // Se despierta al empezar el minuto siguiente para cambiar de estado a tiempo.
            delay(60_000L - value % 60_000L)
        }
    }
    return now
}
