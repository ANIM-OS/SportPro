# Criterios de aceptación: US-12, US-13 y US-14

Criterios acordados para el catálogo y los eventos del partido, con su estado y la prueba
de cada uno. Las pruebas de `tests/events/` usan el SDK cliente contra el emulador
(`demo-sportpro`); las unitarias están en `app/src/test/java/dev/dreamteam/sportpro/`.

Estado: ✅ cumplido · ⚠️ parcial · ❌ no cumplido.

## US-12 Catálogo configurable de eventos

| # | Criterio | Estado | Cómo se comprueba |
|---|---|---|---|
| 1 | Por defecto: gol, falta, penal, tarjeta, fuera de juego, cambio, saque lateral, saque de meta y tiro de esquina. | ✅ | `EventCatalogRules.BASE_TYPES` (IDs estables) y el botón del admin "Crear tipos base que faltan". `EventCatalogTest`, `client-catalog-check.mjs` §2 |
| 2 | El entrenador o el administrador agrega tipos y define qué datos son obligatorios (minuto, equipo, jugador u otros). | ✅ | Admin: catálogo global. Entrenadora dueña aprobada: catálogo del equipo. Obligatorios con casillas y "+ Dato adicional". `client-catalog-check.mjs` §3-4 |
| 3 | Los cambios se guardan en Firestore y aparecen en los demás dispositivos sin reinstalar. | ✅ | `eventCatalog` y `teams/{equipo}/eventTypes` con listeners. `client-catalog-check.mjs` §6 |
| 4 | Un tipo desactivado no aparece al registrar, pero los eventos ya registrados con ese tipo se siguen mostrando. | ✅ | `listenActive` y la regla `eventTypeActive`. El evento copia `typeName`; se puede corregir sin cambiar su tipo. `client-match-events-check.mjs` §3-4 |
| 5 | Agregar un tipo no requiere cambiar el código. | ✅ | Tipos creados desde la app (Firestore). `client-catalog-check.mjs` §3 |

## US-13 Registro dinámico de eventos en vivo

| # | Criterio | Estado | Cómo se comprueba |
|---|---|---|---|
| 1 | El entrenador del equipo registra un evento desde la pestaña LIVE del partido. | ✅ | Partidos → partido → LIVE → "Registrar evento" (solo la dueña aprobada; regla `eventWriter`). `client-match-events-check.mjs` §2 |
| 2 | Tipo obligatorio; opcionales minuto, equipo, jugador y observaciones. | ✅ | Por defecto solo el tipo es obligatorio. Si el catálogo exige más datos, la app los pide antes de guardar (`MatchEvents.validateEvent`) y las reglas los vuelven a validar. `MatchEventsValidationTest`, `client-match-events-check.mjs` §3 |
| 3 | Solo se ofrecen los tipos activos del catálogo. | ✅ | Tipos activos del catálogo del equipo del partido. `client-match-events-check.mjs` §3 |
| 4 | Guarda automáticamente quién lo registró y fecha/hora. | ✅ | `createdBy`, `createdAt` (servidor) y `clientCreatedAt`, exigidos por las reglas. Quien no es la dueña ve "Entrenadora del equipo". `MatchEventsValidationTest.actorLabel_*` |
| 5 | Sin conexión se guarda en el celular y se sincroniza al reconectar. | ✅ | Escrituras en `batch` con la caché de Firestore ("Pendiente de sincronizar"). `client-match-events-check.mjs` §7 |
| 6 | Los eventos aparecen ordenados por minuto. | ✅ | `MatchEvents.chronology`. `MatchEventsTest.chronology_*` |

## US-14 Corrección y anulación con trazabilidad

| # | Criterio | Estado | Cómo se comprueba |
|---|---|---|---|
| 1 | El entrenador corrige un evento desde la pestaña LIVE. | ✅ | LIVE → evento → Corregir (hasta 7 días después del inicio). `client-match-events-check.mjs` §4 |
| 2 | Se anula sin borrarlo; queda visible como anulado. | ✅ | `voided` + revisión `VOID`. Se muestra tachado como "Anulado". Borrar solo en la cascada al eliminar el equipo. `client-match-events-check.mjs` §4 |
| 3 | Cada corrección/anulación guarda quién y cuándo. | ✅ | `revisions/{versión}` con `by` y `at` (servidor), más `updatedBy/At` y `voidedBy/At`. `client-match-events-check.mjs` §4 |
| 4 | Al corregir se conserva el valor anterior para consultarlo. | ✅ | `revisions/{versión}.previous` (la regla `samePrevious` impide falsearlo); pantalla de historial "Dato anterior / Dato nuevo". `MatchEventsFirestoreTest.historyEntries_*` |
| 5 | La cronología y el marcador solo cuentan eventos válidos en su versión más reciente. | ✅ | `MatchEvents.score` (sin anulados, versión actual). La cronología muestra los anulados tachados, pero no cuentan. `MatchEventsTest.score_*`, `client-match-events-check.mjs` §6 |

## Pendiente (fase de interfaz)

- Las pantallas de detalle, corrección e historial (US-14) usan colores oscuros fijos: en tema claro se ven mal.
