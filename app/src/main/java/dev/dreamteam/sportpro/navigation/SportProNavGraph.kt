package dev.dreamteam.sportpro.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import androidx.compose.material.icons.filled.FitnessCenter
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import dev.dreamteam.sportpro.ui.tryouts.CreateTryoutScreen
import dev.dreamteam.sportpro.ui.tryouts.TryoutsScreen
import dev.dreamteam.sportpro.ui.tryouts.TryoutsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
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
import dev.dreamteam.sportpro.ui.community.CommunityViewModel
import dev.dreamteam.sportpro.ui.community.CreatePostScreen
import dev.dreamteam.sportpro.ui.training.TrainingNavHost
import dev.dreamteam.sportpro.ui.community.PostCommentsScreen
import dev.dreamteam.sportpro.ui.events.CatalogScope
import dev.dreamteam.sportpro.ui.events.EventCatalogManagerScreen
import dev.dreamteam.sportpro.ui.events.EventDetailScreen
import dev.dreamteam.sportpro.ui.events.EventEditScreen
import dev.dreamteam.sportpro.ui.events.EventHistoryScreen
import dev.dreamteam.sportpro.domain.access.COACH_PENDING_MESSAGE
import dev.dreamteam.sportpro.ui.events.RegisterEventScreen
import dev.dreamteam.sportpro.ui.events.MatchesForUs13Screen
import dev.dreamteam.sportpro.ui.events.MatchDetailUs13Screen
import dev.dreamteam.sportpro.ui.teams.TeamsScreen
import dev.dreamteam.sportpro.ui.teams.CreateTeamScreen
import dev.dreamteam.sportpro.ui.teams.TeamsViewModel
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
        title = "Partidos"
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

    // US-20: se abre desde la pestaña Convocatorias de Comunidad
    data object CreateTryout : SportProRoute(
        route = "create_tryout",
        title = "Nuevo aviso"
    )

    data object Teams : SportProRoute(
        route = "teams",
        title = "Equipos"
    )

    data object CreateTeam : SportProRoute(
        route = "create_team",
        title = "Nuevo equipo"
    )

    data object Training : SportProRoute(
        route = "training",
        title = "Entrenar"
    )

    // ── US-12 · Catálogo de eventos del equipo (el global del admin está en Entrenamientos) ──
    data object EventCatalog : SportProRoute(
        route = "event_catalog/{teamId}?name={name}",
        title = "Catálogo de eventos"
    ) {
        const val ARG_TEAM_ID = "teamId"
        const val ARG_NAME = "name"
        fun create(teamId: String, name: String) = "event_catalog/${android.net.Uri.encode(teamId)}?name=${android.net.Uri.encode(name)}"
    }

    // Rutas independientes para US-13 y US-14; no reemplazan Partidos de master.
    data object MatchesUs13 : SportProRoute("matches_us13", "Eventos del partido")
    data object MatchDetailUs13 : SportProRoute("match_us13/{matchId}", "Detalle del partido") {
        const val ARG_MATCH_ID = "matchId"
        fun create(matchId: String) = "match_us13/$matchId"
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
    coachApproved: Boolean?,
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
        SportProRoute.Teams,
        SportProRoute.Training
    )

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val isCoachApproved = coachApproved == true
    val coachPending = AppRole.COACH in roles && coachApproved == false

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        // Un solo texto de dos colores: nunca se parte en dos líneas y,
                        // si no entra, la letra baja hasta 14sp en vez de cortarse.
                        val primary = MaterialTheme.colorScheme.primary
                        val logo = remember(primary) {
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = primary)) { append("Sport") }
                                append("Pro")
                            }
                        }
                        BasicText(
                            text = logo,
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 20.sp)
                        )
                    },
                    actions = {
                        // En pantallas angostas (p. ej. Samsung A35) los botones se compactan
                        // para dejar sitio al logo; cada texto sigue en una sola línea.
                        val compact = LocalConfiguration.current.screenWidthDp < 400
                        val actionFont = if (compact) 11.sp else 12.sp
                        val buttonPadding = if (compact) PaddingValues(horizontal = 6.dp)
                            else ButtonDefaults.TextButtonContentPadding
                        val buttonMinWidth = if (compact) Modifier.defaultMinSize(minWidth = 1.dp) else Modifier

                        // Roles management button
                        TextButton(
                            onClick = onEditRoles,
                            contentPadding = buttonPadding,
                            modifier = buttonMinWidth.padding(end = if (compact) 2.dp else 4.dp)
                        ) {
                            Text(
                                text = "Roles",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = actionFont,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Theme toggle pill button
                        Surface(
                            onClick = onToggleTheme,
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.padding(end = if (compact) 2.dp else 4.dp)
                        ) {
                            Text(
                                text = if (isDarkTheme) "🌙 MODO OSCURO" else "☀️ MODO CLARO",
                                modifier = Modifier.padding(
                                    horizontal = if (compact) 6.dp else 10.dp, vertical = 6.dp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = if (compact) 10.sp else 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Logout button
                        TextButton(
                            onClick = onLogout,
                            enabled = !isLoading,
                            contentPadding = buttonPadding,
                            modifier = buttonMinWidth.padding(end = if (compact) 4.dp else 8.dp)
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
                                    fontSize = if (compact) 11.sp else 13.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                if (coachPending) {
                    Text(
                        text = COACH_PENDING_MESSAGE,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                }
            }
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
                                    SportProRoute.Training -> Icons.Default.FitnessCenter
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
                    isLoading = isLoading,
                    canRegisterEvents = RoleAccess.can(roles, RoleCapability.REGISTER_EVENTS, isCoachApproved),
                    onOpenMatches = { navController.navigate(SportProRoute.MatchesUs13.route) }
                )
            }

            composable(SportProRoute.Live.route) {
                LiveMatchScreen(
                    // US-09: "Mis equipos" solo para la entrenadora aprobada.
                    isCoach = RoleAccess.can(roles, RoleCapability.CREATE_TEAM, isCoachApproved),
                    isPlayer = AppRole.PLAYER in roles,
                    isGuardian = AppRole.PARENT in roles
                )
            }

            composable(SportProRoute.Statistics.route) {
                StatisticsScreen()
            }

            composable(SportProRoute.Community.route) {
                // La barra inferior ya tiene 5 destinos: Convocatorias (US-20) es una pestaña de Comunidad.
                var communityTab by rememberSaveable { mutableIntStateOf(0) }
                Column(modifier = Modifier.fillMaxSize()) {
                    PrimaryTabRow(
                        selectedTabIndex = communityTab,
                        containerColor = MaterialTheme.colorScheme.background
                    ) {
                        Tab(selected = communityTab == 0, onClick = { communityTab = 0 }, text = { Text("PUBLICACIONES") })
                        Tab(selected = communityTab == 1, onClick = { communityTab = 1 }, text = { Text("CONVOCATORIAS") })
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (communityTab == 0) {
                            CommunityScreen(
                                canCreatePost = RoleAccess.can(roles, RoleCapability.CREATE_POST, isCoachApproved),
                                onCreatePostClick = {
                                    navController.navigate(
                                        SportProRoute.CreatePost.route
                                    )
                                },
                                onOpenComments = { postId ->
                                    navController.navigate(SportProRoute.PostComments.create(postId))
                                }
                            )
                        } else {
                            TryoutsScreen(
                                canPublish = RoleAccess.can(roles, RoleCapability.PUBLISH_TRYOUT, isCoachApproved),
                                onCreateClick = { navController.navigate(SportProRoute.CreateTryout.route) }
                            )
                        }
                    }
                }
            }

            composable(SportProRoute.CreateTryout.route) { entry ->
                // Mismo ViewModel que la pestaña Convocatorias, como en CreatePost.
                val communityEntry = remember(entry) {
                    runCatching { navController.getBackStackEntry(SportProRoute.Community.route) }.getOrDefault(entry)
                }
                RoleGate(roles, RoleCapability.PUBLISH_TRYOUT, isCoachApproved) {
                    CreateTryoutScreen(
                        onBack = {
                            navController.popBackStack(SportProRoute.CreateTryout.route, inclusive = true)
                        },
                        viewModel = viewModel<TryoutsViewModel>(viewModelStoreOwner = communityEntry)
                    )
                }
            }

            composable(SportProRoute.CreatePost.route) { entry ->
                // Mismo ViewModel que Comunidad: un solo juego de listeners y la
                // lista sabe cuándo se publicó para volver arriba.
                val communityEntry = remember(entry) {
                    runCatching { navController.getBackStackEntry(SportProRoute.Community.route) }.getOrDefault(entry)
                }
                RoleGate(roles, RoleCapability.CREATE_POST, isCoachApproved) {
                    CreatePostScreen(
                        // Solo quita el formulario: si el usuario ya salió mientras se
                        // publicaba, la confirmación no debe sacarlo también de Comunidad.
                        onBack = {
                            navController.popBackStack(SportProRoute.CreatePost.route, inclusive = true)
                        },
                        viewModel = viewModel<CommunityViewModel>(viewModelStoreOwner = communityEntry)
                    )
                }
            }

            composable(SportProRoute.Teams.route) {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val createdTeamId = backStackEntry?.savedStateHandle?.get<String>("createdTeamId")
                TeamsScreen(
                    canCreateTeam = RoleAccess.can(roles, RoleCapability.CREATE_TEAM, isCoachApproved),
                    canJoinWithCode = RoleAccess.can(roles, RoleCapability.JOIN_WITH_CODE, isCoachApproved),
                    canRespondAsGuardian = AppRole.PARENT in roles,
                    newlyCreatedTeamId = createdTeamId,
                    onNewTeamOpened = { backStackEntry?.savedStateHandle?.remove<String>("createdTeamId") },
                    onOpenEventCatalog = { team -> navController.navigate(SportProRoute.EventCatalog.create(team.id, team.name)) },
                    onCreateTeamClick = {
                        navController.navigate(SportProRoute.CreateTeam.route)
                    }
                )
            }

            composable(SportProRoute.Training.route) {
                TrainingNavHost(roles = roles, isCoachApproved = isCoachApproved)
            }

            composable(SportProRoute.CreateTeam.route) { entry ->
                // Mismo TeamsViewModel que la pantalla Equipos: la creación sigue viva al volver
                // y, si el servidor la rechaza después, el error se ve en Equipos.
                val teamsEntry = remember(entry) {
                    runCatching { navController.getBackStackEntry(SportProRoute.Teams.route) }.getOrNull()
                }
                val teamsViewModel: TeamsViewModel = if (teamsEntry != null) viewModel(teamsEntry) else viewModel()
                RoleGate(roles, RoleCapability.CREATE_TEAM, isCoachApproved) {
                    CreateTeamScreen(
                        viewModel = teamsViewModel,
                        canCreateTeam = RoleAccess.can(roles, RoleCapability.CREATE_TEAM, isCoachApproved),
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

            // ───────── US-12 · Catálogo de eventos del equipo (entrenadora dueña aprobada) ─────────
            composable(
                route = SportProRoute.EventCatalog.route,
                arguments = listOf(
                    navArgument(SportProRoute.EventCatalog.ARG_TEAM_ID) { type = NavType.StringType },
                    navArgument(SportProRoute.EventCatalog.ARG_NAME) { type = NavType.StringType; defaultValue = "" }
                )
            ) { entry ->
                RoleGate(roles, RoleCapability.MANAGE_EVENT_CATALOG, isCoachApproved) {
                    EventCatalogManagerScreen(
                        scope = CatalogScope.Team(
                            entry.arguments?.getString(SportProRoute.EventCatalog.ARG_TEAM_ID).orEmpty(),
                            entry.arguments?.getString(SportProRoute.EventCatalog.ARG_NAME).orEmpty()
                        ),
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // US-13: muestra los partidos de demostración mientras se integra
            // el repositorio real de partidos del equipo.
            composable(SportProRoute.MatchesUs13.route) {
                MatchesForUs13Screen(
                    onBack = { navController.popBackStack() },
                    onOpenMatch = { matchId ->
                        navController.navigate(SportProRoute.MatchDetailUs13.create(matchId))
                    }
                )
            }

            composable(
                SportProRoute.MatchDetailUs13.route,
                arguments = listOf(
                    navArgument(SportProRoute.MatchDetailUs13.ARG_MATCH_ID) { type = NavType.StringType }
                )
            ) { entry ->
                val matchId = entry.arguments?.getString(SportProRoute.MatchDetailUs13.ARG_MATCH_ID).orEmpty()
                MatchDetailUs13Screen(
                    matchId = matchId,
                    canRegister = RoleAccess.can(roles, RoleCapability.REGISTER_EVENTS, isCoachApproved),
                    onBack = { navController.popBackStack() },
                    onRegister = { navController.navigate(SportProRoute.RegisterEvent.create(matchId)) },
                    onOpenEvent = { eventId ->
                        navController.navigate(SportProRoute.EventDetail.create(matchId, eventId))
                    }
                )
            }

            // ───────── US-13 · Registro de eventos en vivo ─────────
            composable(
                route = SportProRoute.RegisterEvent.route,
                arguments = listOf(
                    navArgument(SportProRoute.RegisterEvent.ARG_MATCH_ID) { type = NavType.StringType }
                )
            ) { entry ->
                RoleGate(roles, RoleCapability.REGISTER_EVENTS, isCoachApproved) {
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
                    canEdit = RoleAccess.can(roles, RoleCapability.EDIT_EVENTS, isCoachApproved),
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
                RoleGate(roles, RoleCapability.EDIT_EVENTS, isCoachApproved) {
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
                    canComment = RoleAccess.can(roles, RoleCapability.COMMENT_POST, isCoachApproved),
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
    isLoading: Boolean,
    canRegisterEvents: Boolean,
    onOpenMatches: () -> Unit
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

                Spacer(modifier = Modifier.height(20.dp))

                if (canRegisterEvents) {
                    Button(
                        onClick = onOpenMatches,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("PARTIDOS · REGISTRO DE EVENTOS", color = Color.White, fontWeight = FontWeight.Bold) }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Spacer(modifier = Modifier.height(14.dp))

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
private fun RoleGate(roles: Set<AppRole>, capability: RoleCapability, coachApproved: Boolean, content: @Composable () -> Unit) {
    if (RoleAccess.can(roles, capability, coachApproved)) {
        content()
    } else {
        PlaceholderScreen(
            title = "SIN ACCESO",
            subtitle = if (AppRole.COACH in roles && !coachApproved) COACH_PENDING_MESSAGE else "Tu rol no permite abrir esta página"
        )
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
