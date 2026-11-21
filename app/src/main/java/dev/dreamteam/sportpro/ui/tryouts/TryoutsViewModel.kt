package dev.dreamteam.sportpro.ui.tryouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.TryoutNotice
import dev.dreamteam.sportpro.data.repository.TryoutRepository
import dev.dreamteam.sportpro.domain.tryouts.TryoutFilter
import dev.dreamteam.sportpro.domain.tryouts.endOfDayMillis
import dev.dreamteam.sportpro.domain.tryouts.startOfDayMillis
import dev.dreamteam.sportpro.domain.tryouts.validateTryout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TryoutDraft(
    val title: String,
    val description: String,
    val category: String?,
    val position: String?,
    val tryoutDate: LocalDate?,
    val location: String,
    val deadline: LocalDate?
)

data class TryoutsUiState(
    val activeNotices: List<TryoutNotice> = emptyList(),
    val myNotices: List<TryoutNotice> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val filter: TryoutFilter = TryoutFilter(),
    val nowMillis: Long = System.currentTimeMillis(),
    val currentUserId: String? = null,
    val isAdmin: Boolean = false,
    val isPublishing: Boolean = false,
    val publishError: String? = null,
    val cancellingId: String? = null,
    val actionError: String? = null
)

class TryoutsViewModel : ViewModel() {

    private val repository = TryoutRepository()
    private val _uiState = MutableStateFlow(TryoutsUiState(currentUserId = repository.currentUserId))
    val uiState: StateFlow<TryoutsUiState> = _uiState.asStateFlow()
    private val registrations = mutableListOf<ListenerRegistration>()

    init {
        registrations += repository.observeActive(
            onUpdate = { notices -> _uiState.update { it.copy(activeNotices = notices, isLoading = false, loadError = null) } },
            onError = { message -> _uiState.update { it.copy(isLoading = false, loadError = message) } }
        )
        registrations += repository.observeMine(
            onUpdate = { notices -> _uiState.update { it.copy(myNotices = notices) } },
            onError = { message -> _uiState.update { it.copy(actionError = message) } }
        )
        repository.checkAdmin { isAdmin -> _uiState.update { it.copy(isAdmin = isAdmin) } }
        // Vigente o vencido depende de la hora: se recalcula sin recargar la lista.
        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                _uiState.update { it.copy(nowMillis = System.currentTimeMillis()) }
            }
        }
    }

    fun setFilter(filter: TryoutFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    // Se comparte con la lista, así que cada formulario nuevo empieza sin el error anterior.
    fun prepareNewNotice() {
        _uiState.update { it.copy(publishError = null) }
    }

    fun publish(draft: TryoutDraft, onSuccess: () -> Unit) {
        if (_uiState.value.isPublishing) return
        val errors = validateTryout(
            draft.title, draft.description, draft.location, draft.tryoutDate, draft.deadline, LocalDate.now()
        )
        if (errors.hasErrors) {
            _uiState.update { it.copy(publishError = "Revisa los campos marcados") }
            return
        }
        _uiState.update { it.copy(isPublishing = true, publishError = null) }
        repository.create(
            title = draft.title,
            description = draft.description,
            category = draft.category,
            position = draft.position,
            tryoutDateMillis = draft.tryoutDate?.startOfDayMillis(),
            location = draft.location,
            deadlineMillis = draft.deadline?.endOfDayMillis(),
            onSuccess = {
                _uiState.update { it.copy(isPublishing = false, nowMillis = System.currentTimeMillis()) }
                onSuccess()
            },
            onError = { message -> _uiState.update { it.copy(isPublishing = false, publishError = message) } }
        )
    }

    fun cancel(notice: TryoutNotice) {
        if (_uiState.value.cancellingId != null) return
        _uiState.update { it.copy(cancellingId = notice.id, actionError = null) }
        repository.cancel(
            noticeId = notice.id,
            onSuccess = { _uiState.update { it.copy(cancellingId = null) } },
            onError = { message -> _uiState.update { it.copy(cancellingId = null, actionError = message) } }
        )
    }

    fun clearActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    override fun onCleared() {
        registrations.forEach(ListenerRegistration::remove)
        super.onCleared()
    }
}
