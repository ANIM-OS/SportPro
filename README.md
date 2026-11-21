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

Cada equipo tiene una plantilla por categoría. El entrenador puede editar el nombre, la categoría y el dorsal de sus integrantes. Al escanear el QR, el jugador solicita el ingreso y el entrenador decide si lo aprueba. La alineación vive en cada partido.

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
La variante **debug** usa la configuración del Firebase compartido. Para validar cambios
de permisos o datos, usa primero **sandbox** con Firebase Emulator.

El entrenador selecciona equipo y partido, marca jugadores de la categoría y guarda.
Puede registrar el partido y ajustar la fecha límite sugerida para las respuestas.
El jugador o tutor autorizado confirma su disponibilidad antes de ese límite.

`firestore.rules` combina las reglas de partidos con los permisos locales de US-22.
La configuración de los emuladores apunta a ese archivo.
La pestaña **Partidos** y
**Equipos → Convocatorias por partido** leen los mismos documentos `matchFixtures`.
El marcador y los eventos todavía no tienen persistencia; la app no muestra
marcadores ni cronologías ficticias como si fueran reales.

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

Las reglas de estas historias están incluidas en `firestore.rules`. Los módulos
de eventos US-12 a US-15 todavía requieren su propia integración de datos.


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
La disponibilidad aparece junto a los nombres. Las alineaciones incompletas se pueden
preparar y guardar; no se exige tener once titulares desde el primer guardado.

Cambiar la formación conserva las casillas asignadas y pide confirmar la adaptación de
sus posiciones. Cada partido tiene su propia alineación. Los jugadores convocados
pueden consultarla desde **Partidos** o desde su convocatoria.
Solo el propietario aprobado puede editarla, y solamente antes del inicio del partido.

La colección nueva es `matchLineups`, con un documento por partido y subcolecciones
`players` (una asignación por jugador) y `slots` (reserva exclusiva de cada casilla titular).
Los cambios de posición y condición se guardan en transacciones; las reglas exigen
convocatoria activa, categoría correcta, propiedad, aprobación y ausencia de duplicados.
Retirar a un jugador de la convocatoria también retira su asignación en la misma
transacción. Si cambia su categoría o membresía desde otro módulo, la pantalla avisa
que su asignación ya no es válida y permite retirarla; no altera ese otro módulo.

## Pruebas aisladas de Firebase

1. Inicia Authentication y Firestore con `scripts/start-callups-local.command`.
2. Ejecuta `node tests/callups/lineup-check.mjs` para probar partidos, disponibilidad y alineación.
3. Selecciona **sandbox** en Android Studio o ejecuta `./gradlew :app:installSandbox`.
4. Abre **SportPro Pruebas**. Esta variante tiene un identificador propio, usa exclusivamente
   `demo-sportpro` y conecta Authentication por el puerto 9099 y Firestore por el 8080.
   Dos emuladores Android en la misma máquina pueden usar las mismas cuentas de prueba.
   El host predeterminado es `10.0.2.2`. Usa `-PsportproEmulatorHost=<host>` para un host de pruebas accesible.
5. La suite de QR, contactos, avisos y mensualidades ejecuta `node firestore-qr.test.cjs`
   con `firebase.emulator.json`. Esa configuración usa el puerto 8188.

Antes de una publicación, registra la revisión de Git y el archivo de reglas exacto, valida los
permisos por rol y guarda una copia de las reglas activas. Para US-22 revisa los vínculos por correo,
las invitaciones sin categoría y los datos de contacto antiguos. Tras publicar, lee las reglas
activas y comprueba el flujo desde una cuenta de cada rol. `firestore.qr-release.rules` corresponde
a la entrega de QR anterior. El archivo de desarrollo y pruebas actual es `firestore.rules`.
