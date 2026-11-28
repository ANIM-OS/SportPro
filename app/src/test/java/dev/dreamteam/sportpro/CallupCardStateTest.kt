package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.ui.auth.AppRole
import dev.dreamteam.sportpro.ui.auth.RoleAccess
import dev.dreamteam.sportpro.ui.auth.RoleCapability
import dev.dreamteam.sportpro.ui.callups.CallupCardState
import dev.dreamteam.sportpro.ui.callups.callupCardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** US-09: estado que ven el jugador y el tutor en su tarjeta de convocatoria. */
class CallupCardStateTest {
    private val now = 1_000_000L

    @Test
    fun futureMatch_selected_isConvocado() =
        assertEquals(CallupCardState.CONVOCADO, callupCardState(true, true, now + 60_000, now))

    @Test
    fun withdrawn_isRetirada() =
        assertEquals(CallupCardState.RETIRADA, callupCardState(false, true, now + 60_000, now))

    @Test
    fun kickoffPassed_isFinalizado() {
        assertEquals(CallupCardState.FINALIZADO, callupCardState(true, true, now - 1, now))
        assertEquals(CallupCardState.FINALIZADO, callupCardState(true, true, now, now))
    }

    @Test
    fun guardianWithoutFixtureAccess_usesEncounterKickoff() {
        assertEquals(CallupCardState.CONVOCADO, callupCardState(true, null, now + 1, now))
        assertEquals(CallupCardState.FINALIZADO, callupCardState(true, null, now - 1, now))
    }

    @Test
    fun missingFixture_isCancelado_evenIfWithdrawnOrPast() {
        assertEquals(CallupCardState.CANCELADO, callupCardState(true, false, now + 1, now))
        assertEquals(CallupCardState.CANCELADO, callupCardState(false, false, now - 1, now))
        assertEquals(CallupCardState.CANCELADO, callupCardState(true, false, null, now))
    }

    @Test
    fun noKickoffKnown_isSinConfirmar() =
        assertEquals(CallupCardState.SIN_CONFIRMAR, callupCardState(true, null, null, now))

    @Test
    fun matchesScreenCoachSection_requiresApproval() {
        assertTrue(RoleAccess.can(setOf(AppRole.COACH), RoleCapability.CREATE_TEAM, true))
        assertFalse(RoleAccess.can(setOf(AppRole.COACH), RoleCapability.CREATE_TEAM, false))
    }
}
