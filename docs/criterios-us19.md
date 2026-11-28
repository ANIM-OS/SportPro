# Criterios de aceptación US-19: Comentarios y reacciones

Pruebas:
- Unitarias: `CommentRulesTest`, `RoleAccessTest`.
- SDK cliente contra el emulador: `tests/community/client-comments-check.mjs` (y `client-community-check.mjs` de US-18).

| # | Criterio | Estado | Cómo se cumple | Pruebas |
|---|----------|--------|----------------|---------|
| 1 | Los usuarios autorizados comentan publicaciones visibles (máx. 300) en Comunidad | Cumplido | Reglas de `comments`: `canInteractWithPost()` (post activo, no ocultado y visible, US-18) + `canEngageCommunity()` (rol JUGADOR o ENTRENADOR aprobado, igual que para publicar) + texto de 1 a 300. En la UI, `COMMENT_POST` usa el mismo criterio; el tutor y la entrenadora sin aprobar ven "Solo lectura". | `CommentRulesTest`, `RoleAccessTest`, script §1 |
| 2 | El comentario identifica autor y fecha y se puede reportar o moderar | Cumplido | `authorName == profileDisplayName()`: nombre del perfil o "Usuario SportPro", nunca `displayName` ni correo. Se muestra la fecha. REPORTAR (no en los propios ni en los ocultos) con motivo: Contenido inapropiado, Acoso o insulto, Datos personales, Spam u Otro (`reason` de 1 a 500). Una vez por usuario (ID = uid). El moderador o el admin ocultan. | `CommentRulesTest`, script §1 y §3 |
| 3 | Una sola reacción "Me gusta" por publicación, y se puede quitar | Cumplido | `likes/{uid}` con `userId == uid`. El segundo `set` es `update` y está prohibido. Se quita solo el propio. El contador es el número de documentos (no se puede falsear). | script §2 |
| 4 | No se comenta ni reacciona en publicaciones no visibles | Cumplido | `canInteractWithPost()` en comentar, reaccionar y reportar. Los comentarios y likes de posts no visibles u ocultos no se leen (`canReadPostContent()`). | script §1, §2 y §4 |

## Comentarios ocultos
- Su autor los lee por ID y en su consulta propia (`authorId == uid`), con el aviso "Ocultado por moderación".
- Los demás no los reciben (`moderated == false`) ni los pueden leer por ID.
- El moderador y el admin sí los leen.
- El autor de un post oculto ve los comentarios de su post en la app.

## Sin conexión
Comentar, dar o quitar el Me gusta y reportar no bloquean: se guardan en el celular y se ven con "Pendiente de sincronizar" (`MetadataChanges.INCLUDE`, `hasPendingWrites`). El nombre sale del perfil en caché. Si nunca se cargó el perfil, aparece "Conéctate una vez para cargar tu perfil".

## Límite de lecturas en reglas
- Comentar en ACADEMIA: post, teams, academyAccess, teams, membresía, users y coachApprovals = 7.
- Reportar: + el comentario = 8.

Por debajo de 10; se ejecutan en los scripts.

## Fuera de alcance
La pantalla de reportes para moderador o admin queda para US-21. Hoy los reportes se ven en la consola, en `communityPosts/{post}/comments/{comentario}/reports`.

## Cambio respecto a lo anterior
La entrenadora sin aprobar ya no puede comentar ni reaccionar (antes `COMMENT_POST` permitía cualquier rol). Se actualizó `RoleAccessTest`.
