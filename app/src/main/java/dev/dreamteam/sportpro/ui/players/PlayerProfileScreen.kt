package dev.dreamteam.sportpro.ui.players

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dreamteam.sportpro.data.model.PlayerProfile
import dev.dreamteam.sportpro.data.model.GuardianInvite
import dev.dreamteam.sportpro.data.model.GuardianLink
import dev.dreamteam.sportpro.ui.training.DetailLine
import dev.dreamteam.sportpro.ui.training.InfoCard
import dev.dreamteam.sportpro.ui.training.LoadingBox
import dev.dreamteam.sportpro.ui.training.MessageText
import dev.dreamteam.sportpro.ui.training.SectionLabel
import dev.dreamteam.sportpro.ui.training.SportTextField
import dev.dreamteam.sportpro.ui.training.SportTopBar

/**
 * US-04 / US-22: perfil del jugador. El propio jugador ([isSelf]) edita todo y vincula a sus tutores.
 * La entrenadora que entra por uno de sus equipos ([coachTeamId]) edita solo los datos deportivos
 * y ve los sensibles en solo lectura. El padre o tutor vinculado solo consulta.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerProfileScreen(
    playerId: String,
    initialName: String,
    isSelf: Boolean,
    coachTeamId: String?,
    onBack: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenFees: () -> Unit,
    viewModel: PlayerProfileViewModel = viewModel()
) {
    val canEdit = isSelf || !coachTeamId.isNullOrBlank()
    LaunchedEffect(playerId) {
        viewModel.start(playerId, manageGuardians = isSelf, coachTeamId = coachTeamId.takeUnless { isSelf })
    }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var form by remember { mutableStateOf(PlayerProfileForm(fullName = initialName)) }
    var prefilled by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isLoading, uiState.profile) {
        val profile = uiState.profile
        if (!uiState.isLoading && !prefilled) {
            if (profile != null) form = profile.toForm(initialName)
            prefilled = true
        }
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadPhoto(context, uri)
    }
    val displayName = uiState.profile?.fullName?.ifBlank { null } ?: initialName.ifBlank { "Jugador" }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { SportTopBar(title = "PERFIL DEL JUGADOR", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.isLoading) {
                LoadingBox()
                return@Column
            }
            uiState.error?.let {
                MessageText(it)
                return@Column
            }

            // Foto y nombre
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val photo = uiState.photo
                    if (photo != null) {
                        Image(
                            bitmap = photo.asImageBitmap(),
                            contentDescription = "Foto de $displayName",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(displayName, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (canEdit) {
                    TextButton(
                        onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !uiState.isUploadingPhoto
                    ) {
                        if (uiState.isUploadingPhoto) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cambiar foto")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onOpenAttendance, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("Asistencia")
                }
                OutlinedButton(onClick = onOpenFees, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("Mensualidades")
                }
            }

            if (canEdit) {
                EditableProfile(
                    form = form,
                    onFormChange = { form = it },
                    canEditPrivate = isSelf,
                    profile = uiState.profile,
                    isSaving = uiState.isSaving,
                    formError = uiState.formError,
                    onSave = { viewModel.save(form, includePrivate = isSelf) }
                )
                if (isSelf) {
                    GuardiansSection(
                        guardians = uiState.guardians,
                        invites = uiState.guardianInvites,
                        legacyEmails = uiState.legacyGuardianEmails,
                        busy = uiState.guardianBusy,
                        onCreate = { viewModel.createGuardianInvite(form.fullName.ifBlank { displayName }) },
                        onConfirm = viewModel::confirmGuardianInvite,
                        onCancel = viewModel::cancelGuardianInvite,
                        onRemove = viewModel::removeGuardian
                    )
                }
            } else {
                ReadOnlyProfile(uiState.profile)
            }
        }
    }
}

private fun PlayerProfile.toForm(fallbackName: String) = PlayerProfileForm(
    fullName = fullName.ifBlank { fallbackName },
    position = position,
    dominantFoot = dominantFoot,
    heightCm = heightCm?.toString().orEmpty(),
    weightKg = weightKg?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty(),
    phone = phone,
    contactEmail = contactEmail,
    emergencyName = emergencyName,
    emergencyRelation = emergencyRelation,
    emergencyPhone = emergencyPhone
)

@Composable
private fun SensitiveNote() {
    Text(
        text = "🔒 Visible solo para el jugador, sus padres o tutores vinculados, la entrenadora de su equipo y la administración.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditableProfile(
    form: PlayerProfileForm,
    onFormChange: (PlayerProfileForm) -> Unit,
    canEditPrivate: Boolean,
    profile: PlayerProfile?,
    isSaving: Boolean,
    formError: String?,
    onSave: () -> Unit
) {
    SectionLabel("DATOS DEPORTIVOS")
    SportTextField(value = form.fullName, onValueChange = { onFormChange(form.copy(fullName = it)) }, label = "Nombre completo")
    Text("Posición", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    OptionChips(PlayerProfile.POSITIONS, form.position) { onFormChange(form.copy(position = it)) }
    Text("Pie hábil", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    OptionChips(PlayerProfile.FEET, form.dominantFoot) { onFormChange(form.copy(dominantFoot = it)) }

    if (canEditPrivate) {
        EditablePrivateFields(form, onFormChange)
    } else {
        // La entrenadora ve los datos sensibles pero no los modifica (US-22).
        SensitiveDetails(profile)
        Text(
            text = "Solo el jugador puede modificar sus datos físicos y de contacto.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }

    Text(
        text = "Todos los campos son opcionales; puedes completarlos más adelante.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp
    )
    formError?.let { MessageText(it) }
    Button(
        onClick = onSave,
        enabled = !isSaving,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        if (isSaving) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        } else {
            Text("GUARDAR PERFIL", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EditablePrivateFields(form: PlayerProfileForm, onFormChange: (PlayerProfileForm) -> Unit) {
    SectionLabel("DATOS FÍSICOS", modifier = Modifier.padding(top = 8.dp))
    SensitiveNote()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SportTextField(
            value = form.heightCm,
            onValueChange = { value -> onFormChange(form.copy(heightCm = value.filter { it.isDigit() }.take(3))) },
            label = "Estatura (cm)",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
        SportTextField(
            value = form.weightKg,
            onValueChange = { value -> onFormChange(form.copy(weightKg = value.filter { it.isDigit() || it == '.' || it == ',' }.take(5))) },
            label = "Peso (kg)",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f)
        )
    }

    SectionLabel("CONTACTO", modifier = Modifier.padding(top = 8.dp))
    SensitiveNote()
    SportTextField(value = form.phone, onValueChange = { onFormChange(form.copy(phone = it)) }, label = "Teléfono", keyboardType = KeyboardType.Phone)
    SportTextField(
        value = form.contactEmail,
        onValueChange = { onFormChange(form.copy(contactEmail = it)) },
        label = "Correo de contacto",
        keyboardType = KeyboardType.Email
    )

    SectionLabel("CONTACTO DE EMERGENCIA", modifier = Modifier.padding(top = 8.dp))
    SensitiveNote()
    SportTextField(value = form.emergencyName, onValueChange = { onFormChange(form.copy(emergencyName = it)) }, label = "Nombre")
    SportTextField(
        value = form.emergencyRelation,
        onValueChange = { onFormChange(form.copy(emergencyRelation = it)) },
        label = "Parentesco",
        placeholder = "ej. Madre"
    )
    SportTextField(
        value = form.emergencyPhone,
        onValueChange = { onFormChange(form.copy(emergencyPhone = it)) },
        label = "Teléfono de emergencia",
        keyboardType = KeyboardType.Phone
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(if (selected == option) "" else option) },
                label = { Text(option) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun ReadOnlyProfile(profile: PlayerProfile?) {
    if (profile == null) {
        InfoCard {
            Text(
                text = "El jugador aún no completó su perfil.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
        return
    }
    InfoCard {
        SectionLabel("DATOS DEPORTIVOS")
        Spacer(modifier = Modifier.height(4.dp))
        DetailLine("Posición", profile.position)
        DetailLine("Pie hábil", profile.dominantFoot)
    }
    SensitiveDetails(profile)
}

/** Datos físicos, de contacto y de emergencia en solo lectura. */
@Composable
private fun SensitiveDetails(profile: PlayerProfile?) {
    if (profile == null) return
    InfoCard {
        SectionLabel("DATOS FÍSICOS")
        Spacer(modifier = Modifier.height(4.dp))
        DetailLine("Estatura", profile.heightCm?.let { "$it cm" }.orEmpty())
        DetailLine("Peso", profile.weightKg?.let { "$it kg" }.orEmpty())
    }
    InfoCard {
        SectionLabel("CONTACTO")
        Spacer(modifier = Modifier.height(4.dp))
        DetailLine("Teléfono", profile.phone)
        DetailLine("Correo", profile.contactEmail)
    }
    InfoCard {
        SectionLabel("CONTACTO DE EMERGENCIA")
        Spacer(modifier = Modifier.height(4.dp))
        DetailLine("Nombre", profile.emergencyName)
        DetailLine("Parentesco", profile.emergencyRelation)
        DetailLine("Teléfono", profile.emergencyPhone)
    }
    SensitiveNote()
}

@Composable
private fun GuardiansSection(
    guardians: List<GuardianLink>,
    invites: List<GuardianInvite>,
    legacyEmails: List<String>,
    busy: Boolean,
    onCreate: () -> Unit,
    onConfirm: (GuardianInvite) -> Unit,
    onCancel: (GuardianInvite) -> Unit,
    onRemove: (GuardianLink) -> Unit
) {
    val context = LocalContext.current
    SectionLabel("PADRES O TUTORES", modifier = Modifier.padding(top = 8.dp))
    Text(
        text = "Genera un código, compártelo con tu tutor y confirma aquí su solicitud. Solo los tutores confirmados pueden ver tus convocatorias y responder.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp
    )
    guardians.forEach { guardian ->
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(guardian.guardianName.ifBlank { "Tutor confirmado" }, color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(guardian) }, enabled = !busy) {
                    Icon(Icons.Default.Close, contentDescription = "Revocar tutor", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    invites.forEach { invite ->
        val expired = (invite.expiresAt?.toDate()?.time ?: 0L) <= System.currentTimeMillis()
        val expiryLabel = invite.expiresAt?.toDate()?.let { java.text.DateFormat.getDateTimeInstance().format(it) }.orEmpty()
        InfoCard {
            Text(if (invite.status == "CLAIMED") "Solicitud de ${invite.guardianName}" else "Código para tu tutor: ${invite.id}")
            if (invite.status == "CLAIMED") Text("Confirma solo si compartiste este código con esa persona.", fontSize = 12.sp)
            Text(if (expired) "Código vencido" else "Vence: $expiryLabel",
                fontSize = 12.sp)
            Row {
                if (invite.status == "OPEN" && !expired) {
                    TextButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("Código SportPro", invite.id))
                    }) { Text("Copiar código") }
                }
                if (invite.status == "CLAIMED" && !expired) {
                    TextButton(onClick = { onConfirm(invite) }, enabled = !busy) { Text("Confirmar tutor") }
                }
                TextButton(onClick = { onCancel(invite) }, enabled = !busy) { Text("Cancelar") }
            }
        }
    }
    if (legacyEmails.isNotEmpty()) Text("Vínculos anteriores por correo: ${legacyEmails.joinToString()}. Solo los tutores confirmados arriba tienen acceso; si falta alguno, comparte un código nuevo.",
        color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    Button(onClick = onCreate, enabled = !busy) { Text(if (busy) "Procesando…" else "Generar código para tutor") }
}
