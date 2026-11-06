package dev.dreamteam.sportpro.data.model

data class TeamMember(
    val userId: String,
    val playerName: String,
    val category: String,
    val jerseyNumber: Int? = null
)
