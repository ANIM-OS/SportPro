# Criterios de aceptación US-17: Anuncios internos y notificaciones

Pruebas:
- Unitarias: `app/src/test/.../AnnouncementRulesTest.kt`.
- SDK cliente contra el emulador: `tests/announcements/client-announcements-check.mjs`.

| # | Criterio | Estado | Cómo se cumple | Pruebas |
|---|----------|--------|----------------|---------|
| 1 | Entrenador o admin crea un anuncio para un equipo con tipo convocatoria / entrenamiento / cambio de horario | Cumplido | Formulario en **Anuncios** (equipo, tipo, título 1-80, mensaje 1-500). Las reglas (`announcements`) solo permiten crear a la dueña aprobada del equipo o al admin (cualquier equipo tipo EQUIPO), con `hasOnly`, tipos, `authorId` propio y `createdAt == request.time`. Sin edición; borra el autor o el admin. | `AnnouncementRulesTest`, script §1 y §3 |
| 2 | Se guarda en Firestore y aparece en tiempo real | Cumplido | `announcements/{id}`, con un listener por equipo filtrado por `teamId`. Sin conexión aparece "Pendiente de sincronizar" hasta que sube. | script §2 (tiempo real) |
| 3 | Contador de no leídos en la pestaña Anuncios | Cumplido | `BadgedBox` en la barra inferior. No leído = `createdAt > lastSeenAt` de `users/{uid}/announcementReads/{teamId}`; los propios no cuentan. Se marca visto al abrir la pestaña (equipos mirados). El admin no tiene contador. | `AnnouncementRulesTest`, script §4 |
| 4 | Cada usuario solo ve los anuncios de sus equipos (aplicado en las reglas) | Cumplido | Leen la dueña, los miembros (`teamMemberships`), el admin y el tutor con acceso guardado (`announcementGuardians/{teamId}_{uid}`), que en cada lectura vuelve a comprobar el vínculo ACTIVE, el correo verificado y la membresía del jugador. Sin documento de acceso → denegado limpio (`exists()` antes de `get()`). Sin `collectionGroup`. | script §2 |
| 5 | Tema FCM por equipo; push manual desde la consola | Cumplido | `firebase-messaging`; `POST_NOTIFICATIONS` pedido en tiempo de ejecución; `SportProMessagingService` muestra el texto fijo también con la app abierta; `subscribeToTopic("team_<teamId>")` por equipo visible y baja al dejar el equipo o cerrar sesión. Pasos y plantilla en `docs/fcm-envio-manual.md`. | `AnnouncementRulesTest` (temas); FCM solo de forma manual |

## Privacidad
- El push es siempre genérico ("Nuevo anuncio en tu equipo. Abre SportPro"). Ver la lista de lo prohibido en `docs/fcm-envio-manual.md`.
- Los temas FCM son públicos para quien conozca el `teamId`: es un riesgo aceptado, mitigado con el push genérico.
- Las marcas de visto son privadas: solo el propio usuario las lee y escribe (ni el admin).
- Límite de lecturas en reglas: el peor caso de lectura (tutor) usa 6 documentos y el de creación (admin) 4. Ambos están por debajo de 10 y se ejecutan en el script.

## Limitaciones
- El admin elige entre los equipos que tienen partidos registrados (las reglas no le dejan listar `teams`) y no ve nombres de equipo (US-22).
- El tutor no lee el nombre del equipo; se muestra "Equipo de <jugador> · categoría".
- Las notificaciones push no se pueden probar en el emulador: requieren un dispositivo real y la consola.
