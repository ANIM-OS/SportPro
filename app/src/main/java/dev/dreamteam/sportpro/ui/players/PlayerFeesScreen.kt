package dev.dreamteam.sportpro.ui.players

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import dev.dreamteam.sportpro.ui.training.feeColor
import dev.dreamteam.sportpro.ui.training.periodLabel
import dev.dreamteam.sportpro.ui.training.toDateText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlayerFeesUiState(
    val fees: List<MonthlyFee> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/** US-05: historial de mensualidades de un jugador (jugador, padre o tutor y entrenador). */
class PlayerFeesViewModel : ViewModel() {

    private val repository = MonthlyFeeRepository()
    private val _uiState = MutableStateFlow(PlayerFeesUiState())
    val uiState: StateFlow<PlayerFeesUiState> = _uiState.asStateFlow()

    private var listener: ListenerRegistration? = null
    private var started = false

    fun start(playerId: String, asCoach: Boolean) {
        if (started) return
        started = true
        listener = repository.listenPlayer(
            playerId = playerId,
            asCoach = asCoach,
            onChange = { fees -> _uiState.update { it.copy(fees = fees, isLoading = false, error = null) } },
            onError = { message -> _uiState.update { it.copy(isLoading = false, error = message) } }
        )
    }

    override fun onCleared() {
        listener?.remove()
    }
}

@Composable
fun PlayerFeesScreen(
    playerId: String,
    asCoach: Boolean,
    title: String,
    onBack: () -> Unit,
    viewModel: PlayerFeesViewModel = viewModel()
) {
    LaunchedEffect(playerId) { viewModel.start(playerId, asCoach) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { SportTopBar(title = title, onBack = onBack) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Registro simulado: no se procesan pagos reales.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            when {
                uiState.isLoading -> item { LoadingBox() }
                uiState.error != null -> item { MessageText(uiState.error.orEmpty()) }
                uiState.fees.isEmpty() -> item { EmptyState("Aún no hay mensualidades registradas.") }
                else -> {
                    item {
                        val pending = uiState.fees.count { it.status == FeeStatus.PENDIENTE }
                        InfoCard {
                            Text(
                                text = if (pending == 0) "Todo al día" else "$pending pendiente(s)",
                                color = if (pending == 0) feeColor(FeeStatus.PAGADA) else feeColor(FeeStatus.PENDIENTE),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "${uiState.fees.size} periodo(s) registrados",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                    items(uiState.fees, key = { it.id }) { fee ->
                        InfoCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = periodLabel(fee.period),
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
                }
            }
        }
    }
}
