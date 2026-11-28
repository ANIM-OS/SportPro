package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.CommunityPost
import dev.dreamteam.sportpro.data.model.CommunityRules
import dev.dreamteam.sportpro.data.model.CommunityTeamInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** US-18: nombre del autor, rol legible, visibilidad y destinos, y lo que se muestra. */
class CommunityRulesTest {

    @Test
    fun authorName_fromProfile_neverFromEmail() {
        assertEquals("Ana Pérez", CommunityRules.authorName("Ana", "Pérez"))
        assertEquals("Ana", CommunityRules.authorName("Ana", null))
        assertEquals("Ana", CommunityRules.authorName("  Ana ", ""))
        assertEquals("Usuario SportPro", CommunityRules.authorName(null, null))
        assertEquals("Usuario SportPro", CommunityRules.authorName("", "  "))
        assertEquals("x".repeat(80), CommunityRules.authorName("x".repeat(80), null))
        assertEquals("Usuario SportPro", CommunityRules.authorName("x".repeat(50), "y".repeat(40)))
    }

    @Test
    fun roleLabel_readable() {
        assertEquals("Jugador", CommunityRules.roleLabel("JUGADOR"))
        assertEquals("Entrenador", CommunityRules.roleLabel("ENTRENADOR"))
    }

    @Test
    fun validateTarget_visibilityAndTeam() {
        assertNull(CommunityRules.validateTarget("TODOS", null))
        assertTrue(CommunityRules.validateTarget("TODOS", "t1") != null)
        assertNull(CommunityRules.validateTarget("EQUIPO", "t1"))
        assertTrue(CommunityRules.validateTarget("EQUIPO", null) != null)
        assertTrue(CommunityRules.validateTarget("ACADEMIA", " ") != null)
        assertTrue(CommunityRules.validateTarget("PRIVADO", null) != null)
    }

    @Test
    fun validateContent_1to1000() {
        assertNull(CommunityRules.validateContent("hola"))
        assertNull(CommunityRules.validateContent("x".repeat(1000)))
        assertTrue(CommunityRules.validateContent("x".repeat(1001)) != null)
        assertTrue(CommunityRules.validateContent("   ") != null)
    }

    @Test
    fun targets_includeLinkedAcademiesOnce() {
        val teams = listOf(
            CommunityTeamInfo("t1", "Halcones", "EQUIPO", academyId = "ac1"),
            CommunityTeamInfo("t2", "Halcones B", "EQUIPO", academyId = "ac1"),
            CommunityTeamInfo("t3", "Águilas", "EQUIPO"),
            CommunityTeamInfo("ac2", "Mi academia", "ACADEMIA"),
            CommunityTeamInfo("t4", "Propio", "EQUIPO", academyId = "ac2"))
        val targets = CommunityRules.targets(teams)
        val academies = targets.filter { it.type == "ACADEMIA" }
        // ac1 una sola vez (vía t1), ac2 propia (sin viaTeamId).
        assertEquals(setOf("ac1", "ac2"), academies.map { it.id }.toSet())
        assertEquals("t1", academies.first { it.id == "ac1" }.viaTeamId)
        assertNull(academies.first { it.id == "ac2" }.viaTeamId)
        assertEquals(4, targets.count { it.type == "EQUIPO" })
    }

    @Test
    fun visible_hidesModerated_exceptOwn() {
        val posts = listOf(
            CommunityPost(id = "a", authorId = "x", moderated = true),
            CommunityPost(id = "b", authorId = "me", moderated = true),
            CommunityPost(id = "c", authorId = "x"),
            CommunityPost(id = "d", authorId = "x", active = false))
        assertEquals(listOf("b", "c"), CommunityRules.visible(posts, "me").map { it.id })
        assertEquals("Ocultada por moderación", CommunityRules.hiddenNotice(posts[1], "me"))
        assertNull(CommunityRules.hiddenNotice(posts[2], "me"))
        assertNull(CommunityRules.hiddenNotice(posts[0], "me"))
    }
}
