package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.MatchLineupEntry
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.ui.callups.CallupCardState
import dev.dreamteam.sportpro.ui.lineups.LINEUP_CONFLICT_MESSAGE
import dev.dreamteam.sportpro.ui.lineups.LINEUP_DENIED_MESSAGE
import dev.dreamteam.sportpro.ui.lineups.LINEUP_FORMATION_CHANGED
import dev.dreamteam.sportpro.ui.lineups.LINEUP_GENERIC_MESSAGE
import dev.dreamteam.sportpro.ui.lineups.LINEUP_NOT_CALLED
import dev.dreamteam.sportpro.ui.lineups.LINEUP_OFFLINE_MESSAGE
import dev.dreamteam.sportpro.ui.lineups.LINEUP_SLOT_TAKEN
import dev.dreamteam.sportpro.ui.lineups.LineupFailure
import dev.dreamteam.sportpro.ui.lineups.TEAMMATE_LABEL
import dev.dreamteam.sportpro.ui.lineups.guardianRoster
import dev.dreamteam.sportpro.ui.lineups.lineupErrorMessage
import dev.dreamteam.sportpro.ui.lineups.lineupErrorText
import dev.dreamteam.sportpro.ui.lineups.needsUnavailableConfirmation
import dev.dreamteam.sportpro.ui.lineups.readOnlyInvalid
import dev.dreamteam.sportpro.ui.lineups.showLineupButton
import dev.dreamteam.sportpro.ui.lineups.unavailableConfirmationText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** US-11: mensajes, confirmación de "No disponible", botón de la tarjeta y filtros de solo lectura. */
class LineupMessagesTest {
    @Test
    fun permissionDenied_isFriendlySpanish() =
        assertEquals("No se pudo guardar: puede que el partido haya empezado o que la convocatoria haya cambiado.",
            lineupErrorText(LineupFailure.DENIED, "PERMISSION_DENIED: Missing or insufficient permissions."))

    @Test
    fun otherFirestoreFailures_haveSpanishTexts() {
        assertEquals(LINEUP_OFFLINE_MESSAGE, lineupErrorText(LineupFailure.OFFLINE, null))
        assertEquals(LINEUP_CONFLICT_MESSAGE, lineupErrorText(LineupFailure.CONFLICT, null))
        assertEquals(LINEUP_GENERIC_MESSAGE, lineupErrorText(LineupFailure.OTHER, "Required value was null."))
    }

    @Test
    fun ownChecks_areShownAsIs() {
        for (text in listOf(LINEUP_FORMATION_CHANGED, LINEUP_SLOT_TAKEN, LINEUP_NOT_CALLED))
            assertEquals(text, lineupErrorText(LineupFailure.OTHER, text))
    }

    @Test
    fun exceptionChain_findsOwnCheckEvenWhenWrapped() {
        assertEquals(LINEUP_SLOT_TAKEN, lineupErrorMessage(RuntimeException("Transaction failed", IllegalStateException(LINEUP_SLOT_TAKEN))))
        assertEquals(LINEUP_GENERIC_MESSAGE, lineupErrorMessage(IllegalStateException("java.lang.NullPointerException at Foo.kt:12")))
    }

    @Test
    fun onlyUnavailableNeedsConfirmation() {
        assertTrue(needsUnavailableConfirmation("UNAVAILABLE"))
        assertFalse(needsUnavailableConfirmation("AVAILABLE"))
        assertFalse(needsUnavailableConfirmation(null))
        assertEquals("Ana respondió No disponible. ¿Quieres alinearlo igual?", unavailableConfirmationText("Ana"))
    }

    @Test
    fun lineupButton_hiddenForWithdrawnOrCancelled() {
        assertTrue(showLineupButton(CallupCardState.CONVOCADO))
        assertTrue(showLineupButton(CallupCardState.FINALIZADO))
        assertFalse(showLineupButton(CallupCardState.RETIRADA))
        assertFalse(showLineupButton(CallupCardState.CANCELADO))
        assertFalse(showLineupButton(CallupCardState.SIN_CONFIRMAR))
    }

    @Test
    fun readOnly_hidesPlayersOutsideTheCategory() {
        val entries = listOf(MatchLineupEntry("a", "STARTER", "P0"), MatchLineupEntry("b", "BENCH", "DEF"), MatchLineupEntry("c", "BENCH", "MED"))
        val roster = listOf(TeamMember("a", "Ana", "SUB-12"), TeamMember("b", "Bea", "SUB-14"))
        assertEquals(listOf("b", "c"), readOnlyInvalid(entries, roster, "SUB-12").map { it.playerId })
    }

    @Test
    fun guardian_seesOnlyOwnPlayerName() {
        val entries = listOf(MatchLineupEntry("hijo", "STARTER", "P10"), MatchLineupEntry("otro", "BENCH", "DEF"))
        val people = guardianRoster(entries, "hijo", "Lucas", "SUB-12")
        assertEquals(listOf("Lucas", TEAMMATE_LABEL), people.map { it.playerName })
        assertTrue(people.all { it.jerseyNumber == null })
    }
}
