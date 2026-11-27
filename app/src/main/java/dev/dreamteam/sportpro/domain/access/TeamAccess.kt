package dev.dreamteam.sportpro.domain.access

import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.model.TeamJoinRequest

const val COACH_PENDING_MESSAGE = "Tu rol de entrenador está pendiente de aprobación"

/** A profile role never grants authority over a particular team. */
object TeamAccess {
    fun canManage(team: Team, userId: String?): Boolean =
        !userId.isNullOrEmpty() && team.createdBy == userId

    fun canManageAcademy(academy: Team, userId: String?): Boolean =
        academy.type == "ACADEMIA" && canManage(academy, userId)

    /** US-03 criterio 2: un jugador solo se asocia a una de las categorías del equipo. */
    fun isValidCategory(team: Team, category: String?): Boolean =
        !category.isNullOrBlank() && category in team.categories

    /** US-03: la academia debe contener todas las categorías del equipo. firestore.rules repite esta condición. */
    fun canLinkToAcademy(academy: Team, team: Team, userId: String?): Boolean =
        team.type == "EQUIPO" && team.id != academy.id &&
            canManageAcademy(academy, userId) && canManage(team, userId) &&
            academy.categories.containsAll(team.categories)

    /**
     * US-03: el aviso "Solicitud enviada…" solo tiene sentido mientras esa solicitud siga PENDING.
     * Si se aprobó, rechazó o canceló (documento borrado), el aviso debe desaparecer.
     */
    fun isJoinRequestPending(requestId: String, myRequests: List<TeamJoinRequest>): Boolean =
        myRequests.any { it.id == requestId && it.status == "PENDING" }

    fun canManageTeamInAcademy(academy: Team, team: Team, userId: String?): Boolean =
        team.type == "EQUIPO" && team.academyId == academy.id &&
            canManageAcademy(academy, userId) && canManage(team, userId)
}
