package dev.dreamteam.sportpro.ui.players

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dreamteam.sportpro.data.model.FeeStatus
import dev.dreamteam.sportpro.data.model.MonthlyFee
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.ui.training.EmptyState
import dev.dreamteam.sportpro.ui.training.InfoCard
import dev.dreamteam.sportpro.ui.training.LoadingBox
import dev.dreamteam.sportpro.ui.training.MessageText
import dev.dreamteam.sportpro.ui.training.SectionLabel
import dev.dreamteam.sportpro.ui.training.SportTextField
import dev.dreamteam.sportpro.ui.training.SportTopBar
import dev.dreamteam.sportpro.ui.training.StatusPill
import dev.dreamteam.sportpro.ui.training.TeamChips
import dev.dreamteam.sportpro.ui.training.feeColor
import dev.dreamteam.sportpro.ui.training.periodLabel

/** US-05: registro simulado de mensualidades por equipo y periodo (entrenador). */
@Composable
fun MonthlyFeesScreen(
    onBack: () -> Unit,
    viewModel: MonthlyFeesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var amount by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { SportTopBar(title = "MENSUALIDADES", onBack = onBack) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                InfoCard {
                    Text(
                        text = "Registro simulado",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Solo se registra el estado de la mensualidad. No se procesan pagos ni dinero real.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
            uiState.error?.let { error -> item { MessageText(error) } }
            when {
                uiState.teamsLoading -> item { LoadingBox() }
                uiState.teams.isEmpty() -> item { EmptyState("Aún no administras equipos. Créalos en la pestaña Equipos.") }
                else -> {
                    item { SectionLabel("EQUIPO") }
                    item { TeamChips(uiState.teams, uiState.selectedTeamId, onSelect = viewModel::selectTeam) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { viewModel.changePeriod(-1) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
                            }
                            Text(
                                text = periodLabel(uiState.period),
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            IconButton(onClick = { viewModel.changePeriod(1) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
                            }
                        }
                    }
                    item {
                        SportTextField(
                            value = amount,
                            onValueChange = { value -> amount = value.filter { it.isDigit() || it == '.' || it == ',' }.take(9) },
                            label = "Monto referencial (S/) · opcional",
                            keyboardType = KeyboardType.Decimal
                        )
                    }
                    item {
                        val paid = uiState.fees.values.count { it.status == FeeStatus.PAGADA }
                        val pending = uiState.fees.values.count { it.status == FeeStatus.PENDIENTE }
                        val missing = uiState.roster.count { it.userId !in uiState.fees }
                        Text(
                            text = "$paid pagadas · $pending pendientes · $missing sin registrar",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    when {
                        uiState.rosterLoading -> item { LoadingBox() }
                        uiState.roster.isEmpty() -> item { EmptyState("Este equipo aún no tiene jugadores.") }
                        else -> items(uiState.roster, key = { it.userId }) { member ->
                            FeeRow(
                                member = member,
                                fee = uiState.fees[member.userId],
                                busy = member.userId in uiState.busyPlayerIds,
                                onRegister = { status -> viewModel.register(member, amount, status) },
                                onToggle = viewModel::toggleStatus
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeeRow(
    member: TeamMember,
    fee: MonthlyFee?,
    busy: Boolean,
    onRegister: (FeeStatus) -> Unit,
    onToggle: (MonthlyFee) -> Unit
) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(member.playerName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = listOfNotNull(member.category.ifBlank { null }, fee?.amount?.let { "S/ %.2f".format(it) }).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            if (fee != null) StatusPill(fee.status.label, feeColor(fee.status)) else StatusPill("Sin registrar", MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (fee == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onRegister(FeeStatus.PENDIENTE) }) { Text("Pendiente", fontSize = 12.sp) }
                Button(
                    onClick = { onRegister(FeeStatus.PAGADA) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Pagada", fontSize = 12.sp) }
            }
        } else {
            TextButton(onClick = { onToggle(fee) }) {
                Text(if (fee.status == FeeStatus.PAGADA) "Marcar como pendiente" else "Marcar como pagada")
            }
        }
    }
}
