package dev.dreamteam.sportpro.data.model

import com.google.firebase.Timestamp

/** Estados simulados de una mensualidad (US-05). No se procesa dinero real. */
enum class FeeStatus(val label: String) {
    PENDIENTE("Pendiente"),
    PAGADA("Pagada");

    companion object {
        fun fromCode(code: String?): FeeStatus = entries.firstOrNull { it.name == code } ?: PENDIENTE
    }
}

/**
 * US-05: mensualidad simulada de un jugador. El id del documento es "{playerId}_{periodo}"
 * (periodo "yyyy-MM"), así no se puede duplicar el mismo periodo para un jugador.
 */
data class MonthlyFee(
    val id: String = "",
    val playerId: String = "",
    val playerName: String = "",
    val teamId: String = "",
    val teamName: String = "",
    val coachId: String = "",
    val period: String = "",
    val amount: Double? = null,
    val status: FeeStatus = FeeStatus.PENDIENTE,
    val paidAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    companion object {
        fun documentId(playerId: String, period: String): String = "${playerId}_$period"
    }
}
