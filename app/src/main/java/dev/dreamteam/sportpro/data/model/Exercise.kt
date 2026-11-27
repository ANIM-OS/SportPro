package dev.dreamteam.sportpro.data.model

import com.google.firebase.Timestamp

/**
 * US-07: ejercicio de la biblioteca compartida. Todos los roles lo ven; solo su autora
 * (entrenadora aprobada) lo edita o elimina.
 */
data class Exercise(
    val id: String = "",
    val createdBy: String = "",
    // Nombre de la autora guardado al crear o editar (firestore.rules lo contrasta con users/{uid}).
    val creatorName: String = "",
    val name: String = "",
    val description: String = "",
    val durationMinutes: Int = 0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun isOwnedBy(userId: String?): Boolean = !userId.isNullOrEmpty() && createdBy == userId

    /** Texto "Creado por …"; los ejercicios antiguos sin nombre de autora muestran "Entrenadora". */
    fun creatorLabel(currentUserId: String?): String = when {
        isOwnedBy(currentUserId) -> "ti"
        creatorName.isNotBlank() -> creatorName
        else -> "Entrenadora"
    }

    companion object {
        /** Igual que coachDisplayName() en firestore.rules: "nombre apellido" sin espacios sobrantes. */
        fun displayName(firstName: String?, lastName: String?): String =
            "${firstName.orEmpty()} ${lastName.orEmpty()}".trim()
    }
}
