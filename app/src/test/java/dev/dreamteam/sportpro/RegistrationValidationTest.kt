package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.ui.auth.validateRegistration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationValidationTest {
    private fun validate(
        firstName: String = "Ana",
        lastName: String = "Pérez",
        email: String = "ana@sportpro.dev",
        password: String = "secreta",
        confirmPassword: String = password,
        roles: Set<String> = setOf("JUGADOR")
    ) = validateRegistration(firstName, lastName, email, password, confirmPassword, roles)

    @Test
    fun validForm_hasNoErrors() {
        assertFalse(validate().hasErrors)
    }

    @Test
    fun multipleRoles_areAccepted() {
        assertNull(validate(roles = setOf("JUGADOR", "ENTRENADOR", "PADRE / TUTOR")).roles)
    }

    @Test
    fun emptyForm_reportsEveryField() {
        val errors = validate("", "  ", "", "", "", emptySet())
        assertNotNull(errors.firstName)
        assertNotNull(errors.lastName)
        assertNotNull(errors.email)
        assertNotNull(errors.password)
        assertNotNull(errors.confirmPassword)
        assertNotNull(errors.roles)
    }

    @Test
    fun invalidEmails_areRejected() {
        listOf("ana", "ana@", "@sportpro.dev", "ana@sportpro", "ana perez@sportpro.dev").forEach {
            assertEquals(it, "Ingresa un correo electrónico válido", validate(email = it).email)
        }
    }

    @Test
    fun emailWithSurroundingSpaces_isAccepted() {
        assertNull(validate(email = " ana@sportpro.dev ").email)
    }

    @Test
    fun shortPassword_isRejected() {
        assertEquals("La contraseña debe tener al menos 6 caracteres", validate(password = "12345").password)
        assertNull(validate(password = "123456").password)
    }

    @Test
    fun differentPasswords_areRejected() {
        val errors = validate(password = "secreta", confirmPassword = "otra-clave")
        assertEquals("Las contraseñas no coinciden", errors.confirmPassword)
        assertTrue(errors.hasErrors)
    }
}
