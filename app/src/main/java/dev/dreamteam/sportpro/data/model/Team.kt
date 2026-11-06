package dev.dreamteam.sportpro.data.model

import com.google.firebase.Timestamp

data class Team(
    val id: String = "",
    val createdBy: String = "",
    val name: String = "",
    val type: String = "EQUIPO", // "EQUIPO" or "ACADEMIA"
    val academyId: String? = null,
    val categories: List<String> = emptyList(),
    val city: String = "",
    val description: String = "",
    val photoBase64: String = "",
    val activeJoinCodeId: String? = null,
    val photoUpdatedAt: Timestamp? = null,
    val createdAt: Timestamp? = null
)
