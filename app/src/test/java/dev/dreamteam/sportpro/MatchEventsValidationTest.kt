package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.CoreFields
import dev.dreamteam.sportpro.data.model.EventCatalogRules
import dev.dreamteam.sportpro.data.model.EventTeams
import dev.dreamteam.sportpro.data.model.EventType
import dev.dreamteam.sportpro.data.model.ExtraField
import dev.dreamteam.sportpro.data.model.ExtraKinds
import dev.dreamteam.sportpro.data.model.MatchEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** US-13 #2: datos obligatorios del tipo validados en la app antes de guardar, y autoría visible por rol. */
class MatchEventsValidationTest {
    private val goal = EventCatalogRules.BASE_TYPES.first { it.id == "gol" }
    private val card = EventCatalogRules.BASE_TYPES.first { it.id == "tarjeta" }
    private val penalty = EventType("eq_penal_ejecutado", "Penal ejecutado", requiredCore = listOf(CoreFields.MINUTE, CoreFields.PLAYER), custom = true)
    private val everything = EventType("eq_todo", "Todo", requiredCore = CoreFields.ALL, custom = true,
        extraFields = listOf(ExtraField("arquero", "Arquero", ExtraKinds.TEXT, required = true), ExtraField("zona", "Zona")))

    private fun check(type: EventType?, minute: String = "", team: String = "", playerId: String = "", notes: String = "",
        extras: Map<String, String> = emptyMap(), typeId: String = type?.id.orEmpty()) =
        MatchEvents.validateEvent(type, typeId, minute, team, playerId, notes, extras)

    @Test
    fun typeWithoutRequirements_onlyTheTypeIsMandatory() {
        assertNull(check(goal))
        assertEquals(emptyList<String>(), MatchEvents.missingRequired(goal, "", "", "", "", emptyMap()))
    }

    @Test
    fun requiredCoreFields_areReportedWithVisibleNames() {
        assertEquals("Faltan datos obligatorios de «Penal ejecutado»: Minuto, Jugador.", check(penalty))
        assertEquals(listOf("Jugador"), MatchEvents.missingRequired(penalty, "30", EventTeams.OWN, "", "", emptyMap()))
        assertNull(check(penalty, minute = "30", team = EventTeams.OWN, playerId = "p1"))
    }

    @Test
    fun requiredPlayer_needsOurTeam() {
        // Las reglas exigen team == OWN para playerId: con el rival el jugador cuenta como faltante.
        assertEquals(listOf("Jugador"), MatchEvents.missingRequired(penalty, "30", EventTeams.OPPONENT, "p1", "", emptyMap()))
    }

    @Test
    fun requiredExtraFromCatalog_cardColor() {
        assertEquals("Faltan datos obligatorios de «Tarjeta»: Color.", check(card, minute = "40"))
        assertNull(check(card, extras = mapOf("color" to "Amarilla")))
        assertEquals(listOf("Color"), MatchEvents.missingRequired(card, "", "", "", "", mapOf("color" to "  ")))
    }

    @Test
    fun coreAndExtra_togetherAndOptionalExtraIgnored() {
        assertEquals(listOf("Minuto", "Equipo", "Jugador", "Observaciones", "Arquero"),
            MatchEvents.missingRequired(everything, "", "", "", "", mapOf("zona" to "Área")))
        assertNull(check(everything, minute = "5", team = EventTeams.OWN, playerId = "p1", notes = "Mano", extras = mapOf("arquero" to "Ana")))
        assertEquals("Faltan datos obligatorios de «Todo»: Observaciones.",
            check(everything, minute = "5", team = EventTeams.OWN, playerId = "p1", notes = "   ", extras = mapOf("arquero" to "Ana")))
    }

    @Test
    fun basicValidation_runsFirst() {
        assertEquals("Selecciona un tipo de evento.", check(null, typeId = ""))
        assertEquals("Minuto inválido (0 a 130).", check(penalty, minute = "140"))
        assertEquals(MatchEvents.SAME_PLAYER_MESSAGE, check(EventCatalogRules.BASE_TYPES.first { it.id == "cambio" },
            extras = mapOf("sale" to "p1", "entra" to "p1")))
        // Tipo desconocido en la app (p. ej. el original ya desactivado al corregir): solo la validación básica.
        assertNull(check(null, typeId = "fuera_de_juego"))
    }

    @Test
    fun typeRequiringPlayer_selectsOwnTeamAndMarksTeamRequired() {
        assertEquals(EventTeams.OWN, MatchEvents.teamForType(penalty, ""))
        assertEquals(EventTeams.OWN, MatchEvents.teamForType(penalty, EventTeams.OPPONENT))
        assertTrue(MatchEvents.isTeamRequired(penalty))
        // Con el equipo propio puesto solo falta elegir al jugador.
        assertEquals(listOf("Minuto", "Jugador"), MatchEvents.missingRequired(penalty, "", MatchEvents.teamForType(penalty, ""), "", "", emptyMap()))
    }

    @Test
    fun typeWithoutPlayer_keepsTeamAndItStaysOptional() {
        assertEquals("", MatchEvents.teamForType(goal, ""))
        assertEquals(EventTeams.OPPONENT, MatchEvents.teamForType(card, EventTeams.OPPONENT))
        assertTrue(!MatchEvents.isTeamRequired(goal) && !MatchEvents.isTeamRequired(null))
        assertTrue(MatchEvents.isTeamRequired(EventType("x", "X", requiredCore = listOf(CoreFields.TEAM))))
    }

    @Test
    fun actorLabel_teamCoachForEveryoneButHerself() {
        assertEquals(MatchEvents.TEAM_COACH_LABEL, MatchEvents.actorLabel("coach", ownerId = "coach", viewerId = "player"))
        assertEquals(MatchEvents.TEAM_COACH_LABEL, MatchEvents.actorLabel("coach", ownerId = "coach", viewerId = null))
        assertNull(MatchEvents.actorLabel("coach", ownerId = "coach", viewerId = "coach"))
        assertNull(MatchEvents.actorLabel("otra", ownerId = "coach", viewerId = "player"))
        assertNull(MatchEvents.actorLabel(null, ownerId = "coach", viewerId = "player"))
        assertTrue(MatchEvents.actorLabel("coach", ownerId = "", viewerId = "player") == null)
    }
}
