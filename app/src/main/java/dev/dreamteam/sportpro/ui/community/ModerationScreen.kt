package dev.dreamteam.sportpro.ui.community

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import dev.dreamteam.sportpro.data.model.ModeratedContent
import dev.dreamteam.sportpro.data.model.ModerationReport
import dev.dreamteam.sportpro.data.model.ModerationRules
import dev.dreamteam.sportpro.data.repository.CommunityRepository
import dev.dreamteam.sportpro.data.repository.ModerationRepository
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private fun limaDate(timestamp: Timestamp?): String =
    timestamp?.let {
        SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("es", "PE"))
            .apply { timeZone = TimeZone.getTimeZone("America/Lima") }.format(it.toDate())
    } ?: "Fecha pendiente"

/**
 * US-21: pantalla de Moderación (solo moderador o admin; las reglas lo exigen igual). Reportes pendientes,
 * los más nuevos primero, con Ocultar/Restaurar y "Marcar como revisado"; y la sección "Ocultos" para
 * restaurar. Sin conexión las acciones se guardan y se ve "Pendiente de sincronizar". Solo colores del tema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationScreen(onBack: () -> Unit) {
    val repo = remember { ModerationRepository() }
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var reports by remember { mutableStateOf(emptyList<ModerationReport>()) }
    var hidden by remember { mutableStateOf(emptyList<ModeratedContent>()) }
    var contents by remember { mutableStateOf(emptyMap<String, ModeratedContent?>()) }
    var unsynced by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        CommunityRepository().checkModerator(onResult = { allowed = it }, onError = { allowed = false })
        onDispose { }
    }
    DisposableEffect(allowed) {
        if (allowed != true) onDispose { } else {
            val r = repo.observePendingReports(onUpdate = { reports = it }, onError = { error = it })
            val h = repo.observeHidden(onUpdate = { hidden = it }, onError = { error = it })
            onDispose { r.remove(); h.remove() }
        }
    }
    // Texto y estado (visible u oculto) del contenido de cada reporte, en vivo.
    val targets = reports.map { it.postId to it.commentId }.distinct()
    DisposableEffect(targets) {
        val listeners = targets.map { (postId, commentId) ->
            repo.observeContent(postId, commentId) { content ->
                contents = contents + (ModerationRules.contentKey(postId, commentId) to content)
            }
        }
        onDispose { listeners.forEach { it.remove() } }
    }

    // Cada acción: se guarda al instante; el contador indica lo que falta confirmar con el servidor.
    fun act(done: String, run: (onSaved: () -> Unit, onSynced: () -> Unit, onError: (String) -> Unit) -> Unit) {
        error = null
        unsynced++
        run({ notice = done }, { unsynced = (unsynced - 1).coerceAtLeast(0) },
            { unsynced = (unsynced - 1).coerceAtLeast(0); error = it })
    }
    fun toggle(postId: String, commentId: String?, hide: Boolean) =
        act(if (hide) "Contenido ocultado" else "Contenido restaurado") { s, y, e ->
            repo.setHidden(postId, commentId, hide, s, y, e)
        }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TextButton(onClick = onBack) { Text("← VOLVER A COMUNIDAD") }
        Text("MODERACIÓN", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        when (allowed) {
            null -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            false -> Text("No tienes permisos de moderación.", color = MaterialTheme.colorScheme.error)
            true -> {
                PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("PENDIENTES (${reports.size})") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("OCULTOS (${hidden.size})") })
                }
                if (unsynced > 0) Text("Pendiente de sincronizar", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    if (tab == 0) {
                        if (reports.isEmpty()) item { Empty("No hay reportes pendientes.") }
                        items(reports, key = { it.path }) { report ->
                            val content = contents[ModerationRules.contentKey(report.postId, report.commentId)]
                            ReportCard(report, content,
                                onToggle = { hide -> toggle(report.postId, report.commentId, hide) },
                                onReviewed = {
                                    act("Reporte marcado como revisado") { s, y, e -> repo.markReviewed(report.path, s, y, e) }
                                })
                        }
                    } else {
                        if (hidden.isEmpty()) item { Empty("No hay contenido oculto.") }
                        items(hidden, key = { it.key }) { content ->
                            HiddenCard(content, onRestore = { toggle(content.postId, content.commentId, false) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun ModerationCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun ReportCard(report: ModerationReport, content: ModeratedContent?,
                       onToggle: (hide: Boolean) -> Unit, onReviewed: () -> Unit) = ModerationCard {
    Text(ModerationRules.typeLabel(report.targetType).uppercase(), color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold, fontSize = 12.sp)
    Text(content?.text ?: "Contenido no disponible", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
    content?.let {
        Text("Autor: ${it.authorName}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        if (it.hidden) Text("Ocultado por moderación", color = MaterialTheme.colorScheme.error, fontSize = 11.sp,
            fontWeight = FontWeight.Bold)
    }
    Text("Motivo: ${report.reason}", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Text("Reportado por ${report.reporterName} · ${limaDate(report.createdAt)}",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    if (report.pending || content?.pending == true)
        Text("Pendiente de sincronizar", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (content != null) OutlinedButton(onClick = { onToggle(!content.hidden) }) {
            Text(ModerationRules.actionLabel(content.hidden))
        }
        TextButton(onClick = onReviewed) { Text("Marcar como revisado") }
    }
}

@Composable
private fun HiddenCard(content: ModeratedContent, onRestore: () -> Unit) = ModerationCard {
    Text(ModerationRules.typeLabel(content.targetType).uppercase(), color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold, fontSize = 12.sp)
    Text(content.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
    Text("Autor: ${content.authorName} · Ocultado ${limaDate(content.moderatedAt)}",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    if (content.pending) Text("Pendiente de sincronizar", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
    OutlinedButton(onClick = onRestore) { Text("Restaurar") }
}
