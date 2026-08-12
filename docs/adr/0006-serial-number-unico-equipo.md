# Registro de Decisión Arquitectónica (ADR)

## Datos Informativos

- **ID:** ADR-0006
- **Título:** `serialNumber` único por equipo, en vez de restringir nombre/descripción duplicados
- **Fecha:** 08/08/2026
- **Autores:** Jean Pierre Mora Santillán, Luis Mateo Rivera Escalante
- **Estado Actual:** Aceptado

## 1. Estado

Aceptado en la versión actual del software. Reemplaza una validación
anterior, más restrictiva, que impedía crear dos equipos con el mismo
`name` dentro de la misma categoría — esa validación fue removida
explícitamente al tomar esta decisión (no se documentó como ADR aparte
por ser una iteración de la misma decisión de negocio, corregida antes de
llegar a `develop`).

## 2. Contexto

**Problema:** al cargar datos de demostración vía Postman, se crearon por
error cuatro registros idénticos de "Laptop Dell Latitud" (mismo `name`,
misma `description`, sin ningún campo que los distinguiera). Esto reveló
una pregunta de negocio real: ¿debe el sistema impedir nombres de equipo
duplicados, o el inventario puede tener legítimamente varias unidades
físicas idénticas (varias laptops del mismo modelo) que deben poder
crearse como registros separados?

**Requerimientos asociados:** extensión del criterio de aceptación de
HU-07 (registrar equipo); el equipo del proyecto determinó, en
conversación con el flujo de negocio real de un laboratorio, que un
inventario de equipos de laboratorio sí puede tener múltiples unidades
idénticas del mismo modelo.

**Factores influyentes:** cada equipo puede pasar por múltiples préstamos
a lo largo del tiempo, y el laboratorio necesita poder distinguir **qué
unidad física específica** fue prestada (por ejemplo, para rastrear cuál
de cinco laptops idénticas tiene una incidencia reportada), no solo el
modelo.

## 3. Decisión

**Descripción:** se revierte la validación que impedía nombres duplicados
por categoría, y en su lugar se agrega un campo `serialNumber: String`,
**obligatorio y único a nivel de sistema** (no solo por categoría). Crear
un equipo con un `serialNumber` que ya existe devuelve `409 Conflict`
(mismo patrón que ya usaba `EquipmentCategory` para nombres duplicados:
`DuplicateResourceException`). El campo es nullable a nivel de columna de
base de datos (para que `ddl-auto=update` pudiera agregarlo sin romper
las filas ya existentes en producción) pero obligatorio a nivel de
contrato de API (`@NotBlank` en `EquipmentRequest`).

**Alcance:** entidad `Equipment` (columna `serial_number`, `unique =
true`), `EquipmentRepository.existsBySerialNumber`, `EquipmentDtos`
(`EquipmentRequest`/`EquipmentResponse`), `EquipmentService.create()`, y
la colección de Postman (bodies de creación de equipo actualizados con
`serialNumber`).

**Justificación técnica:** `name` y `description` describen el **modelo**
del equipo (ej. "Laptop Dell Latitude", "proyector con cable HDMI"), que
puede legítimamente repetirse entre unidades de inventario; `serialNumber`
identifica la **unidad física** individual, que por definición no puede
repetirse. Modelar la unicidad en el campo semánticamente correcto (en
vez de forzar nombres artificialmente distintos, como "Laptop Dell #1",
"Laptop Dell #2") mantiene el dato de negocio limpio y reutilizable (el
nombre real del modelo queda intacto para mostrarlo en el catálogo).

## 4. Consecuencias (Trade-offs)

**Resultados Positivos (Garantías):**
- El inventario puede modelar correctamente unidades físicas idénticas
  sin recurrir a nombres artificiales para diferenciarlas.
- Cada unidad queda unívocamente identificable (por su `serialNumber`) a
  lo largo de todo su historial de préstamos e incidencias.
- Cubierto con pruebas unitarias (creación exitosa con nombres repetidos
  pero *serial* distinto) y de integración (`409` ante *serial*
  duplicado), sin afectar la cobertura general del proyecto (~99.5% en
  `sigpel`).

**Resultados Negativos (Pasivos/Deuda):**
- Se requirió limpiar manualmente, vía SQL directo, los cuatro registros
  duplicados sin `serialNumber` que ya existían en la base de datos de
  producción antes de este cambio (no había una migración automática de
  backfill).
- El campo sigue siendo nullable a nivel de base de datos (por la
  restricción de `ddl-auto=update` sobre datos ya existentes); la
  garantía de "obligatorio" depende enteramente de la validación en la
  capa de API, no de una restricción `NOT NULL` en el esquema.
