package dev.dreamteam.sportpro.ui.training

import androidx.lifecycle.ViewModel
import dev.dreamteam.sportpro.data.model.Exercise
import dev.dreamteam.sportpro.data.repository.ExerciseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ExerciseLibraryUiState(
    val exercises: List<Exercise> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isSaving: Boolean = false,
    val formError: String? = null,
    val message: String? = null
)

/** US-07: biblioteca reutilizable de ejercicios del entrenador. */
class ExerciseLibraryViewModel : ViewModel() {

    private val repository = ExerciseRepository()
    private val _uiState = MutableStateFlow(ExerciseLibraryUiState())
    val uiState: StateFlow<ExerciseLibraryUiState> = _uiState.asStateFlow()

    private val listener = repository.listenMine(
        onChange = { list -> _uiState.update { it.copy(exercises = list, isLoading = false, error = null) } },
        onError = { message -> _uiState.update { it.copy(isLoading = false, error = message) } }
    )

    fun save(existing: Exercise?, name: String, description: String, durationText: String, onSaved: () -> Unit) {
        val cleanName = name.trim()
        val cleanDescription = description.trim()
        val duration = durationText.trim().toIntOrNull()
        val error = when {
            cleanName.isEmpty() -> "Ingresa el nombre del ejercicio"
            cleanName.length > 80 -> "El nombre admite hasta 80 caracteres"
            cleanDescription.length > 500 -> "La descripción admite hasta 500 caracteres"
            duration == null || duration !in 1..180 -> "La duración referencial debe estar entre 1 y 180 minutos"
            else -> null
        }
        if (error != null || duration == null) {
            _uiState.update { it.copy(formError = error) }
            return
        }
        _uiState.update { it.copy(isSaving = true, formError = null) }
        repository.save(
            existingId = existing?.id,
            name = cleanName,
            description = cleanDescription,
            durationMinutes = duration,
            onSuccess = {
                _uiState.update {
                    it.copy(isSaving = false, message = if (existing == null) "Ejercicio agregado" else "Ejercicio actualizado")
                }
                onSaved()
            },
            onError = { message -> _uiState.update { it.copy(isSaving = false, formError = message) } }
        )
    }

    fun delete(exercise: Exercise) {
        repository.delete(
            exerciseId = exercise.id,
            onSuccess = { _uiState.update { it.copy(message = "Ejercicio eliminado") } },
            onError = { message -> _uiState.update { it.copy(message = message) } }
        )
    }

    fun clearFormError() = _uiState.update { it.copy(formError = null) }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    override fun onCleared() {
        listener?.remove()
    }
}
