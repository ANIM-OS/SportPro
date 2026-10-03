package dev.dreamteam.sportpro.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.padding
import dev.dreamteam.sportpro.ui.community.CommunityScreen
import dev.dreamteam.sportpro.ui.community.CreatePostScreen
import androidx.compose.material.icons.filled.Add
import dev.dreamteam.sportpro.ui.live.LiveMatchScreen
import dev.dreamteam.sportpro.ui.statistics.StatisticsScreen

sealed class SportProRoute(
    val route: String,
    val title: String
) {
    data object Home : SportProRoute(
        route = "home",
        title = "Inicio"
    )

    data object Live : SportProRoute(
        route = "live",
        title = "En vivo"
    )

    data object Statistics : SportProRoute(
        route = "statistics",
        title = "Estadísticas"
    )

    data object Community : SportProRoute(
        route = "community",
        title = "Comunidad"
    )

    data object CreatePost : SportProRoute(
        route = "create_post",
        title = "Nueva publicación"
    )

}

@Composable
fun SportProNavGraph(
    userEmail: String,
    onLogout: () -> Unit,
    isLoading: Boolean = false
) {
    val navController = rememberNavController()

    val menuItems = listOf(
        SportProRoute.Home,
        SportProRoute.Live,
        SportProRoute.Statistics,
        SportProRoute.Community
    )

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    Scaffold(
        containerColor = Color(0xFF0D0F12),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF161920)
            ) {
                menuItems.forEach { item ->

                    val selected = currentRoute == item.route

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(
                                    navController.graph.findStartDestination().id
                                ) {
                                    saveState = true
                                }

                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    SportProRoute.Home -> Icons.Default.Home
                                    SportProRoute.Live -> Icons.Default.SportsSoccer
                                    SportProRoute.Statistics -> Icons.Default.BarChart
                                    SportProRoute.Community -> Icons.Default.Groups
                                    SportProRoute.CreatePost -> Icons.Default.Add
                                },
                                contentDescription = item.title
                            )
                        },
                        label = {
                            Text(
                                text = item.title,
                                fontSize = 10.sp
                            )
                        },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFFF6600),
                            selectedTextColor = Color(0xFFFF6600),
                            indicatorColor = Color(0xFFFF6600).copy(alpha = 0.15f),
                            unselectedIconColor = Color(0xFF9A9CA2),
                            unselectedTextColor = Color(0xFF9A9CA2)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = SportProRoute.Home.route,
            modifier = Modifier
                .padding(innerPadding)
                .background(Color(0xFF0D0F12))
        ) {

            composable(SportProRoute.Home.route) {
                PlaceholderScreen(
                    title = "SPORT PRO",
                    subtitle = "Bienvenido $userEmail"
                )
            }

            composable(SportProRoute.Live.route) {
                LiveMatchScreen()
            }

            composable(SportProRoute.Statistics.route) {
                StatisticsScreen()
            }

            composable(SportProRoute.Community.route) {

                CommunityScreen(
                    onCreatePostClick = {
                        navController.navigate(
                            SportProRoute.CreatePost.route
                        )
                    }
                )
            }
            composable(SportProRoute.CreatePost.route) {

                CreatePostScreen(
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F12)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = title,
            color = Color(0xFFFF6600),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = subtitle,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}