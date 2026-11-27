package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.Exercise
import dev.dreamteam.sportpro.ui.training.filterExercises
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseLibraryTest {

    private val mine = Exercise(id = "a", createdBy = "coach", creatorName = "Ana Pérez", name = "Rondo")
    private val others = Exercise(id = "b", createdBy = "other", creatorName = "Lucía Gómez", name = "Finalización")
    private val legacy = Exercise(id = "c", createdBy = "other", name = "Antiguo")

    @Test
    fun displayName_matchesRulesFormat() {
        // firestore.rules: (firstName + ' ' + lastName).trim()
        assertEquals("Ana Pérez", Exercise.displayName("Ana", "Pérez"))
        assertEquals("Ana", Exercise.displayName("Ana", null))
        assertEquals("Pérez", Exercise.displayName(null, "Pérez"))
        assertEquals("", Exercise.displayName(null, null))
    }

    @Test
    fun creatorLabel_showsYouOwnerNameOrFallback() {
        assertEquals("ti", mine.creatorLabel("coach"))
        assertEquals("Lucía Gómez", others.creatorLabel("coach"))
        assertEquals("Entrenadora", legacy.creatorLabel("coach"))
    }

    @Test
    fun onlyOwnerCanManage() {
        assertTrue(mine.isOwnedBy("coach"))
        assertFalse(others.isOwnedBy("coach"))
        assertFalse(mine.isOwnedBy(null))
    }

    @Test
    fun filter_mineOrAll() {
        val all = listOf(mine, others, legacy)
        assertEquals(listOf(mine), filterExercises(all, "coach", onlyMine = true))
        assertEquals(all, filterExercises(all, "coach", onlyMine = false))
    }
}
