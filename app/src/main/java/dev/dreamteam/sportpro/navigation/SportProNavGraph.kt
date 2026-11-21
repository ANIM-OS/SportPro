package dev.dreamteam.sportpro.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Person
import dev.dreamteam.sportpro.ui.teams.TeamsViewModel
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
import dev.dreamteam.sportpro.ui.auth.AccountDeletionScreen
import dev.dreamteam.sportpro.ui.auth.AppRole
import dev.dreamteam.sportpro.ui.auth.RoleAccess
import dev.dreamteam.sportpro.ui.auth.RoleCapability
import dev.dreamteam.sportpro.ui.community.PostCommentsScreen
import dev.dreamteam.sportpro.ui.community.CommunityScreen
import dev.dreamteam.sportpro.ui.community.CommunityViewModel
import dev.dreamteam.sportpro.ui.community.CreatePostScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dev.dreamteam.sportpro.ui.training.TrainingNavHost
import dev.dreamteam.sportpro.ui.training.AttendanceHistoryScreen
import dev.dreamteam.sportpro.ui.players.PlayerProfileScreen
import dev.dreamteam.sportpro.ui.events.EventCatalogEditScreen
import dev.dreamteam.sportpro.ui.events.EventCatalogScreen
import dev.dreamteam.sportpro.ui.events.EventDetailScreen
import dev.dreamteam.sportpro.ui.events.EventEditScreen
import dev.dreamteam.sportpro.ui.events.EventHistoryScreen
import dev.dreamteam.sportpro.domain.access.COACH_PENDING_MESSAGE
import dev.dreamteam.sportpro.ui.events.RegisterEventScreen
import dev.dreamteam.sportpro.ui.teams.TeamsScreen
import dev.dreamteam.sportpro.ui.teams.CreateTeamScreen
import dev.dreamteam.sportpro.ui.live.LiveMatchScreen
import dev.dreamteam.sportpro.ui.statistics.StatisticsScreen
import dev.dreamteam.sportpro.ui.teams.GuardianScreen
import dev.dreamteam.sportpro.ui.teams.AcademyLinksScreen
import dev.dreamteam.sportpro.ui.teams.FeesScreen
import dev.dreamteam.sportpro.ui.teams.NoticesScreen

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

    data object Administration : SportProRoute(route = "administration", title = "Administración")
    data object LinkedAttendance : SportProRoute(route = "linked_attendance/{playerId}", title = "Asistencia") {
        fun create(playerId: String) = "linked_attendance/${android.net.Uri.encode(playerId)}"
    }
    data object Guardians : SportProRoute(route = "guardians", title = "Tutores")
    data object Profile : SportProRoute(route = "profile", title = "Perfil")
    data object AccountDeletion : SportProRoute(route = "account_deletion", title = "Eliminar cuenta")
    data object AcademyLinks : SportProRoute(route = "academy_links", title = "Academias")
    data object Fees : SportProRoute(route = "fees", title = "Mensualidades")
    data object PostComments : SportProRoute(route = "post_comments/{postId}", title = "Comentarios") {
        fun create(postId: String) = "post_comments/${android.net.Uri.encode(postId)}"
    }
    data object Notices : SportProRoute(route = "notices", title = "Avisos")

    data object CreateTeam : SportProRoute(
        route = "create_team",
        title = "Nuevo equipo"
    )

    data object Training : SportProRoute(
        route = "training",
        title = "Entrenar"
    )

    data object MySessions : SportProRoute(route = "my_sessions", title = "Mis entrenamientos")
    data object MyAttendance : SportProRoute(route = "my_attendance", title = "Mi asistencia")
    data object SportsProfile : SportProRoute(route = "sports_profile", title = "Perfil deportivo")

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportProNavGraph(
    userEmail: String,
    roles: Set<AppRole>,
    coachApproved: Boolean?,
    isAdmin: Boolean,
    privacyReady: Boolean,
    onLogout: () -> Unit,
    onEditRoles: () -> Unit,
    isLoading: Boolean = false,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    val navController = rememberNavController()

    val menuItems = listOf(
        SportProRoute.Home,
        SportProRoute.Teams,
        SportProRoute.Live,
        SportProRoute.Community,
        SportProRoute.Profile
    )

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val isCoach = AppRole.COACH in roles
    val isPlayer = AppRole.PLAYER in roles
    val isGuardian = AppRole.PARENT in roles
    val isCoachApproved = isCoach && coachApproved == true
    val coachPending = AppRole.COACH in roles && coachApproved == false

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
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
                                restoreState = item != SportProRoute.Home
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
                                    SportProRoute.Profile -> Icons.Default.Person
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
                HomeScreenContent(onTeams = { navController.navigate(SportProRoute.Teams.route) { launchSingleTop = true } },
                    onNotices = { navController.navigate(SportProRoute.Notices.route) },
                    onGuardians = { navController.navigate(SportProRoute.Guardians.route) },
                    onTraining = { navController.navigate(SportProRoute.Training.route) },
                    onSessions = { navController.navigate(SportProRoute.MySessions.route) },
                    onAttendance = { navController.navigate(SportProRoute.MyAttendance.route) },
                    onFees = { navController.navigate(SportProRoute.Fees.route) },
                    onAdmin = { navController.navigate(SportProRoute.Administration.route) },
                    isAdmin = isAdmin,
                    privacyReady = privacyReady,
                    roles = roles)
            }

            composable(SportProRoute.Live.route) {
                LiveMatchScreen(
                    isCoach = isCoachApproved && AppRole.COACH in roles,
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
                                onOpenComments = if (privacyReady) { { id -> navController.navigate(SportProRoute.PostComments.create(id)) } } else null,
                                canCreatePost = RoleAccess.can(roles, RoleCapability.CREATE_POST, isCoachApproved),
                                onCreatePostClick = {
                                    navController.navigate(
                                        SportProRoute.CreatePost.route
                                    )
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
                    isGuardian = isGuardian,
                    isCoach = isCoach,
                    privacyReady = privacyReady,
                    onGuardians = { navController.navigate(SportProRoute.Guardians.route) },
                    newlyCreatedTeamId = createdTeamId,
                    onNewTeamOpened = { backStackEntry?.savedStateHandle?.remove<String>("createdTeamId") },
                    onCreateTeamClick = {
                        navController.navigate(SportProRoute.CreateTeam.route)
                    }
                )
            }

            composable(SportProRoute.Training.route) {
                TrainingNavHost(roles = roles, isCoachApproved = isCoachApproved,
                    onFees = { navController.navigate(SportProRoute.Fees.route) },
                    onBack = { navController.popBackStack() })
            }

            composable(SportProRoute.MySessions.route) {
                if (AppRole.PLAYER in roles) {
                    TrainingNavHost(
                        roles = setOf(AppRole.PLAYER),
                        isCoachApproved = false,
                        onFees = { navController.navigate(SportProRoute.Fees.route) },
                        onBack = { navController.popBackStack() },
                        startWithSessions = true
                    )
                }
            }

            composable(SportProRoute.MyAttendance.route) {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null && AppRole.PLAYER in roles) {
                    AttendanceHistoryScreen(
                        playerId = uid,
                        asCoach = false,
                        title = "Mi asistencia",
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(SportProRoute.SportsProfile.route) {
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null && AppRole.PLAYER in roles) {
                    PlayerProfileScreen(
                        playerId = user.uid,
                        initialName = user.displayName.orEmpty(),
                        canEdit = true,
                        isSelf = true,
                        onBack = { navController.popBackStack() },
                        onOpenAttendance = { navController.navigate(SportProRoute.MyAttendance.route) },
                        onOpenFees = { navController.navigate(SportProRoute.Fees.route) },
                        onOpenGuardians = { navController.navigate(SportProRoute.Guardians.route) }
                    )
                }
            }

            composable(SportProRoute.Profile.route) {
                SportProfileScreen(
                    email = userEmail,
                    roles = roles,
                    darkTheme = isDarkTheme,
                    signingOut = isLoading,
                    isAdmin = isAdmin,
                    privacyReady = privacyReady,
                    onAdmin = { navController.navigate(SportProRoute.Administration.route) },
                    onSportsProfile = if (AppRole.PLAYER in roles) {
                        { navController.navigate(SportProRoute.SportsProfile.route) }
                    } else null,
                    onRequestDeletion = { navController.navigate(SportProRoute.AccountDeletion.route) },
                    onRoles = onEditRoles,
                    onTheme = onToggleTheme,
                    onLogout = onLogout,
                    onFees = { navController.navigate(SportProRoute.Fees.route) },
                    onNotices = { navController.navigate(SportProRoute.Notices.route) },
                    onAcademies = { navController.navigate(SportProRoute.AcademyLinks.route) },
                    onGuardians = { navController.navigate(SportProRoute.Guardians.route) }
                )
            }

            composable(SportProRoute.PostComments.route, arguments = listOf(navArgument("postId") { type = NavType.StringType })) { entry ->
                val postId = entry.arguments?.getString("postId")
                if (privacyReady && postId != null) PostCommentsScreen(
                    postId = postId,
                    canComment = RoleAccess.can(roles, RoleCapability.COMMENT_POST, isCoachApproved),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(SportProRoute.AccountDeletion.route) {
                if (privacyReady) AccountDeletionScreen(onBack = { navController.popBackStack() })
            }

            composable(SportProRoute.Notices.route) {
                NoticesScreen(
                    isPlayer = isPlayer,
                    isCoach = isCoach,
                    isGuardian = isGuardian,
                    coachApproved = isCoachApproved,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(SportProRoute.Fees.route) {
                if (privacyReady) {
                    FeesScreen(
                        isPlayer = isPlayer,
                        isCoach = isCoach,
                        isGuardian = isGuardian,
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    PlaceholderScreen("Mensualidades", "Este servicio aún no está disponible en el servidor.")
                }
            }

            composable(SportProRoute.AcademyLinks.route) {
                if (isCoach) {
                    AcademyLinksScreen(onBack = { navController.popBackStack() })
                } else {
                    PlaceholderScreen("Acceso restringido", "Esta sección requiere el rol Entrenador.")
                }
            }

            composable(SportProRoute.Guardians.route) {
                if (AppRole.PLAYER in roles || AppRole.PARENT in roles) {
                    GuardianScreen(
                        isPlayer = AppRole.PLAYER in roles,
                        isGuardian = AppRole.PARENT in roles,
                        privacyReady = privacyReady,
                        onBack = { navController.popBackStack() },
                        onAttendance = { playerId -> navController.navigate(SportProRoute.LinkedAttendance.create(playerId)) }
                    )
                }
            }

            composable(
                SportProRoute.LinkedAttendance.route,
                arguments = listOf(navArgument("playerId") { type = NavType.StringType })
            ) { entry ->
                val playerId = entry.arguments?.getString("playerId")
                if (playerId != null && AppRole.PARENT in roles) {
                    LinkedPlayerAttendance(playerId, onBack = { navController.popBackStack() })
                }
            }

            composable(SportProRoute.Administration.route) {
                if (isAdmin) {
                    SportAdminScreen(
                        onBack = { navController.popBackStack() },
                        onModeration = { navController.navigate(SportProRoute.Community.route) }
                    )
                } else {
                    PlaceholderScreen(title = "Acceso restringido", subtitle = "Esta sección requiere una cuenta de administración autorizada.")
                }
            }

            composable(SportProRoute.CreateTeam.route) {
                RoleGate(roles, RoleCapability.CREATE_TEAM, isCoachApproved) {
                    CreateTeamScreen(
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

            // ───────── US-12 · Catálogo de eventos ─────────
            composable(SportProRoute.EventCatalog.route) {
                RoleGate(roles, RoleCapability.MANAGE_EVENT_CATALOG, isCoachApproved) {
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
                RoleGate(roles, RoleCapability.MANAGE_EVENT_CATALOG, isCoachApproved) {
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

        }
    }
}

@Composable
private fun HomeScreenContent(
    onTeams: () -> Unit,
    onGuardians: () -> Unit,
    onNotices: () -> Unit,
    onTraining: () -> Unit,
    onSessions: () -> Unit,
    onAttendance: () -> Unit,
    onFees: () -> Unit,
    onAdmin: () -> Unit,
    isAdmin: Boolean,
    privacyReady: Boolean,
    roles: Set<AppRole>,
    teamsViewModel: TeamsViewModel = viewModel()
) {
    val state by teamsViewModel.uiState.collectAsState()
    SportHomeScreen(
        state = state,
        roles = roles,
        isAdmin = isAdmin,
        privacyReady = privacyReady,
        onTeams = onTeams,
        onGuardians = onGuardians,
        onNotices = onNotices,
        onTraining = onTraining,
        onSessions = onSessions,
        onAttendance = onAttendance,
        onFees = onFees,
        onAdmin = onAdmin,
        retry = teamsViewModel::loadTeams
    )
}

@Composable
private fun LinkedPlayerAttendance(playerId: String, onBack: () -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var authorized by remember(playerId, uid) { mutableStateOf<Boolean?>(null) }
    DisposableEffect(playerId, uid) {
        val listener = FirebaseFirestore.getInstance().collection("guardianLinks")
            .document("${playerId}_$uid")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    authorized = false
                } else if (snapshot?.metadata?.isFromCache == false) {
                    authorized = snapshot.getString("status") == "ACTIVE"
                }
            }
        onDispose { listener.remove() }
    }
    when (authorized) {
        true -> AttendanceHistoryScreen(playerId, asCoach = false, title = "Asistencia del jugador", onBack = onBack)
        false -> Column(Modifier.padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("El historial requiere un vínculo de tutoría activo.")
        }
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
