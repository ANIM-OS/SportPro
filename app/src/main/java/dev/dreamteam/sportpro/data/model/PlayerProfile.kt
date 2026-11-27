package dev.dreamteam.sportpro.data.model

import com.google.firebase.Timestamp

/**
 * US-04: perfil deportivo y de contacto de un jugador. Une playerProfiles/{playerId} (deportivo)
 * y playerPrivate/{playerId} (físicos, contacto y emergencia, sensibles según la US-22).
 * Todos los campos son opcionales.
 */
data class PlayerProfile(
    val playerId: String = "",
    val fullName: String = "",
    val position: String = "",
    val dominantFoot: String = "",
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val phone: String = "",
    val contactEmail: String = "",
    val emergencyName: String = "",
    val emergencyRelation: String = "",
    val emergencyPhone: String = "",
    val photoPath: String = "",
    val photoBase64: String = "",
    val updatedAt: Timestamp? = null
) {
    companion object {
        val POSITIONS = listOf("Portero", "Defensa central", "Lateral", "Mediocampista", "Extremo", "Delantero")
        val FEET = listOf("Derecho", "Izquierdo", "Ambos")
    }
}

/** Vínculo confirmado por el jugador: guardianLinks/{playerId}_{guardianId}. */
data class GuardianLink(
    val id: String = "",
    val playerId: String = "",
    val playerName: String = "",
    val guardianId: String = "",
    val guardianName: String = "",
    val status: String = "",
    val inviteId: String = ""
)

data class GuardianInvite(
    val id: String = "",
    val playerId: String = "",
    val playerName: String = "",
    val status: String = "",
    val guardianId: String = "",
    val guardianName: String = "",
    val expiresAt: Timestamp? = null
)
