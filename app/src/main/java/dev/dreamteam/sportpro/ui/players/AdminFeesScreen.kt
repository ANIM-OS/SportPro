package dev.dreamteam.sportpro.ui.players

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.FeeStatus
import dev.dreamteam.sportpro.data.model.MonthlyFee
import dev.dreamteam.sportpro.data.repository.MonthlyFeeRepository
import dev.dreamteam.sportpro.ui.training.EmptyState
import dev.dreamteam.sportpro.ui.training.InfoCard
import dev.dreamteam.sportpro.ui.training.LoadingBox
import dev.dreamteam.sportpro.ui.training.MessageText
import dev.dreamteam.sportpro.ui.training.SportTopBar
import dev.dreamteam.sportpro.ui.training.StatusPill
import dev.dreamteam.sportpro.ui.training.currentPeriod
import dev.dreamteam.sportpro.ui.training.feeColor
import dev.dreamteam.sportpro.ui.training.periodLabel
import dev.dreamteam.sportpro.ui.training.shiftPeriod
import dev.dreamteam.sportpro.ui.training.toDateText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AdminFeesUiState(
    val period: String = currentPeriod(),
    val fees: List<MonthlyFee> = emptyList(),
    // null = todos los equipos; el filtro se aplica sobre lo ya cargado del periodo.
    val selectedTeamId: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    /** Equipos presentes en el periodo (id → nombre), para los chips de filtro. */
    val teams: List<Pair<String, String>>
        get() = fees.map { it.teamId to it.teamName.ifBlank { "Equipo" } }.distinctBy { it.first }.sortedBy { it.second }

    val visibleFees: List<MonthlyFee>
        get() = fees.filter { selectedTeamId == null || it.teamId == selectedTeamId }
}

/** US-05 criterio 4: el admin consulta las mensualidades en solo lectura. */
class AdminFeesViewModel : ViewModel() {

    private val repository = MonthlyFeeRepository()
    private val _uiState = MutableStateFlow(AdminFeesUiState())
    val uiState: StateFlow<AdminFeesUiState> = _uiState.asStateFlow()
    private var listener: ListenerRegistration? = null

    init {
        watch()
    }

    fun changePeriod(months: Int) {
        _uiState.update { it.copy(period = shiftPeriod(it.period, months)) }
        watch()
    }

    fun selectTeam(teamId: String?) = _uiState.update { it.copy(selectedTeamId = teamId) }

    private fun watch() {
        listener?.remove()
        _uiState.update { it.copy(fees = emptyList(), isLoading = true, error = null) }
        listener = repository.listenPeriod(
            period = _uiState.value.period,
            onChange = { fees ->
                _uiState.update { state ->
                    state.copy(
                        fees = fees.sortedWith(compareBy({ it.teamName }, { it.playerName })),
                        isLoading = false,
                        error = null,
                        // Si el equipo elegido no tiene registros en este periodo, se vuelve a "Todos".
                        selectedTeamId = state.selectedTeamId?.takeIf { id -> fees.any { it.teamId == id } }
                    )
                }
            },
            onError = { message -> _uiState.update { it.copy(isLoading = false, error = message) } }
        )
    }

    override fun onCleared() {
        listener?.remove()
    }
}

@Composable
fun AdminFeesScreen(
    onBack: () -> Unit,
    viewModel: AdminFeesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { SportTopBar(title = "MENSUALIDADES · ADMIN", onBack = onBack) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Registro simulado: no se procesan pagos reales. Vista de solo lectura.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
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
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { viewModel.changePeriod(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
                    }
                }
            }
            when {
                uiState.isLoading -> item { LoadingBox() }
                uiState.error != null -> item { MessageText(uiState.error.orEmpty()) }
                uiState.fees.isEmpty() -> item { EmptyState("No hay mensualidades registradas en este periodo.") }
                else -> {
                    if (uiState.teams.size > 1) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = uiState.selectedTeamId == null,
                                    onClick = { viewModel.selectTeam(null) },
                                    label = { Text("Todos") }
                                )
                                uiState.teams.forEach { (id, name) ->
                                    FilterChip(
                                        selected = uiState.selectedTeamId == id,
                                        onClick = { viewModel.selectTeam(id) },
                                        label = { Text(name) }
                                    )
                                }
                            }
                        }
                    }
                    item {
                        val visible = uiState.visibleFees
                        val paid = visible.count { it.status == FeeStatus.PAGADA }
                        Text(
                            text = "$paid pagadas · ${visible.size - paid} pendientes",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    items(uiState.visibleFees, key = { it.id }) { fee -> AdminFeeRow(fee) }
                }
            }
        }
    }
}

@Composable
private fun AdminFeeRow(fee: MonthlyFee) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fee.playerName.ifBlank { "Jugador" },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = listOfNotNull(
                        fee.teamName.ifBlank { null },
                        fee.amount?.let { "S/ %.2f".format(it) },
                        fee.paidAt?.let { "pagada el ${it.toDateText()}" }
                    ).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            StatusPill(fee.status.label, feeColor(fee.status))
        }
    }
}
