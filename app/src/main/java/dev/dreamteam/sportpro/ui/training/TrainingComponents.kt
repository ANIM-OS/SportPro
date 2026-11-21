package dev.dreamteam.sportpro.ui.training

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import dev.dreamteam.sportpro.data.model.AttendanceStatus
import dev.dreamteam.sportpro.data.model.FeeStatus
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.model.TrainingSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Colores de estado (los mismos verdes y ámbar que ya usa la app); el resto sale de MaterialTheme.
private val PositiveColor = Color(0xFF00C853)
private val WarningColor = Color(0xFFFFB300)

/** Barra superior de las pantallas internas. Sin insets porque la pantalla ya está dentro del Scaffold principal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        },
        actions = actions,
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = border
        ) { Column(modifier = Modifier.padding(16.dp), content = content) }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = border
        ) { Column(modifier = Modifier.padding(16.dp), content = content) }
    }
}

/** Tarjeta de acceso a un módulo, con la historia de usuario que cubre. */
@Composable
fun ModuleCard(title: String, description: String, story: String, icon: ImageVector, onClick: () -> Unit) {
    InfoCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = story, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SportTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun StatusPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun attendanceColor(status: AttendanceStatus): Color = when (status) {
    AttendanceStatus.PRESENTE -> PositiveColor
    AttendanceStatus.TARDANZA -> WarningColor
    AttendanceStatus.AUSENTE -> MaterialTheme.colorScheme.error
    AttendanceStatus.JUSTIFICADO -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun feeColor(status: FeeStatus): Color = when (status) {
    FeeStatus.PAGADA -> PositiveColor
    FeeStatus.PENDIENTE -> WarningColor
}

@Composable
fun MessageText(text: String, isError: Boolean = true) {
    Text(
        text = text,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        fontSize = 13.sp
    )
}

@Composable
fun EmptyState(text: String) {
    InfoCard {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/** Selector horizontal de equipos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamChips(teams: List<Team>, selectedTeamId: String?, onSelect: (Team) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        teams.forEach { team ->
            FilterChip(
                selected = team.id == selectedTeamId,
                onClick = { onSelect(team) },
                label = { Text(team.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

// ---------- Formatos de fecha y periodo ----------

private val spanishLocale: Locale = Locale.forLanguageTag("es-PE")

fun Timestamp?.toDateTimeText(): String =
    this?.toDate()?.let { SimpleDateFormat("EEE d MMM yyyy · HH:mm", spanishLocale).format(it) } ?: "Sin fecha"

fun Timestamp?.toDateText(): String =
    this?.toDate()?.let { SimpleDateFormat("d MMM yyyy", spanishLocale).format(it) } ?: "Sin fecha"

fun Long.toDateTimeText(): String = SimpleDateFormat("EEE d MMM yyyy · HH:mm", spanishLocale).format(Date(this))

fun categoryLabel(category: String): String =
    if (category == TrainingSession.ALL_CATEGORIES) "Todas las categorías" else category

/** Periodo actual en formato "yyyy-MM". */
fun currentPeriod(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

fun shiftPeriod(period: String, months: Int): String {
    val calendar = Calendar.getInstance()
    calendar.time = SimpleDateFormat("yyyy-MM", Locale.US).parse(period) ?: Date()
    calendar.add(Calendar.MONTH, months)
    return SimpleDateFormat("yyyy-MM", Locale.US).format(calendar.time)
}

fun periodLabel(period: String): String {
    val date = runCatching { SimpleDateFormat("yyyy-MM", Locale.US).parse(period) }.getOrNull() ?: return period
    return SimpleDateFormat("MMMM yyyy", spanishLocale).format(date).replaceFirstChar { it.uppercase() }
}

/** Une la fecha del DatePicker (medianoche UTC) con una hora local. */
fun combineDateAndTime(utcDateMillis: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDateMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), hour, minute)
    }.timeInMillis
}

/** Medianoche UTC del día de hoy, para no permitir fechas pasadas en el DatePicker. */
fun todayUtcMidnight(): Long {
    val local = Calendar.getInstance()
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
