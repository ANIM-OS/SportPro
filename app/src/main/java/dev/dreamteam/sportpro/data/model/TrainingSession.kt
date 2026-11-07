package dev.dreamteam.sportpro.data.model

import com.google.firebase.Timestamp
import java.util.Date

/**
 * Copia de un ejercicio dentro de una sesión. Se guarda completa para que editar o eliminar
 * el ejercicio de la biblioteca no altere las sesiones ya registradas (US-07).
 */
data class SessionExercise(
    val exerciseId: String = "",
    val name: String = "",
    val description: String = "",
    val durationMinutes: Int = 0
)

/** US-06: sesión de entrenamiento planificada para un equipo o una de sus categorías. */
data class TrainingSession(
    val id: String = "",
    val teamId: String = "",
    val teamName: String = "",
    val category: String = ALL_CATEGORIES,
    val createdBy: String = "",
    val date: Timestamp? = null,
    val durationMinutes: Int = 0,
    val objectives: String = "",
    val exercises: List<SessionExercise> = emptyList(),
    val createdAt: Timestamp? = null
) {
    /** Una sesión solo puede modificarse antes de su realización. */
    fun isEditable(now: Date = Date()): Boolean = date?.toDate()?.after(now) == true

    /** Indica si la sesión corresponde a un jugador de la categoría indicada. */
    fun appliesTo(memberCategory: String): Boolean =
        category == ALL_CATEGORIES || category == memberCategory

    companion object {
        const val ALL_CATEGORIES = "TODAS"
    }
}
