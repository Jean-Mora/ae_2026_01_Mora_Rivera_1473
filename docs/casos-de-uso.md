# Especificación de Casos de Uso

**Sistema:** SIGPEL — Sistema de Gestión de Préstamos de Equipos de Laboratorio
**Integrantes:** Jean Pierre Mora Santillán, Luis Mateo Rivera Escalante
**Versión:** 1.0 — 2026-08-09

> **Nota de trazabilidad:** cada paso de estos casos de uso referencia un
> Requisito Funcional (**RF-XXX**) del [`SRS.md`](SRS.md) (ISO/IEC/IEEE
> 29148). El SRS conserva, en su Matriz de Trazabilidad (§5), la Historia
> de Usuario (HU-XX) original de la que se derivó cada RF.

## Índice

| CU | Nombre | Actor | RF |
|---|---|---|---|
| [CU-01](#cu-01-iniciar-sesión) | Iniciar sesión | Estudiante / Encargado | RF-001, RF-002 |
| [CU-02](#cu-02-registrar-categoría-de-equipo) | Registrar categoría de equipo | Encargado | RF-006 |
| [CU-03](#cu-03-editar-categoría-de-equipo) | Editar categoría de equipo | Encargado | RF-007 |
| [CU-04](#cu-04-eliminar-categoría-de-equipo) | Eliminar categoría de equipo | Encargado | RF-008 |
| [CU-05](#cu-05-registrar-equipo) | Registrar equipo | Encargado | RF-011, RF-012, ADR-0006 |
| [CU-06](#cu-06-subir-imagen-de-equipo) | Subir imagen de equipo | Encargado | RF-013, RF-014, ADR-0005 |
| [CU-07](#cu-07-actualizar-estado-de-equipo) | Actualizar estado de equipo | Encargado | RF-015 |
| [CU-08](#cu-08-eliminar-equipo) | Eliminar equipo | Encargado | RF-016 |
| [CU-09](#cu-09-solicitar-préstamo) | Solicitar préstamo | Estudiante | RF-017, RF-018, RF-019, ADR-0002 |
| [CU-10](#cu-10-aprobar-o-rechazar-préstamo) | Aprobar o rechazar préstamo | Encargado | RF-022, RF-024 |
| [CU-11](#cu-11-marcar-préstamo-como-devuelto) | Marcar préstamo como devuelto | Encargado | RF-022, RF-024 |
| [CU-12](#cu-12-cancelar-préstamo) | Cancelar préstamo | Estudiante | RF-023 |
| [CU-13](#cu-13-registrar-incidencia) | Registrar incidencia | Encargado | RF-025, RF-026 |
| [CU-14](#cu-14-actualizar-incidencia) | Actualizar incidencia | Encargado | RF-028 |
| [CU-15](#cu-15-registrar-perfil-de-usuario) | Registrar perfil de usuario | Estudiante / Encargado | RF-030 |
| [CU-16](#cu-16-actualizar-perfil-de-usuario) | Actualizar perfil de usuario | Estudiante / Encargado | RF-032 |

*(Se excluyen del listado las operaciones de solo consulta —listar
categorías, listar equipos, listar mis préstamos, historial completo de
préstamos, consultar incidencia— por no tener flujos alternos ni
excepciones de negocio relevantes que documentar como caso de uso.)*

---

## CU-01: Iniciar sesión

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-01 — Iniciar sesión |
| **Actor Principal** | Estudiante / Encargado |
| **Precondiciones** | El actor tiene una cuenta creada en el User Pool de AWS Cognito, perteneciente al grupo `ESTUDIANTE` o `ENCARGADO` (RF-001). |
| **Flujo Básico** | 1. El actor envía sus credenciales (usuario/contraseña) directamente a AWS Cognito (`InitiateAuth`, fuera del backend de SIGPEL) (RF-001).<br>2. Cognito valida las credenciales y emite un JWT (`IdToken`) con el claim `cognito:groups`.<br>3. El actor incluye el JWT como `Authorization: Bearer <token>` en cada petición posterior a la API.<br>4. Cada microservicio (`sigpel`, `users`), como Resource Server, valida la firma y vigencia del token contra el JWKS de Cognito, sin necesidad de contactar a Cognito en cada petición (RF-001).<br>5. `sigpel` convierte el claim `cognito:groups` en la autoridad `ROLE_ENCARGADO` o `ROLE_ESTUDIANTE` para las decisiones de autorización posteriores (RF-002). |
| **Flujos Alternos** | Ninguno (el login ocurre contra Cognito, fuera del alcance de la API de SIGPEL). |
| **Excepciones** | **E1.** Token ausente en la petición → la API responde `401 Unauthorized` (`LoggingAuthenticationEntryPoint`, RF-001).<br>**E2.** Token expirado, mal firmado, o con un `issuer` distinto al configurado → `401 Unauthorized`.<br>**E3.** Token válido pero sin el rol requerido por el endpoint → `403 Forbidden` (RF-002). |
| **Postcondiciones** | El actor cuenta con un JWT vigente que la API acepta en peticiones subsecuentes hasta su expiración. |

---

## CU-02: Registrar categoría de equipo

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-02 — Registrar categoría de equipo |
| **Actor Principal** | Encargado |
| **Precondiciones** | Encargado autenticado con rol `ENCARGADO` (CU-01). |
| **Flujo Básico** | 1. Encargado envía `POST /sigpel/categories` con el nombre de la categoría (RF-006).<br>2. Sistema valida que el nombre no esté en blanco.<br>3. Sistema verifica que no exista ya una categoría con ese nombre (sin distinguir mayúsculas/minúsculas) (RF-006).<br>4. Sistema crea la categoría y registra el evento `category.created` en el log.<br>5. Sistema devuelve `201 Created` con la categoría creada. |
| **Flujos Alternos** | Ninguno. |
| **Excepciones** | **E1.** Nombre en blanco → `400 Bad Request`.<br>**E2.** Ya existe una categoría con ese nombre → `409 Conflict` (evento `category.rejected` en el log). |
| **Postcondiciones** | Nueva categoría persistida, disponible para asociar equipos. |

---

## CU-03: Editar categoría de equipo

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-03 — Editar categoría de equipo |
| **Actor Principal** | Encargado |
| **Precondiciones** | Encargado autenticado (CU-01); la categoría a editar existe. |
| **Flujo Básico** | 1. Encargado envía `PATCH /sigpel/categories/{id}` con el nuevo nombre (RF-007).<br>2. Sistema busca la categoría por `id`.<br>3. Sistema verifica que el nuevo nombre no pertenezca ya a otra categoría.<br>4. Sistema actualiza el nombre y devuelve `200 OK` con la categoría actualizada. |
| **Flujos Alternos** | Ninguno. |
| **Excepciones** | **E1.** La categoría no existe → `404 Not Found`.<br>**E2.** El nuevo nombre ya lo tiene otra categoría → `409 Conflict`. |
| **Postcondiciones** | La categoría queda con el nuevo nombre; los equipos ya asociados a ella no se ven afectados (la relación es por `id`, no por nombre). |

---

## CU-04: Eliminar categoría de equipo

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-04 — Eliminar categoría de equipo |
| **Actor Principal** | Encargado |
| **Precondiciones** | Encargado autenticado (CU-01); la categoría a eliminar existe. |
| **Flujo Básico** | 1. Encargado envía `DELETE /sigpel/categories/{id}` (RF-008).<br>2. Sistema busca la categoría por `id`.<br>3. Sistema elimina la categoría.<br>4. Sistema devuelve `204 No Content`. |
| **Flujos Alternos** | Ninguno. |
| **Excepciones** | **E1.** La categoría no existe → `404 Not Found`.<br>**E2.** La categoría todavía tiene equipos asociados (violación de integridad referencial) → `409 Conflict`, con mensaje "no se puede eliminar porque todavía está referenciado por otro recurso" (`GlobalExceptionHandler.handleDataIntegrityViolation`, hallazgo encontrado por un test de integración durante el desarrollo — antes devolvía `500` sin controlar). |
| **Postcondiciones** | La categoría deja de existir; el catálogo de categorías ya no la incluye. |

---

## CU-05: Registrar equipo

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-05 — Registrar equipo |
| **Actor Principal** | Encargado |
| **Precondiciones** | Encargado autenticado (CU-01); la categoría destino existe. |
| **Flujo Básico** | 1. Encargado envía `POST /sigpel/equipment` con `categoryId`, `name`, `serialNumber` y opcionalmente `description` (RF-011).<br>2. Sistema verifica que la categoría exista.<br>3. Sistema verifica que ningún otro equipo tenga ya ese `serialNumber` (ADR-0006).<br>4. Sistema crea el equipo con estado inicial `AVAILABLE`.<br>5. Sistema devuelve `201 Created` con el equipo creado (`imageUrl` en `null`, hasta que se ejecute CU-06). |
| **Flujos Alternos** | 3a. El sistema permite explícitamente que dos equipos compartan `name`/`description` (varias unidades físicas idénticas de inventario) — no es un error, es el comportamiento esperado (ADR-0006). |
| **Excepciones** | **E1.** La categoría no existe → `404 Not Found`.<br>**E2.** `name` o `serialNumber` en blanco, o exceden su longitud máxima → `400 Bad Request`.<br>**E3.** Ya existe un equipo con ese `serialNumber` → `409 Conflict` (evento `equipment.rejected`, ADR-0006). |
| **Postcondiciones** | Nuevo equipo persistido, con estado `AVAILABLE`, disponible para préstamo. |

---

## CU-06: Subir imagen de equipo

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-06 — Subir imagen de equipo |
| **Actor Principal** | Encargado |
| **Precondiciones** | Encargado autenticado (CU-01); el equipo existe (CU-05); el bucket de S3 (`sigpel-equipos-imagenes-jpmora`) es accesible con las credenciales del IAM Role de la instancia (ADR-0005). |
| **Flujo Básico** | 1. Encargado envía `POST /sigpel/equipment/{id}/image` (multipart/form-data, campo `file`).<br>2. Sistema busca el equipo por `id`.<br>3. Sistema valida que el archivo no esté vacío, que su tipo sea `image/jpeg` o `image/png`, y que su tamaño no exceda 5MB.<br>4. Sistema genera una key única (`equipment/{id}/{uuid}-{nombre sanitizado}`) y sube el archivo a S3 usando `DefaultCredentialsProvider` (ADR-0005).<br>5. Sistema construye la URL pública del objeto y la guarda en `equipment.imageUrl`.<br>6. Sistema devuelve `200 OK` con el equipo actualizado, incluyendo la nueva `imageUrl`. |
| **Flujos Alternos** | Ninguno. |
| **Excepciones** | **E1.** El equipo no existe → `404 Not Found`.<br>**E2.** Archivo vacío, de tipo no soportado, o mayor a 5MB → `400 Bad Request`.<br>**E3.** El archivo excede el límite de tamaño de multipart configurado en el servidor (6MB) antes de llegar a la validación de negocio → `400 Bad Request` (`MaxUploadSizeExceededException`).<br>**E4.** Falla la subida a S3 (credenciales, red, bucket) → `500 Internal Server Error` genérico, sin exponer el detalle interno del SDK de AWS al cliente (registrado en el log del servidor) (ADR-0005). |
| **Postcondiciones** | El equipo queda con una `imageUrl` pública y accesible directamente (sin *presigned URL*), visible en `GET /equipment` y `GET /equipment/{id}`. |

---

## CU-07: Actualizar estado de equipo

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-07 — Actualizar estado de equipo |
| **Actor Principal** | Encargado |
| **Precondiciones** | Encargado autenticado (CU-01); el equipo existe. |
| **Flujo Básico** | 1. Encargado envía `PATCH /sigpel/equipment/{id}` con el nuevo `status` (`AVAILABLE`, `LOANED` o `MAINTENANCE`) (RF-015).<br>2. Sistema busca el equipo por `id`.<br>3. Sistema actualiza el estado y devuelve `200 OK` con el equipo actualizado. |
| **Flujos Alternos** | Ninguno — este endpoint permite al Encargado forzar un cambio de estado manual (p. ej. enviar a mantenimiento), independiente del ciclo automático que gestionan las solicitudes de préstamo (CU-09 a CU-11). |
| **Excepciones** | **E1.** El equipo no existe → `404 Not Found`.<br>**E2.** El valor de `status` no es uno de los tres válidos → `400 Bad Request`. |
| **Postcondiciones** | El equipo queda con el nuevo estado; si se cambia a `MAINTENANCE` o `LOANED` manualmente, deja de aparecer como disponible para nuevos préstamos. |

---

## CU-08: Eliminar equipo

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-08 — Eliminar equipo |
| **Actor Principal** | Encargado |
| **Precondiciones** | Encargado autenticado (CU-01); el equipo existe. |
| **Flujo Básico** | 1. Encargado envía `DELETE /sigpel/equipment/{id}` (RF-016).<br>2. Sistema busca el equipo por `id`.<br>3. Sistema lo elimina y devuelve `204 No Content`. |
| **Flujos Alternos** | Ninguno. |
| **Excepciones** | **E1.** El equipo no existe → `404 Not Found`.<br>**E2.** El equipo tiene historial de préstamos asociado (violación de integridad referencial) → `409 Conflict`, en vez de un error `500` sin controlar. |
| **Postcondiciones** | El equipo deja de existir y de aparecer en el catálogo. |

---

## CU-09: Solicitar préstamo

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-09 — Solicitar préstamo |
| **Actor Principal** | Estudiante |
| **Precondiciones** | Estudiante autenticado con rol `ESTUDIANTE` (CU-01); el equipo solicitado existe. |
| **Flujo Básico** | 1. Estudiante envía `POST /sigpel/loans` con `equipmentId` y, opcionalmente, `estimatedReturnDate` (RF-017).<br>2. Sistema valida que, si se envió, `estimatedReturnDate` sea una fecha futura (RF-018).<br>3. Sistema busca el equipo y verifica que su estado sea `AVAILABLE`.<br>4. Sistema marca el equipo como `LOANED` (protegido con *optimistic locking* vía `@Version`, ADR-0002) y crea el préstamo con estado `PENDING`, asociado al `sub` del estudiante autenticado.<br>5. Sistema devuelve `201 Created` con el préstamo creado. |
| **Flujos Alternos** | Ninguno. |
| **Excepciones** | **E1.** `estimatedReturnDate` es una fecha pasada → `400 Bad Request` (RF-018).<br>**E2.** El equipo no existe → `404 Not Found`.<br>**E3.** El equipo no está `AVAILABLE` (ya prestado o en mantenimiento) → `409 Conflict` (evento `loan.rejected`).<br>**E4.** Colisión de concurrencia: dos solicitudes simultáneas por el mismo equipo → la segunda transacción falla por *optimistic locking* (`ObjectOptimisticLockingFailureException`) → `409 Conflict` (ADR-0002). |
| **Postcondiciones** | Préstamo creado en estado `PENDING`; el equipo pasa a `LOANED` y deja de estar disponible para otras solicitudes hasta que se resuelva (CU-10) o se cancele (CU-12). |

---

## CU-10: Aprobar o rechazar préstamo

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-10 — Aprobar o rechazar préstamo |
| **Actor Principal** | Encargado |
| **Precondiciones** | Encargado autenticado (CU-01); el préstamo existe y está en un estado válido para la transición. |
| **Flujo Básico** | 1. Encargado envía `PATCH /sigpel/loans/{id}` con `status` = `APPROVED` o `REJECTED`, y opcionalmente `comment` (RF-022).<br>2. Sistema busca el préstamo por `id`.<br>3. Sistema actualiza el estado del préstamo.<br>4. Sistema registra el cambio en la tabla de auditoría `loan_audit` (estado anterior, estado nuevo, quién lo modificó, cuándo) (RF-024).<br>5. Sistema devuelve `200 OK` con el préstamo actualizado. |
| **Flujos Alternos** | 3a. Si `status` = `REJECTED`, el sistema además libera el equipo (`status` vuelve a `AVAILABLE`), quedando disponible para nuevas solicitudes. |
| **Excepciones** | **E1.** El préstamo no existe → `404 Not Found`.<br>**E2.** `status` no es un valor válido de `LoanStatus` → `400 Bad Request`. |
| **Postcondiciones** | El préstamo queda `APPROVED` (el equipo permanece `LOANED`) o `REJECTED` (el equipo vuelve a `AVAILABLE`); en ambos casos, queda un registro en `loan_audit`. |

---

## CU-11: Marcar préstamo como devuelto

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-11 — Marcar préstamo como devuelto |
| **Actor Principal** | Encargado |
| **Precondiciones** | Encargado autenticado (CU-01); el préstamo existe y está `APPROVED`. |
| **Flujo Básico** | 1. Encargado envía `PATCH /sigpel/loans/{id}` con `status` = `RETURNED` (RF-022).<br>2. Sistema busca el préstamo por `id`.<br>3. Sistema marca el préstamo como `RETURNED` y registra `actualReturnDate` con la fecha/hora actual.<br>4. Sistema libera el equipo (`status` vuelve a `AVAILABLE`).<br>5. Sistema registra el cambio en `loan_audit` (RF-024).<br>6. Sistema devuelve `200 OK` con el préstamo actualizado. |
| **Flujos Alternos** | Ninguno. |
| **Excepciones** | **E1.** El préstamo no existe → `404 Not Found`. |
| **Postcondiciones** | Préstamo en estado `RETURNED`, con `actualReturnDate` registrada; el equipo vuelve a estar disponible para nuevos préstamos; queda un registro en `loan_audit`. |

---

## CU-12: Cancelar préstamo

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-12 — Cancelar préstamo |
| **Actor Principal** | Estudiante |
| **Precondiciones** | Estudiante autenticado con rol `ESTUDIANTE` (CU-01); el préstamo existe y pertenece al estudiante autenticado; el préstamo está `PENDING`. |
| **Flujo Básico** | 1. Estudiante envía `DELETE /sigpel/loans/{id}` (RF-023).<br>2. Sistema busca el préstamo por `id`.<br>3. Sistema verifica que el `studentUser` del préstamo coincida con el `sub` del token del solicitante.<br>4. Sistema verifica que el préstamo esté en estado `PENDING`.<br>5. Sistema libera el equipo (`status` vuelve a `AVAILABLE`) y elimina el préstamo.<br>6. Sistema devuelve `204 No Content`. |
| **Flujos Alternos** | Ninguno. |
| **Excepciones** | **E1.** El préstamo no existe → `404 Not Found`.<br>**E2.** El préstamo pertenece a otro estudiante → `403 Forbidden` (autorización por propiedad, no por rol) (RF-023).<br>**E3.** El préstamo ya no está `PENDING` (ya fue aprobado, rechazado o devuelto) → `403 Forbidden` ("solo se puede cancelar mientras está PENDING"). |
| **Postcondiciones** | El préstamo deja de existir; el equipo vuelve a estar disponible. |

---

## CU-13: Registrar incidencia

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-13 — Registrar incidencia |
| **Actor Principal** | Encargado |
| **Precondiciones** | Encargado autenticado (CU-01); el préstamo referenciado existe. |
| **Flujo Básico** | 1. Encargado envía `POST /sigpel/incidents` con `loanId`, `type` (`DAMAGE`, `LOSS` o `DELAY`) y opcionalmente `description` (RF-025).<br>2. Sistema verifica que el préstamo exista.<br>3. Sistema crea la incidencia asociada a ese préstamo, con `reportDate` en el momento actual.<br>4. Sistema devuelve `201 Created` con la incidencia creada. |
| **Flujos Alternos** | 2a. Un mismo préstamo puede tener varias incidencias registradas (p. ej. daño y retraso a la vez) — no hay restricción de unicidad. |
| **Excepciones** | **E1.** El préstamo no existe → `404 Not Found`.<br>**E2.** `type` no es un valor válido, o `description` excede su longitud máxima → `400 Bad Request`. |
| **Postcondiciones** | Nueva incidencia persistida y asociada al préstamo. |

---

## CU-14: Actualizar incidencia

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-14 — Actualizar incidencia |
| **Actor Principal** | Encargado |
| **Precondiciones** | Encargado autenticado (CU-01); la incidencia existe. |
| **Flujo Básico** | 1. Encargado envía `PATCH /sigpel/incidents/{id}` con los campos actualizados (`loanId`, `type`, `description`) (RF-028).<br>2. Sistema busca la incidencia por `id`.<br>3. Sistema actualiza sus datos y devuelve `200 OK` con la incidencia actualizada. |
| **Flujos Alternos** | Ninguno. |
| **Excepciones** | **E1.** La incidencia no existe → `404 Not Found`.<br>**E2.** `type` no es un valor válido → `400 Bad Request`. |
| **Postcondiciones** | La incidencia queda con la información corregida/ampliada. |

---

## CU-15: Registrar perfil de usuario

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-15 — Registrar perfil de usuario |
| **Actor Principal** | Estudiante / Encargado |
| **Precondiciones** | Actor autenticado (CU-01, cualquier rol — `users` no distingue roles propios); el `sub` de Cognito del actor no tiene todavía un perfil asociado. |
| **Flujo Básico** | 1. Actor envía `POST /users/api/users/me` con `name`, `email` y `phone`.<br>2. Sistema toma el `cognitoId` del claim `sub` del JWT (nunca del *body*).<br>3. Sistema valida que `name` no esté en blanco.<br>4. Sistema verifica que ese `cognitoId` no tenga ya un perfil (relación 1:1).<br>5. Sistema crea el perfil y devuelve `200 OK` con los datos guardados (`id`, `cognitoId`, `name`, `email`, `phone`). |
| **Flujos Alternos** | Ninguno. |
| **Excepciones** | **E1.** Sin token → `401 Unauthorized`.<br>**E2.** `name` en blanco → `400 Bad Request`.<br>**E3.** Ese `cognitoId` ya tiene un perfil creado → `409 Conflict`. |
| **Postcondiciones** | Nuevo perfil de usuario persistido, asociado 1:1 a la identidad de Cognito del actor. |

---

## CU-16: Actualizar perfil de usuario

| Campo | Detalle |
|---|---|
| **ID / Nombre** | CU-16 — Actualizar perfil de usuario |
| **Actor Principal** | Estudiante / Encargado |
| **Precondiciones** | Actor autenticado (CU-01); el actor ya tiene un perfil registrado (CU-15). |
| **Flujo Básico** | 1. Actor envía `PUT /users/api/users/me` con `name`, `email` y `phone` actualizados.<br>2. Sistema busca el perfil por el `cognitoId` del token.<br>3. Sistema valida que `name` no esté en blanco.<br>4. Sistema actualiza el perfil y devuelve `200 OK` con los datos guardados. |
| **Flujos Alternos** | Ninguno. |
| **Excepciones** | **E1.** Sin token → `401 Unauthorized`.<br>**E2.** El actor no tiene perfil registrado todavía → `404 Not Found`.<br>**E3.** `name` en blanco → `400 Bad Request`. |
| **Postcondiciones** | El perfil queda con los datos actualizados. |

---

## Notas de alcance

- Se documentan solo las operaciones con reglas de negocio, flujos alternos
  o excepciones relevantes. Las operaciones de solo lectura (listar
  categorías, listar/filtrar equipos, listar mis préstamos, historial
  completo de préstamos como Encargado, consultar una incidencia o un
  usuario) no se documentan como caso de uso independiente por no tener
  comportamiento condicional que documentar más allá de "devuelve la
  lista/el recurso o `404`".
- `users` no diferencia roles propios (`anyRequest().authenticated()`):
  cualquier actor autenticado, sea `ESTUDIANTE` o `ENCARGADO`, puede
  ejecutar cualquier operación de `users` (incluidas las administrativas
  como listar todos los usuarios o eliminar un perfil ajeno). Esto queda
  documentado como riesgo en el SAD (§9), no se reinterpretó aquí como una
  restricción de actor que el sistema realmente aplica.
