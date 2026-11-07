package dev.dreamteam.sportpro.domain.access

import dev.dreamteam.sportpro.data.model.Team

const val COACH_PENDING_MESSAGE = "Tu rol de entrenador está pendiente de aprobación"

/** A profile role never grants authority over a particular team. */
object TeamAccess {
    fun canManage(team: Team, userId: String?): Boolean =
        !userId.isNullOrEmpty() && team.createdBy == userId

    fun canManageAcademy(academy: Team, userId: String?): Boolean =
        academy.type == "ACADEMIA" && canManage(academy, userId)

    fun canManageTeamInAcademy(academy: Team, team: Team, userId: String?): Boolean =
        team.type == "EQUIPO" && team.academyId == academy.id &&
            canManageAcademy(academy, userId) && canManage(team, userId)
}
