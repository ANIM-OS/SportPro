package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.CoreFields
import dev.dreamteam.sportpro.data.model.EventCatalogRules
import dev.dreamteam.sportpro.data.model.EventType
import dev.dreamteam.sportpro.data.model.ExtraKinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** US-12: combinación de tipos activos, claves generadas, tipos base y tipos antiguos del celular. */
class EventCatalogTest {
    private val gol = EventType("gol", "Gol", order = 10, base = true)
    private val falta = EventType("falta", "Falta", order = 20, base = true)
    private val offside = EventType("fuera_de_juego", "Fuera de juego", order = 50, base = true, active = false)
    private val propio = EventType("eq_penal_ejecutado", "Penal ejecutado", custom = true)
    private val propioOff = EventType("eq_lesion", "Lesión", custom = true, active = false)

    @Test
    fun activeTypes_combinesGlobalOverridesAndCustoms() {
        val active = EventCatalogRules.activeTypes(listOf(gol, falta, offside), mapOf("falta" to false), listOf(propio, propioOff))
        assertEquals(listOf("gol", "eq_penal_ejecutado"), active.map { it.id })
    }

    @Test
    fun overrideCannotReviveGlobalDisabledByAdmin() =
        assertEquals(listOf("gol"), EventCatalogRules.activeTypes(listOf(gol, offside), mapOf("fuera_de_juego" to true), emptyList()).map { it.id })

    @Test
    fun reactivatedOverride_offersTypeAgain() =
        assertEquals(listOf("gol", "falta"), EventCatalogRules.activeTypes(listOf(gol, falta), mapOf("falta" to true), emptyList()).map { it.id })

    @Test
    fun emptyGlobalCatalog_fallsBackToNineBaseTypes() {
        val active = EventCatalogRules.activeTypes(emptyList(), emptyMap(), emptyList())
        assertEquals(listOf("gol", "falta", "penal", "tarjeta", "fuera_de_juego", "cambio", "saque_lateral", "saque_de_meta", "tiro_de_esquina"),
            active.map { it.id })
    }

    @Test
    fun baseTypes_onlyTypeRequired_exceptCardColor() {
        val base = EventCatalogRules.BASE_TYPES.associateBy { it.id }
        assertTrue(EventCatalogRules.BASE_TYPES.all { it.requiredCore.isEmpty() && it.base })
        assertEquals(listOf("color"), base.getValue("tarjeta").requiredExtra)
        assertEquals(ExtraKinds.CHOICE, base.getValue("tarjeta").extraFields.single().kind)
        assertEquals(emptyList<String>(), base.getValue("cambio").requiredExtra)
        assertEquals(emptyList<String>(), base.getValue("penal").requiredExtra)
    }

    @Test
    fun slug_removesAccentsSymbolsAndTrims() {
        assertEquals("penal_ejecutado", EventCatalogRules.slug("  Penal ejecutado! "))
        assertEquals("cana_lesion", EventCatalogRules.slug("Caña / Lesión"))
        assertEquals("a".repeat(40), EventCatalogRules.slug("a".repeat(60)))
    }

    @Test
    fun uniqueKey_prefixMinimumAndDuplicates() {
        assertEquals("eq_penal_ejecutado", EventCatalogRules.uniqueKey("Penal ejecutado", emptySet(), "eq_"))
        assertEquals("tipo", EventCatalogRules.uniqueKey("¡!", emptySet()))
        assertEquals("gol_2", EventCatalogRules.uniqueKey("Gol", setOf("gol")))
        assertEquals("gol_3", EventCatalogRules.uniqueKey("Gol", setOf("gol", "gol_2")))
        val long = EventCatalogRules.uniqueKey("x".repeat(80), setOf("eq_" + "x".repeat(37)), "eq_")
        assertTrue(long.length <= 40 && long.startsWith("eq_") && long.endsWith("_2"))
        assertTrue(Regex("^eq_[a-z0-9_]{1,37}$").matches(long))
    }

    @Test
    fun legacyType_becomesOptionalCustomWithReadableLabels() {
        val imported = EventCatalogRules.fromLegacy("penalti", "Penalti", "", listOf("jugadorId", "minuto", "color", "jugadorSaleId"), true, emptySet())
        assertEquals("eq_penalti", imported.id)
        assertTrue(imported.custom && imported.requiredCore.isEmpty() && imported.requiredExtra.isEmpty())
        assertEquals(listOf("Color", "Jugador sale id"), imported.extraFields.map { it.label })
        assertTrue(imported.extraFields.all { Regex("^[a-z0-9_]{2,30}$").matches(it.key) })
    }

    @Test
    fun requirementsLabel_isReadable() {
        val type = EventType("x", "X", requiredCore = listOf(CoreFields.MINUTE, CoreFields.PLAYER),
            extraFields = EventCatalogRules.BASE_TYPES.first { it.id == "tarjeta" }.extraFields)
        assertEquals("Obligatorio: Tipo, Minuto, Jugador · Datos adicionales: Color (obligatorio)", EventCatalogRules.requirementsLabel(type))
        assertFalse(EventCatalogRules.requirementsLabel(gol).contains("Datos adicionales"))
    }
}
