package dev.dreamteam.sportpro.data.model

/**
 * US-16: estadísticas calculadas SOLO a partir de los eventos válidos (no anulados, en su versión
 * actual). No se guardan resúmenes en Firestore: al corregir o anular un evento, el siguiente cálculo
 * ya lo refleja. Funciones puras, sin Firebase, para poder probarlas.
 */

/** Cifras de un lado (nuestro equipo o el rival) en un partido. */
data class SideStats(
    val goals: Int = 0,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    val fouls: Int = 0,
    val penalties: Int = 0,
    val penaltyGoals: Int = 0,
    val substitutions: Int = 0,
    val corners: Int = 0,
    val offsides: Int = 0
)

/** Estadísticas de un partido individual (US-16 #5). */
data class MatchStatsSummary(val own: SideStats, val opponent: SideStats, val goalsWithoutTeam: Int)

/** Datos de un partido para el histórico: el partido, sus eventos, convocados y alineación. */
data class MatchInput(
    val matchId: String,
    val teamId: String,
    val teamName: String,
    val category: String,
    val opponent: String,
    val kickoffMillis: Long,
    val endedAtMillis: Long?,
    val events: List<MatchEventRecord>,
    /** Convocados vigentes (selected == true) que este rol puede conocer. */
    val calledIds: Set<String> = emptySet(),
    val starterIds: Set<String> = emptySet(),
    val benchIds: Set<String> = emptySet()
) {
    fun status(nowMillis: Long): MatchStatus = MatchStatus.of(kickoffMillis, endedAtMillis, nowMillis)
}

/** Fila de un jugador (solo nuestro equipo). */
data class PlayerLine(
    val playerId: String,
    val name: String,
    val called: Int = 0,
    val starter: Int = 0,
    val bench: Int = 0,
    val goals: Int = 0,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    val subIn: Int = 0,
    val subOut: Int = 0
)

/** Totales del equipo con los partidos FINALIZADOS; los que están en juego se cuentan aparte. */
data class TeamTotals(
    val played: Int = 0,
    val won: Int = 0,
    val drawn: Int = 0,
    val lost: Int = 0,
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0,
    val live: Int = 0,
    val notStarted: Int = 0
) {
    val goalDifference: Int get() = goalsFor - goalsAgainst
}

object MatchStats {
    const val CARD_TYPE = "tarjeta"
    const val FOUL_TYPE = "falta"
    const val SUB_TYPE = "cambio"
    const val CORNER_TYPE = "tiro_de_esquina"
    const val OFFSIDE_TYPE = "fuera_de_juego"
    const val YELLOW = "Amarilla"
    const val RED = "Roja"

    private fun valid(events: List<MatchEventRecord>) = events.filter { !it.voided }

    private fun side(events: List<MatchEventRecord>): SideStats = SideStats(
        goals = events.count(MatchEvents::isGoal),
        yellowCards = events.count { it.typeId == CARD_TYPE && it.extraFields["color"] == YELLOW },
        redCards = events.count { it.typeId == CARD_TYPE && it.extraFields["color"] == RED },
        fouls = events.count { it.typeId == FOUL_TYPE },
        penalties = events.count { it.typeId == MatchEvents.PENALTY_TYPE },
        penaltyGoals = events.count { it.typeId == MatchEvents.PENALTY_TYPE && it.extraFields["resultado"] == MatchEvents.PENALTY_GOAL },
        substitutions = events.count { it.typeId == SUB_TYPE },
        corners = events.count { it.typeId == CORNER_TYPE },
        offsides = events.count { it.typeId == OFFSIDE_TYPE }
    )

    /** Estadísticas del partido por OWN/OPPONENT. Los eventos sin equipo no suman a ningún lado. */
    fun forMatch(events: List<MatchEventRecord>): MatchStatsSummary {
        val ok = valid(events)
        return MatchStatsSummary(side(ok.filter { it.team == EventTeams.OWN }), side(ok.filter { it.team == EventTeams.OPPONENT }),
            MatchEvents.score(events).goalsWithoutTeam)
    }
}

object PlayerStats {
    /**
     * Una fila por jugador de nuestro equipo que aparece como convocado, en la alineación o en un evento.
     * Goles y tarjetas cuentan por playerId con team == OWN; los cambios por sale/entra.
     * [names]: nombres que este rol puede ver (si falta, "Jugador").
     */
    fun from(matches: List<MatchInput>, names: Map<String, String> = emptyMap()): List<PlayerLine> {
        val lines = linkedMapOf<String, PlayerLine>()
        fun line(id: String) = lines.getOrPut(id) { PlayerLine(id, names[id] ?: "Jugador") }
        matches.forEach { m ->
            m.calledIds.forEach { id -> line(id).let { lines[id] = it.copy(called = it.called + 1) } }
            m.starterIds.forEach { id -> line(id).let { lines[id] = it.copy(starter = it.starter + 1) } }
            m.benchIds.forEach { id -> line(id).let { lines[id] = it.copy(bench = it.bench + 1) } }
            m.events.filter { !it.voided }.forEach { e ->
                val player = e.playerId?.takeIf { e.team == EventTeams.OWN }
                if (player != null) {
                    val current = line(player)
                    lines[player] = current.copy(
                        goals = current.goals + if (MatchEvents.isGoal(e)) 1 else 0,
                        yellowCards = current.yellowCards + if (e.typeId == MatchStats.CARD_TYPE && e.extraFields["color"] == MatchStats.YELLOW) 1 else 0,
                        redCards = current.redCards + if (e.typeId == MatchStats.CARD_TYPE && e.extraFields["color"] == MatchStats.RED) 1 else 0)
                }
                if (e.typeId == MatchStats.SUB_TYPE) {
                    e.extraFields["entra"]?.takeIf { it.isNotBlank() }?.let { id -> line(id).let { lines[id] = it.copy(subIn = it.subIn + 1) } }
                    e.extraFields["sale"]?.takeIf { it.isNotBlank() }?.let { id -> line(id).let { lines[id] = it.copy(subOut = it.subOut + 1) } }
                }
            }
        }
        return lines.values.sortedWith(compareByDescending<PlayerLine> { it.goals }.thenByDescending { it.called }.thenBy { it.name.lowercase() })
    }

    /** Nombres copiados en los eventos (jugador y datos extra de clase "player"). */
    fun snapshotNames(matches: List<MatchInput>): Map<String, String> = buildMap {
        matches.flatMap { it.events }.forEach { e ->
            if (e.playerId != null && e.playerName != null) put(e.playerId, e.playerName)
            e.extraNames.forEach { (key, name) -> e.extraFields[key]?.let { put(it, name) } }
        }
    }
}

object TeamStats {
    /** PJ, G, E, P, GF y GC solo con partidos FINALIZADOS (los en juego y no iniciados se cuentan aparte). */
    fun from(matches: List<MatchInput>, nowMillis: Long): TeamTotals = matches.fold(TeamTotals()) { acc, m ->
        when (m.status(nowMillis)) {
            MatchStatus.FINISHED -> {
                val score = MatchEvents.score(m.events)
                acc.copy(played = acc.played + 1,
                    won = acc.won + if (score.own > score.opponent) 1 else 0,
                    drawn = acc.drawn + if (score.own == score.opponent) 1 else 0,
                    lost = acc.lost + if (score.own < score.opponent) 1 else 0,
                    goalsFor = acc.goalsFor + score.own, goalsAgainst = acc.goalsAgainst + score.opponent)
            }
            MatchStatus.LIVE -> acc.copy(live = acc.live + 1)
            MatchStatus.NOT_STARTED -> acc.copy(notStarted = acc.notStarted + 1)
            MatchStatus.CANCELLED -> acc
        }
    }
}

/** Un partido del histórico tal como lo ve cada rol. */
data class MatchSummaryView(
    val matchId: String,
    val teamName: String,
    val category: String,
    val opponent: String,
    val kickoffMillis: Long,
    val status: MatchStatus,
    val score: MatchScore,
    /** null = este rol no ve los totales del partido (tutor). */
    val stats: MatchStatsSummary?,
    /** Cifras del jugador propio o vinculado en este partido (jugador y tutor). */
    val ownLine: PlayerLine?
)

/** Lo que muestra la pantalla de Estadísticas para un rol (privacidad, US-22). */
data class StatsView(
    /** null = este rol no ve los totales del equipo (tutor). */
    val team: TeamTotals?,
    val players: List<PlayerLine>,
    val matches: List<MatchSummaryView>,
    val showPlayerNames: Boolean
)

object StatsPrivacy {
    /**
     * US-22: dueña → todo, con nombres. Jugador → sus cifras y los totales del equipo SOLO de los partidos
     * donde fue convocado (nunca filas de compañeros). Tutor → solo las cifras de su jugador vinculado
     * (marcador de sus partidos, sin totales del equipo). Admin → solo cifras, sin jugadores ni nombres.
     * [viewerId] = uid de quien mira; [childId] = jugador vinculado (tutor).
     */
    fun view(role: MatchViewerRole, viewerId: String, childId: String, matches: List<MatchInput>,
        rosterNames: Map<String, String>, nowMillis: Long): StatsView {
        val focus = when (role) {
            MatchViewerRole.PLAYER -> viewerId
            MatchViewerRole.GUARDIAN -> childId
            else -> ""
        }
        val visible = when (role) {
            MatchViewerRole.PLAYER, MatchViewerRole.GUARDIAN -> matches.filter { focus.isNotBlank() && focus in it.calledIds }
            else -> matches
        }
        val names = when (role) {
            MatchViewerRole.OWNER -> PlayerStats.snapshotNames(visible) + rosterNames
            MatchViewerRole.PLAYER, MatchViewerRole.GUARDIAN ->
                (PlayerStats.snapshotNames(visible) + rosterNames).filterKeys { it == focus }
            MatchViewerRole.ADMIN -> emptyMap()
        }
        val allLines = PlayerStats.from(visible, names)
        val players = when (role) {
            MatchViewerRole.OWNER -> allLines
            MatchViewerRole.PLAYER, MatchViewerRole.GUARDIAN -> allLines.filter { it.playerId == focus }
            MatchViewerRole.ADMIN -> emptyList()
        }
        val summaries = visible.sortedByDescending { it.kickoffMillis }.map { m ->
            MatchSummaryView(m.matchId, m.teamName, m.category, m.opponent, m.kickoffMillis, m.status(nowMillis),
                MatchEvents.score(m.events),
                stats = if (role == MatchViewerRole.GUARDIAN) null else MatchStats.forMatch(m.events),
                ownLine = if (focus.isBlank()) null else PlayerStats.from(listOf(m), names).firstOrNull { it.playerId == focus })
        }
        return StatsView(
            team = if (role == MatchViewerRole.GUARDIAN) null else TeamStats.from(visible, nowMillis),
            players = players, matches = summaries, showPlayerNames = role != MatchViewerRole.ADMIN)
    }
}
