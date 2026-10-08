package dev.dreamteam.sportpro.ui.teams

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.model.TeamInvite
import dev.dreamteam.sportpro.domain.access.TeamAccess
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
fun TeamsScreen(
    onCreateTeamClick: () -> Unit,
    canCreateTeam: Boolean,
    canJoinWithCode: Boolean,
    coachNeedsApproval: Boolean = false,
    newlyCreatedTeamId: String? = null,
    onNewTeamOpened: () -> Unit = {},
    viewModel: TeamsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inviteTeam by remember { mutableStateOf<Team?>(null) }
    var invitedEmail by remember { mutableStateOf("") }
    var editingTeam by remember { mutableStateOf<Team?>(null) }
    var deletingTeam by remember { mutableStateOf<Team?>(null) }
    var photoTeam by remember { mutableStateOf<Team?>(null) }
    var selectedTeamId by remember { mutableStateOf<String?>(null) }
    var returnToTeamId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scanner = remember(context) {
        GmsBarcodeScanning.getClient(context,
            GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val team = photoTeam
        if (uri != null && team != null) {
            try {
                val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flag)
            } catch (_: Exception) {}
            viewModel.uploadTeamPhoto(context, team, uri)
        }
        photoTeam = null
    }

    uiState.pendingJoinCode?.let { code ->
        var playerName by remember(code.id) { mutableStateOf("") }
        var category by remember(code.id) { mutableStateOf(code.categories.firstOrNull().orEmpty()) }
        AlertDialog(onDismissRequest = { if (!uiState.managementBusy) viewModel.clearPendingJoinCode() },
            title = { Text("Unirse a ${code.teamName}") },
            text = {
                Column {
                    Text("El QR se usa una sola vez. Confirma tus datos para registrarte.")
                    OutlinedTextField(playerName, { playerName = it.take(80) }, label = { Text("Tu nombre") }, singleLine = true)
                    code.categories.forEach { item ->
                        FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                    }
                    uiState.managementError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !uiState.managementBusy && playerName.isNotBlank() && category.isNotBlank(),
                    onClick = { viewModel.joinWithCode(code, playerName, category) }) { Text("Registrarme") }
            },
            dismissButton = { TextButton(onClick = viewModel::clearPendingJoinCode, enabled = !uiState.managementBusy) { Text("Cancelar") } })
    }

    val selectedTeam = selectedTeamId?.let { id -> uiState.teams.firstOrNull { it.id == id } }

    LaunchedEffect(newlyCreatedTeamId, uiState.teams) {
        val createdTeam = newlyCreatedTeamId?.let { id -> uiState.teams.firstOrNull { it.id == id } }
        if (createdTeam != null) {
            selectedTeamId = createdTeam.id
            onNewTeamOpened()
        }
    }

    editingTeam?.let { team ->
        TeamDetailsDialog(
            team = team,
            saving = uiState.isSavingTeam,
            error = uiState.teamActionError,
            onDismiss = { if (!uiState.isSavingTeam) editingTeam = null },
            onSave = { details -> viewModel.saveTeamDetails(team, details) { editingTeam = null } }
        )
    }

    deletingTeam?.let { team ->
        AlertDialog(
            onDismissRequest = { if (uiState.deletingTeamId == null) deletingTeam = null },
            title = { Text("Eliminar ${team.name}") },
            text = {
                Column {
                    Text("Se eliminarán el equipo, su foto, invitaciones y membresías. Esta acción no se puede deshacer.")
                    uiState.teamActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = uiState.deletingTeamId == null,
                    onClick = { viewModel.deleteTeam(team) { deletingTeam = null } }
                ) { Text(if (uiState.deletingTeamId == team.id) "Eliminando…" else "Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingTeam = null }, enabled = uiState.deletingTeamId == null) { Text("Cancelar") } }
        )
    }

    inviteTeam?.let { team ->
        AlertDialog(
            onDismissRequest = { if (!uiState.isInviting) inviteTeam = null },
            title = { Text("Invitar a ${team.name}") },
            text = {
                Column {
                    Text("Usa el correo exacto con el que el jugador se registró.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = invitedEmail,
                        onValueChange = { invitedEmail = it },
                        label = { Text("Correo del jugador") },
                        singleLine = true
                    )
                    uiState.inviteActionError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = invitedEmail.isNotBlank() && !uiState.isInviting,
                    onClick = {
                        viewModel.invitePlayer(team, invitedEmail) {
                            inviteTeam = null
                            invitedEmail = ""
                        }
                    }
                ) { Text(if (uiState.isInviting) "Enviando…" else "Enviar") }
            },
            dismissButton = {
                TextButton(onClick = { inviteTeam = null }, enabled = !uiState.isInviting) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (selectedTeam != null) {
        TeamManagementScreen(selectedTeam, uiState.currentUserId, viewModel,
            onBack = {
                selectedTeamId = returnToTeamId
                returnToTeamId = null
            },
            onEmailInvite = { viewModel.clearInviteError(); invitedEmail = ""; inviteTeam = selectedTeam },
            onOpenTeam = {
                returnToTeamId = selectedTeam.id
                selectedTeamId = it.id
            })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (canCreateTeam) "MIS EQUIPOS Y ACADEMIAS" else "EQUIPOS",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            if (canJoinWithCode) {
                TextButton(onClick = {
                    scanner.startScan()
                        .addOnSuccessListener { barcode -> viewModel.loadJoinCode(barcode.rawValue.orEmpty()) }
                        .addOnFailureListener { error -> viewModel.showManagementError(error.localizedMessage ?: "No se pudo abrir el escáner") }
                }) { Text("Escanear QR") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!canCreateTeam && !uiState.emailVerified) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Verifica tu correo para recibir invitaciones a equipos.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = viewModel::sendVerificationEmail,
                            enabled = !uiState.isSendingVerification
                        ) { Text("Enviar enlace") }
                        TextButton(onClick = viewModel::refreshEmailVerification) {
                            Text("Ya verifiqué")
                        }
                    }
                    uiState.verificationMessage?.let { Text(it) }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (coachNeedsApproval) {
            Text("Para crear equipos, la administración debe aprobar tu perfil de entrenador.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
        }

        uiState.invitesError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        uiState.managementError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (editingTeam == null) uiState.teamActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (inviteTeam == null) {
            uiState.inviteActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState.teamsError != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = uiState.teamsError ?: "No se pudieron cargar los equipos",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = viewModel::loadTeams) {
                    Text("Reintentar")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!canCreateTeam && uiState.pendingInvites.isNotEmpty()) {
                    item { Text("INVITACIONES PENDIENTES", fontWeight = FontWeight.Bold) }
                    items(uiState.pendingInvites, key = { "invite_${it.id}" }) { invite ->
                        TeamInviteCard(
                            invite = invite,
                            alreadyMember = uiState.teams.any { it.id == invite.teamId },
                            processing = uiState.processingInviteId == invite.id,
                            onAccept = { viewModel.acceptInvite(invite) },
                            onDecline = { viewModel.declineInvite(invite) }
                        )
                    }
                }

                items(uiState.teams, key = { "team_${it.id}" }) { team ->
                    TeamCard(
                        team = team,
                        canInvite = TeamAccess.canManage(team, uiState.currentUserId),
                        onInvite = {
                            viewModel.clearInviteError()
                            invitedEmail = ""
                            inviteTeam = team
                        },
                        onEdit = { viewModel.clearTeamError(); editingTeam = team },
                        onDelete = { viewModel.clearTeamError(); deletingTeam = team },
                        onPhoto = { photoTeam = team; photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        isUploadingPhoto = uiState.isUploadingPhoto,
                        isDeleting = uiState.deletingTeamId == team.id,
                        onManage = { selectedTeamId = team.id }
                    )
                }

                if (uiState.teams.isEmpty()) {
                    item {
                        if (canCreateTeam) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Aún no has creado ningún equipo o academia.",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Crea tu primer equipo para organizar categorías, plantillas y actividades.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = onCreateTeamClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(52.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Crear Equipo o Academia", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        } else {
                            Text(
                                text = "Aún no tienes equipos. Puedes aceptar una invitación para unirte a uno.",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (canCreateTeam && uiState.teams.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onCreateTeamClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Crear Equipo o Academia",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TeamCard(
    team: Team,
    canInvite: Boolean,
    onInvite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPhoto: () -> Unit,
    isUploadingPhoto: Boolean,
    isDeleting: Boolean,
    onManage: () -> Unit
) {
    val isAcademy = team.type == "ACADEMIA"
    val badgeColor = if (isAcademy) Color(0xFFFFCC00) else MaterialTheme.colorScheme.primary
    val textColor = if (isAcademy) Color.Black else Color.White

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Left side: Square Crest / Logo
                TeamPhoto(team, onPhoto, canInvite && !isUploadingPhoto && !isDeleting)

                // Right side: Name, Type, City, Description
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = team.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor
                        ) {
                            Text(
                                text = team.type,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = textColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (team.city.isNotBlank()) {
                        Text(
                            text = "📍 ${team.city}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }

                    if (team.description.isNotBlank()) {
                        Text(
                            text = team.description,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
                }
            }

            // Categories pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                team.categories.forEach { category ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = category,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Button(
                onClick = onManage,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Plantilla y alineación", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
            }

            if (canInvite) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onInvite, contentPadding = PaddingValues(0.dp)) { Text("Invitar", fontSize = 12.sp) }
                    TextButton(onClick = onPhoto, enabled = !isUploadingPhoto && !isDeleting, contentPadding = PaddingValues(0.dp)) {
                        Text(if (isUploadingPhoto) "Subiendo…" else "Cambiar foto", fontSize = 12.sp)
                    }
                    TextButton(onClick = onEdit, enabled = !isDeleting, contentPadding = PaddingValues(0.dp)) { Text("Editar", fontSize = 12.sp) }
                    TextButton(onClick = onDelete, enabled = !isDeleting, contentPadding = PaddingValues(0.dp)) { Text("Eliminar", color = Color(0xFFFF5252), fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun TeamPhoto(team: Team, onPhoto: (() -> Unit)? = null, canClick: Boolean = false) {
    val image = remember(team.photoBase64) {
        if (team.photoBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(team.photoBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (canClick && onPhoto != null) Modifier.clickable { onPhoto() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Logo de ${team.name}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = team.name.take(2).uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TeamDetailsDialog(
    team: Team,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (TeamDetails) -> Unit
) {
    var name by remember(team.id) { mutableStateOf(team.name) }
    var city by remember(team.id) { mutableStateOf(team.city) }
    var description by remember(team.id) { mutableStateOf(team.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar ${team.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nombre") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(city, { city = it.take(80) }, label = { Text("Ciudad") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(description, { description = it.take(500) }, label = { Text("Descripción") }, minLines = 2, shape = RoundedCornerShape(12.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && name.isNotBlank(),
                onClick = { onSave(TeamDetails(name, city, description)) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text(if (saving) "Guardando…" else "Guardar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss, enabled = !saving, shape = RoundedCornerShape(10.dp)) { Text("Cancelar") } },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun TeamInviteCard(
    invite: TeamInvite,
    alreadyMember: Boolean,
    processing: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = " NOTIFICACIÓN ",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Invitación de Entrenador",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
            }
            Text(
                text = "ENTRENADOR te invitó a participar en el EQUIPO / DIVISIÓN: ${invite.teamName}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (alreadyMember) {
                Text(
                    text = "Ya participas en este equipo",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAccept,
                    enabled = !processing && !alreadyMember,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Aceptar", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onDecline,
                    enabled = !processing,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("Rechazar", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
