package dev.dreamteam.sportpro.data.model

data class CommunityTeamTarget(
    val id: String,
    val name: String,
    val type: String,
    /** US-18: destino ACADEMIA alcanzado por pertenecer a este equipo vinculado (academyAccess). */
    val viaTeamId: String? = null
)
