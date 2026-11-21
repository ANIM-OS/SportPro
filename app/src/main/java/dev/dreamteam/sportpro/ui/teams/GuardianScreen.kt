package dev.dreamteam.sportpro.ui.teams

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
fun GuardianScreen(
    isPlayer: Boolean,
    isGuardian: Boolean,
    privacyReady: Boolean,
    onBack: () -> Unit,
    onAttendance: (String) -> Unit,
    viewModel: GuardianViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(isPlayer, isGuardian) { viewModel.loadLinks(isPlayer, isGuardian) }
    val context = LocalContext.current
    val scanner = remember(context) {
        GmsBarcodeScanning.getClient(context,
            GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    var shownQr by remember { mutableStateOf<String?>(null) }
    var enterCode by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    LaunchedEffect(state.createdInviteId) {
        state.createdInviteId?.let {
            shownQr = it
            viewModel.consumeCreatedInvite()
        }
    }
    var confirmInvite by remember { mutableStateOf<GuardianInvite?>(null) }
    var revokeLink by remember { mutableStateOf<GuardianLink?>(null) }
    var contactPlayerId by remember { mutableStateOf<String?>(null) }
    contactPlayerId?.takeIf { id -> isGuardian && state.links.any { it.guardianId == state.uid && it.playerId == id } }
        ?.let { PlayerContactDialog(it, onDismiss = { contactPlayerId = null }) }

    state.scanned?.takeIf { isGuardian }?.let { invite ->
        AlertDialog(onDismissRequest = viewModel::dismissScanned,
            title = { Text("Invitación de ${invite.playerName}") },
            text = { Text("Al aceptar, el jugador deberá confirmar tu cuenta antes de compartir información contigo.") },
            confirmButton = { TextButton(enabled = !state.busy, onClick = { viewModel.accept(invite) }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissScanned) { Text("Cancelar") } })
    }
    shownQr?.takeIf { isPlayer }?.let { inviteId ->
        AlertDialog(
            onDismissRequest = { shownQr = null },
            title = { Text("Invitación para tu tutor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QrImage("sportpro://guardian/$inviteId")
                    Text("Comparte esta invitación solo con la persona que deseas vincular.")
                }
            },
            confirmButton = { TextButton(onClick = { shownQr = null }) { Text("Cerrar") } },
            dismissButton = {
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Invitación de tutor", inviteId))
                }) { Text("Copiar código") }
            }
        )
    }
    if (isGuardian && enterCode) {
        AlertDialog(
            onDismissRequest = { enterCode = false },
            title = { Text("Ingresar código") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Código de invitación") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(enabled = code.isNotBlank(), onClick = {
                    enterCode = false
                    viewModel.scan(code)
                }) { Text("Continuar") }
            },
            dismissButton = { TextButton(onClick = { enterCode = false }) { Text("Cancelar") } }
        )
    }
    confirmInvite?.takeIf { isPlayer }?.let { invite ->
        AlertDialog(onDismissRequest = { confirmInvite = null }, title = { Text("Confirmar tutor") },
            text = { Text("¿Reconoces a ${invite.guardianName}? Al confirmar, esta cuenta podrá consultar los datos y avisos autorizados.") },
            confirmButton = { TextButton(enabled = !state.busy, onClick = { viewModel.confirm(invite); confirmInvite = null }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { confirmInvite = null }) { Text("Volver") } })
    }
    revokeLink?.let { link ->
        AlertDialog(onDismissRequest = { revokeLink = null }, title = { Text("Finalizar vínculo") },
            text = { Text("La cuenta del tutor perderá el acceso a la información compartida.") },
            confirmButton = { TextButton(enabled = !state.busy, onClick = { viewModel.revoke(link); revokeLink = null }) { Text("Finalizar vínculo") } },
            dismissButton = { TextButton(onClick = { revokeLink = null }) { Text("Volver") } })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Volver") }
        Text(
            when {
                isPlayer && !isGuardian -> "Mis tutores"
                isGuardian && !isPlayer -> "Jugadores vinculados"
                else -> "Padres y tutores"
            },
            style = MaterialTheme.typography.headlineSmall
        )
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.loadError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { viewModel.loadLinks(isPlayer, isGuardian) }) { Text("Reintentar") }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (isPlayer) {
            if (isGuardian) Text("Como jugador", style = MaterialTheme.typography.titleMedium)
            Text("Puedes compartir con tu tutor los datos y avisos autorizados. Solo se activa cuando confirmas su cuenta.")
            Button(enabled = !state.busy, onClick = viewModel::createInvite) { Text("Generar invitación QR") }
            val invitations = state.invites.filter { it.playerId == state.uid && it.status in setOf("OPEN", "CLAIMED") }
            invitations.forEach { invite ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        if (invite.status == "OPEN") {
                            Text("Esperando al tutor")
                            TextButton(onClick = { shownQr = invite.id }) { Text("Mostrar QR") }
                        } else {
                            Text("${invite.guardianName} espera tu confirmación")
                            TextButton(enabled = !state.busy, onClick = { confirmInvite = invite }) { Text("Revisar y confirmar") }
                        }
                        TextButton(enabled = !state.busy, onClick = { viewModel.cancel(invite) }) { Text("Cancelar invitación") }
                    }
                }
            }
        }
        if (isGuardian) {
            if (isPlayer) Text("Como padre o tutor", style = MaterialTheme.typography.titleMedium)
            Text("Escanea el QR que te muestra el jugador.")
            OutlinedButton(enabled = !state.busy, onClick = {
                scanner.startScan()
                    .addOnSuccessListener { result -> result.rawValue?.let(viewModel::scan) }
                    .addOnFailureListener { viewModel.reportScannerError() }
            }) { Text("Escanear QR de tutor") }
            TextButton(enabled = !state.busy, onClick = { code = ""; enterCode = true }) { Text("Ingresar código") }
            state.invites.filter { it.guardianId == state.uid && it.status == "CLAIMED" }.forEach { invite ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Esperando confirmación de ${invite.playerName}")
                        TextButton(enabled = !state.busy, onClick = { viewModel.cancel(invite) }) { Text("Cancelar") }
                    }
                }
            }
        }
        Text("Vínculos activos", style = MaterialTheme.typography.titleMedium)
        val visibleLinks = state.links.filter {
            (isPlayer && it.playerId == state.uid) || (isGuardian && it.guardianId == state.uid)
        }
        if (visibleLinks.isEmpty() && state.loadError == null) Text("No tienes vínculos activos.")
        visibleLinks.forEach { link ->
            val invite = state.invites.firstOrNull { it.id == link.inviteId }
            val label = if (link.playerId == state.uid) invite?.guardianName else invite?.playerName
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(label?.takeIf { it.isNotBlank() } ?: "Cuenta vinculada")
                    if (isGuardian && link.guardianId == state.uid) {
                        TextButton(onClick = { onAttendance(link.playerId) }) { Text("Ver asistencia") }
                        if (privacyReady) TextButton(onClick = { contactPlayerId = link.playerId }) { Text("Ver contacto") }
                    }
                    TextButton(enabled = !state.busy, onClick = { revokeLink = link }) { Text("Finalizar vínculo") }
                }
            }
        }
    }
}
