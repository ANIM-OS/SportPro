package dev.dreamteam.sportpro.ui.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * US-19 · Comentarios y reacciones (andamio de navegación).
 * Se llega desde CommunityScreen → "Comentarios y reacciones".
 */
@Composable
fun PostCommentsScreen(
    postId: String,
    canComment: Boolean,
    onBack: () -> Unit
) {
    // TODO(US-19): leer comentarios de la publicación, respetar su visibilidad (TODOS/EQUIPO/ACADEMIA),
    // mostrar autor + fecha, guardar reacciones y permitir reportar/moderar comentarios.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Volver") }

        Text(
            text = "COMENTARIOS",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "US-19 · Publicación: $postId",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { /* TODO(US-19): reacción */ }) { Text("👍") }
            OutlinedButton(onClick = { /* TODO(US-19): reacción */ }) { Text("🔥") }
            OutlinedButton(onClick = { /* TODO(US-19): reacción */ }) { Text("👏") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Aún no hay comentarios.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (canComment) {
            Button(
                onClick = { /* TODO(US-19): abrir campo de comentario */ },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Escribir comentario") }
        }
    }
}
