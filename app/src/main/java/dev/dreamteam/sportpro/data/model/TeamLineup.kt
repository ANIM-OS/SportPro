package dev.dreamteam.sportpro.data.model

data class TeamLineup(
    val category: String,
    val formation: String = "4-3-3",
    val positions: Map<String, String> = emptyMap()
)
