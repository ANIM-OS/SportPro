package dev.dreamteam.sportpro.navigation

import androidx.annotation.DrawableRes
import dev.dreamteam.sportpro.R

/** Rutas del grafo principal de la app. */
object Rutas {
    const val CARGANDO = "cargando"
    const val LOGIN = "login"
    const val REGISTRO = "registro"
    const val SELECCION_ROL = "seleccion_rol"
    const val PRINCIPAL = "principal"
}

/** Pestañas de la barra inferior dentro de [Rutas.PRINCIPAL]. */
enum class PestanaPrincipal(val ruta: String, val titulo: String, @param:DrawableRes val icono: Int) {
    INICIO("inicio", "Inicio", R.drawable.ic_inicio),
    ENTRENAMIENTOS("entrenamientos", "Entrenar", R.drawable.ic_entrenamientos),
    JUGADORES("jugadores", "Jugadores", R.drawable.ic_jugadores),
    PERFIL("perfil", "Perfil", R.drawable.ic_perfil)
}
