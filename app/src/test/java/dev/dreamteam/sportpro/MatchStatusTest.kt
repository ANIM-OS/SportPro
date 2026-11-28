package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.MatchEventContext
import dev.dreamteam.sportpro.data.model.MatchEvents
import dev.dreamteam.sportpro.data.model.MatchStatus
import dev.dreamteam.sportpro.ui.matches.registerClosedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** US-15 #4: estado único del partido (No iniciado / En juego / Finalizado / Cancelado). */
class MatchStatusTest {
    private val kickoff = 10_000_000_000L
    private val day = 24 * 3_600_000L

    @Test
    fun beforeKickoff_isNotStarted() =
        assertEquals(MatchStatus.NOT_STARTED, MatchStatus.of(kickoff, null, kickoff - 1))

    @Test
    fun fromKickoffUntil24h_isLive() {
        assertEquals(MatchStatus.LIVE, MatchStatus.of(kickoff, null, kickoff))
        assertEquals(MatchStatus.LIVE, MatchStatus.of(kickoff, null, kickoff + day - 1))
    }

    @Test
    fun at24h_isFinishedAutomatically() =
        assertEquals(MatchStatus.FINISHED, MatchStatus.of(kickoff, null, kickoff + day))

    @Test
    fun endedByCoach_isFinishedImmediately() {
        assertEquals(MatchStatus.FINISHED, MatchStatus.of(kickoff, kickoff + 60_000, kickoff + 60_001))
        // Aunque el reloj del celular vaya atrasado, endedAt manda.
        assertEquals(MatchStatus.FINISHED, MatchStatus.of(kickoff, kickoff + 60_000, kickoff - 1))
    }

    @Test
    fun missingFixture_isCancelled_andUnknownKickoffIsNull() {
        assertEquals(MatchStatus.CANCELLED, MatchStatus.of(false, kickoff, kickoff + 1, kickoff))
        assertEquals(MatchStatus.CANCELLED, MatchStatus.of(false, null, null, kickoff))
        assertNull(MatchStatus.of(true, null, null, kickoff))
        assertNull(MatchStatus.of(null, null, null, kickoff))
    }

    @Test
    fun labels_inSpanish() =
        assertEquals(listOf("No iniciado", "En juego", "Finalizado", "Cancelado"), MatchStatus.entries.map { it.label })

    @Test
    fun canFinish_onlyWhileLive_once() {
        assertFalse(MatchStatus.canFinish(kickoff, null, kickoff - 1))
        assertTrue(MatchStatus.canFinish(kickoff, null, kickoff))
        assertTrue(MatchStatus.canFinish(kickoff, null, kickoff + day - 1))
        assertFalse(MatchStatus.canFinish(kickoff, null, kickoff + day))
        assertFalse(MatchStatus.canFinish(kickoff, kickoff + 5, kickoff + 10))
    }

    /** Decisión 4: tras finalizar no se registra; corregir/anular sigue hasta 7 días. */
    @Test
    fun afterFinish_noRegister_butCorrectUntil7Days() {
        assertTrue(MatchEvents.canRegister(kickoff + 10, kickoff, null))
        assertFalse(MatchEvents.canRegister(kickoff + 10, kickoff, kickoff + 5))
        assertTrue(MatchEvents.canCorrect(kickoff + 10, kickoff))
        assertTrue(MatchEvents.canCorrect(kickoff + 7 * day - 1, kickoff))
        assertFalse(MatchEvents.canCorrect(kickoff + 7 * day, kickoff))
    }

    @Test
    fun closedMessage_explainsWhy() {
        val ctx = MatchEventContext("m", "t", "Sub-15", kickoff, "Halcones", "Rival")
        assertTrue(registerClosedMessage(kickoff - 1, ctx).startsWith("Podrás registrar eventos"))
        assertTrue(registerClosedMessage(kickoff + 1, ctx.copy(endedAtMillis = kickoff)).startsWith("El partido terminó"))
        assertTrue(registerClosedMessage(kickoff + day, ctx).startsWith("El plazo para registrar eventos terminó"))
    }
}
