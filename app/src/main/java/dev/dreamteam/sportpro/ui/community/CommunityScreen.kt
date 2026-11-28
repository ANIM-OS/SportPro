package dev.dreamteam.sportpro.ui.community

import com.google.firebase.auth.FirebaseAuth
import dev.dreamteam.sportpro.data.model.CommunityRules
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dreamteam.sportpro.data.model.CommunityPost
import dev.dreamteam.sportpro.data.repository.CommunityRepository
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CommunityScreen(
    onCreatePostClick: () -> Unit,
    canCreatePost: Boolean,
    onOpenComments: (String) -> Unit = {},
    viewModel: CommunityViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Tras publicar, la lista conserva la publicación que se estaba viendo y la
    // nueva queda arriba, fuera de pantalla; se sube solo después de publicar.
    LaunchedEffect(uiState.scrollToTopRequested, uiState.posts.firstOrNull()?.id) {
        if (uiState.scrollToTopRequested && uiState.posts.isNotEmpty()) {
            listState.animateScrollToItem(0)
            viewModel.onScrolledToTop()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (canCreatePost) {
                FloatingActionButton(
                    onClick = onCreatePostClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Nueva publicación"
                    )
                }
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "COMUNIDAD",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Comparte y mantente conectado con SportPro",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            uiState.moderationError?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
            }

            when {

                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                uiState.errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Ocurrió un error",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                uiState.posts.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Aún no hay publicaciones",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Sé el primero en compartir algo",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.posts,
                            key = { it.id }
                        ) { post ->

                            CommunityPostCard(
                                post = post,
                                canModerate = uiState.canModerate,
                                isModerating = uiState.moderatingPostId == post.id,
                                onModerate = { viewModel.moderatePost(post.id) },
                                onOpenComments = { onOpenComments(post.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityPostCard(
    post: CommunityPost,
    canModerate: Boolean,
    isModerating: Boolean,
    onModerate: () -> Unit,
    onOpenComments: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.padding(6.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = post.authorName.ifBlank { "Usuario SportPro" },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    // US-18: solo nombre y rol legible del autor.
                    Text(
                        text = CommunityRules.roleLabel(post.authorRole),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = formatDate(post),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            val viewerId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            CommunityRules.hiddenNotice(post, viewerId)?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (post.pending) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Pendiente de sincronizar", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = post.content,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = when (post.visibility) {
                    "EQUIPO" -> "Visible para el equipo"
                    "ACADEMIA" -> "Visible para la academia"
                    else -> "Visible para todos"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )

            CommunityLikesCount(post.id)
            TextButton(onClick = onOpenComments) {
                Text("👍 Me gusta · Ver y escribir comentarios")
            }

            if (canModerate) {
                TextButton(onClick = onModerate, enabled = !isModerating) {
                    Text(if (isModerating) "Ocultando…" else "Ocultar publicación")
                }
            }
        }
    }
}

private fun formatDate(
    post: CommunityPost
): String {

    val timestamp = post.createdAt ?: return "Ahora"
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    return sdf.format(timestamp.toDate())
}


@Composable
private fun CommunityLikesCount(postId: String) {
    val repo = remember { CommunityRepository() }
    var count by remember(postId) { mutableIntStateOf(0) }
    var error by remember(postId) { mutableStateOf(false) }
    DisposableEffect(postId) {
        val listener = repo.observeLikes(postId,
            onUpdate = { total, _ -> count = total; error = false },
            onError = { error = true })
        onDispose { listener.remove() }
    }
    Text(
        text = if (error) "Reacciones no disponibles" else "👍 $count Me gusta",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp
    )
}
