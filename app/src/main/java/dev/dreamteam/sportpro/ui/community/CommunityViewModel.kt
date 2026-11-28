package dev.dreamteam.sportpro.ui.community

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.CommunityPost
import dev.dreamteam.sportpro.data.model.CommunityTeamTarget
import dev.dreamteam.sportpro.data.repository.CommunityRepository
import dev.dreamteam.sportpro.data.repository.ModerationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CommunityUiState(
    val posts: List<CommunityPost> = emptyList(),
    val isLoading: Boolean = true,
    val isPublishing: Boolean = false,
    // Separado de errorMessage: ese reemplaza la lista de publicaciones.
    val publishError: String? = null,
    val scrollToTopRequested: Boolean = false,
    val errorMessage: String? = null,
    val canModerate: Boolean = false,
    val postTargets: List<CommunityTeamTarget> = emptyList(),
    val postTargetsError: String? = null,
    val moderatingPostId: String? = null,
    val moderationError: String? = null,
    val moderationNotice: String? = null
)

class CommunityViewModel : ViewModel() {

    private val repository = CommunityRepository()
    private val moderation = ModerationRepository()

    private val _uiState =
        MutableStateFlow(CommunityUiState())

    val uiState: StateFlow<CommunityUiState> =
        _uiState.asStateFlow()

    private var postsListener: ListenerRegistration? = null

    init {
        loadPosts()
        loadPostTargets()
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

    private fun loadPostTargets() {
        repository.loadPostTargets(
            onResult = { targets -> _uiState.value = _uiState.value.copy(postTargets = targets, postTargetsError = null) },
            onError = { message -> _uiState.value = _uiState.value.copy(postTargetsError = message) }
        )
    }

    // El ViewModel se comparte con Comunidad, así que cada formulario nuevo
    // recarga los equipos disponibles y descarta el error anterior.
    fun prepareNewPost() {
        _uiState.value = _uiState.value.copy(publishError = null)
        loadPostTargets()
    }

    fun createPost(
        content: String,
        visibility: String,
        teamId: String?,
        viaTeamId: String? = null,
        onSuccess: () -> Unit
    ) {
        // El botón se deshabilita al recomponer; dos toques seguidos pueden llegar antes.
        if (_uiState.value.isPublishing) return

        if (content.isBlank()) {
            _uiState.value = _uiState.value.copy(
                publishError = "Escribe algo antes de publicar"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isPublishing = true,
            publishError = null
        )

        repository.createPost(
            content = content,
            visibility = visibility,
            teamId = teamId,
            viaTeamId = viaTeamId,

            onSuccess = {

                _uiState.value = _uiState.value.copy(
                    isPublishing = false,
                    scrollToTopRequested = true
                )

                onSuccess()
            },

            onError = { message ->

                _uiState.value = _uiState.value.copy(
                    isPublishing = false,
                    publishError = message
                )
            }
        )
    }

    fun onScrolledToTop() {
        _uiState.value = _uiState.value.copy(scrollToTopRequested = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null
        )
    }

    /**
     * US-21: ocultar ([hide] = true) o restaurar con moderatedBy/moderatedAt. Se aplica al instante (también
     * sin conexión); el aviso queda en "Pendiente de sincronizar" hasta que el servidor lo confirma.
     */
    fun setPostHidden(postId: String, hide: Boolean) {
        if (!_uiState.value.canModerate || _uiState.value.moderatingPostId != null) return
        _uiState.value = _uiState.value.copy(moderatingPostId = postId, moderationError = null, moderationNotice = null)
        moderation.setHidden(postId, null, hide,
            onSaved = {
                _uiState.value = _uiState.value.copy(moderatingPostId = null,
                    moderationNotice = (if (hide) "Publicación ocultada" else "Publicación restaurada") + " · Pendiente de sincronizar")
            },
            onSynced = {
                _uiState.value = _uiState.value.copy(moderationNotice = if (hide) "Publicación ocultada" else "Publicación restaurada")
            },
            onError = { message ->
                _uiState.value = _uiState.value.copy(moderatingPostId = null, moderationError = message, moderationNotice = null)
            })
    }

    /** US-21: reportar una publicación ajena con motivo (una vez por usuario). */
    fun reportPost(postId: String, reason: String) {
        _uiState.value = _uiState.value.copy(moderationError = null, moderationNotice = null)
        moderation.report(postId, null, reason,
            onSaved = { _uiState.value = _uiState.value.copy(moderationNotice = "Reporte guardado · Pendiente de sincronizar") },
            onSynced = { _uiState.value = _uiState.value.copy(moderationNotice = "Reporte enviado") },
            onError = { message -> _uiState.value = _uiState.value.copy(moderationError = message, moderationNotice = null) })
    }

    override fun onCleared() {
        postsListener?.remove()
        super.onCleared()
    }
}
