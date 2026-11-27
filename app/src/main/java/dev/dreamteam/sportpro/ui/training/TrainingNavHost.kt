package dev.dreamteam.sportpro.ui.training

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import dev.dreamteam.sportpro.domain.access.COACH_PENDING_MESSAGE
import dev.dreamteam.sportpro.data.repository.MonthlyFeeRepository
import dev.dreamteam.sportpro.ui.auth.AppRole
import dev.dreamteam.sportpro.ui.players.AdminFeesScreen
import dev.dreamteam.sportpro.ui.players.MonthlyFeesScreen
import dev.dreamteam.sportpro.ui.players.PlayerFeesScreen
import dev.dreamteam.sportpro.ui.players.PlayerProfileScreen
import dev.dreamteam.sportpro.ui.players.PlayersScreen

/** Rutas internas de la pestaña "Entrenar". */
private object TrainingRoutes {
    const val HUB = "training_hub"
    const val EXERCISES = "training_exercises"
    const val SESSIONS = "training_sessions"
    const val SESSION_FORM = "training_session_form?sessionId={sessionId}"
    const val SESSION_DETAIL = "training_session/{sessionId}"
    const val ATTENDANCE = "training_attendance/{playerId}"
    const val PLAYERS = "training_players"
    // teamId: equipo por el que entra la entrenadora (vacío para el jugador y el tutor).
    const val PLAYER_PROFILE = "training_player/{playerId}?name={name}&teamId={teamId}"
    const val FEES = "training_fees"
    const val PLAYER_FEES = "training_player_fees/{playerId}"
    const val ADMIN_FEES = "training_admin_fees"
    const val ADMIN_SESSIONS = "training_admin_sessions"
    const val GUARDIAN_SESSIONS = "training_guardian_sessions/{playerId}"

    fun sessionForm(sessionId: String? = null) =
        if (sessionId == null) "training_session_form" else "training_session_form?sessionId=$sessionId"

    fun sessionDetail(sessionId: String) = "training_session/$sessionId"
    fun attendance(playerId: String) = "training_attendance/$playerId"
    fun playerProfile(playerId: String, name: String = "", teamId: String? = null) =
        "training_player/$playerId?name=${Uri.encode(name)}&teamId=${Uri.encode(teamId.orEmpty())}"
    fun playerFees(playerId: String) = "training_player_fees/$playerId"
    fun guardianSessions(playerId: String) = "training_guardian_sessions/$playerId"
}

/**
 * Pestaña "Entrenar": US-04 (perfil del jugador), US-05 (mensualidades), US-06 (sesiones),
 * US-07 (biblioteca de ejercicios) y US-08 (asistencia). Cada rol ve solo sus módulos;
 * los módulos de entrenador exigen la aprobación del rol, igual que las reglas de Firestore.
 */
@Composable
fun TrainingNavHost(roles: Set<AppRole>, isCoachApproved: Boolean) {
    val navController = rememberNavController()
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val isCoach = AppRole.COACH in roles && isCoachApproved
    val coachPending = AppRole.COACH in roles && !isCoachApproved
    val isPlayer = AppRole.PLAYER in roles
    val isParent = AppRole.PARENT in roles
    // Admin = existe admins/{uid}; no es un rol elegible. Solo habilita la consulta de mensualidades.
    var isAdmin by remember(uid) { mutableStateOf(false) }
    LaunchedEffect(uid) { MonthlyFeeRepository().checkAdmin { isAdmin = it } }

    // Otra persona viendo el historial de un jugador: el entrenador solo lee lo que él registró.
    fun viewsAsCoach(playerId: String) = playerId != uid && isCoach

    NavHost(navController = navController, startDestination = TrainingRoutes.HUB) {
        composable(TrainingRoutes.HUB) {
            TrainingHub(
                isCoach = isCoach,
                coachPending = coachPending,
                isPlayer = isPlayer,
                isParent = isParent,
                isAdmin = isAdmin,
                onNavigate = { route -> navController.navigate(route) },
                ownPlayerRoutes = OwnPlayerRoutes(
                    profile = TrainingRoutes.playerProfile(uid),
                    attendance = TrainingRoutes.attendance(uid),
                    fees = TrainingRoutes.playerFees(uid)
                )
            )
        }
        composable(TrainingRoutes.EXERCISES) {
            // Todos la ven; solo la entrenadora aprobada gestiona sus ejercicios (US-07).
            ExerciseLibraryScreen(canManage = isCoach, onBack = { navController.popBackStack() })
        }
        composable(TrainingRoutes.SESSIONS) {
            SessionsScreen(
                isCoach = isCoach,
                isPlayer = isPlayer,
                onBack = { navController.popBackStack() },
                onCreateSession = { navController.navigate(TrainingRoutes.sessionForm()) },
                onOpenSession = { id -> navController.navigate(TrainingRoutes.sessionDetail(id)) }
            )
        }
        composable(
            route = TrainingRoutes.SESSION_FORM,
            arguments = listOf(navArgument("sessionId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { entry ->
            SessionFormScreen(
                sessionId = entry.arguments?.getString("sessionId"),
                onBack = { navController.popBackStack() },
                onOpenLibrary = { navController.navigate(TrainingRoutes.EXERCISES) }
            )
        }
        composable(TrainingRoutes.SESSION_DETAIL) { entry ->
            SessionDetailScreen(
                sessionId = entry.arguments?.getString("sessionId").orEmpty(),
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(TrainingRoutes.sessionForm(id)) },
                onOpenPlayerHistory = { playerId -> navController.navigate(TrainingRoutes.attendance(playerId)) }
            )
        }
        composable(TrainingRoutes.ATTENDANCE) { entry ->
            val playerId = entry.arguments?.getString("playerId").orEmpty()
            AttendanceHistoryScreen(
                playerId = playerId,
                asCoach = viewsAsCoach(playerId),
                title = if (playerId == uid) "MI ASISTENCIA" else "ASISTENCIA",
                onBack = { navController.popBackStack() }
            )
        }
        composable(TrainingRoutes.PLAYERS) {
            PlayersScreen(
                isCoach = isCoach,
                isParent = isParent,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { id, name, teamId -> navController.navigate(TrainingRoutes.playerProfile(id, name, teamId)) }
            )
        }
        composable(
            route = TrainingRoutes.PLAYER_PROFILE,
            arguments = listOf(
                navArgument("name") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("teamId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val playerId = entry.arguments?.getString("playerId").orEmpty()
            val coachTeamId = entry.arguments?.getString("teamId")?.takeIf { it.isNotBlank() && isCoach }
            PlayerProfileScreen(
                playerId = playerId,
                initialName = entry.arguments?.getString("name").orEmpty(),
                isSelf = playerId == uid,
                // Solo llega con teamId desde la plantilla de un equipo de la entrenadora.
                coachTeamId = coachTeamId,
                onBack = { navController.popBackStack() },
                onOpenAttendance = { navController.navigate(TrainingRoutes.attendance(playerId)) },
                onOpenFees = { navController.navigate(TrainingRoutes.playerFees(playerId)) },
                // Tutor que abre a su jugador desde "Jugadores a mi cargo" (sin equipo de entrenadora).
                onOpenSessions = if (isParent && playerId != uid && coachTeamId == null) {
                    { navController.navigate(TrainingRoutes.guardianSessions(playerId)) }
                } else null
            )
        }
        composable(TrainingRoutes.FEES) {
            MonthlyFeesScreen(onBack = { navController.popBackStack() })
        }
        composable(TrainingRoutes.ADMIN_FEES) {
            AdminFeesScreen(onBack = { navController.popBackStack() })
        }
        composable(TrainingRoutes.ADMIN_SESSIONS) {
            AdminSessionsScreen(
                onBack = { navController.popBackStack() },
                onOpenSession = { id -> navController.navigate(TrainingRoutes.sessionDetail(id)) }
            )
        }
        composable(TrainingRoutes.GUARDIAN_SESSIONS) { entry ->
            GuardianSessionsScreen(
                playerId = entry.arguments?.getString("playerId").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenSession = { id -> navController.navigate(TrainingRoutes.sessionDetail(id)) }
            )
        }
        composable(TrainingRoutes.PLAYER_FEES) { entry ->
            val playerId = entry.arguments?.getString("playerId").orEmpty()
            PlayerFeesScreen(
                playerId = playerId,
                asCoach = viewsAsCoach(playerId),
                title = if (playerId == uid) "MIS MENSUALIDADES" else "MENSUALIDADES",
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private data class OwnPlayerRoutes(val profile: String, val attendance: String, val fees: String)

@Composable
private fun TrainingHub(
    isCoach: Boolean,
    coachPending: Boolean,
    isPlayer: Boolean,
    isParent: Boolean,
    isAdmin: Boolean,
    onNavigate: (String) -> Unit,
    ownPlayerRoutes: OwnPlayerRoutes
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "ENTRENAMIENTOS",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Planificación, asistencia y seguimiento de jugadores",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )

        if (coachPending) {
            EmptyState(COACH_PENDING_MESSAGE)
        } else if (!isCoach && !isPlayer && !isParent && !isAdmin) {
            EmptyState("Tu cuenta aún no tiene roles asignados. Elígelos con el botón \"Roles\" de la barra superior.")
        }

        if (isCoach) {
            SectionLabel("ENTRENADOR", modifier = Modifier.padding(top = 8.dp))
            ModuleCard(
                title = "Sesiones de entrenamiento",
                description = "Planifica sesiones y registra la asistencia de cada jugador.",
                story = "US-06 · US-08",
                icon = Icons.Default.Event,
                onClick = { onNavigate(TrainingRoutes.SESSIONS) }
            )
            ModuleCard(
                title = "Biblioteca de ejercicios",
                description = "Biblioteca compartida: crea los tuyos y reutiliza los de todas.",
                story = "US-07",
                icon = Icons.Default.FitnessCenter,
                onClick = { onNavigate(TrainingRoutes.EXERCISES) }
            )
            ModuleCard(
                title = "Perfiles de jugadores",
                description = "Datos deportivos, físicos y de contacto de tus jugadores.",
                story = "US-04",
                icon = Icons.Default.Groups,
                onClick = { onNavigate(TrainingRoutes.PLAYERS) }
            )
            ModuleCard(
                title = "Mensualidades",
                description = "Registro simulado del estado de pago por periodo.",
                story = "US-05",
                icon = Icons.Default.Payments,
                onClick = { onNavigate(TrainingRoutes.FEES) }
            )
        }

        if (isPlayer) {
            SectionLabel("JUGADOR", modifier = Modifier.padding(top = 8.dp))
            ModuleCard(
                title = "Mi perfil deportivo",
                description = "Posición, datos físicos, foto y contacto de emergencia.",
                story = "US-04",
                icon = Icons.Default.Person,
                onClick = { onNavigate(ownPlayerRoutes.profile) }
            )
            ModuleCard(
                title = "Mis entrenamientos",
                description = "Sesiones planificadas para tu equipo y categoría.",
                story = "US-06",
                icon = Icons.Default.Event,
                onClick = { onNavigate(TrainingRoutes.SESSIONS) }
            )
            ModuleCard(
                title = "Mi asistencia",
                description = "Tu historial de participación en entrenamientos.",
                story = "US-08",
                icon = Icons.Default.Checklist,
                onClick = { onNavigate(ownPlayerRoutes.attendance) }
            )
            ModuleCard(
                title = "Mis mensualidades",
                description = "Estado de tus mensualidades por periodo.",
                story = "US-05",
                icon = Icons.Default.Payments,
                onClick = { onNavigate(ownPlayerRoutes.fees) }
            )
        }

        if (isParent) {
            SectionLabel("PADRE / TUTOR", modifier = Modifier.padding(top = 8.dp))
            ModuleCard(
                title = "Jugadores a mi cargo",
                description = "Perfil, entrenamientos, asistencia y mensualidades de tus hijos o tutelados.",
                story = "US-04 · US-05 · US-06 · US-08",
                icon = Icons.Default.FamilyRestroom,
                onClick = { onNavigate(TrainingRoutes.PLAYERS) }
            )
        }

        // US-07 criterio 3: la biblioteca es visible para todos los roles (en solo lectura).
        if (!isCoach) {
            SectionLabel("BIBLIOTECA", modifier = Modifier.padding(top = 8.dp))
            ModuleCard(
                title = "Biblioteca de ejercicios",
                description = "Ejercicios que usan las entrenadoras al planificar, en solo lectura.",
                story = "US-07",
                icon = Icons.Default.FitnessCenter,
                onClick = { onNavigate(TrainingRoutes.EXERCISES) }
            )
        }

        if (isAdmin) {
            SectionLabel("ADMINISTRACIÓN", modifier = Modifier.padding(top = 8.dp))
            ModuleCard(
                title = "Mensualidades",
                description = "Consulta de solo lectura por periodo y equipo.",
                story = "US-05",
                icon = Icons.Default.AdminPanelSettings,
                onClick = { onNavigate(TrainingRoutes.ADMIN_FEES) }
            )
            ModuleCard(
                title = "Sesiones de entrenamiento",
                description = "Planificación de todos los equipos, en solo lectura.",
                story = "US-06",
                icon = Icons.Default.Event,
                onClick = { onNavigate(TrainingRoutes.ADMIN_SESSIONS) }
            )
        }
    }
}
