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
    // Hora de la primera marca; los registros anteriores al historial de correcciones no la tienen.
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    // Última corrección (attendance/{id}/changes/{lastChangeId}); null si nunca se corrigió.
    val lastChangeId: String? = null
) {
    val wasCorrected: Boolean get() = lastChangeId != null

    companion object {
        fun documentId(sessionId: String, playerId: String): String = "${sessionId}_$playerId"
    }
}

/** US-08: corrección de una asistencia (de un estado a otro), quién la hizo y cuándo. */
data class AttendanceChange(
    val id: String = "",
    val from: AttendanceStatus,
    val to: AttendanceStatus,
    val changedBy: String = "",
    val changedAt: Timestamp? = null
)

/**
 * US-08: la asistencia solo se marca cuando la sesión ya empezó (firestore.rules usa la hora del
 * servidor; aquí se usa la del teléfono, así que puede variar unos segundos).
 */
fun canMarkAttendance(sessionDate: java.util.Date?, now: java.util.Date = java.util.Date()): Boolean =
    sessionDate != null && !sessionDate.after(now)

/** Registros de la sesión cuyo jugador ya no está en el equipo (se muestran en solo lectura). */
fun recordsOfFormerMembers(records: Collection<AttendanceRecord>, currentMemberIds: Set<String>): List<AttendanceRecord> =
    records.filter { it.playerId !in currentMemberIds }.sortedBy { it.playerName.lowercase() }
