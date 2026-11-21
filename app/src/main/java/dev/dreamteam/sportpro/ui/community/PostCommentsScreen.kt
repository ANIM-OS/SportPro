package dev.dreamteam.sportpro.ui.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query

private data class Comment(val id: String, val author: String, val authorId: String, val body: String)

@Composable
fun PostCommentsScreen(postId: String, canComment: Boolean, onBack: () -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser ?: return
    val post = remember(postId) { FirebaseFirestore.getInstance().collection("communityPosts").document(postId) }
    val commentsRef = post.collection("comments")
    val reactionRef = post.collection("reactions").document(user.uid)
    var comments by remember(postId) { mutableStateOf<List<Comment>?>(null) }
    var reaction by remember(postId) { mutableStateOf<String?>(null) }
    var text by rememberSaveable(postId) { mutableStateOf("") }
    var error by remember(postId) { mutableStateOf<String?>(null) }
    var saving by remember(postId) { mutableStateOf(false) }

    DisposableEffect(postId, user.uid) {
        val commentsListener = commentsRef.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
                if (failure != null) {
                    comments = null
                    error = "No se pudieron cargar los comentarios."
                } else if (snapshot?.metadata?.isFromCache == false) {
                    error = null
                    comments = snapshot.documents.mapNotNull { doc ->
                        val authorId = doc.getString("authorId") ?: return@mapNotNull null
                        val body = doc.getString("body") ?: return@mapNotNull null
                        Comment(doc.id, doc.getString("authorName") ?: "Usuario", authorId, body)
                    }
                }
            }
        val reactionListener = reactionRef.addSnapshotListener { snapshot, failure ->
            reaction = if (failure == null) snapshot?.getString("value") else null
        }
        onDispose { commentsListener.remove(); reactionListener.remove() }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("Comentarios y reacciones", style = MaterialTheme.typography.headlineSmall)
        }
        if (canComment && comments != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("👍", "🔥", "👏").forEach { value ->
                        FilterChip(selected = reaction == value, label = { Text(value) }, onClick = {
                            val change = if (reaction == value) reactionRef.delete()
                                else reactionRef.set(mapOf("value" to value))
                            change.addOnFailureListener { error = "No se pudo guardar tu reacción." }
                        })
                    }
                }
                OutlinedTextField(value = text, onValueChange = { text = it.take(500) },
                    label = { Text("Escribe un comentario") }, modifier = Modifier.fillMaxWidth())
                Button(enabled = text.isNotBlank() && !saving, onClick = {
                    saving = true
                    error = null
                    commentsRef.add(mapOf(
                        "authorId" to user.uid,
                        "authorName" to (user.displayName?.takeIf { it.isNotBlank() } ?: "Usuario").take(100),
                        "body" to text.trim(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )).addOnSuccessListener { text = "" }
                        .addOnFailureListener { error = "No se pudo publicar el comentario." }
                        .addOnCompleteListener { saving = false }
                }) { Text(if (saving) "Enviando…" else "Publicar") }
            }
        }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (comments == null && error == null) item { CircularProgressIndicator() }
        if (comments?.isEmpty() == true) item { Text("Todavía no hay comentarios.") }
        items(comments.orEmpty(), key = { it.id }) { comment ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(comment.author, style = MaterialTheme.typography.titleSmall)
                    Text(comment.body)
                    if (comment.authorId == user.uid) TextButton(onClick = {
                        commentsRef.document(comment.id).delete()
                            .addOnFailureListener { error = "No se pudo eliminar tu comentario." }
                    }) { Text("Eliminar mi comentario") }
                }
            }
        }
    }
}
