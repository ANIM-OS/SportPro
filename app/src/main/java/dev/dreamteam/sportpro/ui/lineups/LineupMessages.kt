package dev.dreamteam.sportpro.ui.lineups

import com.google.firebase.firestore.FirebaseFirestoreException
import dev.dreamteam.sportpro.data.model.MatchLineupEntry
import dev.dreamteam.sportpro.data.model.TeamMember
import dev.dreamteam.sportpro.ui.callups.CallupCardState

// US-11: textos y decisiones de la alineación, sin dependencias de Compose para poder probarlos.

internal const val LINEUP_FORMATION_CHANGED = "La formación cambió. Revisa la cancha antes de guardar."
internal const val LINEUP_SLOT_TAKEN = "Esta posición ya está ocupada. Retira o mueve primero al titular."
internal const val LINEUP_NOT_CALLED = "El jugador ya no está convocado"
internal const val LINEUP_DENIED_MESSAGE = "No se pudo guardar: puede que el partido haya empezado o que la convocatoria haya cambiado."
internal const val LINEUP_OFFLINE_MESSAGE = "No se pudo guardar. Revisa tu conexión e inténtalo de nuevo."
internal const val LINEUP_CONFLICT_MESSAGE = "La alineación cambió en otro dispositivo al mismo tiempo. Revisa la cancha e inténtalo de nuevo."
internal const val LINEUP_GENERIC_MESSAGE = "No se pudo guardar la alineación. Inténtalo de nuevo."
internal const val GUARDIAN_LINEUP_DENIED = "No se pudo abrir la alineación: puede que la convocatoria haya cambiado o que el partido ya no exista."
internal const val TEAMMATE_LABEL = "Compañero"

/** Comprobaciones propias de la app: son las únicas que se muestran tal cual. */
private val ownMessages = setOf(LINEUP_FORMATION_CHANGED, LINEUP_SLOT_TAKEN, LINEUP_NOT_CALLED)

/** Tipo de fallo ya extraído de la excepción. */
internal enum class LineupFailure { DENIED, OFFLINE, CONFLICT, OTHER }

/** Texto en español, sin detalles técnicos, para un fallo de assign, remove o formación. */
internal fun lineupErrorText(failure: LineupFailure, ownMessage: String?): String = when {
    ownMessage in ownMessages -> ownMessage!!
    failure == LineupFailure.DENIED -> LINEUP_DENIED_MESSAGE
    failure == LineupFailure.OFFLINE -> LINEUP_OFFLINE_MESSAGE
    failure == LineupFailure.CONFLICT -> LINEUP_CONFLICT_MESSAGE
    else -> LINEUP_GENERIC_MESSAGE
}

internal fun lineupErrorMessage(error: Throwable): String {
    val chain = generateSequence(error) { it.cause }.take(6).toList()
    val code = chain.firstNotNullOfOrNull { (it as? FirebaseFirestoreException)?.code }
    val failure = when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> LineupFailure.DENIED
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> LineupFailure.OFFLINE
        FirebaseFirestoreException.Code.ABORTED, FirebaseFirestoreException.Code.FAILED_PRECONDITION -> LineupFailure.CONFLICT
        else -> LineupFailure.OTHER
    }
    return lineupErrorText(failure, chain.firstNotNullOfOrNull { it.message?.takeIf { text -> text in ownMessages } })
}

/** P3: se pide confirmación al alinear (titular o suplente) a quien respondió "No disponible". */
internal fun needsUnavailableConfirmation(status: String?): Boolean = status == "UNAVAILABLE"

internal fun unavailableConfirmationText(name: String) = "$name respondió No disponible. ¿Quieres alinearlo igual?"

/** P4c: el botón "Ver alineación del partido" no se ofrece con convocatoria retirada o partido cancelado. */
internal fun showLineupButton(state: CallupCardState): Boolean =
    state == CallupCardState.CONVOCADO || state == CallupCardState.EN_JUEGO || state == CallupCardState.FINALIZADO

/**
 * P6: en solo lectura (jugador) se ocultan los alineados que ya no están en la categoría del partido.
 * La entrenadora usa además la convocatoria; el tutor no lee la plantilla y no filtra.
 */
internal fun readOnlyInvalid(entries: List<MatchLineupEntry>, roster: List<TeamMember>, category: String?): List<MatchLineupEntry> =
    entries.filter { entry -> roster.none { it.userId == entry.playerId && it.category == category } }

/** Opción A (US-22): el tutor ve a su jugador por nombre y al resto como "Compañero", sin dorsal. */
internal fun guardianRoster(entries: List<MatchLineupEntry>, playerId: String, playerName: String, category: String,
    otherLabel: String = TEAMMATE_LABEL): List<TeamMember> =
    entries.map { TeamMember(it.playerId, if (it.playerId == playerId) playerName else otherLabel, category) }

/** Admin (solo lectura): no puede leer la plantilla, así que nadie aparece con nombre (US-22). */
internal const val ANONYMOUS_PLAYER_LABEL = "Jugador"
