# Envío manual de avisos push (US-17)

La app se suscribe al tema `team_<teamId>` de cada equipo que ve el usuario:

- como entrenadora dueña;
- como miembro;
- como tutor de un jugador vinculado.

Se da de baja al dejar el equipo y al cerrar sesión. El admin no se suscribe. El plan Spark no tiene Cloud Functions, así que los push se envían **a mano** desde la consola de Firebase, después de publicar el anuncio en la app.

> **Los temas FCM no tienen control de acceso.** Cualquiera que conozca el `teamId` puede suscribirse. Por eso el push es siempre genérico y el contenido real solo se lee en la app, donde aplican las reglas de Firestore.

## Pasos en la consola de Firebase

1. Publica primero el anuncio en SportPro (pestaña **Anuncios**).
2. Busca el `teamId` del equipo: Firestore → `teams` → ID del documento. También aparece en el campo `teamId` del anuncio.
3. Abre Firebase Console → proyecto → **Messaging** → **Crear campaña** → **Mensajes de Firebase Notifications**.
4. En **Título** escribe `SportPro`. En **Texto de la notificación**, la plantilla fija (abajo). No pongas imagen.
5. **Destino** → **Tema** → `team_<teamId>` (por ejemplo `team_AbC123xyz`). Comprueba dos veces que sea el equipo correcto.
6. **Programación**: Ahora. Sin datos personalizados (opciones adicionales vacías).
7. Revisa y pulsa **Publicar**.

## Plantilla fija (no cambiarla)

- Título: `SportPro`
- Texto: `Nuevo anuncio en tu equipo. Abre SportPro`

Con la app abierta, SportPro muestra siempre este texto aunque el push traiga otro. Con la app cerrada, Android muestra lo que se escribió en la consola: por eso hay que usar la plantilla tal cual.

## Lo que NO debe ir en el push (ni en el título, el texto, la imagen o los datos personalizados)

- Nombres de jugadores, menores, tutores o entrenadores.
- Nombre del equipo, del rival o la categoría (p. ej. "Sub-12").
- Fechas, horas, lugares, direcciones o enlaces a ubicaciones de partidos y entrenamientos.
- El título o el texto del anuncio, ni motivos de convocatoria.
- Lesiones, salud, cuotas, pagos o cualquier dato sensible.
- Teléfonos, correos, uids u otros identificadores.

## Recomendaciones

- Solo el admin del proyecto debe tener acceso a Messaging en la consola.
- No compartas el `teamId` fuera de la app ni lo uses en nombres públicos.
- La app no requiere el push: los anuncios y el contador de no leídos funcionan sin él.
