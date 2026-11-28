package dev.dreamteam.sportpro.data.model

/**
 * US-15: estado único del partido. Lo usan la pestaña Info, las tarjetas de partido y la tarjeta de
 * convocatoria. Reglas: cancelado si el partido ya no existe; finalizado si la entrenadora lo finalizó
 * (endedAt) o pasaron 24 h desde el inicio (fin del plazo de registro); en juego desde el inicio hasta
 * entonces; no iniciado antes del inicio. Se calcula con la hora del celular (las reglas usan la del servidor).
 */
enum class MatchStatus(val label: String) {
    NOT_STARTED("No iniciado"),
    LIVE("En juego"),
    FINISHED("Finalizado"),
    CANCELLED("Cancelado");

    companion object {
        const val AUTO_FINISH_MILLIS = MatchEvents.REGISTER_HOURS * 3_600_000L

        /**
         * [exists]: false = el partido ya no existe (cancelado); null/true = existe o no se sabe.
         * Devuelve null si no se conoce la hora de inicio.
         */
        fun of(exists: Boolean?, kickoffMillis: Long?, endedAtMillis: Long?, nowMillis: Long): MatchStatus? = when {
            exists == false -> CANCELLED
            kickoffMillis == null -> null
            endedAtMillis != null -> FINISHED
            nowMillis < kickoffMillis -> NOT_STARTED
            nowMillis >= kickoffMillis + AUTO_FINISH_MILLIS -> FINISHED
            else -> LIVE
        }

        /** Partido existente con hora conocida. */
        fun of(kickoffMillis: Long, endedAtMillis: Long?, nowMillis: Long): MatchStatus =
            of(true, kickoffMillis, endedAtMillis, nowMillis)!!

        /** La dueña puede finalizar mientras está en juego (las reglas: desde el inicio hasta +24 h, una vez). */
        fun canFinish(kickoffMillis: Long, endedAtMillis: Long?, nowMillis: Long): Boolean =
            of(kickoffMillis, endedAtMillis, nowMillis) == LIVE
    }
}
