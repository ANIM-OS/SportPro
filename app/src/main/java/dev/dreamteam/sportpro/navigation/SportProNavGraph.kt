package dev.dreamteam.sportpro.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.dreamteam.sportpro.ui.community.CommunityScreen
import dev.dreamteam.sportpro.ui.community.CreatePostScreen

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportProNavGraph(
    userEmail: String,
    onLogout: () -> Unit,
    isLoading: Boolean = false,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Sport",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Pro",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    // Theme toggle pill button
                    Surface(
                        onClick = onToggleTheme,
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = if (isDarkTheme) "🌙 MODO OSCURO" else "☀️ MODO CLARO",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Logout button
                    TextButton(
                        onClick = onLogout,
                        enabled = !isLoading,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "Cerrar sesión",
                                color = Color(0xFFFF5252),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                menuItems.forEach { item ->
                    val selected = currentRoute == item.route

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(
                                    navController.graph.startDestinationId
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
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            composable(SportProRoute.Home.route) {
                HomeScreenContent(
                    userEmail = userEmail,
                    onLogout = onLogout,
                    isLoading = isLoading
                )
            }

            composable(SportProRoute.Live.route) {
                PlaceholderScreen(
                    title = "EN VIVO",
                    subtitle = "US-15 · Marcador y cronología"
                )
            }

            composable(SportProRoute.Statistics.route) {
                PlaceholderScreen(
                    title = "ESTADÍSTICAS",
                    subtitle = "US-16 · Rendimiento acumulado"
                )
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
private fun HomeScreenContent(
    userEmail: String,
    onLogout: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sport",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Pro",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "¡Bienvenido de nuevo!",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = userEmail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onLogout,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5252)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = "CERRAR SESIÓN",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
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
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp
        )
    }
}
