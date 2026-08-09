plugins {
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.jpa") version "2.2.21"
    jacoco
}

group = "com.puce"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")
    // Sube y sirve las imagenes de equipos. Usa DefaultCredentialsProvider,
    // que en EC2 toma las credenciales del IAM Role de la instancia
    // (nunca access key/secret key hardcodeadas).
    implementation("software.amazon.awssdk:s3:2.28.11")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.mockk:mockk:1.13.12")
    // Solo para que el @SpringBootTest de integracion no necesite una Postgres
    // real corriendo; la app en runtimeOnly usa exclusivamente Postgres.
    testRuntimeOnly("com.h2database:h2")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// Desactiva el jar plano (sin dependencias): solo se necesita el bootJar
// ejecutable para el Dockerfile, y asi build/libs/*.jar no queda ambiguo.
tasks.named<Jar>("jar") {
    enabled = false
}

jacoco {
    toolVersion = "0.8.12"
}

// Excluye del reporte lo que la rubrica permite dejar fuera: clases de
// configuracion, DTOs sin logica, la clase Application y entidades sin
// comportamiento propio (getters/setters generados).
val coverageExclusions = listOf(
    "com/puce/sigpel/SigpelApplication*",
    "com/puce/sigpel/config/**",
    "com/puce/sigpel/dto/**",
    "com/puce/sigpel/entities/**",
    "com/puce/sigpel/exceptions/ErrorResponse*"
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(coverageExclusions) }
        })
    )
}
