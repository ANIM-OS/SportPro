package dev.dreamteam.sportpro.ui.community

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.CommunityPost
import dev.dreamteam.sportpro.data.repository.CommunityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CommunityUiState(
    val posts: List<CommunityPost> = emptyList(),
    val isLoading: Boolean = true,
    val isPublishing: Boolean = false,
    val errorMessage: String? = null
)

class CommunityViewModel : ViewModel() {

    private val repository = CommunityRepository()

    private val _uiState =
        MutableStateFlow(CommunityUiState())

    val uiState: StateFlow<CommunityUiState> =
        _uiState.asStateFlow()

    private var postsListener: ListenerRegistration? = null

    init {
        loadPosts()
    }

    private fun loadPosts() {

        postsListener?.remove()

        postsListener = repository.observePosts(
            onUpdate = { posts ->

                _uiState.value = _uiState.value.copy(
                    posts = posts,
                    isLoading = false,
                    errorMessage = null
                )
            },

            onError = { message ->

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = message
                )
            }
        )
    }

    fun createPost(
        content: String,
        visibility: String,
        onSuccess: () -> Unit
    ) {

        if (content.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Escribe algo antes de publicar"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isPublishing = true,
            errorMessage = null
        )

        repository.createPost(
            content = content,
            visibility = visibility,

            onSuccess = {

                _uiState.value = _uiState.value.copy(
                    isPublishing = false
                )

                onSuccess()
            },

            onError = { message ->

                _uiState.value = _uiState.value.copy(
                    isPublishing = false,
                    errorMessage = message
                )
            }
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null
        )
    }

    override fun onCleared() {
        postsListener?.remove()
        super.onCleared()
    }
}