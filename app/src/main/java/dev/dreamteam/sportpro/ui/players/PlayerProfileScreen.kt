package dev.dreamteam.sportpro.ui.players

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.dreamteam.sportpro.ui.training.DetailLine
import dev.dreamteam.sportpro.ui.training.InfoCard
import dev.dreamteam.sportpro.ui.training.LoadingBox
import dev.dreamteam.sportpro.ui.training.MessageText
import dev.dreamteam.sportpro.ui.training.SectionLabel
import dev.dreamteam.sportpro.ui.training.SportTextField
import dev.dreamteam.sportpro.ui.training.SportTopBar

/**
 * US-04: perfil del jugador. [canEdit] es true para el propio jugador y para entrenadores;
 * el padre o tutor vinculado solo consulta. Solo el propio jugador ([isSelf]) vincula a sus padres o tutores.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerProfileScreen(
    playerId: String,
    initialName: String,
    canEdit: Boolean,
    isSelf: Boolean,
    onBack: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenFees: () -> Unit,
    viewModel: PlayerProfileViewModel = viewModel()
) {
    LaunchedEffect(playerId) { viewModel.start(playerId, manageGuardians = isSelf) }
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
                    isSaving = uiState.isSaving,
                    formError = uiState.formError,
                    onSave = { viewModel.save(form) }
                )
                if (isSelf) {
                    GuardiansSection(
                        guardians = uiState.guardians.map { it.guardianEmail },
                        onAdd = { email, done -> viewModel.addGuardian(email, form.fullName.ifBlank { displayName }, done) },
                        onRemove = { email -> uiState.guardians.firstOrNull { it.guardianEmail == email }?.let(viewModel::removeGuardian) }
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
        text = "🔒 Visible solo para el jugador, sus padres o tutores vinculados y entrenadores.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditableProfile(
    form: PlayerProfileForm,
    onFormChange: (PlayerProfileForm) -> Unit,
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
    guardians: List<String>,
    onAdd: (email: String, done: () -> Unit) -> Unit,
    onRemove: (String) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    SectionLabel("PADRES O TUTORES", modifier = Modifier.padding(top = 8.dp))
    Text(
        text = "Los correos vinculados pueden consultar el perfil, la asistencia y las mensualidades del jugador.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp
    )
    guardians.forEach { guardianEmail ->
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(guardianEmail, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(guardianEmail) }) {
                    Icon(Icons.Default.Close, contentDescription = "Quitar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SportTextField(
            value = email,
            onValueChange = { email = it },
            label = "Correo del padre o tutor",
            keyboardType = KeyboardType.Email,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = { onAdd(email) { email = "" } }) { Text("Vincular") }
    }
}
