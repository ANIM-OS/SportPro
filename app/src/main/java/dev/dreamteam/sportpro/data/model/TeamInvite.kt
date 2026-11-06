package dev.dreamteam.sportpro.data.model

import com.google.firebase.Timestamp

data class TeamInvite(
    val id: String,
    val teamId: String,
    val teamName: String,
    val invitedEmail: String,
    val invitedBy: String,
    val status: String,
    val createdAt: Timestamp?
)
