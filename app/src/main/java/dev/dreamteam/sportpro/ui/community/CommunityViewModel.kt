package dev.dreamteam.sportpro.ui.community

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.CommunityPost
import dev.dreamteam.sportpro.data.model.CommunityTeamTarget
import dev.dreamteam.sportpro.data.repository.CommunityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CommunityUiState(
    val posts: List<CommunityPost> = emptyList(),
    val isLoading: Boolean = true,
    val isPublishing: Boolean = false,
    val errorMessage: String? = null,
    val canModerate: Boolean = false,
    val postTargets: List<CommunityTeamTarget> = emptyList(),
    val postTargetsError: String? = null,
    val moderatingPostId: String? = null,
    val moderationError: String? = null
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
        repository.loadPostTargets(
            onResult = { targets -> _uiState.value = _uiState.value.copy(postTargets = targets, postTargetsError = null) },
            onError = { message -> _uiState.value = _uiState.value.copy(postTargetsError = message) }
        )
        repository.checkModerator(
            onResult = { allowed -> _uiState.value = _uiState.value.copy(canModerate = allowed) },
            onError = { _uiState.value = _uiState.value.copy(canModerate = false) }
        )
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
        teamId: String?,
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
            teamId = teamId,

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

    fun moderatePost(postId: String) {
        if (!_uiState.value.canModerate || _uiState.value.moderatingPostId != null) return
        _uiState.value = _uiState.value.copy(moderatingPostId = postId, moderationError = null)
        repository.moderatePost(
            postId = postId,
            onSuccess = { _uiState.value = _uiState.value.copy(moderatingPostId = null) },
            onError = { message ->
                _uiState.value = _uiState.value.copy(moderatingPostId = null, moderationError = message)
            }
        )
    }

    override fun onCleared() {
        postsListener?.remove()
        super.onCleared()
    }
}
