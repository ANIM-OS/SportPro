package dev.dreamteam.sportpro.ui.announcements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.Announcement
import dev.dreamteam.sportpro.data.model.AnnouncementAccess
import dev.dreamteam.sportpro.data.model.AnnouncementTeam
import dev.dreamteam.sportpro.data.model.Announcements
import dev.dreamteam.sportpro.data.model.GuardianLink
import dev.dreamteam.sportpro.data.repository.AnnouncementRepository
import dev.dreamteam.sportpro.data.repository.PlayerProfileRepository
import dev.dreamteam.sportpro.data.repository.TeamDirectoryRepository
import dev.dreamteam.sportpro.notifications.TeamTopics
import dev.dreamteam.sportpro.ui.auth.AppRole

/** Estado de Anuncios compartido por la barra inferior (contador) y la pantalla. */
data class AnnouncementFeed(
    /** Equipos propios (dueña, miembro, tutor): tienen tema FCM y contador. */
    val teams: List<AnnouncementTeam> = emptyList(),
    val byTeam: Map<String, List<Announcement>> = emptyMap(),
    val lastSeen: Map<String, Long?> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    val viewerId: String = ""
) {
    val unread: Int get() = Announcements.unreadCount(teams, byTeam, lastSeen, viewerId)
}

/**
 * US-17: equipos visibles del usuario y sus anuncios en tiempo real (un listener por equipo, filtrado por
 * teamId como exigen las reglas). Mantiene las suscripciones FCM `team_<teamId>` de esos equipos.
 * El tutor guarda antes su acceso (announcementGuardians) para cada equipo de su jugador vinculado.
 */
@Composable
fun rememberAnnouncementFeed(roles: Set<AppRole>): AnnouncementFeed {
    val context = LocalContext.current
    val repository = remember { AnnouncementRepository() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var owned by remember(uid) { mutableStateOf<List<AnnouncementTeam>>(emptyList()) }
    var member by remember(uid) { mutableStateOf<List<AnnouncementTeam>>(emptyList()) }
    var guardian by remember(uid) { mutableStateOf<List<AnnouncementTeam>>(emptyList()) }
    var links by remember(uid) { mutableStateOf<List<GuardianLink>>(emptyList()) }
    var byTeam by remember(uid) { mutableStateOf<Map<String, List<Announcement>>>(emptyMap()) }
    var errors by remember(uid) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var lastSeen by remember(uid) { mutableStateOf<Map<String, Long?>>(emptyMap()) }
    // Cada origen avisa cuando cargó: así no se dan de baja temas por un origen que aún no respondió.
    var ownedLoaded by remember(uid) { mutableStateOf(false) }
    var memberLoaded by remember(uid) { mutableStateOf(false) }
    val watchesChildren = AppRole.PARENT in roles && FirebaseAuth.getInstance().currentUser?.isEmailVerified == true
    var linksLoaded by remember(uid) { mutableStateOf(!watchesChildren) }
    val loaded = ownedLoaded && memberLoaded && linksLoaded
    if (uid.isBlank()) return AnnouncementFeed(loading = false)

    DisposableEffect(uid) {
        val registrations = mutableListOf<ListenerRegistration?>()
        // Dueña (aprobada o no: lee los anuncios de sus equipos; publicar exige aprobación).
        registrations += TeamDirectoryRepository().listenOwnedTeams({ teams ->
            owned = teams.filter { it.type == "EQUIPO" }.map { AnnouncementTeam(it.id, it.name, AnnouncementAccess.OWNER) }
            ownedLoaded = true
        }, { ownedLoaded = true })
        // Miembro: sus membresías; el nombre se lee del equipo (los miembros pueden).
        registrations += repository.listenMemberTeams(uid) { rows ->
            member = rows.map { (teamId, category) -> AnnouncementTeam(teamId, listOf("Equipo", category).filter(String::isNotBlank).joinToString(" · "), AnnouncementAccess.MEMBER) }
            memberLoaded = true
            rows.forEach { (teamId, _) ->
                repository.teamName(teamId).addOnSuccessListener { name ->
                    if (name != null) member = member.map { if (it.teamId == teamId) it.copy(name = name) else it }
                }
            }
        }
        if (watchesChildren) {
            registrations += PlayerProfileRepository().listenLinkedPlayers({ links = it; linksLoaded = true }, { links = emptyList(); linksLoaded = true })
        }
        onDispose { registrations.forEach { it?.remove() } }
    }

    // Tutor: equipos de cada jugador vinculado (el tutor no lee el nombre del equipo).
    val childIds = links.map { it.playerId }
    DisposableEffect(uid, childIds) {
        val perChild = mutableMapOf<String, List<AnnouncementTeam>>()
        val registrations = links.map { link ->
            repository.listenMemberTeams(link.playerId) { rows ->
                perChild[link.playerId] = rows.map { (teamId, category) ->
                    AnnouncementTeam(teamId, listOf("Equipo de ${link.playerName.ifBlank { "tu jugador" }}", category).filter(String::isNotBlank).joinToString(" · "),
                        AnnouncementAccess.GUARDIAN, childId = link.playerId)
                }
                guardian = perChild.values.flatten()
            }
        }
        if (links.isEmpty()) guardian = emptyList()
        onDispose { registrations.forEach { it.remove() } }
    }

    DisposableEffect(uid) {
        val registration = repository.listenReads { lastSeen = it }
        onDispose { registration.remove() }
    }

    val teams = Announcements.merge(owned, member, guardian)
    val keys = teams.map { "${it.teamId}|${it.access}|${it.childId}" }
    DisposableEffect(uid, keys) {
        var active = true
        val registrations = mutableListOf<ListenerRegistration>()
        fun listen(team: AnnouncementTeam) {
            if (!active) return
            registrations += repository.listenTeam(team.teamId,
                { list -> byTeam = byTeam + (team.teamId to list); errors = errors - team.teamId },
                { message -> errors = errors + (team.teamId to message) })
        }
        teams.forEach { team ->
            if (team.access == AnnouncementAccess.GUARDIAN) {
                // Primero el acceso del tutor; sin él las reglas no saben por qué jugador lee.
                repository.openAsGuardian(team.teamId, team.childId)
                    .addOnSuccessListener { listen(team) }
                    .addOnFailureListener { errors = errors + (team.teamId to "No se pudo abrir los anuncios de este equipo.") }
            } else listen(team)
        }
        byTeam = byTeam.filterKeys { id -> teams.any { it.teamId == id } }
        onDispose { active = false; registrations.forEach { it.remove() } }
    }

    // FCM: un tema por equipo visible; baja de los que ya no corresponden.
    val wanted = Announcements.wantedTopics(teams)
    LaunchedEffect(uid, wanted, loaded) { if (loaded) TeamTopics.sync(context, wanted) }

    return AnnouncementFeed(teams, byTeam, lastSeen, errors, loading = !loaded, viewerId = uid)
}
