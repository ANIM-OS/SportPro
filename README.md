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

La aplicación combina los permisos de todos los roles de una cuenta. Actualmente, Jugador, Entrenador y Padre/Tutor pueden abrir Inicio, En vivo, Estadísticas, Comunidad y Equipos. Jugador y Entrenador pueden publicar en Comunidad; para registrar equipos o academias se requiere el rol Entrenador. Las pantallas de creación se protegen también al navegar directamente a ellas. Las páginas específicas de padres y la vinculación con sus hijos aún no están implementadas.

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

* [ ] Integrar Firebase Authentication.
* [ ] Implementar inicio de sesión con Google.
* [ ] Implementar Firestore.
* [ ] Completar el sistema de roles y permisos.
* [ ] Completar la gestión de equipos y jugadores.
* [ ] Desarrollar estadísticas.
* [ ] Integrar la generación de resúmenes mediante IA.

## Seguridad

Las credenciales, claves de API y archivos con información sensible no deben almacenarse en el repositorio.

Las variables de entorno y credenciales privadas deberán mantenerse fuera del control de versiones mediante `.gitignore`.

Para habilitar la moderación de comunidad, un administrador del proyecto debe crear en Firestore el documento `moderators/{uid}` para la cuenta designada. La aplicación solo permite a esa cuenta ocultar publicaciones; los usuarios no pueden crear documentos de moderadores mediante las reglas de Firestore. Tras asignar el permiso, el moderador debe volver a abrir la pantalla de Comunidad.

La configuración actual de Firestore permite registrar equipos a las cuentas con el rol `ENTRENADOR`; no exige un documento de aprobación adicional. Los propietarios de equipos existentes conservan la administración de sus equipos.

## Licencia

Este proyecto fue desarrollado inicialmente con fines educativos y actualmente se encuentra en desarrollo.

Copyright © 2026 SPORT PRO. Todos los derechos reservados.

El código fuente, diseño y documentación del proyecto no pueden ser copiados, modificados, distribuidos o utilizados con fines comerciales sin autorización de sus autores.

Actualmente el proyecto no se encuentra abierto a contribuciones externas.
