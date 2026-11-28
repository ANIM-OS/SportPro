package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.EventTeams
import dev.dreamteam.sportpro.data.model.EventType
import dev.dreamteam.sportpro.data.model.MatchEventContext
import dev.dreamteam.sportpro.data.model.MatchEventRecord
import dev.dreamteam.sportpro.data.model.MatchEvents
import dev.dreamteam.sportpro.data.model.MatchScore
import dev.dreamteam.sportpro.ui.events.EventFormState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** US-15 #2: el marcador se calcula de los eventos válidos (gol, penal "Gol" y tipos propios "Cuenta como gol"). */
class MatchScoreTest {
    private var n = 0
    private fun ev(typeId: String, team: String? = EventTeams.OWN, extras: Map<String, String> = emptyMap(),
        voided: Boolean = false, countsAsGoal: Boolean = false) =
        MatchEventRecord(id = "e${n++}", matchId = "m", typeId = typeId, typeName = typeId, minute = 10, team = team,
            playerId = null, notes = "", extraFields = extras, authorId = "coach", voided = voided, countsAsGoal = countsAsGoal)

    @Test
    fun goal_counts_byTeam() =
        assertEquals(MatchScore(2, 1, 0), MatchEvents.score(listOf(ev("gol"), ev("gol"), ev("gol", EventTeams.OPPONENT))))

    @Test
    fun penalty_countsOnlyWithResultGol() {
        val events = listOf(ev("penal", extras = mapOf("resultado" to "Gol")), ev("penal", extras = mapOf("resultado" to "Fallado")),
            ev("penal", extras = mapOf("resultado" to "Atajado")), ev("penal"), ev("penal", EventTeams.OPPONENT, mapOf("resultado" to "Gol")))
        assertEquals(MatchScore(1, 1, 0), MatchEvents.score(events))
    }

    @Test
    fun customType_countsOnlyWithCountsAsGoal() {
        val events = listOf(ev("eq_gol_olimpico", countsAsGoal = true), ev("eq_tiro_al_palo"),
            // Un tipo global nunca cuenta por countsAsGoal (las reglas tampoco lo permiten).
            ev("falta", countsAsGoal = true))
        assertEquals(MatchScore(1, 0, 0), MatchEvents.score(events))
    }

    @Test
    fun noOwnGoalType() =
        assertEquals(MatchScore(0, 0, 0), MatchEvents.score(listOf(ev("autogol"))))

    @Test
    fun voidedGoals_doNotCount() {
        val events = listOf(ev("gol", voided = true), ev("penal", extras = mapOf("resultado" to "Gol"), voided = true),
            ev("eq_x", countsAsGoal = true, voided = true), ev("gol", EventTeams.OPPONENT))
        assertEquals(MatchScore(0, 1, 0), MatchEvents.score(events))
    }

    @Test
    fun correction_currentVersionDecides() {
        val goal = ev("gol")
        assertEquals(1, MatchEvents.score(listOf(goal)).own)
        // Corregido de gol a falta: deja de contar; cambiado al rival: cuenta para el rival.
        assertEquals(0, MatchEvents.score(listOf(goal.copy(typeId = "falta", version = 2))).own)
        assertEquals(MatchScore(0, 1, 0), MatchEvents.score(listOf(goal.copy(team = EventTeams.OPPONENT, version = 2))))
        // Penal corregido de Fallado a Gol.
        val penalty = ev("penal", extras = mapOf("resultado" to "Fallado"))
        assertEquals(1, MatchEvents.score(listOf(penalty.copy(extraFields = mapOf("resultado" to "Gol"), version = 2))).own)
    }

    @Test
    fun goalsWithoutTeam_doNotCount_butAreReported() {
        val events = listOf(ev("gol", team = null), ev("penal", team = null, extras = mapOf("resultado" to "Gol")), ev("gol"))
        assertEquals(MatchScore(1, 0, 2), MatchEvents.score(events))
    }

    @Test
    fun isGoal_rules() {
        assertTrue(MatchEvents.isGoal(ev("gol")))
        assertFalse(MatchEvents.isGoal(ev("tarjeta")))
        assertFalse(MatchEvents.isGoal(ev("eq_x")))
    }

    @Test
    fun formCopiesCountsAsGoal_onlyForCustomTypes() {
        val ctx = MatchEventContext("m", "t", "Sub-15", 0L, "Halcones", "Rival")
        val state = EventFormState().apply { typeId = "eq_olimpico"; team = EventTeams.OWN }
        val custom = EventType("eq_olimpico", "Gol olímpico", custom = true, countsAsGoal = true)
        assertTrue(state.toRecord(ctx, custom, "coach", null).countsAsGoal)
        assertFalse(state.toRecord(ctx, custom.copy(countsAsGoal = false), "coach", null).countsAsGoal)
        val global = EventType("gol", "Gol", base = true, countsAsGoal = true)
        assertFalse(state.toRecord(ctx, global, "coach", null).countsAsGoal)
    }
}
