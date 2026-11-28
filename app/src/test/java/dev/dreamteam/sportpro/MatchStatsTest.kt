package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.EventTeams
import dev.dreamteam.sportpro.data.model.MatchEventRecord
import dev.dreamteam.sportpro.data.model.MatchInput
import dev.dreamteam.sportpro.data.model.MatchStats
import dev.dreamteam.sportpro.data.model.PlayerStats
import dev.dreamteam.sportpro.data.model.TeamStats
import dev.dreamteam.sportpro.data.model.TeamTotals
import org.junit.Assert.assertEquals
import org.junit.Test

/** US-16 #1-#5: estadísticas por partido, jugador y equipo con eventos válidos, correcciones y anulaciones. */
class MatchStatsTest {
    private val day = 24 * 3_600_000L
    private val now = 100 * day
    private var n = 0

    private fun ev(typeId: String, team: String? = EventTeams.OWN, player: String? = null, extras: Map<String, String> = emptyMap(),
        voided: Boolean = false, version: Int = 1, countsAsGoal: Boolean = false) =
        MatchEventRecord(id = "e${n++}", matchId = "m", typeId = typeId, typeName = typeId, minute = 10, team = team,
            playerId = player, notes = "", extraFields = extras, authorId = "coach", voided = voided, version = version,
            countsAsGoal = countsAsGoal)

    private fun match(id: String, events: List<MatchEventRecord>, kickoff: Long = now - 2 * day, endedAt: Long? = null,
        called: Set<String> = emptySet(), starters: Set<String> = emptySet(), bench: Set<String> = emptySet()) =
        MatchInput(id, "t", "Halcones", "Sub-15", "Rival $id", kickoff, endedAt, events, called, starters, bench)

    @Test
    fun matchStats_bySide_ignoringVoided() {
        val stats = MatchStats.forMatch(listOf(
            ev("gol", player = "a"), ev("gol", EventTeams.OPPONENT),
            ev("tarjeta", player = "a", extras = mapOf("color" to "Amarilla")),
            ev("tarjeta", EventTeams.OPPONENT, extras = mapOf("color" to "Roja")),
            ev("tarjeta", player = "b", extras = mapOf("color" to "Roja"), voided = true),
            ev("falta"), ev("falta", EventTeams.OPPONENT), ev("falta", EventTeams.OPPONENT),
            ev("penal", extras = mapOf("resultado" to "Gol")), ev("penal", extras = mapOf("resultado" to "Atajado")),
            ev("cambio", extras = mapOf("sale" to "a", "entra" to "c")), ev("tiro_de_esquina"), ev("fuera_de_juego", EventTeams.OPPONENT),
            ev("gol", team = null)))
        assertEquals(2, stats.own.goals)
        assertEquals(1, stats.opponent.goals)
        assertEquals(1, stats.own.yellowCards)
        assertEquals(0, stats.own.redCards)
        assertEquals(1, stats.opponent.redCards)
        assertEquals(1, stats.own.fouls)
        assertEquals(2, stats.opponent.fouls)
        assertEquals(2, stats.own.penalties)
        assertEquals(1, stats.own.penaltyGoals)
        assertEquals(1, stats.own.substitutions)
        assertEquals(1, stats.own.corners)
        assertEquals(1, stats.opponent.offsides)
        assertEquals(1, stats.goalsWithoutTeam)
    }

    @Test
    fun playerStats_goalsCardsSubsAndAppearances() {
        val m1 = match("m1", listOf(ev("gol", player = "a"), ev("penal", player = "a", extras = mapOf("resultado" to "Gol")),
            ev("eq_olimpico", player = "b", countsAsGoal = true), ev("tarjeta", player = "b", extras = mapOf("color" to "Amarilla")),
            ev("cambio", extras = mapOf("sale" to "b", "entra" to "c"))),
            called = setOf("a", "b", "c"), starters = setOf("a", "b"), bench = setOf("c"))
        val m2 = match("m2", listOf(ev("tarjeta", player = "a", extras = mapOf("color" to "Roja"))), called = setOf("a"), starters = setOf("a"))
        val lines = PlayerStats.from(listOf(m1, m2), mapOf("a" to "Ana", "b" to "Bea", "c" to "Cris")).associateBy { it.playerId }
        assertEquals(2, lines.getValue("a").called)
        assertEquals(2, lines.getValue("a").starter)
        assertEquals(2, lines.getValue("a").goals)
        assertEquals(1, lines.getValue("a").redCards)
        assertEquals(1, lines.getValue("b").goals)
        assertEquals(1, lines.getValue("b").yellowCards)
        assertEquals(1, lines.getValue("b").subOut)
        assertEquals(1, lines.getValue("c").subIn)
        assertEquals(1, lines.getValue("c").bench)
        assertEquals("Ana", lines.getValue("a").name)
    }

    @Test
    fun playerStats_opponentEventsNeverCountForOurPlayers() {
        val lines = PlayerStats.from(listOf(match("m", listOf(ev("gol", EventTeams.OPPONENT, player = "a")))))
        assertEquals(0, lines.sumOf { it.goals })
    }

    /** US-16 #4: corregir el jugador de un gol lo mueve de A a B; anular una roja la quita. */
    @Test
    fun corrections_andVoids_areReflected() {
        val original = ev("gol", player = "a")
        val red = ev("tarjeta", player = "a", extras = mapOf("color" to "Roja"))
        val before = PlayerStats.from(listOf(match("m", listOf(original, red)))).associateBy { it.playerId }
        assertEquals(1, before.getValue("a").goals)
        assertEquals(1, before.getValue("a").redCards)
        val after = PlayerStats.from(listOf(match("m", listOf(original.copy(playerId = "b", version = 2), red.copy(voided = true, version = 2)))))
            .associateBy { it.playerId }
        assertEquals(0, after["a"]?.goals ?: 0)
        assertEquals(0, after["a"]?.redCards ?: 0)
        assertEquals(1, after.getValue("b").goals)
        // Corregido de gol a falta: ya no es gol de nadie.
        assertEquals(0, PlayerStats.from(listOf(match("m", listOf(original.copy(typeId = "falta", version = 2))))).sumOf { it.goals })
    }

    @Test
    fun teamStats_onlyFinishedMatches() {
        val won = match("w", listOf(ev("gol"), ev("gol"), ev("gol", EventTeams.OPPONENT)))                       // auto: +24 h
        val drawn = match("d", listOf(ev("gol"), ev("gol", EventTeams.OPPONENT)), kickoff = now - 3_600_000L, endedAt = now - 60_000)
        val lost = match("l", listOf(ev("gol", EventTeams.OPPONENT)), kickoff = now - 5 * day)
        val live = match("live", listOf(ev("gol"), ev("gol")), kickoff = now - 3_600_000L)
        val future = match("f", emptyList(), kickoff = now + day)
        assertEquals(TeamTotals(played = 3, won = 1, drawn = 1, lost = 1, goalsFor = 3, goalsAgainst = 3, live = 1, notStarted = 1),
            TeamStats.from(listOf(won, drawn, lost, live, future), now))
    }

    @Test
    fun teamStats_voidedGoalChangesResult() {
        val goal = ev("gol")
        val m = match("m", listOf(goal, ev("gol", EventTeams.OPPONENT)))
        assertEquals(1, TeamStats.from(listOf(m), now).drawn)
        val voided = m.copy(events = listOf(goal.copy(voided = true, version = 2), m.events[1]))
        val totals = TeamStats.from(listOf(voided), now)
        assertEquals(1, totals.lost)
        assertEquals(-1, totals.goalDifference)
    }
}
