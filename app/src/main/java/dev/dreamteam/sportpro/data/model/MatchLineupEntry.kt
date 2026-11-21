package dev.dreamteam.sportpro.data.model

/** One assignment per called player and match; the player cannot be starter and substitute at once. */
data class MatchLineupEntry(val playerId: String, val kind: String, val position: String)
