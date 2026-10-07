package dev.dreamteam.sportpro.ui.teams

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.model.TeamLineup
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.domain.access.TeamAccess

@Composable
fun TeamManagementScreen(team: Team, userId: String, viewModel: TeamsViewModel, onBack: () -> Unit, onEmailInvite: () -> Unit, onOpenTeam: (Team) -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    val owner = TeamAccess.canManage(team, userId)
    var tab by remember(team.id) { mutableStateOf(0) }
    var category by remember(team.id) { mutableStateOf(team.categories.firstOrNull().orEmpty()) }
    var editingMember by remember { mutableStateOf<TeamMember?>(null) }
    var removingMember by remember { mutableStateOf<TeamMember?>(null) }
    var selectingAcademyTeam by remember { mutableStateOf(false) }
    var formation by remember(team.id, category) { mutableStateOf("4-3-3") }
    var positions by remember(team.id, category) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedSlot by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flag)
            } catch (_: Exception) {}
            viewModel.uploadTeamPhoto(context, team, uri)
        }
    }

    LaunchedEffect(team.id) {
        viewModel.openTeam(team)
        if (owner && state.joinCode == null) {
            viewModel.loadOrCreateJoinCode(team)
        }
    }

    DisposableEffect(team.id) { onDispose { viewModel.closeTeam() } }
    LaunchedEffect(team.id, category) { if (category.isNotBlank()) viewModel.watchLineup(team, category) }
    LaunchedEffect(state.lineup, category) {
        formation = state.lineup?.formation ?: "4-3-3"
        positions = state.lineup?.positions ?: emptyMap()
    }

    editingMember?.let { member ->
        MemberDialog(team, member, state.managementBusy, state.managementError,
            onDismiss = { editingMember = null },
            onSave = { viewModel.updateMember(team, it); editingMember = null },
            onRemove = { editingMember = null; removingMember = member })
    }
    removingMember?.let { member ->
        AlertDialog(onDismissRequest = { removingMember = null }, title = { Text("Retirar jugador") },
            text = { Text("${member.playerName} dejará de pertenecer a ${team.name}.") },
            confirmButton = { TextButton(onClick = { viewModel.removeMember(team, member); removingMember = null }) { Text("Retirar") } },
            dismissButton = { TextButton(onClick = { removingMember = null }) { Text("Cancelar") } })
    }
    if (selectingAcademyTeam) {
        val candidates = state.teams.filter { it.type == "EQUIPO" && it.academyId != team.id && it.createdBy == team.createdBy }
        AlertDialog(onDismissRequest = { selectingAcademyTeam = false },
            title = { Text("Vincular equipo") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (candidates.isEmpty()) Text("No hay equipos disponibles que administres.")
                    candidates.forEach { candidate ->
                        TextButton(enabled = !state.isSavingTeam, onClick = {
                            viewModel.setTeamAcademy(candidate, team.id)
                            selectingAcademyTeam = false
                        }) { Text(candidate.name) }
                    }
                    state.teamActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { selectingAcademyTeam = false }) { Text("Cerrar") } })
    }
    selectedSlot?.let { index ->
        val available = state.members.filter { it.category == category &&
            (it.userId == positions["P$index"] || it.userId !in positions.values) }
        AlertDialog(onDismissRequest = { selectedSlot = null }, title = { Text("Elegir jugador") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { positions = positions - "P$index"; selectedSlot = null }) { Text("Sin asignar") }
                    available.forEach { member ->
                        TextButton(onClick = { positions = positions + ("P$index" to member.userId); selectedSlot = null }) {
                            Text("${member.playerName}${member.jerseyNumber?.let { " · #$it" } ?: ""}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedSlot = null }) { Text("Cancelar") } })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text(team.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TeamPhoto(team)
                if (owner) {
                    TextButton(
                        onClick = {
                            viewModel.clearTeamError()
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        enabled = !state.isUploadingPhoto
                    ) {
                        Text(if (state.isUploadingPhoto) "Subiendo logo…" else "📷 Cambiar / Agregar logo del equipo")
                    }
                }
                state.teamActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(if (team.type == "ACADEMIA") "ACADEMIA" else "EQUIPO", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (team.city.isNotBlank()) Text("📍 ${team.city}")
                if (team.description.isNotBlank()) Text(team.description)
                Text("Categorías: ${team.categories.joinToString(" · ")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (owner && team.type == "ACADEMIA") {
            val academyTeams = state.teams.filter { TeamAccess.canManageTeamInAcademy(team, it, userId) }
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Equipos de la academia (${academyTeams.size})", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    if (academyTeams.isEmpty()) {
                        Text("Aún no hay equipos vinculados. Al crear uno, selecciónala como academia.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else academyTeams.forEach { academyTeam ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { onOpenTeam(academyTeam) }) {
                                Text("${academyTeam.name} · ${academyTeam.categories.joinToString(" / ")}")
                            }
                            TextButton(enabled = !state.isSavingTeam, onClick = {
                                viewModel.setTeamAcademy(academyTeam, null)
                            }) { Text("Desvincular") }
                        }
                    }
                    OutlinedButton(onClick = { selectingAcademyTeam = true }, enabled = !state.isSavingTeam) {
                        Text("Vincular equipo existente")
                    }
                }
            }
        }
        if (owner) {
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("QR para unirse", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    when {
                        state.joinCode != null -> {
                            val code = state.joinCode!!
                            QrImage("sportpro://join/${code.id}")
                            Text("${code.teamName} · ${code.categories.joinToString(" · ")}", textAlign = TextAlign.Center)
                            Text("Código QR estático del equipo.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                        state.managementBusy -> CircularProgressIndicator()
                        else -> Text("Aún no hay un QR vigente. Genera uno para invitar jugadores.", textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.createJoinCode(team) }, enabled = !state.managementBusy) {
                        Text(if (state.joinCode == null) "Generar QR" else "Renovar QR")
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("PLANTILLA") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("ALINEACIÓN") })
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            team.categories.forEach { item ->
                FilterChip(selected = item == category, onClick = { category = item }, label = { Text(item) })
            }
        }
        state.managementError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.membersLoading) CircularProgressIndicator()
        if (tab == 0) {
            if (owner) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEmailInvite) { Text("Invitar por correo") }
                }
                Spacer(Modifier.height(10.dp))
            }
            val roster = state.members.filter { it.category == category }
            Text("${roster.size} jugadores en $category", fontWeight = FontWeight.SemiBold)
            if (roster.isEmpty()) Text("Todavía no hay jugadores en esta categoría.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            roster.forEach { member ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${member.jerseyNumber?.let { "#$it  " } ?: ""}${member.playerName}")
                        if (owner) TextButton(onClick = { editingMember = member }) { Text("Editar") }
                    }
                }
            }
            val uncategorized = state.members.filter { it.category.isBlank() }
            if (uncategorized.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("SIN CATEGORÍA", fontWeight = FontWeight.Bold)
                uncategorized.forEach { member ->
                    if (owner) TextButton(onClick = { editingMember = member }) { Text("${member.playerName} · Asignar categoría") }
                    else Text(member.playerName)
                }
            }
        } else {
            Text("FORMACIÓN BASE", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("4-3-3", "4-4-2", "3-5-2").forEach { item ->
                    FilterChip(selected = formation == item, onClick = { if (owner) { formation = item; positions = emptyMap() } },
                        label = { Text(item) })
                }
            }
            Text(if (owner) "Toca una posición para asignar un jugador" else "Alineación guardada", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Pitch(formation, positions, state.members, owner) { selectedSlot = it }
            Spacer(Modifier.height(12.dp))
            if (owner) Button(onClick = { viewModel.saveLineup(team, TeamLineup(category, formation, positions)) },
                enabled = !state.managementBusy && !state.lineupLoading && category.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Guardar alineación")
            }
        }
    }
}

@Composable
private fun MemberDialog(team: Team, member: TeamMember, busy: Boolean, error: String?,
                         onDismiss: () -> Unit, onSave: (TeamMember) -> Unit, onRemove: () -> Unit) {
    var name by remember(member.userId) { mutableStateOf(member.playerName) }
    var category by remember(member.userId) { mutableStateOf(member.category.ifBlank { team.categories.firstOrNull().orEmpty() }) }
    var jersey by remember(member.userId) { mutableStateOf(member.jerseyNumber?.toString().orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Editar jugador") },
        text = {
            Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nombre") })
                OutlinedTextField(jersey, { jersey = it.filter(Char::isDigit).take(2) }, label = { Text("Dorsal") })
                team.categories.forEach { item -> FilterChip(selected = item == category, onClick = { category = item }, label = { Text(item) }) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onRemove, enabled = !busy) { Text("Retirar del equipo", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(member.copy(playerName = name, category = category, jerseyNumber = jersey.toIntOrNull())) }, enabled = !busy) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

private fun formationSlots(formation: String): List<Triple<Float, Float, String>> {
    val parts = formation.split('-').map(String::toInt)
    val rows = listOf(parts[2] to 0.18f, parts[1] to 0.43f, parts[0] to 0.72f, 1 to 0.90f)
    val names = listOf("DEL", "MED", "DEF", "POR")
    return rows.flatMapIndexed { rowIndex, (count, y) ->
        (0 until count).map { i -> Triple((i + 1f) / (count + 1f), y, names[rowIndex]) }
    }
}

@Composable
private fun Pitch(formation: String, positions: Map<String, String>, members: List<TeamMember>, owner: Boolean, onSlot: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(450.dp).background(Color(0xFF174735), RoundedCornerShape(16.dp))
        .border(BorderStroke(2.dp, Color(0xFF679983)), RoundedCornerShape(16.dp))) {
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val line = Color.White.copy(alpha = 0.45f)
            drawLine(line,
                Offset(0f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), 2f)
            drawCircle(line, radius = size.width * .10f, center = center, style = Stroke(2f))
            drawRect(line, style = Stroke(2f))
        }
        formationSlots(formation).forEachIndexed { index, (x, y, role) ->
            val member = members.firstOrNull { it.userId == positions["P$index"] }
            Surface(onClick = { if (owner) onSlot(index) },
                modifier = Modifier.offset(x = maxWidth * x - 30.dp, y = maxHeight * y - 30.dp).size(60.dp),
                shape = CircleShape, color = Color(0xFF10362B), border = BorderStroke(2.dp, Color.White.copy(alpha = .75f))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(member?.jerseyNumber?.let { "#$it" } ?: if (owner) "+" else "·", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(member?.playerName?.take(8) ?: role, color = Color.White, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun QrImage(value: String) {
    val bitmap = remember(value) {
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
        val pixels = IntArray(512 * 512) { index ->
            if (matrix[index % 512, index / 512]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        Bitmap.createBitmap(pixels, 512, 512, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(bitmap, contentDescription = "Código QR para unirse", modifier = Modifier.size(260.dp).background(Color.White))
}
