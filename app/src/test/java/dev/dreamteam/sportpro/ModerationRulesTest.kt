package dev.dreamteam.sportpro

import com.google.firebase.Timestamp
import dev.dreamteam.sportpro.data.model.CommunityPost
import dev.dreamteam.sportpro.data.model.ModeratedContent
import dev.dreamteam.sportpro.data.model.ModerationReport
import dev.dreamteam.sportpro.data.model.ModerationRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** US-21: campos del reporte, orden de pendientes y ocultos, y cuándo se puede reportar una publicación. */
class ModerationRulesTest {
    private fun r(path: String, at: Long?, status: String = ModerationRules.PENDING) = ModerationReport(
        path, ModerationRules.POST, "p", null, "u", "Ana", "Spam", status, at?.let { Timestamp(it, 0) })

    private fun h(post: String, comment: String?, at: Long?, hidden: Boolean = true) =
        ModeratedContent(post, comment, "Autor", "texto", hidden, at?.let { Timestamp(it, 0) })

    private fun post(author: String = "otra", active: Boolean = true, moderated: Boolean = false, pending: Boolean = false) =
        CommunityPost(id = "p", authorId = author, active = active, moderated = moderated, pending = pending)

    @Test
    fun reportFields_post_withoutCommentId() {
        val f = ModerationRules.reportFields("p1", null, "u1", "Ana Díaz", "  Spam ")
        assertEquals(mapOf("targetType" to "POST", "postId" to "p1", "reporterId" to "u1",
            "reporterName" to "Ana Díaz", "reason" to "Spam", "status" to "PENDING"), f)
    }

    @Test
    fun reportFields_comment_withCommentId() {
        val f = ModerationRules.reportFields("p1", "c1", "u1", "Usuario SportPro", "Acoso o insulto")
        assertEquals("COMMENT", f["targetType"])
        assertEquals("c1", f["commentId"])
        assertEquals("PENDING", f["status"])
        assertFalse(f.containsKey("createdAt"))
    }

    @Test
    fun pending_newestFirst_withoutReviewedNorDuplicates() {
        val list = ModerationRules.pendingNewestFirst(listOf(
            r("a", 10), r("b", 30), r("c", 20, ModerationRules.REVIEWED), r("d", null), r("b", 30)))
        assertEquals(listOf("d", "b", "a"), list.map { it.path })
    }

    @Test
    fun hidden_newestFirst_onlyHidden() {
        val list = ModerationRules.hiddenNewestFirst(listOf(
            h("p1", null, 5), h("p1", "c1", 9), h("p2", null, 7, hidden = false), h("p1", "c1", 9)))
        assertEquals(listOf("comment:p1/c1", "post:p1"), list.map { it.key })
        assertEquals("COMMENT", list.first().targetType)
    }

    @Test
    fun canReportPost_onlyOthersVisibleAndSynced() {
        assertTrue(ModerationRules.canReportPost(post(), "yo", canEngage = true))
        assertFalse(ModerationRules.canReportPost(post(author = "yo"), "yo", canEngage = true))
        assertFalse(ModerationRules.canReportPost(post(), "yo", canEngage = false))
        assertFalse(ModerationRules.canReportPost(post(moderated = true), "yo", canEngage = true))
        assertFalse(ModerationRules.canReportPost(post(active = false), "yo", canEngage = true))
        assertFalse(ModerationRules.canReportPost(post(pending = true), "yo", canEngage = true))
        assertFalse(ModerationRules.canReportPost(post(), "", canEngage = true))
    }

    @Test
    fun labels() {
        assertEquals("Publicación", ModerationRules.typeLabel("POST"))
        assertEquals("Comentario", ModerationRules.typeLabel("COMMENT"))
        assertEquals("Ocultar", ModerationRules.actionLabel(hidden = false))
        assertEquals("Restaurar", ModerationRules.actionLabel(hidden = true))
    }
}
