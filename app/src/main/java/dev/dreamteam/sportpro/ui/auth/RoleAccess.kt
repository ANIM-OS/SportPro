package dev.dreamteam.sportpro.ui.auth

enum class AppRole(val code: String) {
    PLAYER("JUGADOR"),
    COACH("ENTRENADOR"),
    PARENT("PADRE / TUTOR");

    companion object {
        fun fromCode(code: String): AppRole? {
            val cleaned = code.trim().uppercase()
            return entries.firstOrNull { 
                it.code.uppercase() == cleaned || 
                it.name == cleaned || 
                (it == PARENT && (cleaned == "PADRE/TUTOR" || cleaned == "PADRE" || cleaned == "PADRE / TUTOR")) ||
                (it == COACH && (cleaned == "ENTRENADOR" || cleaned == "COACH")) ||
                (it == PLAYER && (cleaned == "JUGADOR" || cleaned == "PLAYER"))
            }
        }
    }
}

enum class RoleCapability {
    CREATE_POST,
    CREATE_TEAM,
    JOIN_WITH_CODE,
    MANAGE_EVENT_CATALOG, // US-12
    REGISTER_EVENTS,      // US-13
    EDIT_EVENTS,          // US-14
    COMMENT_POST          // US-19
}

/** Account-wide UI capabilities derived from profile roles. Firestore enforces writes. */
object RoleAccess {
    fun can(roles: Set<AppRole>, capability: RoleCapability, coachApproved: Boolean = true): Boolean = when (capability) {
        RoleCapability.CREATE_POST -> AppRole.PLAYER in roles || AppRole.COACH in roles
        RoleCapability.CREATE_TEAM -> AppRole.COACH in roles
        RoleCapability.JOIN_WITH_CODE -> AppRole.PLAYER in roles
        RoleCapability.MANAGE_EVENT_CATALOG -> AppRole.COACH in roles
        RoleCapability.REGISTER_EVENTS -> AppRole.COACH in roles
        RoleCapability.EDIT_EVENTS -> AppRole.COACH in roles
        RoleCapability.COMMENT_POST -> roles.isNotEmpty()
    }
}
