package dev.dreamteam.sportpro.ui.training

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.dreamteam.sportpro.ui.auth.AppRole
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

    fun sessionForm(sessionId: String? = null) =
        if (sessionId == null) "training_session_form" else "training_session_form?sessionId=$sessionId"

    fun sessionDetail(sessionId: String) = "training_session/$sessionId"
    fun attendance(playerId: String) = "training_attendance/$playerId"
}

/**
 * Pestaña "Entrenar": US-04 (perfil del jugador), US-05 (mensualidades), US-06 (sesiones),
 * US-07 (biblioteca de ejercicios) y US-08 (asistencia). Cada rol ve solo sus módulos;
 * los módulos de entrenador exigen la aprobación del rol, igual que las reglas de Firestore.
 */
@Composable
fun TrainingNavHost(
    roles: Set<AppRole>,
    isCoachApproved: Boolean,
    onFees: () -> Unit,
    onBack: () -> Unit,
    startWithSessions: Boolean = false
) {
    val navController = rememberNavController()
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val isCoach = AppRole.COACH in roles && isCoachApproved
    val coachPending = AppRole.COACH in roles && !isCoachApproved
    val isPlayer = AppRole.PLAYER in roles
    val isParent = AppRole.PARENT in roles

    // Otra persona viendo el historial de un jugador: el entrenador solo lee lo que él registró.
    fun viewsAsCoach(playerId: String) = playerId != uid && isCoach

    NavHost(
        navController = navController,
        startDestination = if (startWithSessions) TrainingRoutes.SESSIONS else TrainingRoutes.HUB
    ) {
        composable(TrainingRoutes.HUB) {
            TrainingHub(
                isCoach = isCoach,
                coachPending = coachPending,
                isParent = isParent,
                onNavigate = { route -> navController.navigate(route) },
                onFees = onFees
            )
        }
        composable(TrainingRoutes.EXERCISES) {
            ExerciseLibraryScreen(onBack = { navController.popBackStack() })
        }
        composable(TrainingRoutes.SESSIONS) {
            SessionsScreen(
                isCoach = isCoach,
                isPlayer = isPlayer,
                onBack = {
                    if (startWithSessions) onBack() else navController.popBackStack()
                },
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
                canManage = isCoach,
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
                onOpenPlayer = { id, _ -> navController.navigate(TrainingRoutes.attendance(id)) }
            )
        }

    }
}

@Composable
private fun TrainingHub(
    isCoach: Boolean,
    coachPending: Boolean,
    isParent: Boolean,
    onNavigate: (String) -> Unit,
    onFees: () -> Unit
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
        } else if (!isCoach && !isParent) {
            EmptyState("Tu cuenta aún no tiene roles asignados. Elígelos en Perfil.")
        }

        if (isCoach) {
            SectionLabel("ENTRENADOR", modifier = Modifier.padding(top = 8.dp))
            ModuleCard(
                title = "Sesiones de entrenamiento",
                description = "Planifica sesiones y registra la asistencia de cada jugador.",
                icon = Icons.Default.Event,
                onClick = { onNavigate(TrainingRoutes.SESSIONS) }
            )
            ModuleCard(
                title = "Biblioteca de ejercicios",
                description = "Ejercicios reutilizables con nombre, descripción y duración.",
                icon = Icons.Default.FitnessCenter,
                onClick = { onNavigate(TrainingRoutes.EXERCISES) }
            )
            ModuleCard(
                title = "Asistencia de jugadores",
                description = "Historial de asistencia de tus jugadores.",
                icon = Icons.Default.Groups,
                onClick = { onNavigate(TrainingRoutes.PLAYERS) }
            )
            ModuleCard(
                title = "Mensualidades",
                description = "Consulta el estado de las mensualidades de academia.",
                icon = Icons.Default.Payments,
                onClick = onFees
            )
        }

        if (isParent) {
            SectionLabel("PADRE / TUTOR", modifier = Modifier.padding(top = 8.dp))
            ModuleCard(
                title = "Jugadores a mi cargo",
                description = "Consulta la asistencia de los jugadores vinculados a tu cuenta.",
                icon = Icons.Default.FamilyRestroom,
                onClick = { onNavigate(TrainingRoutes.PLAYERS) }
            )
        }
    }
}
