package dev.dreamteam.sportpro.ui.community

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dreamteam.sportpro.data.model.CommunityRules

/** US-19/US-21: elegir el motivo del reporte (publicación o comentario). */
@Composable
fun ReportReasonDialog(title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf(CommunityRules.REPORT_REASONS.first()) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Elige el motivo. Solo lo verá el equipo de moderación.")
                Spacer(Modifier.height(8.dp))
                CommunityRules.REPORT_REASONS.forEach { option ->
                    Row(Modifier.fillMaxWidth().selectable(selected = reason == option, onClick = { reason = option })
                        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == option, onClick = { reason = option })
                        Text(option)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason) }) { Text("REPORTAR") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } })
}
