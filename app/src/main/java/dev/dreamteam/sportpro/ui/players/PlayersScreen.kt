package dev.dreamteam.sportpro.ui.players

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dreamteam.sportpro.ui.training.EmptyState
import dev.dreamteam.sportpro.ui.training.InfoCard
import dev.dreamteam.sportpro.ui.training.LoadingBox
import dev.dreamteam.sportpro.ui.training.MessageText
import dev.dreamteam.sportpro.ui.training.SectionLabel
import dev.dreamteam.sportpro.ui.training.SportTopBar
import dev.dreamteam.sportpro.ui.training.TeamChips

/** US-04: acceso a los perfiles de jugadores según el rol. */
@Composable
fun PlayersScreen(
    isCoach: Boolean,
    isParent: Boolean,
    onBack: () -> Unit,
    onOpenPlayer: (playerId: String, playerName: String) -> Unit,
    viewModel: PlayersViewModel = viewModel()
) {
    LaunchedEffect(isCoach, isParent) { viewModel.start(isCoach, isParent) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { SportTopBar(title = "JUGADORES", onBack = onBack) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.error?.let { error -> item { MessageText(error) } }

            if (isCoach) {
                item { SectionLabel("MIS EQUIPOS") }
                when {
                    uiState.teamsLoading -> item { LoadingBox() }
                    uiState.teams.isEmpty() -> item { EmptyState("Aún no administras equipos. Créalos en la pestaña Equipos.") }
                    else -> {
                        item {
                            TeamChips(uiState.teams, uiState.selectedTeamId, onSelect = viewModel::selectTeam)
                        }
                        when {
                            uiState.rosterLoading -> item { LoadingBox() }
                            uiState.roster.isEmpty() -> item {
                                EmptyState("Este equipo aún no tiene jugadores. Invítalos desde la gestión del equipo.")
                            }
                            else -> items(uiState.roster, key = { "roster-${it.userId}" }) { member ->
                                PlayerRow(
                                    name = member.playerName,
                                    detail = listOfNotNull(
                                        member.category.ifBlank { null },
                                        member.jerseyNumber?.let { "#$it" }
                                    ).joinToString(" · "),
                                    onClick = { onOpenPlayer(member.userId, member.playerName) }
                                )
                            }
                        }
                    }
                }
            }

            if (isParent) {
                item { SectionLabel("A MI CARGO", modifier = Modifier.padding(top = 8.dp)) }
                if (!uiState.emailVerified) {
                    item {
                        InfoCard {
                            Text(
                                text = "Verifica tu correo",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Para proteger los datos de los menores, solo los padres o tutores con correo verificado pueden ver a sus jugadores.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            uiState.verificationMessage?.let {
                                Spacer(modifier = Modifier.height(6.dp))
                                MessageText(it, isError = false)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = viewModel::sendVerificationEmail) { Text("Enviar correo") }
                                TextButton(onClick = viewModel::refreshVerification) { Text("Ya verifiqué") }
                            }
                        }
                    }
                } else {
                    when {
                        uiState.linkedLoading -> item { LoadingBox() }
                        uiState.linkedPlayers.isEmpty() -> item {
                            EmptyState(
                                "No tienes jugadores vinculados. El jugador debe agregar tu correo en la sección " +
                                    "\"Padres o tutores\" de su perfil."
                            )
                        }
                        else -> items(uiState.linkedPlayers, key = { "linked-${it.id}" }) { link ->
                            PlayerRow(
                                name = link.playerName.ifBlank { "Jugador" },
                                detail = "Jugador a tu cargo",
                                onClick = { onOpenPlayer(link.playerId, link.playerName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(name: String, detail: String, onClick: () -> Unit) {
    InfoCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (detail.isNotBlank()) {
                    Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
