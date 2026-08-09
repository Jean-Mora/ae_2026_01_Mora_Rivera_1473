# Registro de Decisión Arquitectónica (ADR)

## Datos Informativos

- **ID:** ADR-0001
- **Título:** Arquitectura en capas (controller / service / repository)
- **Fecha:** 03/08/2026
- **Autores:** Jean Pierre Mora Santillán, Luis Mateo Rivera Escalante
- **Estado Actual:** Aceptado

## 1. Estado

Aceptado desde el primer commit funcional del microservicio `sigpel`
(HU-01, autenticación con Cognito) y aplicado de manera consistente a
todas las Historias de Usuario posteriores (HU-02 a HU-25) y al
microservicio `users`. No ha sido superado por ningún ADR posterior.

## 2. Contexto

**Problema:** el backend de SIGPEL necesita exponer una API REST sobre una
base de datos relacional, con reglas de negocio (disponibilidad de
equipos, quién puede aprobar/cancelar un préstamo, unicidad de nombres de
categoría y de número de serie) y validaciones de seguridad (rol y
propiedad del recurso). Había que decidir dónde vive esa lógica: repartida
en los controllers (más rápido de escribir al inicio) o separada en capas
explícitas con responsabilidad única.

**Requerimientos asociados:** transversal a todas las Historias de Usuario
del proyecto (HU-01 a HU-25); en particular a los criterios de aceptación
que exigen autorización por rol y por propiedad (HU-02, HU-15) y a los
que exigen consultas eficientes sin N+1 (HU-08, HU-23).

**Factores influyentes:** el proyecto es evaluado también por cobertura de
pruebas (JaCoCo) y por mantenibilidad del código; una lógica de negocio
mezclada con el manejo de HTTP dificulta escribir pruebas unitarias
rápidas (obligaría a levantar el contexto completo de Spring para probar
cualquier regla de negocio).

## 3. Decisión

**Descripción:** se separa el código de cada microservicio en capas de
responsabilidad única:

- **controllers** — reciben la petición HTTP, delegan al service y
  devuelven el DTO de respuesta. No contienen lógica de negocio; solo
  declaran la autorización por rol (`@PreAuthorize`).
- **services** — contienen toda la lógica de negocio, las transacciones
  (`@Transactional`) y las validaciones de autorización por propiedad
  (p. ej. que el préstamo pertenezca al estudiante que lo cancela).
- **repositories** — acceso a datos vía Spring Data JPA, sin lógica de
  negocio.
- **entities** — mapeo objeto-relacional puro.
- **dto / mappers** — separan lo que la API expone de las entidades JPA,
  evitando fugas del modelo de persistencia hacia el cliente.
- **exceptions** — excepciones de dominio traducidas a códigos HTTP por un
  `GlobalExceptionHandler` centralizado.

**Alcance:** aplica a los dos microservicios del monorepo (`sigpel` y
`users`) y a todos sus módulos de dominio (categorías, equipos, préstamos,
incidencias, usuarios).

**Justificación técnica:** esta separación maximiza la **cohesión
funcional** dentro de cada capa (cada una tiene un único motivo de cambio)
y minimiza el **acoplamiento** entre la exposición HTTP, la lógica de
negocio y la persistencia (Senn, 2004). Se descartó concentrar la lógica
en los controllers porque, aunque más rápido al inicio, mezcla
responsabilidades y obliga a probar reglas de negocio a través de HTTP
simulado en vez de con pruebas unitarias directas y rápidas.

## 4. Consecuencias (Trade-offs)

**Resultados Positivos (Garantías):**
- Los controllers quedan casi triviales, lo que facilita agregar
  validaciones de rol con `@PreAuthorize` sin ensuciar la lógica de
  negocio.
- Las pruebas unitarias se escriben contra los `services` (mockeando
  `repositories` con MockK/Mockito), sin necesidad de levantar el
  contexto de Spring completo — esto contribuyó directamente a alcanzar
  ~99.5% de cobertura de líneas en `sigpel` y ~94.3% en `users`.
- Los DTO desacoplan el contrato público de la API del modelo de
  persistencia: cambiar una entidad (p. ej. agregar `serialNumber` o
  `imageUrl` a `Equipment`) no obliga a romper el contrato JSON existente.

**Resultados Negativos (Pasivos/Deuda):**
- Más archivos y más *boilerplate* (un DTO y un mapper por cada entidad
  expuesta) que si se expusieran las entidades directamente.
- Requiere disciplina del equipo para no "filtrar" lógica de negocio hacia
  el controller cuando se agrega un endpoint nuevo rápido.
