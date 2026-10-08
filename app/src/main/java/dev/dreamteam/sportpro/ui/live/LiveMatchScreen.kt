package dev.dreamteam.sportpro.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LiveEventUi(
    val minute: String,
    val type: String,
    val player: String,
    val team: String
)

@Composable
fun LiveMatchScreen() {

    // DATOS DEMO
    // Más adelante se reemplazarán por eventos reales de Firestore.
    val events = listOf(
        LiveEventUi(
            minute = "11'",
            type = "GOL",
            player = "Carlos Mendoza",
            team = "FC Halcones"
        ),
        LiveEventUi(
            minute = "35'",
            type = "TARJETA",
            player = "Pablo Ortiz",
            team = "FC Monterrey"
        ),
        LiveEventUi(
            minute = "49'",
            type = "GOL",
            player = "Luis Herrera",
            team = "FC Halcones"
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
            text = "EN VIVO",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Sigue el partido en tiempo real",
            color = Color(0xFF9A9CA2),
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        ScoreCard()

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "CRONOLOGÍA DEL PARTIDO",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            items(events) { event ->
                EventCard(event)
            }

        }
    }
}

@Composable
private fun ScoreCard() {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(0xFF2A2E35),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161920)
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        Color(0xFF00C853).copy(alpha = 0.15f)
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFF00C853),
                        shape = RoundedCornerShape(50)
                    )
                    .padding(
                        horizontal = 12.dp,
                        vertical = 5.dp
                    )
            ) {

                Text(
                    text = "● EN JUEGO",
                    color = Color(0xFF00C853),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                TeamScore(
                    modifier = Modifier.weight(1f),
                    team = "FC Halcones",
                    score = "2"
                )

                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "-",
                        color = Color(0xFF9A9CA2),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "67'",
                        color = Color(0xFFFF6600),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                TeamScore(
                    modifier = Modifier.weight(1f),
                    team = "FC Monterrey",
                    score = "0"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Partido de liga · Categoría Sub-17",
                color = Color(0xFF9A9CA2),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TeamScore(
    modifier: Modifier = Modifier,
    team: String,
    score: String
) {

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    Color(0xFFFF6600).copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {

            Icon(
                imageVector = Icons.Default.SportsSoccer,
                contentDescription = null,
                tint = Color(0xFFFF6600)
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = team,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = score,
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EventCard(
    event: LiveEventUi
) {

    val isGoal = event.type == "GOL"

    val eventColor =
        if (isGoal) {
            Color(0xFF00C853)
        } else {
            Color(0xFFFFCC00)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161920)
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        eventColor.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {

                if (isGoal) {

                    Icon(
                        imageVector = Icons.Default.SportsSoccer,
                        contentDescription = null,
                        tint = eventColor
                    )

                } else {

                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = eventColor
                    )
                }
            }

            Spacer(modifier = Modifier.padding(7.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = event.type,
                    color = eventColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = event.player,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = event.team,
                    color = Color(0xFF9A9CA2),
                    fontSize = 11.sp
                )
            }

            Text(
                text = event.minute,
                color = Color(0xFFFF6600),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}