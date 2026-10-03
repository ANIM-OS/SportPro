package dev.dreamteam.sportpro.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class StatisticsTab {
    TEAM,
    PLAYER
}

data class PlayerStatUi(
    val name: String,
    val position: String,
    val matches: Int,
    val goals: Int,
    val yellowCards: Int
)

data class MatchStatUi(
    val rival: String,
    val date: String,
    val score: String
)

@Composable
fun StatisticsScreen() {

    var selectedTab by remember {
        mutableStateOf(StatisticsTab.TEAM)
    }

    val players = listOf(
        PlayerStatUi(
            name = "Carlos Mendoza",
            position = "Delantero",
            matches = 7,
            goals = 5,
            yellowCards = 1
        ),
        PlayerStatUi(
            name = "Luis Herrera",
            position = "Mediocampista",
            matches = 8,
            goals = 3,
            yellowCards = 0
        ),
        PlayerStatUi(
            name = "Pablo Ortiz",
            position = "Defensa",
            matches = 8,
            goals = 1,
            yellowCards = 2
        )
    )

    val recentMatches = listOf(
        MatchStatUi(
            rival = "FC Monterrey",
            date = "28 Sep 2026",
            score = "2 - 0"
        ),
        MatchStatUi(
            rival = "Tigres Sub-17",
            date = "21 Sep 2026",
            score = "1 - 1"
        ),
        MatchStatUi(
            rival = "Atlético Juvenil",
            date = "14 Sep 2026",
            score = "3 - 1"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F12))
            .padding(horizontal = 16.dp)
    ) {

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "ESTADÍSTICAS",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Rendimiento acumulado de la temporada",
            color = Color(0xFF9A9CA2),
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        StatisticsTabs(
            selectedTab = selectedTab,
            onTabSelected = {
                selectedTab = it
            }
        )

        Spacer(modifier = Modifier.height(18.dp))

        when (selectedTab) {

            StatisticsTab.TEAM -> {
                TeamStatistics(
                    matches = recentMatches
                )
            }

            StatisticsTab.PLAYER -> {
                PlayerStatistics(
                    players = players
                )
            }
        }
    }
}

@Composable
private fun StatisticsTabs(
    selectedTab: StatisticsTab,
    onTabSelected: (StatisticsTab) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF161920),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(4.dp)
    ) {

        StatisticsTabButton(
            text = "EQUIPO",
            selected = selectedTab == StatisticsTab.TEAM,
            modifier = Modifier.weight(1f)
        ) {
            onTabSelected(StatisticsTab.TEAM)
        }

        StatisticsTabButton(
            text = "JUGADOR",
            selected = selectedTab == StatisticsTab.PLAYER,
            modifier = Modifier.weight(1f)
        ) {
            onTabSelected(StatisticsTab.PLAYER)
        }
    }
}

@Composable
private fun StatisticsTabButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {

    Box(
        modifier = modifier
            .background(
                color =
                    if (selected)
                        Color(0xFFFF6600)
                    else
                        Color.Transparent,
                shape = RoundedCornerShape(9.dp)
            )
            .clickable {
                onClick()
            }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {

        Text(
            text = text,
            color =
                if (selected)
                    Color.White
                else
                    Color(0xFF9A9CA2),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TeamStatistics(
    matches: List<MatchStatUi>
) {

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item {

            Text(
                text = "FC HALCONES · SUB-17",
                color = Color(0xFFFF6600),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                StatisticCard(
                    title = "PARTIDOS",
                    value = "8",
                    modifier = Modifier.weight(1f)
                )

                StatisticCard(
                    title = "VICTORIAS",
                    value = "5",
                    modifier = Modifier.weight(1f)
                )

                StatisticCard(
                    title = "EMPATES",
                    value = "2",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                StatisticCard(
                    title = "DERROTAS",
                    value = "1",
                    modifier = Modifier.weight(1f)
                )

                StatisticCard(
                    title = "GOLES A FAVOR",
                    value = "16",
                    modifier = Modifier.weight(1f)
                )

                StatisticCard(
                    title = "GOLES EN CONTRA",
                    value = "7",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "ÚLTIMOS PARTIDOS",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        items(matches) { match ->

            MatchCard(match)
        }

    }
}

@Composable
private fun PlayerStatistics(
    players: List<PlayerStatUi>
) {

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item {

            Text(
                text = "ESTADÍSTICAS POR JUGADOR",
                color = Color(0xFFFF6600),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        items(players) { player ->

            PlayerCard(player)
        }

        item {

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Datos acumulados de demostración",
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF777A80),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Pendiente de conexión con eventos del partido",
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF777A80),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun StatisticCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier
            .border(
                width = 1.dp,
                color = Color(0xFF2A2E35),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161920)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 8.dp,
                    vertical = 14.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = value,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = title,
                color = Color(0xFF9A9CA2),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MatchCard(
    match: MatchStatUi
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161920)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "vs ${match.rival}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = match.date,
                    color = Color(0xFF9A9CA2),
                    fontSize = 11.sp
                )
            }

            Text(
                text = match.score,
                color = Color(0xFF00C853),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PlayerCard(
    player: PlayerStatUi
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161920)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp)
        ) {

            Text(
                text = player.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = player.position,
                color = Color(0xFFFF6600),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {

                PlayerMiniStat(
                    title = "PARTIDOS",
                    value = player.matches.toString(),
                    modifier = Modifier.weight(1f)
                )

                PlayerMiniStat(
                    title = "GOLES",
                    value = player.goals.toString(),
                    modifier = Modifier.weight(1f)
                )

                PlayerMiniStat(
                    title = "TARJETAS",
                    value = player.yellowCards.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PlayerMiniStat(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = title,
            color = Color(0xFF9A9CA2),
            fontSize = 9.sp
        )
    }
}