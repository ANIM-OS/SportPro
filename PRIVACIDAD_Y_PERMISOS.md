# Privacidad y permisos de SportPro

Propuesta funcional de US-22. Define las relaciones que necesitan US-03 (equipos y academias), US-05 (mensualidades) y US-17 (avisos internos). Describe el comportamiento objetivo.

## Identidad y autoridad

- El correo se verifica en el alta. Los recorridos de invitación parten de una cuenta identificada y evitan pedirle al usuario que repita la verificación.
- Jugador, Entrenador y Padre/Tutor son roles de perfil. La pertenencia y el acceso se determinan por relaciones activas asociadas al UID de Firebase Authentication.
- La autoridad administrativa de SportPro reside en `admins/{uid}`. La lista es privada y los clientes no pueden leerla ni modificarla. Una persona autorizada en Firebase designa al primer administrador desde la consola. Delegar la gestión de la lista queda fuera de esta primera etapa.
- El responsable de una academia es una autoridad sobre esa academia. Inicialmente puede ser su propietario registrado. El entrenador administra sus equipos. La misma persona puede ejercer ambas funciones si tiene ambas relaciones.

## Relaciones e invitaciones

| Relación | Inicio | Confirmación y activación | Fin del vínculo |
| --- | --- | --- | --- |
| Jugador → equipo | El entrenador responsable invita para un equipo y una categoría, o el jugador escanea el QR del equipo. | La invitación dirigida requiere aceptación del jugador. El QR genera únicamente una solicitud que el entrenador debe aprobar. El ingreso por cualquier vía crea una membresía única por jugador y equipo, con categoría válida. | El entrenador retira la membresía o el jugador sale. El historial deportivo se conserva con sus propios permisos. |
| Equipo → academia | El responsable propone vincular un equipo. | Si una persona administra ambas entidades puede confirmar directamente. Si son distintas, ambas deben aceptar. El equipo tiene una academia activa como máximo. Un traslado requiere un vínculo nuevo. | Cualquiera de los dos responsables puede cerrar el vínculo. El equipo conserva su historial y los períodos de la academia anterior se conservan donde se generaron. |
| Jugador → padre o tutor | Solo el jugador inicia una invitación desde su cuenta y muestra un QR temporal de un solo uso. | El tutor autenticado escanea y acepta; el jugador confirma la identidad de esa cuenta antes de activar el vínculo entre ambos UID. Ningún tercero interviene. | El jugador puede cancelar la invitación pendiente o revocar el vínculo activo en cualquier momento. El tutor puede rechazar la invitación o renunciar al vínculo. Se registran las fechas y quién realizó cada acción. |

Los padres y tutores no pueden buscar jugadores para solicitarles acceso ni enviarles solicitudes. La academia y la administración tampoco pueden iniciar ni aprobar este vínculo. Antes de enviar la invitación, el jugador ve qué datos y avisos compartirá al activarse. Un tutor puede estar vinculado a varios jugadores y un jugador puede tener más de un tutor autorizado. Cada vínculo se gestiona por separado. El tutor puede recibir avisos para sus jugadores sin incorporarse a la plantilla del equipo.

Una invitación tiene entidad, emisor, vencimiento y estado. Las invitaciones dirigidas identifican a su destinatario; en el QR de tutor, la cuenta que lo escanea queda identificada por UID y espera la confirmación del jugador. El emisor autorizado puede cancelar una invitación pendiente. Una invitación vencida, cancelada o consumida no puede activar un vínculo. Los cambios que activan una relación deben ser atómicos. Los vínculos antiguos de tutor creados sin aceptación se conservan como pendientes y no conceden acceso hasta completar el nuevo flujo.

El responsable de la academia puede conceder y revocar a un entrenador responsabilidades adicionales, delimitadas a un equipo y a permisos explícitos.

La academia activa del jugador se deduce de su membresía de equipo y de la academia asociada a ese equipo. Puede haber más de una academia si el jugador pertenece a varios equipos. La ruta de acceso se vuelve a comprobar cuando cambia cualquiera de las relaciones.

## Mensajes y avisos internos

La primera versión trata US-17 como un tablón de avisos con audiencia explícita. Un chat con respuestas o mensajes directos requiere definir permisos propios.

| Alcance | Quién publica | Quién recibe o consulta |
| --- | --- | --- |
| Equipo | Entrenador responsable del equipo. | Jugadores con membresía activa, sus tutores activos y responsables autorizados de ese equipo, según la audiencia elegida. |
| Academia | Responsable de la academia. | Jugadores de equipos vinculados, sus tutores activos y entrenadores autorizados de esos equipos, según la audiencia elegida. |

El autor ve el alcance y los grupos destinatarios antes de enviar. Puede elegir, por ejemplo, jugadores, tutores o entrenadores. Una notificación abre el aviso solo si la cuenta sigue autorizada. Las notificaciones no incluyen datos sensibles ni estados de mensualidad. Los avisos de una academia no dan acceso a la plantilla ni a la ficha privada de otros equipos. La comunidad tiene sus propios permisos y no comparte automáticamente las audiencias de avisos internos.

## Mensualidades de academia

US-05 registra mensualidades por jugador con estados de pago simulados. Cada registro pertenece a una academia, un jugador y un período de año y mes. Se admite una sola mensualidad por esa combinación, aunque el jugador tenga varios equipos en la academia. El responsable de la academia registra el período, el monto y la fecha de vencimiento, y puede cambiar el estado simulado entre pendiente y pagada. La aplicación identifica estos datos como simulados y no procesa dinero.

Cada creación o cambio conserva quién lo realizó y cuándo. El historial permanece asociado a la academia y al jugador incluso cuando termine la membresía. Los montos se representan en unidades mínimas enteras junto con su moneda.

| Persona | Lectura | Cambios de estado |
| --- | --- | --- |
| Jugador | Sus propias mensualidades e historial. | Sin permiso. |
| Padre o tutor activo | Mensualidades e historial de sus jugadores vinculados. | Sin permiso. |
| Entrenador | Período y estado de jugadores de sus equipos activos y de la academia correspondiente; conserva el historial al que tuvo acceso durante la relación. | Sin permiso. |
| Responsable de academia | Mensualidades registradas por su academia, incluido su historial. | Puede crear mensualidades y actualizar su estado simulado. |
| Administrador de SportPro | Acceso según necesidad administrativa. | Según el permiso explícito otorgado para soporte. |

Los montos se reservan al jugador, sus tutores activos y los responsables autorizados de la academia. Esos datos se guardan aparte del estado que consulta el entrenador. Al salir del equipo, el entrenador conserva únicamente el período y estado de las mensualidades a las que tenía acceso durante la relación; pierde acceso al monto y a los datos personales. El jugador conserva su historial y lo puede consultar junto con sus tutores activos. Si cambia de academia, la nueva academia consulta únicamente los registros propios. Los datos de ejemplo hardcodeados se eliminan del código; cualquier eliminación de registros en Firebase se identifica y comunica antes de aplicarse.

## Datos privados y aplicación de permisos

- El titular, el tutor activo, el entrenador del equipo del jugador y el administrador autorizado pueden consultar teléfono y contacto de emergencia. Otros jugadores y responsables de academia no adquieren ese permiso por la sola pertenencia a la misma entidad.
- Altura y peso quedan fuera de la comunidad y de las vistas de otros jugadores. La ficha de cuenta queda reservada al titular, salvo operaciones administrativas autorizadas.
- El acceso de un tutor se verifica por jugador. Los enlaces revocados dejan de autorizar lecturas, incluso cuando la cuenta conserva el rol Padre/Tutor. Una relación de tutor no otorga permisos de edición por defecto.
- Firestore autoriza documentos completos. Los datos con destinatarios distintos deben guardarse por separado. La interfaz, las consultas, las reglas y las notificaciones deben respetar las mismas relaciones activas.

## Eliminación de cuenta

Al eliminar una cuenta se conserva el historial deportivo y de mensualidades simuladas de forma anonimizada. Quitar solo el nombre visible no basta: deben retirarse los identificadores de cuenta de los datos conservados, incluidas las claves de documentos, referencias y campos de texto que permitan identificar a la persona. Los registros que no puedan anonimizarse se deben revisar antes de conservarlos. Los perfiles, contactos y vínculos de acceso deben eliminarse o dejar de autorizar el acceso.

La aplicación registra la solicitud. Su procesamiento requiere un servicio autorizado que pueda revisar los registros compartidos y eliminar la identidad de Firebase Authentication una vez tratadas las referencias. Hasta disponer del proceso, la solicitud debe mostrarse como pendiente.

## Implementación y criterios de aceptación

1. Añadir la autoridad privada de SportPro y cerrar el acceso amplio de las reglas locales a fichas y publicaciones restringidas. Verificar que el cliente no pueda concederse autoridad.
2. Unificar las altas por invitación y QR, exigir categoría y habilitar la asociación equipo–academia en las reglas con control por ambos responsables. Verificar que una invitación no se pueda consumir dos veces ni para otra cuenta.
3. Implementar el QR de tutor iniciado por el jugador, la aceptación por la cuenta que lo escanea, la confirmación de esa cuenta por el jugador y la revocación. Verificar que nadie pueda invitar en nombre de otro jugador, que un tutor no pueda iniciar solicitudes, que el QR no se reutilice y que un vínculo pendiente o revocado no dé acceso a la ficha, los avisos ni las mensualidades.
4. Añadir avisos internos por audiencia. Verificar que salir del equipo, cerrar el vínculo con la academia o revocar la tutela retire el acceso correspondiente.
5. Implementar mensualidades con estados de pago simulados por jugador y academia. Verificar que jugador y tutor solo vean sus registros autorizados, que el entrenador sea de solo lectura y que un segundo equipo de la misma academia no duplique el período.

La implementación debe contrastarse con el emulador y con los datos existentes antes de desplegar reglas. Las invitaciones de equipo ya tienen una base local. El vínculo con academias, el flujo de tutores, los avisos y las mensualidades todavía requieren trabajo de implementación o corrección.
