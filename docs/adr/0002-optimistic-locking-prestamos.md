# Registro de Decisión Arquitectónica (ADR)

## Datos Informativos

- **ID:** ADR-0002
- **Título:** Optimistic locking para evitar préstamos duplicados
- **Fecha:** 04/08/2026
- **Autores:** Jean Pierre Mora Santillán, Luis Mateo Rivera Escalante
- **Estado Actual:** Aceptado

## 1. Estado

Aceptado desde la implementación de HU-11 (solicitar préstamo). Vigente
en la versión actual del software; no ha sido superado.

## 2. Contexto

**Problema:** dos estudiantes podrían solicitar el mismo equipo casi al
mismo tiempo. Sin control de concurrencia, ambas solicitudes podrían leer
el equipo como `AVAILABLE` y las dos transacciones terminarían creando un
préstamo para el mismo equipo, dejando el inventario inconsistente (un
equipo prestado dos veces a la vez).

**Requerimientos asociados:** HU-11 (solicitar préstamo) — el criterio de
aceptación implícito es que un equipo no puede quedar prestado a dos
estudiantes simultáneamente; también sustenta la meta de calidad de
"Consistencia de datos bajo concurrencia" del SAD del proyecto.

**Factores influyentes:** el sistema se despliega sobre una única
instancia EC2 `t3.micro`, sin infraestructura para locks distribuidos; el
volumen esperado de colisiones reales (dos personas pidiendo el mismo
equipo en el mismo instante) es bajo frente al volumen total de lecturas
(consultar el catálogo es la operación más frecuente).

## 3. Decisión

**Descripción:** se evaluaron dos opciones:

1. **Pessimistic locking** (`SELECT ... FOR UPDATE` / `@Lock(PESSIMISTIC_WRITE)`):
   bloquea la fila del equipo mientras dura la transacción.
2. **Optimistic locking** (`@Version` de JPA/Hibernate): cada `Equipment`
   tiene una columna `version` que Hibernate incrementa en cada `UPDATE`;
   si dos transacciones intentan modificar la misma versión, la segunda
   falla.

Se eligió **optimistic locking**, implementado con `@Version` en la
entidad `Equipment`.

**Alcance:** afecta la entidad `Equipment` (columna `version`) y el flujo
`LoanService.request()`, que valida el estado del equipo y lo actualiza
dentro de la misma transacción (`@Transactional`).

**Justificación técnica:** el locking pesimista bloquearía filas en cada
lectura con intención de escritura, lo cual no es necesario para este
volumen de tráfico y agregaría complejidad de *deadlocks* entre
transacciones concurrentes, además de reducir el *throughput* de lecturas
del catálogo. El optimistic locking no requiere infraestructura adicional
(a diferencia de un lock distribuido con Redis, por ejemplo), lo cual es
apropiado para el alcance de este proyecto académico, y solo penaliza el
caso infrecuente de colisión real.

## 4. Consecuencias (Trade-offs)

**Resultados Positivos (Garantías):**
- El inventario nunca queda inconsistente: dos solicitudes simultáneas
  por el mismo equipo no pueden generar dos préstamos activos a la vez.
- No se paga el costo de bloquear filas en cada lectura del catálogo
  (operación mucho más frecuente que la escritura).
- No se requiere infraestructura adicional (sin locks distribuidos, sin
  dependencia externa como Redis).

**Resultados Negativos (Pasivos/Deuda):**
- Si ocurre una colisión, Hibernate lanza
  `ObjectOptimisticLockingFailureException`, que `GlobalExceptionHandler`
  traduce a `409 Conflict`. El cliente (app móvil/Postman) debe
  interpretar ese código como "alguien más se adelantó, refresca el
  catálogo" — es responsabilidad del cliente reintentar, no algo que el
  servidor resuelve automáticamente.
- No se ha validado empíricamente el comportamiento bajo carga alta
  (muchas colisiones simultáneas), ya que el proyecto no incluye pruebas
  de carga (ver riesgos técnicos en el SAD).
