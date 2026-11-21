package dev.dreamteam.sportpro.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dreamteam.sportpro.ui.auth.AppRole
import dev.dreamteam.sportpro.ui.teams.TeamsUiState

@Composable
internal fun SportHomeScreen(
    state: TeamsUiState,
    roles: Set<AppRole>,
    isAdmin: Boolean,
    privacyReady: Boolean,
    onTeams: () -> Unit,
    onGuardians: () -> Unit,
    onNotices: () -> Unit,
    onTraining: () -> Unit,
    onSessions: () -> Unit,
    onAttendance: () -> Unit,
    onFees: () -> Unit,
    onAdmin: () -> Unit,
    retry: () -> Unit
) {
    DashboardColumn {
        if (AppRole.PLAYER in roles || AppRole.COACH in roles) {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFF392417), Color(0xFF221C1A), Color(0xFF181A22))),
                    RoundedCornerShape(24.dp)
                ).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFFFA469))
                Text("Tu vestuario", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = Color.White)
                val team = state.teams.firstOrNull()
                val summary = when {
                    team != null -> team.name
                    state.isLoading -> "Cargando tus equipos…"
                    state.teamsError != null -> "No se pudieron cargar tus equipos."
                    else -> "Todavía no tienes equipos."
                }
                Text(summary, color = Color(0xFFE6D8CF), style = MaterialTheme.typography.titleMedium)
                if (team != null && state.teams.size > 1) {
                    Text("Equipos y academias: ${state.teams.size}", color = Color(0xFFE6D8CF))
                }
                Button(
                    onClick = if (team == null && state.teamsError != null) retry else onTeams,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA469), contentColor = Color(0xFF25150C))
                ) {
                    Text(if (team == null && state.teamsError != null) "Reintentar" else "Ver mis equipos")
                }
            }
        }

        val invites = state.pendingInvites.size
        val requests = state.myJoinRequests.count { it.status == "PENDING" }
        if (AppRole.PLAYER in roles && (invites > 0 || requests > 0)) {
            DashboardSection("Pendientes") {
                if (invites > 0) DashboardRow("Invitaciones a equipos", Icons.Default.Mail, "$invites pendientes", onTeams)
                if (requests > 0) DashboardRow("Solicitudes de ingreso", Icons.Default.Schedule, "$requests pendientes", onTeams)
            }
        }

        if (AppRole.PLAYER in roles) {
            DashboardSection("Mi actividad") {
                DashboardRow("Mis entrenamientos", Icons.Default.Event, onClick = onSessions)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                DashboardRow("Mi asistencia", Icons.Default.Checklist, onClick = onAttendance)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                if (privacyReady) DashboardRow("Mis mensualidades", Icons.Default.Payments, onClick = onFees)
            }
        }

        if (AppRole.COACH in roles) {
            DashboardSection("Entrenador") {
                DashboardRow("Gestionar entrenamientos", Icons.Default.FitnessCenter, "Sesiones y asistencia de jugadores", onTraining)
            }
        }
        if (AppRole.PARENT in roles) {
            DashboardSection("Padre o tutor") {
                DashboardRow("Jugadores vinculados", Icons.Default.Groups, "Vínculos y asistencia", onGuardians)
                if (privacyReady) DashboardRow("Mensualidades de jugadores", Icons.Default.Payments, onClick = onFees)
            }
        }
        if (isAdmin) {
            DashboardSection("Administración") {
                DashboardRow("Moderar la comunidad", Icons.Default.AdminPanelSettings, onClick = onAdmin)
            }
        }
        DashboardSection("Accesos rápidos") {
            if (privacyReady) DashboardRow("Avisos internos", Icons.Default.Notifications, "Comunicados de tus equipos", onNotices)
            if (AppRole.PLAYER in roles) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                DashboardRow("Padres y tutores", Icons.Default.Groups, "Invita a tu tutor y gestiona sus accesos", onGuardians)
            }
        }
    }
}

@Composable
internal fun SportProfileScreen(
    email: String,
    roles: Set<AppRole>,
    darkTheme: Boolean,
    signingOut: Boolean,
    isAdmin: Boolean,
    privacyReady: Boolean,
    onAdmin: () -> Unit,
    onSportsProfile: (() -> Unit)?,
    onRequestDeletion: () -> Unit,
    onRoles: () -> Unit,
    onTheme: () -> Unit,
    onLogout: () -> Unit,
    onFees: () -> Unit,
    onNotices: () -> Unit,
    onAcademies: () -> Unit,
    onGuardians: () -> Unit
) {
    DashboardColumn {
        Text("Perfil", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            val cardAction = if (onSportsProfile == null) Modifier else Modifier.clickable(onClick = onSportsProfile)
            Row(Modifier.fillMaxWidth().then(cardAction).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .15f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text(email.firstOrNull()?.uppercase() ?: "S", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(email, style = MaterialTheme.typography.titleMedium)
                    Text(roles.joinToString(" · ") {
                        when (it) {
                            AppRole.PLAYER -> "Jugador"
                            AppRole.COACH -> "Entrenador"
                            AppRole.PARENT -> "Padre o tutor"
                        }
                    }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    if (isAdmin) {
                        Text("Administrador", style = MaterialTheme.typography.labelLarge)
                    }
                    if (onSportsProfile != null) {
                        Text("Ver perfil deportivo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (onSportsProfile != null) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        DashboardSection("Actividad") {
            if (privacyReady) DashboardRow("Avisos internos", Icons.Default.Notifications, onClick = onNotices)
            if (privacyReady && (AppRole.PLAYER !in roles || AppRole.COACH in roles || AppRole.PARENT in roles)) {
                DashboardRow("Mensualidades", Icons.Default.DateRange, onClick = onFees)
            }
            if (privacyReady && AppRole.COACH in roles) DashboardRow("Vínculos con academias", Icons.Default.Shield, onClick = onAcademies)
            if (AppRole.PLAYER in roles || AppRole.PARENT in roles) {
                DashboardRow("Padres y tutores", Icons.Default.Groups, onClick = onGuardians)
            }
        }

        if (isAdmin) {
            DashboardSection("Administración") {
                DashboardRow("Moderar la comunidad", Icons.Default.AdminPanelSettings, onClick = onAdmin)
            }
        }

        DashboardSection("Preferencias") {
            DashboardRow("Editar roles", Icons.Default.Person, onClick = onRoles)
            if (privacyReady) DashboardRow("Solicitar eliminación de cuenta", Icons.Default.DeleteOutline, onClick = onRequestDeletion)
            DashboardRow("Tema oscuro", Icons.Default.DarkMode, if (darkTheme) "Activado" else "Desactivado", onTheme)
        }

        OutlinedButton(onClick = onLogout, enabled = !signingOut, modifier = Modifier.fillMaxWidth()) {
            Text(if (signingOut) "Cerrando sesión…" else "Cerrar sesión")
        }
    }
}

@Composable
internal fun SportAdminScreen(onBack: () -> Unit, onModeration: () -> Unit) {
    DashboardColumn {
        TextButton(onClick = onBack) { Text("← Volver") }
        Text("Administración", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        DashboardSection("Comunidad") {
            DashboardRow("Moderar publicaciones", Icons.Default.AdminPanelSettings, onClick = onModeration)
        }
    }
}

@Composable
private fun DashboardColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}

@Composable
private fun DashboardSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun DashboardRow(title: String, icon: ImageVector, subtitle: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
