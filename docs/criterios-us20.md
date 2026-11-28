# Criterios de aceptación US-20: Avisos de pruebas o convocatorias abiertas

Pruebas:
- Unitarias: `app/src/test/.../TryoutRulesTest.kt`.
- SDK cliente contra el emulador: `tests/tryouts/client-tryouts-check.mjs`.

| # | Criterio | Estado | Cómo se cumple | Pruebas |
|---|----------|--------|----------------|---------|
| 1 | Solo publica el Entrenador aprobado (US-02) | Cumplido | Reglas: `isApprovedCoach()`. UI: `PUBLISH_TRYOUT` y `RoleGate` en la ruta. | script §1 |
| 2 | Título y descripción obligatorios; el resto opcional | Cumplido | `hasAll`/`hasOnly`; título 1-100, descripción 1-2000, ubicación 1-120, categoría y posición de listas cerradas. `authorName == profileDisplayName()`: nombre del perfil o "Usuario SportPro", nunca el correo. | `TryoutRulesTest`, script §1 |
| 3 | Los jugadores filtran por categoría, posición, fecha y ubicación | Cumplido | `TryoutFilter` (las fechas en hora de Lima; la ubicación sin mayúsculas ni tildes). | `TryoutRulesTest` |
| 4 | Pasada la fecha límite pasa a "Vencidos" y sale de "Vigentes" | Cumplido | La fecha límite se guarda como **23:59:59 del día elegido en America/Lima** (zona fija) y las fechas se muestran en Lima. Vigente/vencido compara instantes con la hora actual (reloj cada minuto). Reglas: prueba ≥ ahora − 1 día; fecha límite > ahora y ≤ prueba + 1 día. | `TryoutRulesTest`, script §1 |
| 5 | El autor cancela; un cancelado deja de ser vigente | Cumplido | Cancelar es irreversible (ACTIVE → CANCELLED, con `cancelledAt` del servidor y `cancelledBy = uid`). Pueden el autor, el admin y los moderadores. Si no lo cancela el autor, la app muestra "Cancelado por moderación". | `TryoutRulesTest`, script §3 |

## Lectura
- Vigentes (`status == ACTIVE`): cualquier usuario con sesión.
- Cancelados: solo el autor, el admin y los moderadores.
- Consultas del cliente: `status == ACTIVE` (Vigentes y Vencidos) y `authorId == uid` (Mis avisos).

## Otros
- Nadie edita ni borra avisos.
- **Sin conexión:** crear y cancelar no bloquean y se ve "Pendiente de sincronizar". Si un aviso creado sin conexión llega al servidor cuando la fecha límite ya pasó, la regla lo rechaza.
- Solo colores de `MaterialTheme`.
- **Límite de lecturas en reglas:** crear 2 (users y coachApprovals); leer o cancelar 2 (admins y moderators).
- **Avisos antiguos:** pueden tener `authorName` sacado del correo y la fecha límite en la zona del celular que publicó. No se migran (Spark, sin Functions). Se corrigen a mano en la consola: `tryoutNotices/{id}` → `authorName` = nombre del perfil o "Usuario SportPro".
