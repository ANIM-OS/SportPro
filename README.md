# SportPro

SportPro es una aplicación Android en desarrollo para la gestión deportiva de equipos y academias. Reúne jugadores, entrenadores y tutores alrededor de equipos, entrenamientos, partidos y comunidad.

## Estado del proyecto

El proyecto está en fase de desarrollo. La aplicación y las reglas locales incluyen funciones que aún requieren validación y despliegue coordinado antes de estar disponibles en producción. Algunas pantallas de estadísticas y eventos siguen utilizando datos de demostración. Los resúmenes de partidos con IA forman parte del prototipo y requieren una integración definitiva de backend.

## Funcionalidades

- Registro e inicio de sesión mediante Firebase Authentication.
- Perfiles de jugador, entrenador y padre o tutor.
- Equipos, categorías y solicitudes de ingreso mediante QR o invitación.
- Vinculación de equipos con academias y gestión de tutores.
- Entrenamientos, asistencia y convocatorias para partidos.
- Disponibilidad y alineaciones asociadas a cada partido.
- Comunidad, avisos internos y mensualidades simuladas.

La disponibilidad de cada función depende de las reglas activas de Firebase y de los permisos de la cuenta. Las mensualidades representan estados simulados; la aplicación no procesa pagos.

## Roles y permisos

Una cuenta puede tener varios roles de perfil. El acceso a información y acciones depende también de las relaciones activas y los permisos del servidor.

- **Jugador:** consulta sus equipos, entrenamientos y convocatorias; puede iniciar una invitación para vincular a un tutor.
- **Padre o tutor:** consulta la información permitida de los jugadores con los que mantiene un vínculo activo.
- **Entrenador:** gestiona sus equipos y sus actividades; algunas acciones requieren aprobación.
- **Responsable de academia:** gestiona su academia y las relaciones autorizadas con equipos.
- **Administrador de SportPro:** dispone de autoridad asignada de forma privada en Firebase, independiente de los roles de perfil.

Las decisiones funcionales sobre relaciones, visibilidad y revocaciones están documentadas en [Privacidad y permisos](PRIVACIDAD_Y_PERMISOS.md).

## Partidos y convocatorias

Desde **Partidos**, el entrenador puede registrar encuentros y gestionar sus convocatorias. Los jugadores y tutores autorizados pueden consultar las convocatorias y responder su disponibilidad dentro del plazo establecido.

La alineación pertenece a un partido concreto. El entrenador puede asignar titulares y suplentes entre los jugadores convocados; una alineación puede guardarse incompleta. La disponibilidad se muestra como referencia y no impide al entrenador seleccionar a un jugador.

Los partidos y convocatorias utilizan Firestore. La integración de marcadores y eventos persistentes todavía está pendiente; no deben tratarse las estadísticas de demostración como datos reales.

## Desarrollo local

### Requisitos

- Android Studio y un SDK compatible con la configuración de Gradle.
- Un JDK compatible con la versión de Gradle del proyecto y Node.js para las herramientas de Firebase.
- Una configuración de Firebase válida para ejecutar la variante habitual.

La variante **debug** se conecta al proyecto Firebase configurado en la aplicación. Para probar Authentication y Firestore de forma aislada, utiliza la variante **sandbox**, que usa el proyecto de demostración `demo-sportpro`. El emulador de Storage no está configurado en esta variante.

### Ejecutar con Firebase Emulator

Desde la raíz del proyecto, inicia los emuladores de Authentication y Firestore:

```bash
scripts/start-callups-local.command
```

En otra terminal instala la variante de pruebas:

```bash
./gradlew :app:installSandbox
```

Abre **SportPro Pruebas** en el emulador Android. Por defecto utiliza `10.0.2.2` para conectar con Authentication en el puerto 9099 y Firestore en el 8080. Para otro dispositivo o entorno de red, configura una dirección accesible mediante `-PsportproEmulatorHost=<host>`.

### Pruebas

Con los emuladores de Authentication y Firestore iniciados, ejecuta las pruebas de partidos, disponibilidad y alineaciones:

```bash
node tests/callups/lineup-check.mjs
```

La suite de permisos de relaciones, QR, contactos y avisos usa una instancia de Firestore en el puerto 8188:

```bash
npx --yes firebase-tools@15.33.0 emulators:exec --project demo-sportpro --config firebase.emulator.json --only firestore 'node firestore-qr.test.cjs'
```

Para compilar y ejecutar las comprobaciones Android:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

## Firebase y seguridad

`firestore.rules` contiene las reglas locales candidatas y `firestore.indexes.json` define los índices gestionados por el proyecto. Los archivos locales no garantizan que las mismas reglas estén activas en producción. Antes de publicar, contrasta la versión desplegada, comprueba los datos existentes y valida los permisos y recorridos por rol.

La autoridad de SportPro se asigna desde un entorno autorizado mediante `admins/{uid}`. La aprobación de entrenadores se gestiona por separado mediante `coachApprovals/{uid}`. Las cuentas no deben poder concederse estos permisos desde la aplicación.

La pantalla de eliminación de cuenta registra una solicitud; el proceso completo de atención y eliminación de datos todavía está pendiente. No almacenes credenciales privadas en el repositorio.

## Tecnologías

Kotlin, Jetpack Compose, Firebase Authentication, Cloud Firestore, Firebase Storage y Gradle.

## Licencia

Este proyecto fue desarrollado inicialmente con fines educativos y actualmente se encuentra en desarrollo.

Copyright © 2026 SPORT PRO. Todos los derechos reservados.

El código fuente, diseño y documentación del proyecto no pueden ser copiados, modificados, distribuidos o utilizados con fines comerciales sin autorización de sus autores.

Actualmente el proyecto no se encuentra abierto a contribuciones externas.
