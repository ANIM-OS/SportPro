# Criterios de aceptación US-15 y US-16

Estado tras la rama `fix/us15-us16-marcador-estadisticas`. Las pruebas citadas están en
`app/src/test/...` (unitarias) y `tests/events/client-status-stats-check.mjs` (SDK cliente contra el emulador).

## US-15: Marcador, cronología y estado del partido

| # | Criterio | Estado | Cómo se cumple | Pruebas |
|---|----------|--------|----------------|---------|
| 1 | Actualizaciones sin recarga manual | Cumplido | Listeners de Firestore en los eventos y en el partido (`endedAt` llega a todos). El estado se recalcula cada minuto (`rememberMinuteClock`). | `client-status-stats-check` §1 (tiempo real de `endedAt`), `client-match-events-check` |
| 2 | El marcador se calcula de los eventos | Cumplido | `MatchEvents.isGoal`/`score`: "gol", "penal" con resultado "Gol" y tipos propios con `countsAsGoal` (copiado en el evento y validado por las reglas). Sin autogol. Los goles sin equipo no cuentan y se avisa. | `MatchScoreTest`, `client-status-stats-check` §3 |
| 3 | La cronología muestra los eventos válidos en orden | Cumplido | `MatchEvents.chronology` (minuto y luego hora de registro). Los anulados se ven tachados con "Anulado: no cuenta en el marcador" (decisión 3). | `MatchEventsTest` |
| 4 | Estado no iniciado / en juego / finalizado según reglas definidas | Cumplido | `MatchStatus.of`: No iniciado antes del inicio; En juego desde el inicio; Finalizado al pulsar "Finalizar partido" (`endedAt`) o 24 h después del inicio; Cancelado si el partido ya no existe. Mismo estado en Info, tarjetas y `callupCardState`. | `MatchStatusTest`, `CallupCardStateTest`, `client-status-stats-check` §1 |
| 5 | Correcciones y anulaciones se propagan a los dispositivos | Cumplido | Mismo documento actualizado en batch con su revisión; los listeners recalculan marcador y cronología. Tras finalizar se puede corregir y anular hasta 7 días. | `client-match-events-check`, `client-status-stats-check` §2 |

**Reglas de finalizar:** solo la dueña aprobada, entre el inicio y 24 h después, una sola vez, y solo
`endedAt == request.time`, `endedBy == uid` y `updatedAt`. Tras finalizar, `validNewEvent` rechaza eventos nuevos.

## US-16: Estadísticas

| # | Criterio | Estado | Cómo se cumple | Pruebas |
|---|----------|--------|----------------|---------|
| 1 | Calculadas con eventos válidos | Cumplido | `MatchStats`, `PlayerStats` y `TeamStats` ignoran los anulados y usan la versión actual. No hay datos de demostración. | `MatchStatsTest` |
| 2 | Por jugador | Cumplido | Convocado, titular, suplente, goles, amarillas, rojas, entradas y salidas. | `MatchStatsTest` |
| 3 | Por equipo | Cumplido | PJ, G, E, P, GF, GC y DG, solo con partidos finalizados; los partidos en juego se cuentan aparte. | `MatchStatsTest` |
| 4 | Correcciones y anulaciones se reflejan | Cumplido | Se calcula a partir de los eventos, sin resúmenes guardados. El partido en juego o abierto se escucha en tiempo real; el resto al pulsar "Actualizar" o al volver a entrar. | `MatchStatsTest` (corregir jugador o tipo, anular roja o gol) |
| 5 | Datos históricos de un partido individual | Cumplido | Pestaña **Partidos** de Estadísticas: marcador, estado y totales nuestro/rival de cada partido (y las cifras del jugador propio o vinculado). | `StatsPrivacyTest` |

## Privacidad (US-22)

| Rol | Ve |
|-----|----|
| Entrenadora dueña | Todo, con nombres |
| Jugador | Sus cifras y los totales del equipo de los partidos donde fue convocado. Nunca filas de compañeros |
| Tutor | Solo las cifras de su jugador vinculado (sin totales del equipo) |
| Admin | Solo cifras de equipo y partido, sin jugadores ni nombres |

Las reglas no cambian quién lee los eventos (`eventReader` igual y sin `collectionGroup('events')`): la
privacidad de las estadísticas se aplica en la app. Riesgo aceptado: un jugador convocado puede leer
los eventos de ese partido, igual que en LIVE.
