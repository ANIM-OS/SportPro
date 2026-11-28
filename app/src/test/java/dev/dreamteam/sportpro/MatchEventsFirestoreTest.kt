package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.EventRevision
import dev.dreamteam.sportpro.data.model.EventTeams
import dev.dreamteam.sportpro.data.model.MatchEventRecord
import dev.dreamteam.sportpro.data.model.MatchEvents
import dev.dreamteam.sportpro.data.model.MatchViewerRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parte 3: plazos, Sale ≠ Entra, campos guardados en Firestore, historial y nombres visibles por rol. */
class MatchEventsFirestoreTest {
    private val hour = 3_600_000L
    private val kickoff = 1_000_000_000L

    private fun goal(playerId: String? = "p1", playerName: String? = "Paula", extras: Map<String, String> = emptyMap(),
        extraNames: Map<String, String> = emptyMap()) =
        MatchEventRecord(id = "e", matchId = "m", typeId = "gol", typeName = "Gol", minute = 12, team = EventTeams.OWN,
            playerId = playerId, playerName = playerName, notes = "", extraFields = extras, extraNames = extraNames, authorId = "coach")

    @Test
    fun registerWindow_fromKickoffTo24Hours() {
        assertFalse(MatchEvents.canRegister(kickoff - 1, kickoff))
        assertTrue(MatchEvents.canRegister(kickoff, kickoff))
        assertTrue(MatchEvents.canRegister(kickoff + 24 * hour - 1, kickoff))
        assertFalse(MatchEvents.canRegister(kickoff + 24 * hour, kickoff))
    }

    @Test
    fun correctWindow_until7Days() {
        assertTrue(MatchEvents.canCorrect(kickoff - hour, kickoff))
        assertTrue(MatchEvents.canCorrect(kickoff + 7 * 24 * hour - 1, kickoff))
        assertFalse(MatchEvents.canCorrect(kickoff + 7 * 24 * hour, kickoff))
    }

    @Test
    fun samePlayerInAndOut_isRejected() {
        assertTrue(MatchEvents.sameSaleEntra(mapOf("sale" to "p1", "entra" to "p1")))
        assertFalse(MatchEvents.sameSaleEntra(mapOf("sale" to "p1", "entra" to "p2")))
        assertFalse(MatchEvents.sameSaleEntra(mapOf("sale" to "p1")))
        assertFalse(MatchEvents.sameSaleEntra(mapOf("sale" to "", "entra" to "")))
        assertEquals(MatchEvents.SAME_PLAYER_MESSAGE, MatchEvents.validate("cambio", "60", mapOf("sale" to "p1", "entra" to "p1")))
        assertNull(MatchEvents.validate("cambio", "60", mapOf("sale" to "p1", "entra" to "p2")))
    }

    @Test
    fun editableSnapshot_omitsEmptyOptionalFields() {
        val empty = MatchEventRecord(matchId = "m", typeId = "falta", typeName = "Falta", minute = null, team = null,
            playerId = null, notes = "  ", authorId = "coach")
        assertEquals(setOf("typeId", "typeName", "extras", "extraNames", "voided"), MatchEvents.editableSnapshot(empty).keys)
        val full = MatchEvents.editableSnapshot(goal().copy(notes = "Cabezazo"))
        assertEquals(12L, full["minute"])
        assertEquals("Paula", full["playerName"])
        assertEquals("Cabezazo", full["notes"])
    }

    @Test
    fun historyEntries_chainPreviousAndCurrent_newestFirst() {
        val v1 = mapOf<String, Any?>("typeName" to "Gol", "minute" to 10L)
        val v2 = mapOf<String, Any?>("typeName" to "Gol", "minute" to 12L)
        val current = mapOf<String, Any?>("typeName" to "Gol", "minute" to 12L, "voided" to true)
        val revisions = listOf(EventRevision(1, "CREATE", emptyMap(), "coach", 1), EventRevision(2, "CORRECTION", v1, "coach", 2),
            EventRevision(3, "VOID", v2, "coach", 3))
        val entries = MatchEvents.historyEntries("m", "e", revisions, current)
        assertEquals(listOf(3, 2), entries.map { it.version })
        assertEquals(v2, entries[0].previous); assertEquals(current, entries[0].current)
        assertEquals(v1, entries[1].previous); assertEquals(v2, entries[1].current)
    }

    @Test
    fun visibleNames_byRole_likeTheLineup() {
        val events = listOf(goal(), goal(playerId = "p2", playerName = "Bea"),
            goal(playerId = null, playerName = null, extras = mapOf("sale" to "p3", "entra" to "p4"), extraNames = mapOf("sale" to "Caro", "entra" to "Dani")))
        val (owner, ownerDefault) = MatchEvents.visibleNames(MatchViewerRole.OWNER, events, mapOf("p1" to "Paula R."))
        assertEquals("Paula R.", owner["p1"]); assertEquals("Caro", owner["p3"]); assertEquals("Jugador", ownerDefault)
        val (player, _) = MatchEvents.visibleNames(MatchViewerRole.PLAYER, events, emptyMap())
        assertEquals(setOf("p1", "p2", "p3", "p4"), player.keys)
        val (guardian, guardianDefault) = MatchEvents.visibleNames(MatchViewerRole.GUARDIAN, events, emptyMap(), "p2", "Beatriz")
        assertEquals(mapOf("p2" to "Bea"), guardian); assertEquals("Compañero", guardianDefault)
        val (admin, adminDefault) = MatchEvents.visibleNames(MatchViewerRole.ADMIN, events, mapOf("p1" to "Paula"))
        assertTrue(admin.isEmpty()); assertEquals("Jugador", adminDefault)
    }
}
