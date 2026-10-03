package dev.dreamteam.sportpro.ui.principal

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.dreamteam.sportpro.navigation.PestanaPrincipal
import dev.dreamteam.sportpro.ui.entrenamientos.EntrenamientosScreen
import dev.dreamteam.sportpro.ui.inicio.InicioScreen
import dev.dreamteam.sportpro.ui.jugadores.JugadoresScreen
import dev.dreamteam.sportpro.ui.perfil.PerfilScreen
import dev.dreamteam.sportpro.ui.theme.FondoSportPro
import dev.dreamteam.sportpro.ui.theme.NaranjaSportPro
import dev.dreamteam.sportpro.ui.theme.SuperficieSportPro

/** Contenedor de la app con sesión iniciada: barra inferior y una pestaña por módulo. */
@Composable
fun PrincipalScreen(
    userEmail: String,
    onLogout: () -> Unit,
    isSigningOut: Boolean = false
) {
    val navController = rememberNavController()
    val entradaActual by navController.currentBackStackEntryAsState()
    val destinoActual = entradaActual?.destination

    Scaffold(
        containerColor = FondoSportPro,
        bottomBar = {
            NavigationBar(containerColor = SuperficieSportPro) {
                PestanaPrincipal.entries.forEach { pestana ->
                    NavigationBarItem(
                        selected = destinoActual?.hierarchy?.any { it.route == pestana.ruta } == true,
                        onClick = {
                            navController.navigate(pestana.ruta) {
                                // Conserva el estado de cada pestaña y evita apilar copias al tocarlas varias veces.
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(pestana.icono), contentDescription = null) },
                        label = { Text(pestana.titulo) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NaranjaSportPro,
                            selectedTextColor = NaranjaSportPro,
                            indicatorColor = NaranjaSportPro.copy(alpha = 0.15f),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = PestanaPrincipal.INICIO.ruta,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(PestanaPrincipal.INICIO.ruta) {
                InicioScreen(userEmail = userEmail)
            }
            composable(PestanaPrincipal.ENTRENAMIENTOS.ruta) {
                EntrenamientosScreen()
            }
            composable(PestanaPrincipal.JUGADORES.ruta) {
                JugadoresScreen()
            }
            composable(PestanaPrincipal.PERFIL.ruta) {
                PerfilScreen(userEmail = userEmail, onLogout = onLogout, isLoading = isSigningOut)
            }
        }
    }
}
