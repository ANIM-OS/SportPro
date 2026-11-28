package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.ui.callups.AVAILABILITY_CLOSED_MESSAGE
import dev.dreamteam.sportpro.ui.callups.AVAILABILITY_DENIED_MESSAGE
import dev.dreamteam.sportpro.ui.callups.AVAILABILITY_GENERIC_MESSAGE
import dev.dreamteam.sportpro.ui.callups.AvailabilityResponse
import dev.dreamteam.sportpro.ui.callups.availabilityErrorMessage
import dev.dreamteam.sportpro.ui.callups.responderLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/** US-10: mensajes al fallar una respuesta y etiqueta de quién respondió. */
class AvailabilityMessagesTest {
    private val now = 10_000_000L
    private val minute = 60_000L

    @Test
    fun deniedAfterDeadline_isClosed() =
        assertEquals(AVAILABILITY_CLOSED_MESSAGE, availabilityErrorMessage(true, now - minute, now + 60 * minute, now))

    @Test
    fun deniedAfterKickoff_isClosed() =
        assertEquals(AVAILABILITY_CLOSED_MESSAGE, availabilityErrorMessage(true, null, now - 1, now))

    @Test
    fun deniedWithinClockMargin_isClosed() {
        assertEquals(AVAILABILITY_CLOSED_MESSAGE, availabilityErrorMessage(true, now + 4 * minute, now + 60 * minute, now))
        assertEquals(AVAILABILITY_CLOSED_MESSAGE, availabilityErrorMessage(true, now + 5 * minute, now + 60 * minute, now))
    }

    @Test
    fun deniedWellBeforeDeadline_isMembershipOrChange() =
        assertEquals(AVAILABILITY_DENIED_MESSAGE, availabilityErrorMessage(true, now + 6 * minute, now + 60 * minute, now))

    @Test
    fun deniedWithoutDates_isMembershipOrChange() =
        assertEquals(AVAILABILITY_DENIED_MESSAGE, availabilityErrorMessage(true, null, null, now))

    @Test
    fun otherErrors_keepGenericMessage() =
        assertEquals(AVAILABILITY_GENERIC_MESSAGE, availabilityErrorMessage(false, now - minute, now - minute, now))

    @Test
    fun responderLabel_distinguishesPlayerAndGuardian() {
        assertEquals("respondió el jugador", responderLabel(AvailabilityResponse("m_p", "p", "AVAILABLE", "p")))
        assertEquals("respondió el tutor", responderLabel(AvailabilityResponse("m_p", "p", "AVAILABLE", "g")))
    }
}
