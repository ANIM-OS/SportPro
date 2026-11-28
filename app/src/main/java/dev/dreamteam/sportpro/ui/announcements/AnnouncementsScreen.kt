package dev.dreamteam.sportpro.ui.announcements

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dreamteam.sportpro.data.model.Announcement
import dev.dreamteam.sportpro.data.model.AnnouncementAccess
import dev.dreamteam.sportpro.data.model.AnnouncementTeam
import dev.dreamteam.sportpro.data.model.AnnouncementType
import dev.dreamteam.sportpro.data.model.Announcements
import dev.dreamteam.sportpro.data.repository.AnnouncementRepository
import dev.dreamteam.sportpro.data.repository.announcementErrorMessage
import dev.dreamteam.sportpro.ui.callups.dateLabel

/**
 * US-17: anuncios internos de los equipos del usuario, en tiempo real y agrupados por equipo. Al abrir la
 * pestaña se marcan como vistos los equipos que se están mirando. Publica la dueña aprobada (sus equipos)
 * o el admin (elige un equipo). Solo usa colores del tema (claro y oscuro).
 */
@Composable
fun AnnouncementsScreen(feed: AnnouncementFeed, coachApproved: Boolean, isAdmin: Boolean) {
    val repository = remember { AnnouncementRepository() }
    var filter by remember { mutableStateOf<String?>(null) }
    if (filter != null && feed.teams.none { it.teamId == filter }) filter = null
    var message by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Announcement?>(null) }
    var composing by remember { mutableStateOf(false) }

    // Visto al abrir la pestaña (y de nuevo si llega algo mientras está abierta) para los equipos mirados.
    val viewed = feed.teams.filter { it.notifies && (filter == null || it.teamId == filter) }.map { it.teamId }
    val newest = viewed.associateWith { id -> feed.byTeam[id].orEmpty().maxOfOrNull { it.createdAtMillis ?: 0L } }
    LaunchedEffect(viewed, newest) { repository.markSeen(viewed) }

    // Admin: elige un equipo (de los que tienen partidos) para consultar o publicar.
    var adminTeams by remember { mutableStateOf<List<AnnouncementTeam>>(emptyList()) }
    var adminTeam by remember { mutableStateOf<String?>(null) }
    var adminList by remember { mutableStateOf<List<Announcement>>(emptyList()) }
    DisposableEffect(isAdmin) {
        val registration = if (isAdmin) repository.listenAdminTeams { rows ->
            adminTeams = rows.map { (id, category) -> AnnouncementTeam(id, listOf("Equipo", category, id.take(6)).filter(String::isNotBlank).joinToString(" · "), AnnouncementAccess.ADMIN) }
        } else null
        onDispose { registration?.remove() }
    }
    DisposableEffect(adminTeam) {
        adminList = emptyList()
        val registration = adminTeam?.let { id -> repository.listenTeam(id, { adminList = it }, { message = it }) }
        onDispose { registration?.remove() }
    }

    val publishable = (feed.teams.filter { Announcements.canPublish(it, coachApproved, isAdmin) } +
        if (isAdmin) adminTeams else emptyList()).distinctBy { it.teamId }

    toDelete?.let { item ->
        AlertDialog(onDismissRequest = { toDelete = null }, title = { Text("¿Borrar anuncio?") },
            text = { Text("«${item.title}» dejará de verse para todo el equipo.") },
            confirmButton = { TextButton(onClick = {
                repository.delete(item.id).addOnFailureListener { message = announcementErrorMessage(it) }
                toDelete = null
            }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Anuncios", style = MaterialTheme.typography.headlineSmall)
        Text("Avisos internos de tus equipos.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (publishable.isNotEmpty()) {
            if (composing) AnnouncementForm(publishable, repository, onDone = { composing = false; message = it })
            else Button(onClick = { composing = true }, modifier = Modifier.fillMaxWidth()) { Text("Nuevo anuncio") }
        }

        if (feed.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (feed.teams.size > 1) ChipRow(listOf("Todos") + feed.teams.map { it.name },
            selected = if (filter == null) 0 else feed.teams.indexOfFirst { it.teamId == filter } + 1) { i ->
            filter = if (i == 0) null else feed.teams[i - 1].teamId
        }
        if (!feed.loading && feed.teams.isEmpty() && !isAdmin) Text("Aún no perteneces a ningún equipo. Cuando formes parte de uno verás aquí sus anuncios.")
        Announcements.grouped(feed.teams, feed.byTeam, filter).forEach { (team, items) ->
            TeamSection(team, items, feed.errors[team.teamId], feed.viewerId, isAdmin, feed.lastSeen[team.teamId], onDelete = { toDelete = it })
        }

        if (isAdmin) {
            HorizontalDivider()
            Text("Administración", style = MaterialTheme.typography.titleMedium)
            Text("Elige un equipo para ver o publicar anuncios. No se muestran nombres de equipo.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (adminTeams.isEmpty()) Text("No hay equipos con partidos registrados.", style = MaterialTheme.typography.bodySmall)
            else ChipRow(adminTeams.map { it.name }, adminTeams.indexOfFirst { it.teamId == adminTeam }) { adminTeam = adminTeams[it].teamId }
            adminTeams.firstOrNull { it.teamId == adminTeam }?.let { team ->
                TeamSection(team, Announcements.sorted(adminList), null, feed.viewerId, isAdmin = true, lastSeen = null, onDelete = { toDelete = it })
            }
        }
    }
}

@Composable
private fun TeamSection(team: AnnouncementTeam, items: List<Announcement>, error: String?, viewerId: String, isAdmin: Boolean,
    lastSeen: Long?, onDelete: (Announcement) -> Unit) {
    Text(team.name, style = MaterialTheme.typography.titleMedium)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (items.isEmpty() && error == null) Text("Sin anuncios.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    items.forEach { item ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(item.type.label) })
                    if (team.notifies && Announcements.isUnread(item, viewerId, lastSeen))
                        Text("Nuevo", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
                Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(item.body, style = MaterialTheme.typography.bodyMedium)
                Text(item.createdAtMillis?.let { dateLabel(it) } ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.pending) Text("Pendiente de sincronizar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (Announcements.canDelete(item, viewerId, isAdmin)) TextButton(onClick = { onDelete(item) }) { Text("Borrar") }
            }
        }
    }
}

@Composable
private fun AnnouncementForm(teams: List<AnnouncementTeam>, repository: AnnouncementRepository, onDone: (String?) -> Unit) {
    var teamId by remember { mutableStateOf(teams.singleOrNull()?.teamId.orEmpty()) }
    var type by remember { mutableStateOf<AnnouncementType?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nuevo anuncio", style = MaterialTheme.typography.titleMedium)
            Text("Equipo", style = MaterialTheme.typography.labelLarge)
            ChipRow(teams.map { it.name }, teams.indexOfFirst { it.teamId == teamId }) { teamId = teams[it].teamId; error = null }
            Text("Tipo", style = MaterialTheme.typography.labelLarge)
            ChipRow(AnnouncementType.entries.map { it.label }, AnnouncementType.entries.indexOf(type)) { type = AnnouncementType.entries[it]; error = null }
            OutlinedTextField(title, { title = it.take(Announcements.MAX_TITLE); error = null }, label = { Text("Título") }, singleLine = true,
                supportingText = { Text("${title.length}/${Announcements.MAX_TITLE}") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it.take(Announcements.MAX_BODY); error = null }, label = { Text("Mensaje") }, minLines = 3,
                supportingText = { Text("${body.length}/${Announcements.MAX_BODY}") }, modifier = Modifier.fillMaxWidth())
            Text("Recuerda: el aviso push es genérico; los detalles solo se ven aquí.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onDone(null) }) { Text("Cancelar") }
                Button(onClick = {
                    error = Announcements.validate(type, title, body, teamId)
                    val chosen = type
                    if (error == null && chosen != null) {
                        // Se aplica al instante en la caché (también sin conexión: aparece "Pendiente de sincronizar").
                        repository.create(teamId, chosen, title, body).addOnFailureListener { onDone(announcementErrorMessage(it)) }
                        onDone(null)
                    }
                }) { Text("Publicar") }
            }
        }
    }
}

@Composable
private fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label -> FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(label) }) }
    }
}
