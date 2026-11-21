package dev.dreamteam.sportpro.ui.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import dev.dreamteam.sportpro.data.repository.PlayerProfileRepository

@Composable
internal fun PlayerContactDialog(playerId: String, teamId: String? = null, onDismiss: () -> Unit) {
    val repository = remember { PlayerProfileRepository() }
    var contact by remember(playerId, teamId) { mutableStateOf<List<String>?>(null) }
    var error by remember(playerId, teamId) { mutableStateOf<String?>(null) }
    DisposableEffect(playerId, teamId) {
        var open = true
        repository.loadContact(playerId, teamId, { if (open) contact = it }, { if (open) error = it })
        onDispose { open = false }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contacto autorizado") },
        text = {
            val fields = contact
            when {
                error != null -> Text(error.orEmpty())
                fields == null -> CircularProgressIndicator()
                fields.isEmpty() -> Text("El jugador aún no ha compartido un contacto.")
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fields.forEach { Text(it) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
