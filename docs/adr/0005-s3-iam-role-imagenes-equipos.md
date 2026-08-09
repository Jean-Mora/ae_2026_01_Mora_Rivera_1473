# Registro de Decisión Arquitectónica (ADR)

## Datos Informativos

- **ID:** ADR-0005
- **Título:** Subida de imágenes de equipos a S3 vía IAM Role de instancia, sin credenciales embebidas
- **Fecha:** 09/08/2026
- **Autores:** Jean Pierre Mora Santillán, Luis Mateo Rivera Escalante
- **Estado Actual:** Aceptado

## 1. Estado

Aceptado e implementado en la versión actual del software (endpoint
`POST /sigpel/equipment/{id}/image`), verificado en producción contra el
bucket real (`sigpel-equipos-imagenes-jpmora`) desplegado en la instancia
EC2. No ha sido superado.

## 2. Contexto

**Problema:** el catálogo de equipos necesita mostrar una foto por
equipo. Había que decidir dónde almacenar los archivos binarios (no tiene
sentido guardarlos en PostgreSQL) y, sobre todo, cómo autenticar al
backend contra AWS S3 sin exponer credenciales de larga duración en el
repositorio, en la imagen de Docker o en variables de entorno del `.env`.

**Requerimientos asociados:** funcionalidad de subida y visualización de
imágenes de equipo (extensión posterior a las Historias de Usuario
originales de `sigpel`), y el criterio transversal de seguridad del SAD
("ninguna credencial hardcodeada").

**Factores influyentes:** el backend se despliega en una instancia EC2
con un **IAM Role** ya adjunto (`sigpel-ec2-s3-role`, con permiso de
escritura sobre el bucket); esto hace innecesario y contraproducente usar
una access key/secret key estática.

## 3. Decisión

**Descripción:** las imágenes se almacenan en un bucket de **AWS S3**
(`sigpel-equipos-imagenes-jpmora`, `us-east-1`) con una *bucket policy* de
lectura pública (`s3:GetObject` para `*`), de forma que la URL devuelta
por la API es servible directamente sin necesidad de *presigned URLs*. El
`S3Client` del SDK v2 se configura con
`DefaultCredentialsProvider.create()` — **nunca** con una access
key/secret key explícita —, que en tiempo de ejecución dentro del
contenedor en EC2 resuelve automáticamente las credenciales temporales
del IAM Role de la instancia (vía el servicio de metadatos, IMDS). Cada
archivo se guarda bajo la key `equipment/{equipmentId}/{uuid}-{nombre
sanitizado}`, y el `EquipmentService` valida tipo (`image/jpeg`,
`image/png`) y tamaño (≤5MB) **antes** de intentar la subida.

**Alcance:** un nuevo paquete `storage/` (`S3Service`), un nuevo
`config/S3Config.kt` (bean `S3Client`), el campo `imageUrl` en la entidad
`Equipment` y su DTO de respuesta, el endpoint
`POST /equipment/{id}/image` en `EquipmentController`, y un manejador
genérico (`catch-all`) en `GlobalExceptionHandler` para que cualquier
fallo del SDK de S3 se traduzca a un `500` sin filtrar detalles internos
al cliente.

**Justificación técnica:** usar el IAM Role de la instancia en vez de
credenciales estáticas elimina por completo el riesgo de que una access
key quede filtrada en el repositorio, en un log o en una imagen de Docker
— es la práctica recomendada por AWS para cargas de trabajo que corren
dentro de su propia infraestructura. Delegar la lectura pública a una
*bucket policy* de S3, en vez de generar *presigned URLs* con expiración,
simplifica el cliente (la URL en `imageUrl` es estable y cacheable) a
costa de que cualquiera con la URL pueda leer el objeto — aceptable para
fotos de equipos de laboratorio, que no son información sensible.

## 4. Consecuencias (Trade-offs)

**Resultados Positivos (Garantías):**
- Cero credenciales de AWS en el código, en `.env.example` o en
  `docker-compose.yml`: solo el nombre del bucket y la región (no
  secretos) se pasan por variable de entorno.
- Verificado de punta a punta contra el bucket real: subida vía la API
  pública (a través de nginx), y lectura directa de la URL resultante,
  ambas exitosas.
- Los errores del SDK de S3 (credenciales inválidas, bucket inexistente,
  etc.) nunca llegan al cliente como detalle interno: se registran en el
  log del servidor y se devuelven como un `500` genérico.

**Resultados Negativos (Pasivos/Deuda):**
- El bucket es de lectura pública para **cualquiera** que tenga o
  adivine una key, no solo para las imágenes que el sistema sube;
  mitigado parcialmente por usar keys con UUID no enumerables por fuerza
  bruta razonable, pero sigue siendo una superficie de exposición mayor
  que unas *presigned URLs* con expiración.
- La resolución de credenciales por IMDS depende de que el contenedor
  Docker pueda alcanzar el endpoint de metadatos de la instancia
  (`169.254.169.254`); en configuraciones de red más restrictivas esto
  requeriría configuración adicional (no necesaria en el despliegue
  actual, verificado en producción).
- No hay borrado automático de imágenes huérfanas en S3 cuando se elimina
  un equipo o se reemplaza su foto: el objeto anterior queda en el bucket
  indefinidamente (deuda técnica no resuelta en este ADR).
