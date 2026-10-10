package dev.dreamteam.sportpro.data.model

import com.google.firebase.Timestamp

/** Estados de asistencia a una sesión (US-08). */
enum class AttendanceStatus(val label: String) {
    PRESENTE("Presente"),
    TARDANZA("Tardanza"),
    AUSENTE("Ausente"),
    JUSTIFICADO("Justificado");

    /** Presente y tardanza cuentan como participación en el historial. */
    val countsAsAttended: Boolean get() = this == PRESENTE || this == TARDANZA

    companion object {
        fun fromCode(code: String?): AttendanceStatus? = entries.firstOrNull { it.name == code }
    }
}

/**
 * US-08: asistencia de un jugador a una sesión. El id del documento es "{sessionId}_{playerId}",
 * así un jugador nunca tiene dos registros en la misma sesión.
 */
data class AttendanceRecord(
    val id: String = "",
    val sessionId: String = "",
    val teamId: String = "",
    val teamName: String = "",
    val coachId: String = "",
    val playerId: String = "",
    val playerName: String = "",
    val status: AttendanceStatus = AttendanceStatus.PRESENTE,
    val sessionDate: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    companion object {
        fun documentId(sessionId: String, playerId: String): String = "${sessionId}_$playerId"
    }
}
