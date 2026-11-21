package dev.dreamteam.sportpro.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges

@Composable
internal fun AccountDeletionScreen(onBack: () -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val firestore = remember { FirebaseFirestore.getInstance() }
    val request = remember(uid) { firestore.collection("accountDeletionRequests").document(uid) }
    var submitted by remember(uid) { mutableStateOf<Boolean?>(null) }
    var confirm by remember(uid) { mutableStateOf(false) }
    var sending by remember(uid) { mutableStateOf(false) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    DisposableEffect(request) {
        val listener = request.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
            if (failure != null) {
                error = "No se pudo consultar la solicitud."
            } else if (snapshot?.metadata?.isFromCache == false && !snapshot.metadata.hasPendingWrites()) {
                submitted = snapshot.exists()
            }
        }
        onDispose { listener.remove() }
    }

    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Volver") }
        Text("Eliminar cuenta", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (submitted == true) "Recibimos tu solicitud. La cuenta sigue activa mientras SportPro la revisa."
            else "Puedes solicitar la eliminación de tu cuenta y sus datos asociados."
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (submitted == false) {
            Button(onClick = { confirm = true }, enabled = !sending) {
                Text(if (sending) "Enviando…" else "Solicitar eliminación")
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("¿Enviar solicitud de eliminación?") },
        text = { Text("SportPro revisará qué datos se deben eliminar y qué registros deben conservarse.") },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                sending = true
                error = null
                firestore.runTransaction { transaction ->
                    if (!transaction.get(request).exists()) {
                        transaction.set(request, mapOf("status" to "PENDING", "requestedAt" to FieldValue.serverTimestamp()))
                    }
                }.addOnSuccessListener { submitted = true }
                    .addOnFailureListener { error = "No se pudo enviar la solicitud. Intenta de nuevo." }
                    .addOnCompleteListener { sending = false }
            }) { Text("Enviar solicitud") }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } }
    )
}
