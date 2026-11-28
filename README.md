# SPORT PRO

SPORT PRO es una aplicación de gestión deportiva orientada a clubes y academias. Busca centralizar la gestión de jugadores, entrenadores, equipos, partidos, estadísticas y seguimiento deportivo en una sola plataforma.

Actualmente el proyecto se encuentra en fase de desarrollo y prototipado.

## Funcionalidades

* Gestión de jugadores, entrenadores y equipos.
* Registro y seguimiento de partidos.
* Registro de eventos durante los encuentros.
* Estadísticas deportivas.
* Convocatorias e historial de jugadores.
* Vinculación entre padres/tutores y jugadores.
* Sistema de usuarios con múltiples roles.
* Generación de resúmenes de partidos mediante inteligencia artificial.

## Roles

Una misma cuenta puede tener uno o varios roles dentro de SPORT PRO:

* Jugador
* Entrenador
* Padre o tutor
* Administrador

Esto permite contemplar casos como un entrenador que también es jugador o un padre/tutor que también forma parte del cuerpo técnico.

La aplicación combina los permisos de todos los roles de una cuenta. Actualmente, Jugador, Entrenador y Padre/Tutor pueden abrir Inicio, En vivo, Estadísticas, Comunidad y Equipos. Jugador y Entrenador aprobado pueden publicar en Comunidad; para registrar equipos o academias se requiere el rol Entrenador aprobado. Las pantallas de creación se protegen también al navegar directamente a ellas. Las páginas específicas de padres y la vinculación con sus hijos aún no están implementadas.

La pestaña Equipos solo muestra equipos creados por la cuenta o equipos a los que se unió aceptando una invitación. El entrenador creador puede invitar a otra cuenta por su correo de registro. Para recibir y aceptar invitaciones, esa cuenta debe verificar su correo; la pantalla Equipos permite enviar el enlace de verificación y actualizar su estado. Firestore comprueba los mismos permisos de lectura, creación e invitación.

El entrenador puede crear una academia y vincular equipos que administra a esa academia. La ficha de la academia lista los equipos asociados; cada equipo conserva su ficha y gestión propia. No se puede eliminar una academia mientras tenga equipos vinculados. El propietario puede editar el nombre, la ciudad, la descripción, los colores y el año de fundación, subir una foto de perfil y eliminar el equipo. Al eliminarlo, la app borra primero sus invitaciones y membresías. Las fotos nuevas se reducen y se guardan junto al equipo en Firestore; los logos que ya estaban en Firebase Storage siguen siendo visibles.

Cada equipo tiene una vista de Plantilla y Alineación por categoría. El entrenador puede editar nombre, categoría y dorsal de cada integrante, retirar jugadores y guardar una formación 4-3-3, 4-4-2 o 3-5-2 con asignaciones por posición. Al crear el equipo se genera un QR de registro de un solo uso y 15 minutos de validez; la ficha lo renueva al usarse o vencer. Solo el código activo del equipo permite unirse. Un jugador con correo verificado y rol JUGADOR puede escanearlo desde Equipos, confirmar su nombre y categoría y unirse; el equipo sigue siendo privado. Los QR, las membresías y las alineaciones se borran cuando el propietario elimina el equipo.

## Autenticación

El proyecto contempla Firebase Authentication para gestionar el acceso de los usuarios mediante:

* Correo electrónico y contraseña.
* Inicio de sesión con Google.

Los roles y permisos serán administrados por SPORT PRO independientemente del método de autenticación.

## Inteligencia Artificial

SPORT PRO contempla el uso de inteligencia artificial para generar resúmenes de partidos a partir de los eventos registrados en la plataforma.

El flujo previsto es:

`Partido → Eventos → IA → Borrador → Revisión del entrenador → Aprobación`

Actualmente esta funcionalidad se encuentra simulada dentro del prototipo. La integración definitiva se realizará desde el backend para evitar exponer credenciales en el cliente.

## Tecnologías
* Android Studio
* Kotlin
* Firebase Authentication
* Firebase / Firestore
* Gemini API 
* Git y GitHub

## Estado del proyecto

El proyecto se encuentra actualmente en etapa de prototipo. Algunas funcionalidades utilizan datos simulados mientras se desarrollan el backend, la autenticación y las integraciones externas.

Próximos objetivos:

* [x] Integrar Firebase Authentication.
* [x] Implementar inicio de sesión con Google.
* [x] Implementar Firestore para los módulos persistidos.
* [ ] Completar el sistema de roles y permisos.
* [ ] Completar la gestión de equipos y jugadores.
* [ ] Desarrollar estadísticas.
* [ ] Integrar la generación de resúmenes mediante IA.

## Seguridad

Las credenciales, claves de API y archivos con información sensible no deben almacenarse en el repositorio.

Las variables de entorno y credenciales privadas deberán mantenerse fuera del control de versiones mediante `.gitignore`.

**Publicaciones (US-18).** Criterios en [`docs/criterios-us18.md`](docs/criterios-us18.md). Jugador y Entrenador aprobado publican hasta 1000 caracteres con visibilidad Todos, Equipo o Academia; el Padre/Tutor solo lee. Las reglas comprueban que Todos no lleve equipo y que Equipo/Academia apunte a un `teams` del mismo tipo del que el autor es dueño o miembro. La Academia también la ven los jugadores y entrenadores de los equipos vinculados (`academyId`), tras guardar `academyAccess/{academyId}_{uid}`, y el tutor lee las de Equipo del equipo de su jugador vinculado con el acceso de US-17 (`announcementGuardians`). El autor aparece con el nombre de su perfil o "Usuario SportPro", nunca con el correo, y su rol legible. Las publicaciones ocultadas solo las ven su autor ("Ocultada por moderación"), los moderadores y el admin, igual que sus comentarios. Sin conexión se publica igual y aparece "Pendiente de sincronizar". Nadie edita ni borra publicaciones.

**Comentarios y reacciones (US-19).** Criterios en [`docs/criterios-us19.md`](docs/criterios-us19.md). Comentan (hasta 300 caracteres), dan "Me gusta" y reportan el Jugador y el Entrenador aprobado, solo en publicaciones activas y visibles para ellos; el Padre/Tutor y la entrenadora sin aprobar ven "Solo lectura". El autor del comentario aparece con el nombre de su perfil o "Usuario SportPro" (las reglas lo comprueban). Hay un solo "Me gusta" por persona (documento `likes/{uid}`) y se puede quitar. Al reportar se elige el motivo; no se pueden reportar los comentarios propios. Los comentarios ocultados por moderación solo los ven su autor ("Ocultado por moderación"), los moderadores y el admin. Sin conexión no se bloquea y se ve "Pendiente de sincronizar".

Para habilitar la moderación de comunidad, un administrador del proyecto debe crear en Firestore el documento `moderators/{uid}` para la cuenta designada. La aplicación solo permite a esa cuenta ocultar publicaciones; los usuarios no pueden crear documentos de moderadores mediante las reglas de Firestore. Tras asignar el permiso, el moderador debe volver a abrir la pantalla de Comunidad.

Para designar un administrador, crea en Firestore el documento `admins/{uid}` desde la consola de Firebase. Un administrador puede ocultar publicaciones igual que un moderador; no se guarda en `roles` porque cada usuario puede editar ese campo, y no lo convierte en entrenador aprobado.

Cualquier usuario puede elegir el rol `ENTRENADOR`, pero crear equipos, invitar jugadores, generar códigos de unión, editar alineaciones y publicar como entrenador exige el documento `coachApprovals/{uid}` con el campo booleano `approved` en `true`, creado desde la consola de Firebase. Las reglas de Firestore aplican esta condición; los eventos en vivo se guardan solo en la app, por lo que ahí la aprobación se comprueba únicamente en la interfaz. Para retirar la aprobación, cambia `approved` a `false` o elimina el documento.

Los avisos de pruebas y convocatorias (US-20) están en la pestaña Convocatorias de Comunidad y se guardan en la colección `tryoutNotices`. Solo un entrenador aprobado puede publicarlos; título y descripción son obligatorios. Un aviso vence al terminar el día de su fecha límite y, sin fecha límite, sigue vigente hasta que se cancela. Vigente o vencido se calcula en la app con la hora del dispositivo, porque el plan Spark no permite Cloud Functions. Solo el autor o un administrador pueden cancelar un aviso; los avisos no se eliminan. Los propietarios de equipos sin aprobación conservan la edición y eliminación de sus equipos y la gestión de sus miembros.

## Licencia

Este proyecto fue desarrollado inicialmente con fines educativos y actualmente se encuentra en desarrollo.

Copyright © 2026 SPORT PRO. Todos los derechos reservados.

El código fuente, diseño y documentación del proyecto no pueden ser copiados, modificados, distribuidos o utilizados con fines comerciales sin autorización de sus autores.

Actualmente el proyecto no se encuentra abierto a contribuciones externas.

## US-09 y US-10 en SportPro

Convocatorias, disponibilidad y alineaciones por partido se abren desde
**Equipos → Convocatorias por partido** en la app SportPro habitual.
En Android Studio selecciona la variante **debug** y ejecuta la app. No existe
una variante ni una aplicación separada «SportPro Local».
La app usa la configuración existente de `app/google-services.json` y las cuentas,
equipos y jugadores del Firebase compartido; no se copian datos de los emuladores.

El entrenador selecciona equipo y partido, marca jugadores de la categoría y guarda.
Puede registrar el partido y ajustar la fecha límite sugerida para las respuestas.
El jugador o tutor autorizado confirma su disponibilidad antes de ese límite.
Para llegar a la plantilla con QR, el jugador envía una solicitud en
`teamJoinRequests`; el entrenador la aprueba y la membresía se crea en la misma
operación. El QR puede recibir varias solicitudes hasta vencer. Los jugadores
pueden cancelar solicitudes pendientes y el dueño limpia las solicitudes al
eliminar su equipo.
Para autorizar a un tutor, el jugador genera un código de 30 minutos en su perfil,
el tutor lo acepta en **Equipos → Convocatorias por partido** y el jugador confirma
la solicitud. El vínculo activo se guarda en `guardianLinks` y se puede revocar.
Los vínculos antiguos de `playerGuardians` siguen visibles para que sus usuarios
sepan que deben repetir la confirmación; ya no conceden acceso a los datos del jugador.

Las reglas de US-09, US-10 y US-11 se comprobaron. El 21 de noviembre de 2026
se sincronizó `firestore.rules` con las reglas publicadas por el equipo en
`sportpro-6f6ad` y se publicó el permiso para cancelar y limpiar solicitudes de
ingreso; después se verificó que el archivo coincide con producción. Incluye el
flujo actual de `guardianInvites` y `guardianLinks` y conserva los cambios de
reglas hechos por otros integrantes.
Antes de cambiarlas de nuevo, compara la versión publicada con el repositorio.
La pestaña **Partidos** y
**Equipos → Convocatorias por partido** leen los mismos documentos `matchFixtures`.
Al tocar un partido en **Partidos** se abre su detalle con las pestañas Info,
Convocatoria, Alineación y LIVE (marcador y eventos, ver US-12 a US-14).

Los partidos se guardan en `matchFixtures`; cada selección, en `matchCallupPlayers`,
con identidad estable de partido y jugador. El rival se introduce únicamente al registrar
el partido; en la convocatoria todos los datos se seleccionan o se muestran automáticamente.
La categoría de un partido guardado es inmutable para no invalidar las convocatorias existentes.
Las escrituras validan propiedad del equipo, rol, membresía, categoría y fecha futura.
La retirada se conserva como estado visible para el jugador. Las actualizaciones usan listeners.
Los cambios de una convocatoria se guardan en una sola transacción: si una
selección falla, ninguna de esa edición se aplica. Para respetar el límite de
lecturas de las reglas de Firestore, se pueden guardar hasta 15 altas o 5
retiros por vez; después se continúa con el resto. Cambiar la categoría de un
jugador o retirarlo del equipo requiere quitarlo antes de sus convocatorias futuras.
Al eliminar un equipo, la app elimina también sus partidos, convocatorias,
respuestas de disponibilidad y alineaciones por partido.
Los borradores antiguos del dispositivo no se migran ni se eliminan.

Las reglas de estas historias están incluidas en `firestore.rules`. Los eventos
del partido (US-12 a US-14), el marcador y el estado del partido (US-15) y las
estadísticas (US-16) se describen más abajo.


## US-11: alineación por partido

Depende de las convocatorias de US-09. US-10 aporta el estado de disponibilidad como
referencia: no responder no excluye al jugador y no se impide al entrenador alinear
un convocado que marcó «No disponible», porque esa restricción no forma parte de US-11.
Las pruebas locales cubren las respuestas del jugador y del tutor verificado y vinculado,
la revocación del vínculo, el cierre del plazo y la consulta del entrenador.

En **Equipos → Convocatorias**, selecciona un equipo y un partido y abre **Alineación del
partido**. Guarda o descarta primero cualquier cambio pendiente en la convocatoria.
Toca una casilla para elegir un titular; usa **Agregar suplente** para el banco. Cada
jugador tiene una sola asignación y se puede mover entre titular y suplente mediante
**Editar**. **Guardar asignación** persiste el cambio inmediatamente; **Retirar** libera
su lugar. Una posición ocupada debe liberarse antes de colocar otro jugador.
La disponibilidad aparece junto a los nombres, incluso cuando el partido ya
comenzó. Las alineaciones incompletas se pueden
preparar y guardar; no se exige tener once titulares desde el primer guardado.

La cancha y las formaciones se comparten con la alineación general del equipo. Cambiar
la formación de un partido conserva las casillas asignadas y pide confirmar que se
adaptarán sus posiciones; no modifica la alineación general ni otro encuentro.
Los jugadores del equipo pueden consultar la alineación desde su convocatoria.
Solo el propietario aprobado puede editarla, y solamente antes del inicio del partido.

La colección nueva es `matchLineups`, con un documento por partido y subcolecciones
`players` (una asignación por jugador) y `slots` (reserva exclusiva de cada casilla titular).
Los cambios de posición y condición se guardan en transacciones; las reglas exigen
convocatoria activa, categoría correcta, propiedad, aprobación y ausencia de duplicados.
Retirar a un jugador de la convocatoria también retira su asignación en la misma
transacción. Si cambia su categoría o membresía desde otro módulo, la pantalla avisa
que su asignación ya no es válida y permite retirarla; no altera ese otro módulo.

## US-12, US-13 y US-14: catálogo y eventos del partido

Los criterios de aceptación y su estado están en
[`docs/criterios-us12-14.md`](docs/criterios-us12-14.md).

**Catálogo (US-12).** `eventCatalog/{tipo}` es el catálogo general (solo el admin, desde
**Entrenamientos → Administración → Catálogo global**, con el botón para crear los nueve
tipos base). `teams/{equipo}/eventTypes/{tipo}` guarda los tipos propios del equipo
(ID `eq_…`) y los generales que el equipo desactiva; lo gestiona la entrenadora dueña
aprobada desde **Equipos → Catálogo de eventos**. Cada tipo define qué datos son
obligatorios (minuto, equipo, jugador, observaciones y datos adicionales). Desactivar un
tipo no borra nada.

**Eventos (US-13).** Se registran en la pestaña **LIVE** del partido y se guardan en
`matchFixtures/{partido}/events/{evento}`. Solo la entrenadora dueña aprobada registra,
con los tipos activos del catálogo de su equipo, desde el inicio del partido hasta 24 h
después. La app comprueba los datos obligatorios del tipo antes de guardar y las reglas
vuelven a validarlos, junto con el minuto, el equipo (propio o rival), que el jugador
esté convocado y que en un cambio salga y entre un jugador distinto. Leen los eventos
la dueña, los jugadores convocados, el tutor vinculado (con el mismo acceso que la
alineación) y el admin. Cada evento guarda quién y cuándo lo registró, y una copia del
nombre del jugador: la dueña y los jugadores ven los nombres, el tutor ve a su jugador y
"Compañero" para el resto, y el admin ve "Jugador". Quien no es la dueña ve como autor
"Entrenadora del equipo".

Las escrituras van en `batch` (evento + revisión), así que funcionan sin conexión: el
evento aparece como "Pendiente de sincronizar" y se envía al reconectar. Si el servidor
lo rechaza al sincronizar (por ejemplo, porque el tipo se desactivó), desaparece y LIVE
muestra el motivo. Los eventos que versiones anteriores guardaban solo en el celular se
descartan una vez, con un aviso.

**Corrección y anulación (US-14).** Desde el detalle del evento, la dueña corrige o anula
hasta 7 días después del inicio. Cada cambio sube la versión y crea
`events/{evento}/revisions/{versión}` con el valor anterior, quién y cuándo. Anular marca
el evento como anulado (nunca se borra) y deja de contar en el marcador. Los eventos y su
historial solo se borran al eliminar el equipo.

**Marcador y estado del partido (US-15).** Criterios en
[`docs/criterios-us15-16.md`](docs/criterios-us15-16.md). El marcador de LIVE se calcula
con los eventos válidos: "Gol", "Penal" con resultado "Gol" y los tipos propios marcados
**Cuenta como gol** en el catálogo del equipo (la casilla se copia en cada evento). No hay
autogol y los goles sin equipo no cuentan (se avisa). El estado es único en Info, tarjetas
y convocatorias: **No iniciado** antes del inicio, **En juego** desde el inicio, y
**Finalizado** cuando la entrenadora dueña aprobada pulsa **Finalizar partido** (pide
confirmación, guarda `endedAt`/`endedBy` y no se puede deshacer) o, si no lo pulsa, 24 h
después del inicio. Tras finalizar no se registran eventos nuevos; corregir y anular sigue
hasta 7 días. El estado se actualiza cada minuto con la hora del celular (las reglas usan
la del servidor). La tutora no lee el partido, así que para ella finaliza a las 24 h.

**Estadísticas (US-16).** La pestaña Estadísticas calcula todo con los eventos válidos
(sin datos de demostración ni resúmenes guardados): por equipo (PJ, G, E, P, GF, GC, DG
con partidos finalizados), por jugador (convocado, titular, suplente, goles, tarjetas,
entradas y salidas) y por partido (totales nuestro/rival). Privacidad (US-22): la dueña ve
todo; el jugador, sus cifras y los totales del equipo de los partidos donde fue convocado;
la tutora, solo las de su jugador vinculado; el admin, solo cifras sin nombres. El
histórico se lee una vez (`get`) y el partido en juego o abierto se escucha en tiempo
real; "Actualizar" vuelve a leer. No se usa `collectionGroup('events')` ni se amplía quién
lee los eventos: la privacidad de las estadísticas se aplica en la app.

**Anuncios internos (US-17).** Criterios en [`docs/criterios-us17.md`](docs/criterios-us17.md).
La pestaña **Anuncios** muestra en tiempo real los anuncios de los equipos del usuario
(dueña, miembro o tutor de un jugador vinculado), agrupados por equipo y con filtro. La
barra inferior muestra el número de no leídos (`users/{uid}/announcementReads/{teamId}`);
los anuncios propios no cuentan y se marcan como vistos al abrir la pestaña. Publica la
entrenadora dueña aprobada en sus equipos o el admin en cualquiera (tipo convocatoria,
entrenamiento o cambio de horario; título hasta 80 y mensaje hasta 500 caracteres). No se
editan; los borra el autor o el admin. El tutor guarda una vez su acceso
(`announcementGuardians/{teamId}_{uid}`) y las reglas comprueban en cada lectura que el
vínculo siga activo. La app se suscribe al tema FCM `team_<teamId>` de cada equipo (no el
admin) y se da de baja al salir del equipo o cerrar sesión. Los push se envían a mano desde
la consola con un texto genérico: ver [`docs/fcm-envio-manual.md`](docs/fcm-envio-manual.md).

## Pruebas aisladas de Firebase

Los emuladores se conservan únicamente para las pruebas automatizadas de reglas;
no son una variante de la aplicación ni se necesitan para abrir SportPro.

1. Ejecuta `scripts/start-callups-local.command` y mantén la ventana abierta.
2. Ejecuta `scripts/prepare-callups-local.command` para correr la suite.

`tests/callups/lineup-check.mjs` encadena las comprobaciones de US-09, US-10 y US-11,
incluido el recorrido QR → membresía → convocatoria → respuesta con reglas reales
en el emulador local. También cubre autorizaciones, persistencia, fechas límite, alineaciones independientes,
escrituras simultáneas y conservación de la alineación general.
Los eventos tienen sus propios scripts con el SDK cliente en `tests/events/`:
`client-catalog-check.mjs` (catálogo, US-12), `client-matches-check.mjs` (lecturas de
la pestaña Partidos), `client-match-events-check.mjs` (eventos e historial, US-13/14) y
`client-status-stats-check.mjs` (finalizar partido, "Cuenta como gol" y lecturas de
Estadísticas, US-15/16). Los anuncios (US-17) se prueban con
`tests/announcements/client-announcements-check.mjs` y las publicaciones de la comunidad
(US-18) con `tests/community/client-community-check.mjs`; los comentarios y reacciones (US-19)
con `tests/community/client-comments-check.mjs`.
Solo utiliza `demo-sportpro` en localhost; sus cuentas y datos ficticios no funcionan
en SportPro ni se migran al Firebase compartido. La interfaz de los datos de prueba
está en http://127.0.0.1:4000/firestore. No ejecutar `firebase deploy` para estas pruebas.
