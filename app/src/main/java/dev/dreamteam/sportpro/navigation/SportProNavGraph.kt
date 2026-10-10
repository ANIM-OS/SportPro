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
import androidx.compose.material.icons.filled.Shield
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.dreamteam.sportpro.ui.auth.AppRole
import dev.dreamteam.sportpro.ui.auth.RoleAccess
import dev.dreamteam.sportpro.ui.auth.RoleCapability
import dev.dreamteam.sportpro.ui.community.CommunityScreen
import dev.dreamteam.sportpro.ui.community.CreatePostScreen
import dev.dreamteam.sportpro.ui.community.PostCommentsScreen
import dev.dreamteam.sportpro.ui.events.EventCatalogEditScreen
import dev.dreamteam.sportpro.ui.events.EventCatalogScreen
import dev.dreamteam.sportpro.ui.events.EventDetailScreen
import dev.dreamteam.sportpro.ui.events.EventEditScreen
import dev.dreamteam.sportpro.ui.events.EventHistoryScreen
import dev.dreamteam.sportpro.ui.events.LiveEventsEntryScreen
import dev.dreamteam.sportpro.ui.events.RegisterEventScreen
import dev.dreamteam.sportpro.ui.teams.TeamsScreen
import dev.dreamteam.sportpro.ui.teams.CreateTeamScreen

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

    data object Teams : SportProRoute(
        route = "teams",
        title = "Equipos"
    )

    data object CreateTeam : SportProRoute(
        route = "create_team",
        title = "Nuevo equipo"
    )

    // ── US-12 · Catálogo configurable de eventos del partido ──
    data object EventCatalog : SportProRoute(
        route = "event_catalog",
        title = "Catálogo de eventos"
    )

    data object EventCatalogEdit : SportProRoute(
        route = "event_catalog_edit?typeId={typeId}", // typeId vacío = crear tipo nuevo
        title = "Tipo de evento"
    ) {
        const val ARG_TYPE_ID = "typeId"
        fun create(typeId: String? = null) =
            if (typeId == null) "event_catalog_edit" else "event_catalog_edit?typeId=$typeId"
    }

    // ── US-13 · Registro dinámico de eventos en vivo ──
    data object RegisterEvent : SportProRoute(
        route = "register_event/{matchId}",
        title = "Registrar evento"
    ) {
        const val ARG_MATCH_ID = "matchId"
        fun create(matchId: String) = "register_event/$matchId"
    }

    // ── US-14 · Corrección y anulación con trazabilidad ──
    data object EventDetail : SportProRoute(
        route = "event_detail/{matchId}/{eventId}",
        title = "Detalle del evento"
    ) {
        const val ARG_MATCH_ID = "matchId"
        const val ARG_EVENT_ID = "eventId"
        fun create(matchId: String, eventId: String) = "event_detail/$matchId/$eventId"
    }

    data object EventEdit : SportProRoute(
        route = "event_edit/{matchId}/{eventId}",
        title = "Corregir evento"
    ) {
        const val ARG_MATCH_ID = "matchId"
        const val ARG_EVENT_ID = "eventId"
        fun create(matchId: String, eventId: String) = "event_edit/$matchId/$eventId"
    }

    data object EventHistory : SportProRoute(
        route = "event_history/{matchId}/{eventId}",
        title = "Historial del evento"
    ) {
        const val ARG_MATCH_ID = "matchId"
        const val ARG_EVENT_ID = "eventId"
        fun create(matchId: String, eventId: String) = "event_history/$matchId/$eventId"
    }

    // ── US-19 · Comentarios y reacciones ──
    data object PostComments : SportProRoute(
        route = "post_comments/{postId}",
        title = "Comentarios"
    ) {
        const val ARG_POST_ID = "postId"
        fun create(postId: String) = "post_comments/$postId"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportProNavGraph(
    userEmail: String,
    roles: Set<AppRole>,
    coachApproved: Boolean,
    onLogout: () -> Unit,
    onEditRoles: () -> Unit,
    isLoading: Boolean = false,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    val navController = rememberNavController()

    val menuItems = listOf(
        SportProRoute.Home,
        SportProRoute.Live,
        SportProRoute.Statistics,
        SportProRoute.Community,
        SportProRoute.Teams
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
                    // Roles management button
                    TextButton(
                        onClick = onEditRoles,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = "Roles",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

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
                                    SportProRoute.Teams -> Icons.Default.Shield
                                    // CreatePost, CreateTeam y las rutas de US-12/13/14/19 no están en la barra
                                    else -> Icons.Default.Add
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
                // TODO(US-15): cuando se integre la pantalla real de En vivo, mover estos
                // tres callbacks a ella (botón "Catálogo", botón "Registrar evento" y clic en un evento).
                LiveEventsEntryScreen(
                    canManageCatalog = RoleAccess.can(roles, RoleCapability.MANAGE_EVENT_CATALOG),
                    canRegisterEvents = RoleAccess.can(roles, RoleCapability.REGISTER_EVENTS),
                    onOpenCatalog = { navController.navigate(SportProRoute.EventCatalog.route) },
                    onRegisterEvent = { matchId ->
                        navController.navigate(SportProRoute.RegisterEvent.create(matchId))
                    },
                    onOpenEvent = { matchId, eventId ->
                        navController.navigate(SportProRoute.EventDetail.create(matchId, eventId))
                    }
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
                    canCreatePost = RoleAccess.can(roles, RoleCapability.CREATE_POST),
                    onCreatePostClick = {
                        navController.navigate(
                            SportProRoute.CreatePost.route
                        )
                    },
                    onOpenComments = { postId ->
                        navController.navigate(SportProRoute.PostComments.create(postId))
                    }
                )
            }

            composable(SportProRoute.CreatePost.route) {
                RoleGate(roles, RoleCapability.CREATE_POST, true) {
                    CreatePostScreen(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }

            composable(SportProRoute.Teams.route) {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val createdTeamId = backStackEntry?.savedStateHandle?.get<String>("createdTeamId")
                TeamsScreen(
                    canCreateTeam = RoleAccess.can(roles, RoleCapability.CREATE_TEAM, true),
                    coachNeedsApproval = false,
                    canJoinWithCode = RoleAccess.can(roles, RoleCapability.JOIN_WITH_CODE),
                    newlyCreatedTeamId = createdTeamId,
                    onNewTeamOpened = { backStackEntry?.savedStateHandle?.remove<String>("createdTeamId") },
                    onCreateTeamClick = {
                        navController.navigate(SportProRoute.CreateTeam.route)
                    }
                )
            }

            composable(SportProRoute.CreateTeam.route) {
                RoleGate(roles, RoleCapability.CREATE_TEAM, true) {
                    CreateTeamScreen(
                        canCreateTeam = RoleAccess.can(roles, RoleCapability.CREATE_TEAM, true),
                        onBack = {
                            navController.popBackStack()
                        },
                        onTeamCreated = { teamId ->
                            navController.previousBackStackEntry?.savedStateHandle?.set("createdTeamId", teamId)
                            navController.popBackStack()
                        }
                    )
                }
            }

            // ───────── US-12 · Catálogo de eventos ─────────
            composable(SportProRoute.EventCatalog.route) {
                RoleGate(roles, RoleCapability.MANAGE_EVENT_CATALOG) {
                    EventCatalogScreen(
                        onBack = { navController.popBackStack() },
                        onNewType = { navController.navigate(SportProRoute.EventCatalogEdit.create()) },
                        onEditType = { typeId ->
                            navController.navigate(SportProRoute.EventCatalogEdit.create(typeId))
                        }
                    )
                }
            }

            composable(
                route = SportProRoute.EventCatalogEdit.route,
                arguments = listOf(
                    navArgument(SportProRoute.EventCatalogEdit.ARG_TYPE_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                RoleGate(roles, RoleCapability.MANAGE_EVENT_CATALOG) {
                    EventCatalogEditScreen(
                        typeId = entry.arguments?.getString(SportProRoute.EventCatalogEdit.ARG_TYPE_ID),
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // ───────── US-13 · Registro de eventos en vivo ─────────
            composable(
                route = SportProRoute.RegisterEvent.route,
                arguments = listOf(
                    navArgument(SportProRoute.RegisterEvent.ARG_MATCH_ID) { type = NavType.StringType }
                )
            ) { entry ->
                RoleGate(roles, RoleCapability.REGISTER_EVENTS) {
                    RegisterEventScreen(
                        matchId = entry.arguments?.getString(SportProRoute.RegisterEvent.ARG_MATCH_ID).orEmpty(),
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() } // vuelve a la cronología
                    )
                }
            }

            // ───────── US-14 · Corrección y anulación ─────────
            composable(
                route = SportProRoute.EventDetail.route,
                arguments = listOf(
                    navArgument(SportProRoute.EventDetail.ARG_MATCH_ID) { type = NavType.StringType },
                    navArgument(SportProRoute.EventDetail.ARG_EVENT_ID) { type = NavType.StringType }
                )
            ) { entry ->
                val matchId = entry.arguments?.getString(SportProRoute.EventDetail.ARG_MATCH_ID).orEmpty()
                val eventId = entry.arguments?.getString(SportProRoute.EventDetail.ARG_EVENT_ID).orEmpty()
                EventDetailScreen(
                    matchId = matchId,
                    eventId = eventId,
                    canEdit = RoleAccess.can(roles, RoleCapability.EDIT_EVENTS),
                    onBack = { navController.popBackStack() },
                    onCorrect = { navController.navigate(SportProRoute.EventEdit.create(matchId, eventId)) },
                    onVoided = { navController.popBackStack() },
                    onShowHistory = { navController.navigate(SportProRoute.EventHistory.create(matchId, eventId)) }
                )
            }

            composable(
                route = SportProRoute.EventEdit.route,
                arguments = listOf(
                    navArgument(SportProRoute.EventEdit.ARG_MATCH_ID) { type = NavType.StringType },
                    navArgument(SportProRoute.EventEdit.ARG_EVENT_ID) { type = NavType.StringType }
                )
            ) { entry ->
                RoleGate(roles, RoleCapability.EDIT_EVENTS) {
                    EventEditScreen(
                        matchId = entry.arguments?.getString(SportProRoute.EventEdit.ARG_MATCH_ID).orEmpty(),
                        eventId = entry.arguments?.getString(SportProRoute.EventEdit.ARG_EVENT_ID).orEmpty(),
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() }
                    )
                }
            }

            composable(
                route = SportProRoute.EventHistory.route,
                arguments = listOf(
                    navArgument(SportProRoute.EventHistory.ARG_MATCH_ID) { type = NavType.StringType },
                    navArgument(SportProRoute.EventHistory.ARG_EVENT_ID) { type = NavType.StringType }
                )
            ) { entry ->
                EventHistoryScreen(
                    matchId = entry.arguments?.getString(SportProRoute.EventHistory.ARG_MATCH_ID).orEmpty(),
                    eventId = entry.arguments?.getString(SportProRoute.EventHistory.ARG_EVENT_ID).orEmpty(),
                    onBack = { navController.popBackStack() }
                )
            }

            // ───────── US-19 · Comentarios y reacciones ─────────
            composable(
                route = SportProRoute.PostComments.route,
                arguments = listOf(
                    navArgument(SportProRoute.PostComments.ARG_POST_ID) { type = NavType.StringType }
                )
            ) { entry ->
                PostCommentsScreen(
                    postId = entry.arguments?.getString(SportProRoute.PostComments.ARG_POST_ID).orEmpty(),
                    canComment = RoleAccess.can(roles, RoleCapability.COMMENT_POST),
                    onBack = { navController.popBackStack() }
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
private fun RoleGate(roles: Set<AppRole>, capability: RoleCapability, coachApproved: Boolean = true, content: @Composable () -> Unit) {
    if (RoleAccess.can(roles, capability, coachApproved)) {
        content()
    } else {
        PlaceholderScreen(title = "SIN ACCESO", subtitle = "Tu rol no permite abrir esta página")
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
