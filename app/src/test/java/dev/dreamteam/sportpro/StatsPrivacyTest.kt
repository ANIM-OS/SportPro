package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.EventTeams
import dev.dreamteam.sportpro.data.model.MatchEventRecord
import dev.dreamteam.sportpro.data.model.MatchInput
import dev.dreamteam.sportpro.data.model.MatchViewerRole
import dev.dreamteam.sportpro.data.model.StatsPrivacy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** US-16 + US-22: qué ve cada rol en Estadísticas. */
class StatsPrivacyTest {
    private val day = 24 * 3_600_000L
    private val now = 100 * day
    private var n = 0

    private fun goal(player: String, name: String) = MatchEventRecord(id = "e${n++}", matchId = "m", typeId = "gol", typeName = "Gol",
        minute = 5, team = EventTeams.OWN, playerId = player, notes = "", authorId = "coach", playerName = name)

    // m1: Ana y Bea convocadas; m2: solo Bea. Ambos finalizados.
    private val m1 = MatchInput("m1", "t", "Halcones", "Sub-15", "Rival 1", now - 3 * day, null,
        listOf(goal("ana", "Ana"), goal("bea", "Bea")), calledIds = setOf("ana", "bea"))
    private val m2 = MatchInput("m2", "t", "Halcones", "Sub-15", "Rival 2", now - 2 * day, null,
        listOf(goal("bea", "Bea"), goal("bea", "Bea")), calledIds = setOf("bea"))
    private val roster = mapOf("ana" to "Ana Pérez", "bea" to "Bea Ruiz")
    private val all = listOf(m1, m2)

    @Test
    fun owner_seesEverything_withNames() {
        val v = StatsPrivacy.view(MatchViewerRole.OWNER, "coach", "", all, roster, now)
        assertEquals(setOf("ana", "bea"), v.players.map { it.playerId }.toSet())
        assertEquals("Ana Pérez", v.players.first { it.playerId == "ana" }.name)
        assertEquals(2, v.team!!.played)
        assertEquals(4, v.team!!.goalsFor)
        assertTrue(v.showPlayerNames)
        assertEquals(2, v.matches.size)
        assertNotNull(v.matches.first().stats)
    }

    @Test
    fun player_seesOnlyOwnLine_andTeamTotalsOfCalledMatches() {
        val v = StatsPrivacy.view(MatchViewerRole.PLAYER, "ana", "", all, emptyMap(), now)
        assertEquals(listOf("ana"), v.players.map { it.playerId })
        assertEquals(1, v.players.single().goals)
        // Solo m1 (convocada); m2 no aparece ni suma al equipo.
        assertEquals(listOf("m1"), v.matches.map { it.matchId })
        assertEquals(1, v.team!!.played)
        assertEquals(2, v.team!!.goalsFor)
        // Ninguna fila ni nombre de compañeras.
        assertTrue(v.matches.all { it.ownLine?.playerId == "ana" })
        assertFalse(v.players.any { it.name.contains("Bea") })
    }

    @Test
    fun guardian_seesOnlyLinkedPlayer_withoutTeamTotals() {
        val v = StatsPrivacy.view(MatchViewerRole.GUARDIAN, "tutor", "bea", all, mapOf("bea" to "Bea Ruiz"), now)
        assertEquals(listOf("bea"), v.players.map { it.playerId })
        assertEquals(3, v.players.single().goals)
        assertEquals("Bea Ruiz", v.players.single().name)
        assertNull(v.team)
        assertTrue(v.matches.all { it.stats == null && it.ownLine?.playerId == "bea" })
        assertEquals(setOf("m1", "m2"), v.matches.map { it.matchId }.toSet())
    }

    @Test
    fun guardian_withoutCalledMatches_seesNothing() {
        val v = StatsPrivacy.view(MatchViewerRole.GUARDIAN, "tutor", "otro", all, emptyMap(), now)
        assertTrue(v.players.isEmpty())
        assertTrue(v.matches.isEmpty())
    }

    @Test
    fun admin_seesOnlyNumbers_noPlayersNoNames() {
        val v = StatsPrivacy.view(MatchViewerRole.ADMIN, "admin", "", all, roster, now)
        assertTrue(v.players.isEmpty())
        assertFalse(v.showPlayerNames)
        assertEquals(4, v.team!!.goalsFor)
        assertTrue(v.matches.all { it.ownLine == null && it.stats != null })
    }
}
