package dev.dreamteam.sportpro.data.model

import com.google.firebase.Timestamp

/**
 * US-04: perfil deportivo y de contacto de un jugador (documento playerProfiles/{playerId}).
 * Todos los campos son opcionales; los físicos y de contacto son sensibles (US-22).
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

/**
 * Vínculo entre un jugador y el correo de su padre o tutor.
 * El id del documento es "{playerId}_{correo en minúsculas}".
 */
data class GuardianLink(
    val id: String = "",
    val playerId: String = "",
    val playerName: String = "",
    val guardianEmail: String = ""
) {
    companion object {
        fun documentId(playerId: String, email: String): String = "${playerId}_${email.trim().lowercase()}"
    }
}
