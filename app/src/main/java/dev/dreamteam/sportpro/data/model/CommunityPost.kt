package dev.dreamteam.sportpro.data.model

import com.google.firebase.Timestamp

data class CommunityPost(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorRole: String = "",
    val content: String = "",
    val visibility: String = "TODOS",
    val teamId: String? = null,
    val createdAt: Timestamp? = null,
    val active: Boolean = true,
    val moderated: Boolean = false
)
