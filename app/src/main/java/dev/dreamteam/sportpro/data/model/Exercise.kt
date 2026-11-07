package dev.dreamteam.sportpro.data.model

import com.google.firebase.Timestamp

/** US-07: ejercicio de la biblioteca reutilizable de un entrenador. */
data class Exercise(
    val id: String = "",
    val createdBy: String = "",
    val name: String = "",
    val description: String = "",
    val durationMinutes: Int = 0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
