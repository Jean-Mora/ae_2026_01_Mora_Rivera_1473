# Registro de Decisión Arquitectónica (ADR)

## Datos Informativos

- **ID:** ADR-0004
- **Título:** AWS Cognito como Resource Server compartido, roles vía claim `cognito:groups`
- **Fecha:** 03/08/2026
- **Autores:** Jean Pierre Mora Santillán, Luis Mateo Rivera Escalante
- **Estado Actual:** Aceptado

## 1. Estado

Aceptado desde HU-01 (autenticación con AWS Cognito vía JWT) y HU-02
(protección de endpoints por rol) en `sigpel`, y replicado en `users` al
ensamblar el monorepo. Vigente en la versión actual.

## 2. Contexto

**Problema:** el sistema tiene dos roles de usuario (`ENCARGADO` y
`ESTUDIANTE`) con permisos distintos, y dos microservicios independientes
que ambos necesitan autenticar y autorizar peticiones. Había que decidir
dónde vive la identidad de los usuarios y cómo cada microservicio conoce
el rol de quien hace la petición, sin duplicar un sistema de login propio
en cada uno.

**Requerimientos asociados:** HU-01 (login), HU-02 (protección de
endpoints por rol), y el criterio de rúbrica de autenticación/autorización
con Cognito y roles.

**Factores influyentes:** el equipo no debía implementar su propio sistema
de gestión de contraseñas/sesiones (fuera del alcance del curso y riesgo
de seguridad innecesario); ambos microservicios deben poder validar el
mismo token sin llamarse entre sí.

## 3. Decisión

**Descripción:** se usa un único **User Pool de AWS Cognito** como
proveedor de identidad para todo el sistema. Ambos microservicios se
configuran como **OAuth2 Resource Server** de Spring Security, validando
el JWT contra el mismo `issuer-uri`
(`https://cognito-idp.${AWS_REGION}.amazonaws.com/${COGNITO_USER_POOL_ID}`,
compartido vía `.env`). El rol del usuario no se guarda en una tabla
propia: se deriva del claim `cognito:groups` del token, convertido a
autoridades `ROLE_ENCARGADO` / `ROLE_ESTUDIANTE` por un
`JwtGrantedAuthoritiesConverter`, y se declara en cada endpoint con
`@PreAuthorize("hasRole('...')")`.

**Alcance:** afecta `SecurityConfig` de ambos microservicios,
`application.yml` (issuer compartido), y todos los `controllers` que
declaran `@PreAuthorize`. `users` valida el token pero no distingue roles
propios (`anyRequest().authenticated()`); solo `sigpel` diferencia
`ENCARGADO` de `ESTUDIANTE`.

**Justificación técnica:** centralizar la identidad en Cognito evita
duplicar lógica de autenticación (hashing de contraseñas, expiración de
sesión, recuperación de cuenta) en cada microservicio, y permite validar
el token de forma **stateless** (cada microservicio descarga el JWKS de
Cognito y verifica la firma localmente, sin una llamada de red por cada
petición a un servicio de sesión centralizado). Derivar el rol del propio
token (en vez de consultarlo en una base de datos propia) reduce el
acoplamiento entre microservicios y el número de *round-trips* por
petición.

## 4. Consecuencias (Trade-offs)

**Resultados Positivos (Garantías):**
- Autenticación stateless: ningún microservicio necesita mantener sesión
  ni consultar a un tercero en cada petición para saber si el token es
  válido.
- Un solo lugar (Cognito) para gestionar usuarios y sus roles, en vez de
  duplicar esa gestión en `sigpel` y en `users`.
- Verificado exhaustivamente con pruebas de integración HTTP que cubren
  401 (sin token) y 403 (rol incorrecto) en cada endpoint protegido.

**Resultados Negativos (Pasivos/Deuda):**
- Ambos microservicios dependen de que `AWS_REGION` y
  `COGNITO_USER_POOL_ID` estén correctamente sincronizados en su `.env`;
  un valor desalineado rompe la autenticación de todo el sistema sin un
  mensaje de error obvio para quien despliega.
- `users` no tiene autorización por rol propia (cualquier usuario
  autenticado puede listar o eliminar cualquier perfil vía
  `/api/users/{id}`), una limitación heredada del microservicio base que
  queda documentada como riesgo en el SAD, no resuelta en este ADR.
- El sistema depende de la disponibilidad de AWS Cognito como servicio
  externo: si Cognito no responde, ningún microservicio puede validar
  tokens nuevos (aunque los ya validados en cache de JWKS siguen
  funcionando hasta que expire la clave).
