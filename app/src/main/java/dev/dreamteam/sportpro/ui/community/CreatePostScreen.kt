package dev.dreamteam.sportpro.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun CreatePostScreen(
    onBack: () -> Unit,
    viewModel: CommunityViewModel = viewModel()
) {

    val uiState by viewModel.uiState.collectAsState()

    var content by remember {
        mutableStateOf("")
    }

    var selectedVisibility by remember {
        mutableStateOf("TODOS")
    }

    var expanded by remember {
        mutableStateOf(false)
    }

    val visibilityOptions = listOf(
        "TODOS",
        "EQUIPO",
        "ACADEMIA"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = "NUEVA PUBLICACIÓN",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "PUBLICACIÓN",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = content,
            onValueChange = {
                if (it.length <= 1000) {
                    content = it
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            placeholder = {
                Text(
                    "¿Qué quieres compartir con la comunidad?"
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = "${content.length}/1000",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.End)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "VISIBILIDAD",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        expanded = true
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = visibilityText(selectedVisibility),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                }
            ) {

                visibilityOptions.forEach { visibility ->

                    DropdownMenuItem(
                        text = {
                            Text(
                                visibilityText(visibility)
                            )
                        },
                        onClick = {
                            selectedVisibility = visibility
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (uiState.errorMessage != null) {

            Text(
                text = uiState.errorMessage ?: "",
                color = Color(0xFFFF5252),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text(
                    text = "CANCELAR",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = {
                    viewModel.createPost(
                        content = content,
                        visibility = selectedVisibility
                    ) {
                        onBack()
                    }
                },
                enabled =
                    content.isNotBlank() &&
                            !uiState.isPublishing,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {

                if (uiState.isPublishing) {

                    CircularProgressIndicator(
                        color = Color.White
                    )

                } else {

                    Text(
                        text = "PUBLICAR",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun visibilityText(
    visibility: String
): String {

    return when (visibility) {
        "EQUIPO" -> "Mi equipo"
        "ACADEMIA" -> "Mi academia"
        else -> "Todos"
    }
}
