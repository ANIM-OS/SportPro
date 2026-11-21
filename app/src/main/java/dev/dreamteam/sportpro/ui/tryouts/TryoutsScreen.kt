package dev.dreamteam.sportpro.ui.tryouts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dreamteam.sportpro.data.model.TryoutNotice
import dev.dreamteam.sportpro.domain.SportCatalog
import dev.dreamteam.sportpro.domain.tryouts.TryoutFilter
import dev.dreamteam.sportpro.domain.tryouts.TryoutState
import dev.dreamteam.sportpro.domain.tryouts.state

private const val TAB_CURRENT = 0
private const val TAB_EXPIRED = 1
private const val TAB_MINE = 2

/** US-20: tryout and open call notices, shown as a tab inside Community. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryoutsScreen(
    canPublish: Boolean,
    onCreateClick: () -> Unit,
    viewModel: TryoutsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(TAB_CURRENT) }
    var confirmCancel by remember { mutableStateOf<TryoutNotice?>(null) }

    // "Mis avisos" también aparece si ya no es entrenador aprobado pero publicó antes.
    val showMine = canPublish || state.myNotices.isNotEmpty()
    val selectedTab = if (tab == TAB_MINE && !showMine) TAB_CURRENT else tab

    val filtered = state.activeNotices.filter { state.filter.matches(it) }
    val current = filtered.filter { it.state(state.nowMillis) == TryoutState.VIGENTE }
        .sortedByDescending { it.createdAtMillis ?: Long.MAX_VALUE }
    val expired = filtered.filter { it.state(state.nowMillis) == TryoutState.VENCIDO }
        .sortedByDescending { it.deadlineMillis }
    val mine = state.myNotices.sortedByDescending { it.createdAtMillis ?: Long.MAX_VALUE }

    confirmCancel?.let { notice ->
        AlertDialog(
            onDismissRequest = { confirmCancel = null },
            title = { Text("Cancelar aviso") },
            text = { Text("\"${notice.title}\" dejará de mostrarse como vigente. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { viewModel.cancel(notice); confirmCancel = null }) {
                    Text("Cancelar aviso", color = Color(0xFFFF5252))
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = null }) { Text("Volver") } }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (canPublish) {
                FloatingActionButton(
                    onClick = onCreateClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo aviso")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = selectedTab == TAB_CURRENT, onClick = { tab = TAB_CURRENT }, label = { Text("Vigentes (${current.size})") })
                FilterChip(selected = selectedTab == TAB_EXPIRED, onClick = { tab = TAB_EXPIRED }, label = { Text("Vencidos (${expired.size})") })
                if (showMine) {
                    FilterChip(selected = selectedTab == TAB_MINE, onClick = { tab = TAB_MINE }, label = { Text("Mis avisos") })
                }
            }

            if (selectedTab != TAB_MINE) {
                TryoutFilters(filter = state.filter, onChange = viewModel::setFilter)
            }

            state.actionError?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, color = Color(0xFFFF5252), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearActionError) { Text("Cerrar") }
                }
            }

            val notices = when (selectedTab) {
                TAB_EXPIRED -> expired
                TAB_MINE -> mine
                else -> current
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                state.loadError != null && selectedTab != TAB_MINE -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.loadError ?: "", color = Color(0xFFFF5252))
                }
                notices.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = when {
                            selectedTab == TAB_MINE -> "Aún no publicaste avisos"
                            state.filter.isActive -> "Ningún aviso coincide con los filtros"
                            selectedTab == TAB_EXPIRED -> "No hay avisos vencidos"
                            else -> "No hay convocatorias vigentes"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(items = notices, key = { it.id }) { notice ->
                        val noticeState = notice.state(state.nowMillis)
                        TryoutCard(
                            notice = notice,
                            noticeState = noticeState,
                            showStatus = selectedTab == TAB_MINE,
                            canCancel = noticeState == TryoutState.VIGENTE &&
                                (notice.authorId == state.currentUserId || state.isAdmin),
                            isCancelling = state.cancellingId == notice.id,
                            onCancel = { confirmCancel = notice }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TryoutFilters(filter: TryoutFilter, onChange: (TryoutFilter) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Ocultar filtros" else if (filter.isActive) "Filtros activos" else "Filtrar")
        }
        if (filter.isActive) {
            TextButton(onClick = { onChange(TryoutFilter()) }) { Text("Limpiar") }
        }
    }
    if (expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                OptionPicker("CATEGORÍA", filter.category, "Todas las categorías", SportCatalog.CATEGORIES) {
                    onChange(filter.copy(category = it))
                }
            }
            Column {
                OptionPicker("POSICIÓN", filter.position, "Todas las posiciones", SportCatalog.POSITIONS) {
                    onChange(filter.copy(position = it))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    DateField("PRUEBA DESDE", filter.fromDate, "Cualquier día", { onChange(filter.copy(fromDate = it)) })
                }
                Column(Modifier.weight(1f)) {
                    DateField("HASTA", filter.toDate, "Cualquier día", { onChange(filter.copy(toDate = it)) })
                }
            }
            Column {
                TryoutTextField(
                    label = "UBICACIÓN",
                    value = filter.location,
                    onValueChange = { onChange(filter.copy(location = it)) },
                    placeholder = "Distrito, cancha o ciudad"
                )
            }
            Text(
                text = "Los avisos sin categoría o posición están abiertos a todas y siempre se muestran. " +
                    "Al filtrar por fecha o ubicación solo aparecen los avisos que tienen ese dato.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TryoutCard(
    notice: TryoutNotice,
    noticeState: TryoutState,
    showStatus: Boolean,
    canCancel: Boolean,
    isCancelling: Boolean,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notice.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                if (showStatus) {
                    val (label, color) = when (noticeState) {
                        TryoutState.VIGENTE -> "VIGENTE" to MaterialTheme.colorScheme.primary
                        TryoutState.VENCIDO -> "VENCIDO" to MaterialTheme.colorScheme.onSurfaceVariant
                        TryoutState.CANCELADO -> "CANCELADO" to Color(0xFFFF5252)
                    }
                    Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Por ${notice.authorName}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(notice.description, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(2.dp))
            InfoLine("Categoría", notice.category ?: "Todas las categorías")
            InfoLine("Posición", notice.position ?: "Todas las posiciones")
            notice.tryoutDateMillis?.let { InfoLine("Fecha de prueba", it.formattedDate()) }
            notice.location?.let { InfoLine("Ubicación", it) }
            InfoLine("Fecha límite", notice.deadlineMillis?.formattedDate() ?: "Sin fecha límite")
            if (canCancel) {
                TextButton(onClick = onCancel, enabled = !isCancelling) {
                    Text(if (isCancelling) "Cancelando…" else "Cancelar aviso", color = Color(0xFFFF5252))
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text("$label: ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
