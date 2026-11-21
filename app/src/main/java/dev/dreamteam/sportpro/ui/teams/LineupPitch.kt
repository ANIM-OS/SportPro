package dev.dreamteam.sportpro.ui.teams

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dreamteam.sportpro.data.model.TeamMember

internal fun formationSlots(formation: String): List<Triple<Float, Float, String>> {
    val parts = formation.split('-').map(String::toInt)
    val rows = listOf(parts[2] to 0.18f, parts[1] to 0.43f, parts[0] to 0.72f, 1 to 0.90f)
    val names = listOf("DEL", "MED", "DEF", "POR")
    return rows.flatMapIndexed { rowIndex, (count, y) ->
        (0 until count).map { i -> Triple((i + 1f) / (count + 1f), y, names[rowIndex]) }
    }
}

@Composable
internal fun LineupPitch(formation: String, positions: Map<String, String>, members: List<TeamMember>, owner: Boolean, onSlot: (Int) -> Unit) {
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

