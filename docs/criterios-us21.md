# Criterios de aceptación US-21: Moderación

No había criterios escritos de US-21 en el repo; se usan los acordados para la HU.

Pruebas:
- Unitarias: `app/src/test/.../ModerationRulesTest.kt`.
- SDK cliente contra el emulador: `tests/community/client-moderation-check.mjs` (90 comprobaciones).

| # | Criterio | Estado | Cómo se cumple | Pruebas |
|---|----------|--------|----------------|---------|
| 1 | Se reportan publicaciones y comentarios con motivo | Cumplido | `communityPosts/{p}/reports/{uid}` y `.../comments/{c}/reports/{uid}` con `targetType` (POST/COMMENT), `postId`, `commentId` (solo comentarios), `reporterId`, `reporterName == profileDisplayName()`, `reason` (lista de la app, 1-500), `status = PENDING` y `createdAt` del servidor. Un reporte por usuario y contenido (ID = uid; un segundo `set` es update y se rechaza). No se reporta lo propio, lo oculto ni lo que no se ve. Botón REPORTAR en la publicación y en el comentario. | `ModerationRulesTest`, script §1-2 |
| 2 | El moderador o el admin ve la lista de reportes | Cumplido | Pantalla **Moderación** (Comunidad → "MODERACIÓN · REPORTES Y OCULTOS", solo si `moderators/{uid}` o `admins/{uid}`). Pendientes de más nuevo a más antiguo con tipo, texto del contenido, motivo, quién reportó y fecha (hora de Lima). | `ModerationRulesTest`, script §3 |
| 3 | Ocultar, restaurar y "Marcar como revisado" | Cumplido | Ocultar/Restaurar en ambos sentidos en publicaciones y comentarios. Revisado: PENDING → REVIEWED con `reviewedBy == uid` y `reviewedAt` del servidor; no vuelve a PENDING. Sección **Ocultos** (publicaciones y comentarios) para restaurar. | script §4-5 |
| 4 | Lo oculto deja de verse para terceros | Cumplido | Igual que US-18/US-19: el autor lo ve con "Ocultada/Ocultado por moderación"; terceros no lo leen. Al restaurar vuelve a verse. | script §5 |
| 5 | Cada acción guarda quién y cuándo | Cumplido | `moderatedBy == uid` y `moderatedAt == request.time` (ocultar o restaurar); `reviewedBy`/`reviewedAt`; avisos: `cancelledBy`/`cancelledAt` (US-20). | script §4-6 |
| 6 | Solo moderador o admin (app y reglas) | Cumplido | `canModerateCommunity()`; los reportes solo los leen moderador y admin, también por `collectionGroup` (`match /{path=**}/reports`). La app oculta la entrada y la pantalla muestra "No tienes permisos de moderación". | script §3-5 |
| 7 | Cancelar avisos de US-20 | Cumplido | Ya existía (pestaña Convocatorias, "Cancelado por moderación"). | script §6, `client-tryouts-check` |

## Otros
- **Sin conexión:** reportar, ocultar, restaurar y revisar se guardan al instante y se ve "Pendiente de sincronizar" hasta que el servidor confirma.
- Solo colores de `MaterialTheme`.
- **Límite de lecturas en reglas:** reportar publicación ≤ 7 (ACADEMIA con academyAccess), reportar comentario ≤ 8; ocultar, restaurar, revisar y leer reportes u ocultos = 2 (admins y moderators).
- **Índices en producción:** las consultas `collectionGroup("reports")` por `status` y `collectionGroup("comments")` por `moderated` necesitan habilitar el índice de campo único con alcance *grupo de colecciones* (consola de Firebase → Índices → Exenciones, o el enlace del error en Logcat). El emulador no los necesita.
- **Reportes antiguos (US-19):** los que solo tienen `reporterId`, `reason` y `createdAt` no tienen `status`, así que no salen en Pendientes; se ven en la consola.
- Ocultar y revisar son acciones separadas: ocultar no marca el reporte como revisado.
