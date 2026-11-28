package dev.dreamteam.sportpro.ui.community

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import dev.dreamteam.sportpro.data.model.CommunityPost
import dev.dreamteam.sportpro.data.model.CommunityRules
import dev.dreamteam.sportpro.data.repository.CommunityRepository
import java.text.SimpleDateFormat
import java.util.Locale

private fun date19(timestamp: com.google.firebase.Timestamp?): String =
    timestamp?.let { SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("es", "PE")).format(it.toDate()) }
        ?: "Fecha pendiente"

/**
 * US-19: comentarios y "Me gusta" de una publicación. Comentan y reaccionan el jugador y la entrenadora
 * aprobada ([canComment], mismo criterio que las reglas); el tutor ve "Solo lectura". El autor ve sus
 * comentarios ocultos con aviso y el autor de un post oculto ve sus comentarios. Sin conexión no se
 * bloquea: aparece "Pendiente de sincronizar". Solo colores del tema.
 */
@Composable
fun PostCommentsScreen(postId: String, canComment: Boolean, onBack: () -> Unit) {
    val repo = remember { CommunityRepository() }
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val signedIn = viewerId.isNotBlank()
    var post by remember(postId) { mutableStateOf<CommunityPost?>(null) }
    var loading by remember(postId) { mutableStateOf(true) }
    var error by remember(postId) { mutableStateOf<String?>(null) }
    var comments by remember(postId) { mutableStateOf(emptyList<CommunityRepository.PostComment>()) }
    var likes by remember(postId) { mutableIntStateOf(0) }
    var liked by remember(postId) { mutableStateOf(false) }
    var likePending by remember(postId) { mutableStateOf(false) }
    var input by remember(postId) { mutableStateOf("") }
    var reportId by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var canModerate by remember { mutableStateOf(false) }

    // Se obtiene desde las consultas de Comunidad (que ya incluyen las propias ocultas del autor).
    DisposableEffect(postId) {
        val listener = repo.observePosts(
            onUpdate = { visible ->
                post = visible.firstOrNull { it.id == postId }
                loading = false
                if (post == null) {
                    comments = emptyList()
                    likes = 0
                }
            },
            onError = { error = it; loading = false }
        )
        repo.checkModerator(onResult = { canModerate = it }, onError = { canModerate = false })
        onDispose { listener.remove() }
    }

    // No consultar comentarios ni likes mientras la publicación no esté visible para esta cuenta.
    DisposableEffect(postId, post?.id) {
        if (post == null) {
            onDispose { }
        } else {
            val c = repo.observeComments(postId, onUpdate = { comments = it }, onError = { error = it })
            val l = repo.observeLikes(postId,
                onUpdate = { count, mine, pending -> likes = count; liked = mine; likePending = pending },
                onError = { error = it })
            onDispose { c.remove(); l.remove() }
        }
    }

    val interact = signedIn && CommunityRules.canInteract(post, canComment)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TextButton(onClick = onBack) { Text("← VOLVER A COMUNIDAD") }
        Text("PUBLICACIÓN", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        when {
            loading -> CircularProgressIndicator()
            post == null -> Text(error ?: "Esta publicación no está disponible para tu cuenta.",
                color = MaterialTheme.colorScheme.error)
            else -> {
                val visiblePost = post!!
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Text(visiblePost.authorName.ifBlank { CommunityRules.DEFAULT_AUTHOR },
                                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                Text(CommunityRules.roleLabel(visiblePost.authorRole), fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary)
                                Text(date19(visiblePost.createdAt), fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                CommunityRules.hiddenNotice(visiblePost, viewerId)?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(visiblePost.content, color = MaterialTheme.colorScheme.onSurface)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (interact) {
                                        OutlinedButton(onClick = {
                                            error = null
                                            repo.setLiked(postId, !liked, onSuccess = { }, onError = { error = it })
                                        }) {
                                            Icon(Icons.Default.ThumbUp, contentDescription = if (liked) "Quitar Me gusta" else "Me gusta",
                                                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Me gusta · $likes")
                                        }
                                    } else {
                                        Text("👍 $likes Me gusta", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("${comments.size} comentarios", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (likePending) Text("Pendiente de sincronizar", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    item {
                        Text("COMENTARIOS (${comments.size})", color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    if (comments.isEmpty()) item {
                        Text("Aún no hay comentarios.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(comments, key = { it.id }) { comment ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(comment.authorName.ifBlank { CommunityRules.DEFAULT_AUTHOR },
                                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.sp)
                                        Text(date19(comment.createdAt),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                    }
                                    // Nunca en los propios ni en los ocultos.
                                    if (interact && CommunityRules.canReport(comment, viewerId, canComment)) {
                                        TextButton(onClick = { reportId = comment.id }) { Text("REPORTAR", fontSize = 11.sp) }
                                    }
                                }
                                CommunityRules.commentNotice(comment, viewerId)?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(comment.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                                if (comment.pending) Text("Pendiente de sincronizar", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                if (canModerate && !comment.moderated) TextButton(onClick = {
                                    repo.moderateComment(postId, comment.id,
                                        onSuccess = { notice = "Comentario ocultado" },
                                        onError = { error = it })
                                }) { Text("Ocultar comentario", fontSize = 11.sp) }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                if (interact) {
                    OutlinedTextField(value = input, onValueChange = { if (it.length <= CommunityRules.MAX_COMMENT) input = it },
                        label = { Text("Escribe un comentario...") }, modifier = Modifier.fillMaxWidth(),
                        minLines = 2, maxLines = 4,
                        supportingText = { Text("${input.length}/${CommunityRules.MAX_COMMENT}") })
                    Button(onClick = {
                        error = null
                        // Se guarda al instante en el celular (también sin conexión) y se envía al reconectar.
                        repo.addComment(postId, input, onSuccess = { input = "" }, onError = { error = it })
                    }, enabled = CommunityRules.validateComment(input) == null, modifier = Modifier.fillMaxWidth()) {
                        Text("COMENTAR")
                    }
                } else {
                    Text(
                        if (!canComment) "Solo lectura: tu rol puede ver los comentarios, pero no comentar ni reaccionar."
                        else "Esta publicación ya no admite comentarios ni reacciones.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    reportId?.let { id ->
        var reason by remember(id) { mutableStateOf(CommunityRules.REPORT_REASONS.first()) }
        AlertDialog(onDismissRequest = { reportId = null },
            title = { Text("Reportar comentario") },
            text = {
                Column {
                    Text("Elige el motivo. Solo lo verá el equipo de moderación.")
                    Spacer(Modifier.height(8.dp))
                    CommunityRules.REPORT_REASONS.forEach { option ->
                        Row(Modifier.fillMaxWidth().selectable(selected = reason == option, onClick = { reason = option })
                            .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = reason == option, onClick = { reason = option })
                            Text(option)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.reportComment(postId, id, reason,
                        onSuccess = { notice = "Reporte enviado" },
                        onError = { error = it })
                    reportId = null
                }) { Text("REPORTAR") }
            }, dismissButton = {
                TextButton(onClick = { reportId = null }) { Text("CANCELAR") }
            })
    }
}
