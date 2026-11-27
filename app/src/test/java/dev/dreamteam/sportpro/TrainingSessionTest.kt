package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.TrainingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingSessionTest {

    @Test
    fun memberWithCategory_seesAllAndOwnCategory() {
        assertEquals(listOf("TODAS", "SUB-12"), TrainingSession.visibleCategories("SUB-12"))
    }

    @Test
    fun memberWithoutCategory_seesOnlyAll() {
        assertEquals(listOf("TODAS"), TrainingSession.visibleCategories(""))
        assertEquals(listOf("TODAS"), TrainingSession.visibleCategories("   "))
        // Nunca se repite TODAS (evita una consulta duplicada).
        assertEquals(listOf("TODAS"), TrainingSession.visibleCategories("TODAS"))
    }

    @Test
    fun appliesTo_matchesAllOrSameCategory() {
        assertTrue(TrainingSession(category = "TODAS").appliesTo("SUB-15"))
        assertTrue(TrainingSession(category = "SUB-12").appliesTo("SUB-12"))
        assertFalse(TrainingSession(category = "SUB-12").appliesTo("SUB-15"))
    }
}
