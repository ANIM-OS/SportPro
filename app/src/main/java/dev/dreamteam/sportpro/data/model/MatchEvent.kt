package dev.dreamteam.sportpro.data.model

import java.util.UUID

/**
 * US-13/14: evento de un partido REAL (matchFixtures). Mismos campos que tendrá en Firestore
 * (parte 3): equipo como OWN/OPPONENT, jugador por uid y datos extra por clave del catálogo.
 */
data class MatchEventRecord(
    val id: String = UUID.randomUUID().toString(),
    val matchId: String,
    val teamId: String = "",
    val typeId: String,
    val typeName: String,
    val minute: Int?,
    /** [EventTeams.OWN] o [EventTeams.OPPONENT]; null = sin especificar. */
    val team: String?,
    /** uid de un convocado (solo con team == OWN). */
    val playerId: String?,
    val notes: String,
    /** Datos extra del tipo; los de clase "player" guardan el uid del convocado. */
    val extraFields: Map<String, String> = emptyMap(),
    val authorId: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val voided: Boolean = false,
    val lastModifiedBy: String? = null,
    val lastModifiedAtMillis: Long? = null,
    val version: Int = 1
)

data class EventAuditEntry(
    val eventId: String,
    val matchId: String,
    val action: String,
    val actorId: String,
    val changedAtMillis: Long,
    val previous: String,
    val current: String
)

/** Datos del partido real que necesitan registrar, corregir y mostrar eventos. */
data class MatchEventContext(
    val matchId: String,
    val teamId: String,
    val category: String,
    val kickoffMillis: Long,
    val ownName: String,
    val opponent: String
)

object EventTeams {
    const val OWN = "OWN"
    const val OPPONENT = "OPPONENT"
}

data class MatchScore(val own: Int, val opponent: Int, val goalsWithoutTeam: Int)

/** Jugador ofrecido al registrar: titulares primero, luego suplentes y el resto de convocados. */
data class PlayerOption(val id: String, val name: String, val role: String) {
    val label: String get() = when (role) { ROLE_STARTER -> "$name · Titular"; ROLE_BENCH -> "$name · Suplente"; else -> name }
    companion object {
        const val ROLE_STARTER = "STARTER"
        const val ROLE_BENCH = "BENCH"
        const val ROLE_CALLED = "CALLED"
    }
}

enum class MatchTab(val label: String) { INFO("Info"), CALLUP("Convocatoria"), LINEUP("Alineación"), LIVE("LIVE") }

enum class MatchViewerRole { OWNER, PLAYER, GUARDIAN, ADMIN }

/** Qué ve y qué puede hacer cada rol en el detalle del partido. */
data class MatchAccess(val tabs: List<MatchTab>, val canManage: Boolean, val canEditEvents: Boolean)

object MatchEvents {
    const val GOAL_TYPE = "gol"
    const val DEMO_PREFIX = "demo-"

    /**
     * Pestañas por rol (US-09/10/11 + LIVE). La dueña aprobada gestiona; jugador, tutor y admin solo
     * consultan. Con la convocatoria retirada o el partido cancelado no hay alineación ni LIVE.
     */
    fun access(role: MatchViewerRole, callupSelected: Boolean, cancelled: Boolean): MatchAccess {
        if (cancelled) return MatchAccess(listOf(MatchTab.INFO), canManage = false, canEditEvents = false)
        return when (role) {
            MatchViewerRole.OWNER -> MatchAccess(MatchTab.entries.toList(), canManage = true, canEditEvents = true)
            MatchViewerRole.ADMIN -> MatchAccess(MatchTab.entries.toList(), canManage = false, canEditEvents = false)
            MatchViewerRole.PLAYER, MatchViewerRole.GUARDIAN ->
                if (callupSelected) MatchAccess(MatchTab.entries.toList(), canManage = false, canEditEvents = false)
                else MatchAccess(listOf(MatchTab.INFO, MatchTab.CALLUP), canManage = false, canEditEvents = false)
        }
    }

    /** Marcador: goles válidos (no anulados) en su versión actual, por OWN/OPPONENT. */
    fun score(events: List<MatchEventRecord>): MatchScore {
        val goals = events.filter { !it.voided && it.typeId == GOAL_TYPE }
        return MatchScore(goals.count { it.team == EventTeams.OWN }, goals.count { it.team == EventTeams.OPPONENT },
            goals.count { it.team != EventTeams.OWN && it.team != EventTeams.OPPONENT })
    }

    /** Cronología por minuto (sin minuto al final) y, a igual minuto, por hora de registro. */
    fun chronology(events: List<MatchEventRecord>): List<MatchEventRecord> =
        events.sortedWith(compareBy<MatchEventRecord> { it.minute ?: Int.MAX_VALUE }.thenBy { it.createdAtMillis })

    /**
     * Convocados para elegir jugador: titulares (portero primero: P10 → P0), suplentes y el resto,
     * cada grupo por nombre dentro de su orden. Solo entran los convocados vigentes.
     */
    fun playerOptions(selectedIds: Collection<String>, lineup: List<MatchLineupEntry>, names: Map<String, String>): List<PlayerOption> {
        val called = selectedIds.toSet()
        val byId = lineup.filter { it.playerId in called }.associateBy { it.playerId }
        fun name(id: String) = names[id] ?: "Jugador"
        val starters = byId.values.filter { it.kind == "STARTER" }
            .sortedByDescending { it.position.removePrefix("P").toIntOrNull() ?: -1 }
            .map { PlayerOption(it.playerId, name(it.playerId), PlayerOption.ROLE_STARTER) }
        val bench = byId.values.filter { it.kind == "BENCH" }.map { PlayerOption(it.playerId, name(it.playerId), PlayerOption.ROLE_BENCH) }
            .sortedBy { it.name.lowercase() }
        val rest = called.filter { it !in byId }.map { PlayerOption(it, name(it), PlayerOption.ROLE_CALLED) }.sortedBy { it.name.lowercase() }
        return starters + bench + rest
    }

    /** Dato extra que se elige entre convocados: clase "player", o sale/entra de "cambio" guardados como texto. */
    fun isPlayerField(typeId: String, field: ExtraField): Boolean =
        field.kind == ExtraKinds.PLAYER || (typeId == "cambio" && field.key in setOf("sale", "entra"))

    fun teamLabel(code: String?, ownName: String, opponent: String): String = when (code) {
        EventTeams.OWN -> ownName.ifBlank { "Nuestro equipo" }
        EventTeams.OPPONENT -> opponent.ifBlank { "Rival" }
        else -> "Sin especificar"
    }

    fun isDemoMatch(matchId: String): Boolean = matchId.startsWith(DEMO_PREFIX)

    /** Tipo obligatorio y minuto 0-130; devuelve el mensaje de error o null. */
    fun validate(typeId: String, minute: String): String? {
        val parsed = minute.toIntOrNull()
        return when {
            typeId.isBlank() -> "Selecciona un tipo de evento."
            minute.isNotBlank() && (parsed == null || parsed !in 0..130) -> "Minuto inválido (0 a 130)."
            else -> null
        }
    }
}
