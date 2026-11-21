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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.ui.unit.Dp
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
    isGuardian: Boolean,
    isCoach: Boolean,
    privacyReady: Boolean,
    onGuardians: () -> Unit,
    newlyCreatedTeamId: String? = null,
    onNewTeamOpened: () -> Unit = {},
    viewModel: TeamsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inviteTeam by remember { mutableStateOf<Team?>(null) }
    var invitedEmail by remember { mutableStateOf("") }
    var editingTeam by remember { mutableStateOf<Team?>(null) }
    var archivingTeam by remember { mutableStateOf<Team?>(null) }
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
                    Text("Envía tus datos. El entrenador revisará tu solicitud.")
                    OutlinedTextField(playerName, { playerName = it.take(80) }, label = { Text("Tu nombre") }, singleLine = true)
                    code.categories.forEach { item ->
                        FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                    }
                    uiState.managementError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !uiState.managementBusy && playerName.isNotBlank() && category.isNotBlank(),
                    onClick = { viewModel.joinWithCode(code, playerName, category) }) { Text("Enviar solicitud") }
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

    archivingTeam?.let { team ->
        AlertDialog(
            onDismissRequest = { if (uiState.archivingTeamId == null) archivingTeam = null },
            title = { Text(if (team.archived) "Reactivar ${team.name}" else "Archivar ${team.name}") },
            text = {
                Column {
                    Text(if (team.archived) "El equipo volverá a aceptar solicitudes y nuevas actividades." else "Se suspenderá el ingreso de jugadores y se conservarán la plantilla y el historial.")
                    uiState.teamActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = uiState.archivingTeamId == null,
                    onClick = { viewModel.setTeamArchived(team, !team.archived) { archivingTeam = null } }
                ) { Text(if (uiState.archivingTeamId == team.id) "Guardando…" else if (team.archived) "Reactivar" else "Archivar") }
            },
            dismissButton = { TextButton(onClick = { archivingTeam = null }, enabled = uiState.archivingTeamId == null) { Text("Cancelar") } }
        )
    }

    inviteTeam?.let { team ->
        var inviteCategory by remember(team.id) { mutableStateOf(team.categories.singleOrNull().orEmpty()) }
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
                    team.categories.forEach { category ->
                        FilterChip(selected = inviteCategory == category, onClick = { inviteCategory = category }, label = { Text(category) })
                    }
                    uiState.inviteActionError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = invitedEmail.isNotBlank() && inviteCategory.isNotBlank() && !uiState.isInviting,
                    onClick = {
                        viewModel.invitePlayer(team, invitedEmail, inviteCategory) {
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
            canUseCoachTools = canCreateTeam,
            canManageOwnTeams = isCoach,
            privacyReady = privacyReady,
            canLeaveTeam = privacyReady && canJoinWithCode && !TeamAccess.canManage(selectedTeam, uiState.currentUserId),
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    canCreateTeam -> "Equipos y academias"
                    canJoinWithCode -> "Mis equipos"
                    else -> "Equipos"
                },
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (canJoinWithCode) {
                FilledTonalButton(
                    onClick = {
                        scanner.startScan()
                            .addOnSuccessListener { barcode -> viewModel.loadJoinCode(barcode.rawValue.orEmpty()) }
                            .addOnFailureListener { error ->
                                viewModel.showManagementError(error.localizedMessage ?: "No se pudo abrir el escáner")
                            }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Escanear QR")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (canJoinWithCode && !uiState.emailVerified) {
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

        uiState.requestMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        uiState.managementError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (editingTeam == null) uiState.teamActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (inviteTeam == null) {
            uiState.inviteActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState.teamsError != null && uiState.teams.isEmpty() && !isGuardian) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No se pudieron cargar tus equipos.",
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
                items(uiState.teams, key = { "team_${it.id}" }) { team ->
                    TeamCard(
                        team = team,
                        canManage = isCoach && TeamAccess.canManage(team, uiState.currentUserId),
                        canInvite = privacyReady && canCreateTeam && !team.archived && team.type == "EQUIPO" && TeamAccess.canManage(team, uiState.currentUserId),
                        onInvite = {
                            viewModel.clearInviteError()
                            invitedEmail = ""
                            inviteTeam = team
                        },
                        onEdit = { viewModel.clearTeamError(); editingTeam = team },
                        onArchive = if (privacyReady && team.type == "EQUIPO") ({ viewModel.clearTeamError(); archivingTeam = team }) else null,
                        onPhoto = { photoTeam = team; photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        isUploadingPhoto = uiState.isUploadingPhoto,
                        isArchiving = uiState.archivingTeamId == team.id,
                        onManage = { selectedTeamId = team.id }
                    )
                }

                if (isGuardian) {
                    item {
                        OutlinedButton(onClick = onGuardians, modifier = Modifier.fillMaxWidth()) {
                            Text("Ver jugadores vinculados")
                        }
                    }
                }
                if (uiState.teamsError != null) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Algunos equipos no se pudieron cargar.", modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::loadTeams) { Text("Reintentar") }
                        }
                    }
                }

                if (uiState.teams.isEmpty() && uiState.teamsError == null && (canCreateTeam || canJoinWithCode)) {
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
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Crear equipo o academia", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Text(
                                text = "Aún no perteneces a un equipo. Escanea el QR del entrenador para solicitar el ingreso o acepta una invitación.",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (canJoinWithCode && uiState.pendingInvites.isNotEmpty()) {
                    item { Text("Invitaciones pendientes", style = MaterialTheme.typography.titleMedium) }
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

                if (canJoinWithCode && uiState.myJoinRequests.isNotEmpty()) {
                    item { Text("Solicitudes de ingreso", style = MaterialTheme.typography.titleMedium) }
                    items(uiState.myJoinRequests, key = { "request_${it.id}" }) { request ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(request.teamName, fontWeight = FontWeight.SemiBold)
                                Text(request.category)
                                Text(when (request.status) {
                                    "PENDING" -> "Pendiente de aprobación"
                                    "APPROVED" -> "Aprobada"
                                    "DECLINED" -> "Rechazada"
                                    else -> "Estado no disponible"
                                })
                            }
                        }
                    }
                }
                if (canJoinWithCode && (uiState.myJoinRequestsError != null || uiState.invitesError != null)) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                Modifier.padding(start = 12.dp, end = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = when {
                                        uiState.myJoinRequestsError == null -> "No se pudieron cargar las invitaciones"
                                        uiState.invitesError == null -> "No se pudieron cargar las solicitudes"
                                        else -> "No se pudieron cargar las invitaciones ni las solicitudes"
                                    },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = viewModel::loadTeams) { Text("Reintentar") }
                            }
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
                                .heightIn(min = 52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Crear equipo o academia",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamCard(
    team: Team,
    canManage: Boolean,
    canInvite: Boolean,
    onInvite: () -> Unit,
    onEdit: () -> Unit,
    onArchive: (() -> Unit)?,
    onPhoto: () -> Unit,
    isUploadingPhoto: Boolean,
    isArchiving: Boolean,
    onManage: () -> Unit
) {
    val isAcademy = team.type == "ACADEMIA"
    var showActions by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f)
                    .clickable(onClickLabel = if (isAcademy) "Ver academia" else "Ver plantilla", onClick = onManage)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TeamPhoto(team, size = 56.dp, decorative = true)
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            team.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        listOfNotNull(
                            "Archivado".takeIf { team.archived },
                            "Academia".takeIf { isAcademy },
                            team.city.takeIf { it.isNotBlank() }
                        ).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { subtitle ->
                            Text(
                                subtitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                if (team.categories.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        team.categories.forEach { category ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    category,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isAcademy) "Ver academia" else "Ver plantilla",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (canManage) {
                Box(Modifier.padding(top = 8.dp, end = 8.dp)) {
                    IconButton(onClick = { showActions = true }, enabled = !isArchiving) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones de ${team.name}")
                    }
                    DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                        if (canInvite) {
                            DropdownMenuItem(text = { Text("Invitar") }, onClick = {
                                showActions = false
                                onInvite()
                            })
                        }
                        DropdownMenuItem(
                            text = { Text(if (isUploadingPhoto) "Subiendo foto…" else "Cambiar foto") },
                            enabled = !isUploadingPhoto,
                            onClick = {
                                showActions = false
                                onPhoto()
                            }
                        )
                        DropdownMenuItem(text = { Text("Editar") }, onClick = {
                            showActions = false
                            onEdit()
                        })
                        if (onArchive != null) {
                            DropdownMenuItem(
                                text = { Text(if (team.archived) "Reactivar" else "Archivar") },
                                onClick = {
                                    showActions = false
                                    onArchive()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TeamPhoto(team: Team, size: Dp = 76.dp, decorative: Boolean = false) {
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
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = if (decorative) null else "Logo de ${team.name}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = team.name.take(2).uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge,
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
                text = invite.teamName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (invite.category.isBlank()) {
                Text("Pide al entrenador una invitación nueva con categoría.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(invite.category, style = MaterialTheme.typography.bodyMedium)
            }
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
                    enabled = !processing && !alreadyMember && invite.category.isNotBlank(),
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
