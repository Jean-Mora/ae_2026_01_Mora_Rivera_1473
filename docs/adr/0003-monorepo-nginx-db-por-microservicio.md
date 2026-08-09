# Registro de Decisión Arquitectónica (ADR)

## Datos Informativos

- **ID:** ADR-0003
- **Título:** Monorepo con nginx como único punto de entrada y base de datos por microservicio
- **Fecha:** 04/08/2026
- **Autores:** Jean Pierre Mora Santillán, Luis Mateo Rivera Escalante
- **Estado Actual:** Aceptado

## 1. Estado

Aceptado desde el ensamblado del monorepo completo (`users` + `nginx` +
`sigpel` + `docker-compose.yml`), posterior a las Historias de Usuario
individuales del microservicio `sigpel`. Vigente en la versión actual.

## 2. Contexto

**Problema:** el proyecto integra dos microservicios de distinto origen:
`users`, provisto como base por la cátedra (originalmente con H2), y
`sigpel`, el dominio propio del equipo. Había que decidir cómo
organizarlos en el repositorio, cómo exponerlos públicamente, y si
debían compartir base de datos.

**Requerimientos asociados:** criterio de entrega de la rúbrica del curso
("Monorepo: `users` + `nginx` + microservicio propio +
`docker-compose.yml`"; "cada microservicio con su propia base de datos,
con healthchecks").

**Factores influyentes:** `users` es un componente que el equipo no debe
reescribir desde cero, solo adaptar (migración de H2 a PostgreSQL,
logging, tests); el despliegue final es sobre una única instancia EC2, lo
que hace valioso reducir la superficie pública expuesta a un solo puerto.

## 3. Decisión

**Descripción:** se organiza el repositorio como un monorepo con
`users/`, `sigpel/` y `nginx/` como directorios hermanos, orquestados por
un único `docker-compose.yml` en la raíz. `nginx` es el **único** servicio
con puerto publicado al host (`ports:`); `sigpel-microservice` y
`users-microservice` usan `expose` (solo alcanzables dentro de la red
interna de Docker Compose) y enrutan por prefijo de contexto
(`SERVER_SERVLET_CONTEXT_PATH=/sigpel` y `/users`, reflejado en
`nginx.conf`). Cada microservicio tiene **su propia instancia de
PostgreSQL**, con credenciales, volumen y healthcheck (`pg_isready`)
independientes; ninguno consulta la base del otro directamente.

**Alcance:** afecta la estructura completa del repositorio, la
configuración de red de `docker-compose.yml`, `nginx/nginx.conf`, y el
`SecurityConfig`/`application.yml` de ambos microservicios (mismo
`AWS_REGION` + `COGNITO_USER_POOL_ID`, para resolver el mismo issuer de
Cognito a través del proxy).

**Justificación técnica:** publicar solo el puerto de `nginx` reduce la
superficie de ataque (los microservicios nunca son alcanzables
directamente desde internet) y centraliza el punto de entrada, requisito
explícito de la rúbrica. Separar las bases de datos respeta el patrón
*database-per-service*: evita acoplar el esquema de `sigpel` al de
`users` (que el equipo no controla completamente) y permite que cada
microservicio evolucione, se reinicie o se reindexe sin afectar al otro.

## 4. Consecuencias (Trade-offs)

**Resultados Positivos (Garantías):**
- Superficie de ataque reducida: un compromiso de red no permite acceso
  directo a los microservicios ni a las bases de datos desde fuera del
  host.
- Los dos microservicios pueden desplegarse, reiniciarse o reconstruirse
  de forma independiente (`docker compose build sigpel-microservice`)
  sin afectar al otro.
- El esquema de cada base de datos evoluciona sin coordinación cruzada.

**Resultados Negativos (Pasivos/Deuda):**
- Si `sigpel` necesitara datos de `users` (p. ej. el nombre real de un
  estudiante en vez de solo su `sub` de Cognito), tendría que llamar a la
  API de `users` por red en vez de hacer un `JOIN` — hoy esa
  comunicación entre microservicios no existe (`Loan.studentUser` guarda
  directamente el `sub`).
- Un error de configuración en `nginx.conf` (como el ocurrido durante el
  desarrollo con el formato YAML de `command:` en los servicios de
  Postgres) puede tumbar el acceso a **ambos** microservicios a la vez,
  al ser el único punto de entrada.
