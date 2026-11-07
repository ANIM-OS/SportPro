package dev.dreamteam.sportpro.ui.auth

const val MIN_PASSWORD_LENGTH = 6

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** One message per registration field; null means the field is valid. */
data class RegistrationErrors(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val password: String? = null,
    val confirmPassword: String? = null,
    val roles: String? = null
) {
    val hasErrors: Boolean
        get() = listOf(firstName, lastName, email, password, confirmPassword, roles).any { it != null }
}

fun validateRegistration(
    firstName: String,
    lastName: String,
    email: String,
    password: String,
    confirmPassword: String,
    roles: Set<String>
): RegistrationErrors = RegistrationErrors(
    firstName = if (firstName.isBlank()) "Ingresa tu nombre" else null,
    lastName = if (lastName.isBlank()) "Ingresa tu apellido" else null,
    email = when {
        email.isBlank() -> "Ingresa tu correo electrónico"
        !EMAIL_PATTERN.matches(email.trim()) -> "Ingresa un correo electrónico válido"
        else -> null
    },
    password = when {
        password.isEmpty() -> "Ingresa una contraseña"
        password.length < MIN_PASSWORD_LENGTH -> "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres"
        else -> null
    },
    confirmPassword = when {
        confirmPassword.isEmpty() -> "Confirma tu contraseña"
        confirmPassword != password -> "Las contraseñas no coinciden"
        else -> null
    },
    roles = if (roles.isEmpty()) "Selecciona al menos un rol" else null
)
