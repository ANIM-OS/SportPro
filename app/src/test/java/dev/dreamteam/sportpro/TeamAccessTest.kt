package dev.dreamteam.sportpro

import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.model.TeamJoinRequest
import dev.dreamteam.sportpro.domain.access.TeamAccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamAccessTest {
    private val owner = "owner"
    private val academy = Team(id = "academy", createdBy = owner, type = "ACADEMIA", categories = listOf("SUB-12", "SUB-15"))

    private fun team(categories: List<String>, createdBy: String = owner, type: String = "EQUIPO") =
        Team(id = "team", createdBy = createdBy, type = type, categories = categories)

    @Test
    fun teamWithContainedCategories_canBeLinked() {
        assertTrue(TeamAccess.canLinkToAcademy(academy, team(listOf("SUB-15")), owner))
        assertTrue(TeamAccess.canLinkToAcademy(academy, team(listOf("SUB-12", "SUB-15")), owner))
    }

    @Test
    fun teamWithCategoryMissingInAcademy_cannotBeLinked() {
        assertFalse(TeamAccess.canLinkToAcademy(academy, team(listOf("SUB-17")), owner))
        assertFalse(TeamAccess.canLinkToAcademy(academy, team(listOf("SUB-15", "SUB-17")), owner))
    }

    @Test
    fun teamOrAcademyOfAnotherOwner_cannotBeLinked() {
        assertFalse(TeamAccess.canLinkToAcademy(academy, team(listOf("SUB-15"), createdBy = "other"), owner))
        assertFalse(TeamAccess.canLinkToAcademy(academy.copy(createdBy = "other"), team(listOf("SUB-15")), owner))
    }

    @Test
    fun inviteCategory_mustBeOneOfTheTeamCategories() {
        val team = team(listOf("SUB-12"))
        assertTrue(TeamAccess.isValidCategory(team, "SUB-12"))
        assertFalse(TeamAccess.isValidCategory(team, "SUB-15"))
        assertFalse(TeamAccess.isValidCategory(team, ""))
        assertFalse(TeamAccess.isValidCategory(team, null))
    }

    @Test
    fun academy_cannotBeLinkedToAnotherAcademy() {
        assertFalse(TeamAccess.canLinkToAcademy(academy, team(listOf("SUB-15"), type = "ACADEMIA"), owner))
    }

    private fun request(status: String, id: String = "team_player") =
        TeamJoinRequest(id = id, teamId = "team", playerId = "player", playerName = "J", category = "SUB-12", status = status)

    @Test
    fun pendingJoinRequest_keepsWaitingMessage() {
        assertTrue(TeamAccess.isJoinRequestPending("team_player", listOf(request("PENDING"))))
    }

    @Test
    fun resolvedOrCancelledJoinRequest_hidesWaitingMessage() {
        assertFalse(TeamAccess.isJoinRequestPending("team_player", listOf(request("APPROVED"))))
        assertFalse(TeamAccess.isJoinRequestPending("team_player", listOf(request("DECLINED"))))
        // Cancelada: el documento se borra y ya no llega en la escucha.
        assertFalse(TeamAccess.isJoinRequestPending("team_player", emptyList()))
        // Otra solicitud pendiente no mantiene el aviso de esta.
        assertFalse(TeamAccess.isJoinRequestPending("team_player", listOf(request("PENDING", id = "other_player"))))
    }
}
