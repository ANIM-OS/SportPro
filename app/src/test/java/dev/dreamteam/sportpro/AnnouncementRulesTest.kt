package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.Announcement
import dev.dreamteam.sportpro.data.model.AnnouncementAccess
import dev.dreamteam.sportpro.data.model.AnnouncementTeam
import dev.dreamteam.sportpro.data.model.AnnouncementType
import dev.dreamteam.sportpro.data.model.Announcements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** US-17: validación, no leídos, agrupación, temas FCM y permisos de la UI de anuncios. */
class AnnouncementRulesTest {
    private fun a(id: String, team: String = "t1", author: String = "coach", at: Long? = 1_000L, pending: Boolean = false) =
        Announcement(id, team, AnnouncementType.CONVOCATORIA, "Título", "Cuerpo", author, at, pending)

    private val owner = AnnouncementTeam("t1", "Halcones", AnnouncementAccess.OWNER)
    private val member = AnnouncementTeam("t2", "Águilas", AnnouncementAccess.MEMBER)
    private val guardian = AnnouncementTeam("t3", "Equipo de Ana", AnnouncementAccess.GUARDIAN, childId = "ana")
    private val adminOnly = AnnouncementTeam("t9", "Equipo · Sub-15", AnnouncementAccess.ADMIN)

    @Test
    fun validate_typeTitleBodyAndTeam() {
        val type = AnnouncementType.ENTRENAMIENTO
        assertNull(Announcements.validate(type, "a", "b", "t1"))
        assertNull(Announcements.validate(type, "x".repeat(80), "y".repeat(500), "t1"))
        assertEquals("Elige un equipo.", Announcements.validate(type, "a", "b", ""))
        assertEquals("Elige el tipo de anuncio.", Announcements.validate(null, "a", "b", "t1"))
        assertEquals("Escribe un título.", Announcements.validate(type, "   ", "b", "t1"))
        assertTrue(Announcements.validate(type, "x".repeat(81), "b", "t1")!!.contains("80"))
        assertEquals("Escribe el mensaje.", Announcements.validate(type, "a", "", "t1"))
        assertTrue(Announcements.validate(type, "a", "y".repeat(501), "t1")!!.contains("500"))
    }

    @Test
    fun types_matchRulesCodes() {
        assertEquals(listOf("CONVOCATORIA", "ENTRENAMIENTO", "CAMBIO_HORARIO"), AnnouncementType.entries.map { it.name })
        assertEquals(AnnouncementType.CAMBIO_HORARIO, AnnouncementType.fromCode("CAMBIO_HORARIO"))
        assertNull(AnnouncementType.fromCode("OTRO"))
    }

    @Test
    fun unread_afterLastSeen_notOwn_notPending() {
        assertTrue(Announcements.isUnread(a("1", at = 2_000), "me", null))
        assertTrue(Announcements.isUnread(a("1", at = 2_000), "me", 1_999))
        assertFalse(Announcements.isUnread(a("1", at = 2_000), "me", 2_000))
        assertFalse(Announcements.isUnread(a("1", at = 2_000), "me", 3_000))
        // Los que escribió el propio usuario no cuentan.
        assertFalse(Announcements.isUnread(a("1", author = "me", at = 2_000), "me", null))
        // Sin hora conocida todavía (pendiente sin estimación).
        assertFalse(Announcements.isUnread(a("1", at = null), "me", null))
    }

    @Test
    fun unreadCount_perTeam_excludingAdminTeams() {
        val byTeam = mapOf("t1" to listOf(a("1", at = 10), a("2", at = 20), a("3", author = "me", at = 30)),
            "t2" to listOf(a("4", "t2", at = 5)), "t9" to listOf(a("9", "t9", at = 99)))
        val lastSeen = mapOf<String, Long?>("t1" to 15L)
        assertEquals(2, Announcements.unreadCount(listOf(owner, member, adminOnly), byTeam, lastSeen, "me"))
        assertEquals(0, Announcements.unreadCount(listOf(adminOnly), byTeam, emptyMap(), "me"))
    }

    @Test
    fun grouped_byTeam_newestFirst_withFilter() {
        val byTeam = mapOf("t1" to listOf(a("old", at = 1), a("new", at = 5), a("pend", at = null, pending = true)), "t2" to listOf(a("x", "t2")))
        val groups = Announcements.grouped(listOf(owner, member), byTeam, null)
        assertEquals(listOf("t1", "t2"), groups.map { it.first.teamId })
        assertEquals(listOf("pend", "new", "old"), groups[0].second.map { it.id })
        assertEquals(listOf("t2"), Announcements.grouped(listOf(owner, member), byTeam, "t2").map { it.first.teamId })
    }

    @Test
    fun topics_perTeam_andDiff() {
        assertEquals("team_abc", Announcements.topic("abc"))
        assertEquals(setOf("team_t1", "team_t2", "team_t3"), Announcements.wantedTopics(listOf(owner, member, guardian, adminOnly)))
        val (add, remove) = Announcements.topicDiff(setOf("team_t1", "team_old"), setOf("team_t1", "team_t2"))
        assertEquals(setOf("team_t2"), add)
        assertEquals(setOf("team_old"), remove)
        // Cerrar sesión = baja de todo.
        assertEquals(emptySet<String>() to setOf("team_t1"), Announcements.topicDiff(setOf("team_t1"), emptySet()))
    }

    @Test
    fun merge_keepsStrongestAccess() {
        val asMember = AnnouncementTeam("t1", "Halcones", AnnouncementAccess.MEMBER)
        val asGuardian = AnnouncementTeam("t1", "Equipo de Ana", AnnouncementAccess.GUARDIAN, "ana")
        val merged = Announcements.merge(listOf(asGuardian), listOf(asMember, member))
        assertEquals(2, merged.size)
        assertEquals(AnnouncementAccess.MEMBER, merged.first { it.teamId == "t1" }.access)
    }

    @Test
    fun publishAndDelete_permissions() {
        assertTrue(Announcements.canPublish(owner, coachApproved = true, isAdmin = false))
        assertFalse(Announcements.canPublish(owner, coachApproved = false, isAdmin = false))
        assertFalse(Announcements.canPublish(member, coachApproved = true, isAdmin = false))
        assertFalse(Announcements.canPublish(guardian, coachApproved = true, isAdmin = false))
        assertTrue(Announcements.canPublish(adminOnly, coachApproved = false, isAdmin = true))
        assertTrue(Announcements.canDelete(a("1", author = "me"), "me", isAdmin = false))
        assertFalse(Announcements.canDelete(a("1", author = "coach"), "me", isAdmin = false))
        assertTrue(Announcements.canDelete(a("1", author = "coach"), "admin", isAdmin = true))
    }

    @Test
    fun pushText_isGeneric() {
        assertEquals("Nuevo anuncio en tu equipo. Abre SportPro", Announcements.PUSH_TEXT)
        assertFalse(adminOnly.notifies)
        assertTrue(guardian.notifies)
    }
}
