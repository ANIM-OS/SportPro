package dev.dreamteam.sportpro.ui.community

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import dev.dreamteam.sportpro.data.model.CommunityPost
import dev.dreamteam.sportpro.data.repository.CommunityRepository
import java.text.SimpleDateFormat
import java.util.Locale

private val Orange19 = Color(0xFFFF6600)

private fun date19(timestamp: com.google.firebase.Timestamp?): String =
    timestamp?.let { SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("es", "PE")).format(it.toDate()) }
        ?: "Fecha pendiente"

@Composable
fun PostCommentsScreen(postId: String, canComment: Boolean, onBack: () -> Unit) {
    val repo = remember { CommunityRepository() }
    val signedIn = FirebaseAuth.getInstance().currentUser != null
    var post by remember(postId) { mutableStateOf<CommunityPost?>(null) }
    var loading by remember(postId) { mutableStateOf(true) }
    var error by remember(postId) { mutableStateOf<String?>(null) }
    var comments by remember(postId) { mutableStateOf(emptyList<CommunityRepository.PostComment>()) }
    var likes by remember(postId) { mutableIntStateOf(0) }
    var liked by remember(postId) { mutableStateOf(false) }
    var input by remember(postId) { mutableStateOf("") }
    var sending by remember(postId) { mutableStateOf(false) }
    var reacting by remember(postId) { mutableStateOf(false) }
    var reportId by remember { mutableStateOf<String?>(null) }
    var reportSending by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var canModerate by remember { mutableStateOf(false) }

    // Se obtiene desde las consultas filtradas de Comunidad, no por lectura directa
    // del post. Firestore Rules deben imponer idéntica restricción en el servidor.
    DisposableEffect(postId) {
        val listener = repo.observePosts(
            onUpdate = { visible ->
                post = visible.firstOrNull { it.id == postId && it.active && !it.moderated }
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

    // No consultar comentarios ni likes mientras la publicación no esté visible.
    DisposableEffect(postId, post?.id) {
        val visible = post != null
        if (!visible) {
            onDispose { }
        } else {
            val c = repo.observeComments(postId,
                onUpdate = { comments = it }, onError = { error = it })
            val l = repo.observeLikes(postId,
                onUpdate = { count, mine -> likes = count; liked = mine },
                onError = { error = it })
            onDispose { c.remove(); l.remove() }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TextButton(onClick = onBack) { Text("← VOLVER A COMUNIDAD", color = Orange19) }
        Text("PUBLICACIÓN", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        when {
            loading -> CircularProgressIndicator(color = Orange19)
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
                                Text(visiblePost.authorName.ifBlank { "Usuario SportPro" },
                                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                Text(date19(visiblePost.createdAt), fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(visiblePost.content, color = MaterialTheme.colorScheme.onSurface)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(onClick = {
                                        if (!reacting && signedIn && canComment) {
                                            reacting = true; error = null
                                            repo.setLiked(postId, !liked,
                                                onSuccess = { reacting = false },
                                                onError = { reacting = false; error = it })
                                        }
                                    }, enabled = signedIn && canComment && !reacting) {
                                        Icon(Icons.Default.ThumbUp, contentDescription = null,
                                            tint = if (liked) Orange19 else MaterialTheme.colorScheme.onSurface)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Me gusta · $likes")
                                    }
                                    Text("${comments.size} comentarios", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 14.dp))
                                }
                            }
                        }
                    }
                    item {
                        Text("COMENTARIOS (${comments.size})", color = Orange19,
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
                                        Text(comment.authorName.ifBlank { "Usuario SportPro" },
                                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.sp)
                                        Text(date19(comment.createdAt),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                    }
                                    if (signedIn && canComment) {
                                        TextButton(onClick = { reportId = comment.id }) {
                                            Text("REPORTAR", color = Orange19, fontSize = 11.sp)
                                        }
                                    }
                                }
                                Text(comment.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                                if (canModerate) TextButton(onClick = {
                                    repo.moderateComment(postId, comment.id,
                                        onSuccess = { notice = "Comentario ocultado" },
                                        onError = { error = it })
                                }) { Text("Ocultar comentario", color = Orange19, fontSize = 11.sp) }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                notice?.let { Text(it, color = Orange19, fontSize = 12.sp) }
                if (canComment && signedIn) {
                    OutlinedTextField(value = input, onValueChange = { if (it.length <= 300) input = it },
                        label = { Text("Escribe un comentario...") }, modifier = Modifier.fillMaxWidth(),
                        minLines = 2, maxLines = 4,
                        supportingText = { Text("${input.length}/300") })
                    Button(onClick = {
                        if (!sending) {
                            sending = true; error = null
                            repo.addComment(postId, input,
                                onSuccess = { input = ""; sending = false },
                                onError = { error = it; sending = false })
                        }
                    }, enabled = !sending && input.trim().isNotEmpty() && input.length <= 300,
                        colors = ButtonDefaults.buttonColors(containerColor = Orange19),
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (sending) "PUBLICANDO…" else "COMENTAR", color = Color.White)
                    }
                } else {
                    Text("No tienes permiso para comentar o reaccionar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    reportId?.let { id ->
        AlertDialog(onDismissRequest = { if (!reportSending) reportId = null },
            title = { Text("Reportar comentario") },
            text = { Text("¿Deseas enviar este comentario para revisión por moderación?") },
            confirmButton = {
                TextButton(enabled = !reportSending, onClick = {
                    reportSending = true
                    repo.reportComment(postId, id, "Contenido inapropiado",
                        onSuccess = { reportSending = false; reportId = null; notice = "Reporte enviado" },
                        onError = { reportSending = false; reportId = null; error = it })
                }) { Text("REPORTAR", color = Orange19) }
            }, dismissButton = {
                TextButton(onClick = { reportId = null }) { Text("CANCELAR") }
            })
    }
}
