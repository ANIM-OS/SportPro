package dev.dreamteam.sportpro

import com.google.firebase.Timestamp
import dev.dreamteam.sportpro.data.model.CommunityPost
import dev.dreamteam.sportpro.data.model.CommunityRules
import dev.dreamteam.sportpro.data.repository.CommunityRepository.PostComment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** US-19: validación de comentarios y reportes, avisos, reportar y unión de consultas. */
class CommentRulesTest {
    private fun c(id: String, author: String = "x", moderated: Boolean = false, pending: Boolean = false, at: Long? = 1L) =
        PostComment(id, author, "Nombre", "texto", at?.let { Timestamp(it, 0) }, moderated, pending)

    @Test
    fun validateComment_1to300() {
        assertNull(CommunityRules.validateComment("hola"))
        assertNull(CommunityRules.validateComment("x".repeat(300)))
        assertTrue(CommunityRules.validateComment("x".repeat(301)) != null)
        assertTrue(CommunityRules.validateComment("   ") != null)
    }

    @Test
    fun reportReasons_andValidation() {
        assertEquals(listOf("Contenido inapropiado", "Acoso o insulto", "Datos personales", "Spam", "Otro"), CommunityRules.REPORT_REASONS)
        CommunityRules.REPORT_REASONS.forEach { assertNull(CommunityRules.validateReason(it)) }
        assertTrue(CommunityRules.validateReason("") != null)
        assertTrue(CommunityRules.validateReason("x".repeat(501)) != null)
    }

    @Test
    fun canReport_notOwn_notHidden_notPending() {
        assertTrue(CommunityRules.canReport(c("1"), "me", canEngage = true))
        assertFalse(CommunityRules.canReport(c("1", author = "me"), "me", canEngage = true))
        assertFalse(CommunityRules.canReport(c("1", moderated = true), "me", canEngage = true))
        assertFalse(CommunityRules.canReport(c("1", pending = true), "me", canEngage = true))
        assertFalse(CommunityRules.canReport(c("1"), "me", canEngage = false))
    }

    @Test
    fun hiddenComment_noticeOnlyForAuthor() {
        assertEquals("Ocultado por moderación", CommunityRules.commentNotice(c("1", author = "me", moderated = true), "me"))
        assertNull(CommunityRules.commentNotice(c("1", author = "x", moderated = true), "me"))
        assertNull(CommunityRules.commentNotice(c("1", author = "me"), "me"))
    }

    @Test
    fun merge_visibleAndOwn_withoutDuplicates_byDate() {
        val merged = CommunityRules.mergeComments(listOf(c("b", at = 20), c("a", at = 10), c("a", at = 10, pending = true),
            c("p", author = "me", at = null, pending = true)))
        assertEquals(listOf("a", "b", "p"), merged.map { it.id })
        assertTrue(merged.first { it.id == "a" }.pending)
    }

    @Test
    fun canInteract_activeVisiblePost_andRole() {
        assertTrue(CommunityRules.canInteract(CommunityPost(id = "p"), canEngage = true))
        assertFalse(CommunityRules.canInteract(CommunityPost(id = "p"), canEngage = false))
        assertFalse(CommunityRules.canInteract(CommunityPost(id = "p", moderated = true), canEngage = true))
        assertFalse(CommunityRules.canInteract(CommunityPost(id = "p", active = false), canEngage = true))
        assertFalse(CommunityRules.canInteract(null, canEngage = true))
    }
}
