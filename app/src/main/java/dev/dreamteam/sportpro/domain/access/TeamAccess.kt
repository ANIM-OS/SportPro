package dev.dreamteam.sportpro.domain.access

import dev.dreamteam.sportpro.data.model.Team

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

    fun canManageTeamInAcademy(academy: Team, team: Team, userId: String?): Boolean =
        team.type == "EQUIPO" && team.academyId == academy.id &&
            canManageAcademy(academy, userId) && canManage(team, userId)
}
