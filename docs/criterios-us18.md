# Criterios de aceptación US-18: Publicaciones en la comunidad

Pruebas:
- Unitarias: `app/src/test/.../CommunityRulesTest.kt`.
- SDK cliente contra el emulador: `tests/community/client-community-check.mjs`.

| # | Criterio | Estado | Cómo se cumple | Pruebas |
|---|----------|--------|----------------|---------|
| 1 | Jugador o Entrenador publica hasta 1000 caracteres; el Tutor solo lee | Cumplido | Reglas de `communityPosts`: roles JUGADOR/ENTRENADOR, `authorRole` entre los propios, entrenador aprobado y contenido de 1 a 1000. Quien es Jugador y Tutor publica como Jugador. El tutor no publica, no comenta ni da like. | `CommunityRulesTest`, script §1 y §3 |
| 2 | Muestra autor, rol y fecha | Cumplido | Tarjeta con nombre, rol legible ("Jugador"/"Entrenador") y fecha. Sin conexión: "Pendiente de sincronizar". | `CommunityRulesTest` |
| 3 | Visibilidad Todos / Equipo / Academia aplicada por las reglas | Cumplido | **Crear:** TODOS sin `teamId`; EQUIPO/ACADEMIA con un `teams` existente del mismo tipo del que el autor es dueño o miembro (ACADEMIA: también miembro de un equipo vinculado con `academyAccess`). **Leer** (`postAudience`): EQUIPO, la dueña, los miembros y el tutor con acceso (`announcementGuardians`, US-17); ACADEMIA, la dueña y los miembros de equipos con `academyId` (acceso revalidado en cada lectura). Las consultas del cliente filtran igual que las reglas. | script §1 y §2 |
| 4 | Lo ocultado por moderación (US-21) deja de mostrarse | Cumplido | La lectura exige `active == true` y `moderated == false`, salvo el autor (la ve con "Ocultada por moderación"), el moderador y el admin. Los comentarios y likes de un post oculto tampoco se leen ni se escriben. | script §3 |
| 5 | Solo nombre y rol, sin edad, contacto ni datos físicos | Cumplido | `authorName` = nombre y apellido del perfil o "Usuario SportPro" (las reglas lo comprueban con `profileDisplayName()`). Nunca sale del correo. No se guardan edad, contacto ni datos físicos. | `CommunityRulesTest`, script §1 |

**Sin cambios:** nadie edita ni borra; solo el moderador o el admin ocultan.

**Límite de 10 `get()`/`exists()` por petición:**
- lectura del tutor: 7;
- comentarios del tutor: 8;
- crear ACADEMIA: 6;
- lectura ACADEMIA: 6.

Todos se ejecutan en el script.

## Publicaciones antiguas

Las publicaciones creadas antes de este cambio pueden tener un `authorName` sacado del correo (la parte antes de "@", con los puntos convertidos en espacios y la primera letra en mayúscula). No hay migración automática (plan Spark, sin Cloud Functions). Para corregirlas a mano:

1. **Buscar las afectadas.** Firebase Console → Firestore Database → colección `communityPosts`.
   - Ordena o filtra por `authorId` y abre cada publicación.
   - Compara su `authorName` con el documento `users/{authorId}`: si no coincide con `firstName` + `lastName`, y se parece al correo de la cuenta (Authentication → Usuarios), está afectada.
   - Con muchas publicaciones, puedes filtrar en la consola por `authorId` de cada usuario sin nombre en el perfil.
2. **Corregir.** Abre la publicación → campo `authorName` → **Editar** → escribe el nombre del perfil (`firstName lastName`) o, si el perfil no tiene nombre, `Usuario SportPro` → Actualizar.
   - No cambies ningún otro campo.
   - La consola usa permisos de administrador, así que las reglas no bloquean esta edición.
3. **Si no se puede corregir:** un moderador puede ocultarla desde la app ("Ocultar publicación").
4. **Comentarios:** revisa también `authorName` en `communityPosts/{id}/comments`.

## Limitaciones
- El tutor ve las publicaciones de EQUIPO del equipo de su jugador y sus comentarios, pero no publica, no comenta ni da like.
- Las academias se ven tras guardar `academyAccess`. Si el equipo se desvincula (`academyId` cambia), el acceso deja de valer solo.
