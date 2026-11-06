package dev.dreamteam.sportpro.data.model

import com.google.firebase.Timestamp

data class TeamJoinCode(
    val id: String,
    val teamId: String,
    val teamName: String,
    val categories: List<String>,
    val expiresAt: Timestamp
)
