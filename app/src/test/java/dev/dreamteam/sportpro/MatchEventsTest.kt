package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.EventCatalogRules
import dev.dreamteam.sportpro.data.model.EventTeams
import dev.dreamteam.sportpro.data.model.ExtraField
import dev.dreamteam.sportpro.data.model.ExtraKinds
import dev.dreamteam.sportpro.data.model.MatchEventRecord
import dev.dreamteam.sportpro.data.model.MatchEvents
import dev.dreamteam.sportpro.data.model.MatchLineupEntry
import dev.dreamteam.sportpro.data.model.MatchTab
import dev.dreamteam.sportpro.data.model.MatchViewerRole
import dev.dreamteam.sportpro.data.model.PlayerOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parte 2: pestañas por rol, marcador OWN/OPPONENT, orden de jugadores y validaciones del evento. */
class MatchEventsTest {
    private fun event(id: String, type: String = "gol", team: String? = EventTeams.OWN, minute: Int? = 10,
        voided: Boolean = false, created: Long = 0) =
        MatchEventRecord(id = id, matchId = "m", typeId = type, typeName = type, minute = minute, team = team,
            playerId = null, notes = "", authorId = "coach", createdAtMillis = created, voided = voided)

    @Test
    fun owner_seesAllTabsAndManages() {
        val access = MatchEvents.access(MatchViewerRole.OWNER, callupSelected = true, cancelled = false)
        assertEquals(MatchTab.entries.toList(), access.tabs)
        assertTrue(access.canManage && access.canEditEvents)
    }

    @Test
    fun calledPlayerGuardianAndAdmin_seeAllTabsReadOnly() {
        for (role in listOf(MatchViewerRole.PLAYER, MatchViewerRole.GUARDIAN, MatchViewerRole.ADMIN)) {
            val access = MatchEvents.access(role, callupSelected = true, cancelled = false)
            assertEquals(role.name, MatchTab.entries.toList(), access.tabs)
            assertFalse(role.name, access.canManage || access.canEditEvents)
        }
    }

    @Test
    fun withdrawnCallup_hidesLineupAndLive() {
        for (role in listOf(MatchViewerRole.PLAYER, MatchViewerRole.GUARDIAN)) {
            assertEquals(listOf(MatchTab.INFO, MatchTab.CALLUP), MatchEvents.access(role, callupSelected = false, cancelled = false).tabs)
        }
    }

    @Test
    fun cancelledMatch_onlyInfoForEveryone() {
        for (role in MatchViewerRole.entries) {
            val access = MatchEvents.access(role, callupSelected = true, cancelled = true)
            assertEquals(listOf(MatchTab.INFO), access.tabs)
            assertFalse(access.canManage || access.canEditEvents)
        }
    }

    @Test
    fun score_countsValidGoalsByOwnAndOpponent() {
        val score = MatchEvents.score(listOf(
            event("1"), event("2"), event("3", team = EventTeams.OPPONENT),
            event("4", voided = true), // anulado: no cuenta
            event("5", team = null), // sin equipo: aparte
            event("6", type = "tarjeta"), // no es gol
            event("7", type = "falta", team = EventTeams.OPPONENT)))
        assertEquals(2, score.own)
        assertEquals(1, score.opponent)
        assertEquals(1, score.goalsWithoutTeam)
    }

    @Test
    fun score_usesCurrentVersionOfCorrectedEvent() {
        // Un gol corregido de "nuestro" a "rival" cuenta solo en su versión actual.
        val corrected = event("1").copy(team = EventTeams.OPPONENT, version = 2)
        val score = MatchEvents.score(listOf(corrected))
        assertEquals(0, score.own)
        assertEquals(1, score.opponent)
    }

    @Test
    fun chronology_ordersByMinuteThenCreationWithoutMinuteLast() {
        val ordered = MatchEvents.chronology(listOf(event("a", minute = null, created = 1), event("b", minute = 30, created = 5),
            event("c", minute = 5, created = 9), event("d", minute = 30, created = 2)))
        assertEquals(listOf("c", "d", "b", "a"), ordered.map { it.id })
    }

    @Test
    fun playerOptions_startersThenBenchThenRest_onlyCalled() {
        val lineup = listOf(MatchLineupEntry("del", "STARTER", "P0"), MatchLineupEntry("por", "STARTER", "P10"),
            MatchLineupEntry("sup", "BENCH", "DEF"), MatchLineupEntry("retirado", "STARTER", "P5"))
        val names = mapOf("del" to "Dana", "por" to "Paula", "sup" to "Sara", "resto" to "Rosa", "otra" to "Ana")
        val options = MatchEvents.playerOptions(listOf("del", "por", "sup", "resto", "otra"), lineup, names)
        assertEquals(listOf("por", "del", "sup", "otra", "resto"), options.map { it.id })
        assertEquals(listOf(PlayerOption.ROLE_STARTER, PlayerOption.ROLE_STARTER, PlayerOption.ROLE_BENCH, PlayerOption.ROLE_CALLED, PlayerOption.ROLE_CALLED),
            options.map { it.role })
        assertEquals("Paula · Titular", options.first().label)
        assertFalse(options.any { it.id == "retirado" })
    }

    @Test
    fun playerFields_newKindAndLegacyChangeText() {
        val base = EventCatalogRules.BASE_TYPES.first { it.id == "cambio" }
        assertTrue(base.extraFields.all { it.kind == ExtraKinds.PLAYER && MatchEvents.isPlayerField("cambio", it) })
        // Compatibilidad: sale/entra de "cambio" guardados como texto antes de la parte 2.
        assertTrue(MatchEvents.isPlayerField("cambio", ExtraField("sale", "Sale", ExtraKinds.TEXT)))
        assertFalse(MatchEvents.isPlayerField("penal", ExtraField("sale", "Sale", ExtraKinds.TEXT)))
        assertTrue(MatchEvents.isPlayerField("eq_tipo", ExtraField("x", "X", ExtraKinds.PLAYER)))
    }

    @Test
    fun teamLabelsAndDemoIds() {
        assertEquals("Equipo Valido", MatchEvents.teamLabel(EventTeams.OWN, "Equipo Valido", "Rival FC"))
        assertEquals("Rival FC", MatchEvents.teamLabel(EventTeams.OPPONENT, "Equipo Valido", "Rival FC"))
        assertEquals("Sin especificar", MatchEvents.teamLabel(null, "Equipo Valido", "Rival FC"))
        assertTrue(MatchEvents.isDemoMatch("demo-monterrey") && MatchEvents.isDemoMatch("demo-match"))
        assertFalse(MatchEvents.isDemoMatch("AbC123demo"))
    }

    @Test
    fun validate_typeRequiredAndMinuteRange() {
        assertEquals("Selecciona un tipo de evento.", MatchEvents.validate("", ""))
        assertEquals("Minuto inválido (0 a 130).", MatchEvents.validate("gol", "131"))
        assertNull(MatchEvents.validate("gol", ""))
        assertNull(MatchEvents.validate("gol", "90"))
    }
}
