package dev.dreamteam.sportpro.ui.tryouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dreamteam.sportpro.domain.SportCatalog
import dev.dreamteam.sportpro.domain.tryouts.TryoutFormErrors
import dev.dreamteam.sportpro.domain.tryouts.validateTryout
import java.time.LocalDate

@Composable
fun CreateTryoutScreen(
    onBack: () -> Unit,
    viewModel: TryoutsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.prepareNewNotice() }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableStateOf<String?>(null) }
    var tryoutDate by remember { mutableStateOf<LocalDate?>(null) }
    var location by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf<LocalDate?>(null) }
    var submitted by remember { mutableStateOf(false) }

    val errors = if (submitted) {
        validateTryout(title, description, location, tryoutDate, deadline, LocalDate.now())
    } else {
        TryoutFormErrors()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "NUEVO AVISO",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Título y descripción son obligatorios; lo demás es opcional.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )

        Column {
            TryoutTextField("TÍTULO", title, { title = it }, errors.title, placeholder = "Ej.: Prueba de arqueros Sub-15")
        }
        Column {
            TryoutTextField(
                "DESCRIPCIÓN", description, { description = it }, errors.description,
                placeholder = "Requisitos, qué llevar, a quién contactar", singleLine = false, minLines = 4
            )
        }
        Column {
            OptionPicker("CATEGORÍA", category, "Todas las categorías", SportCatalog.CATEGORIES) { category = it }
        }
        Column {
            OptionPicker("POSICIÓN", position, "Todas las posiciones", SportCatalog.POSITIONS) { position = it }
        }
        Column {
            DateField("FECHA DE LA PRUEBA", tryoutDate, "Sin fecha", { tryoutDate = it }, errors.tryoutDate)
        }
        Column {
            TryoutTextField("UBICACIÓN", location, { location = it }, errors.location, placeholder = "Cancha, distrito o ciudad")
        }
        Column {
            DateField("FECHA LÍMITE", deadline, "Sin fecha límite", { deadline = it }, errors.deadline)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "El aviso vence al terminar ese día. Sin fecha límite, sigue vigente hasta que lo canceles.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        uiState.publishError?.let { Text(it, color = Color(0xFFFF5252), fontSize = 13.sp) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text("CANCELAR", color = MaterialTheme.colorScheme.primary)
            }
            Button(
                onClick = {
                    submitted = true
                    val currentErrors = validateTryout(title, description, location, tryoutDate, deadline, LocalDate.now())
                    if (!currentErrors.hasErrors) {
                        viewModel.publish(
                            TryoutDraft(title, description, category, position, tryoutDate, location, deadline)
                        ) { onBack() }
                    }
                },
                enabled = !uiState.isPublishing,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                if (uiState.isPublishing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.height(20.dp))
                } else {
                    Text("PUBLICAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
