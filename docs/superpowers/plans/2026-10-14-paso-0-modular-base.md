# Paso 0 — Base modular · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dejar el backend listo para crecer como ERP modular: límites entre módulos verificados con Spring Modulith, errores HTTP homogéneos (ProblemDetail), API documentada con Swagger y un README para arrancarlo en Windows.

**Architecture:** Monolito modular. Cada paquete directo bajo `com.pedropalazon.sales_manager_backend` es un módulo de negocio. El módulo `shared` contiene lo transversal: excepciones públicas en su raíz y la parte web (health, manejo de errores, OpenAPI) en `shared.web`, que es interna. `ModularityTests` hace fallar el build si se rompe un límite.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring Modulith 2.1.1, springdoc-openapi 3.1.1, PostgreSQL 17 (Docker) y H2 (tests), JUnit 5 + MockMvc.

**Spec:** [docs/ROADMAP.md](../../ROADMAP.md), sección 1 y Paso 0.

## Global Constraints

- Java 21 y Spring Boot 4.1.0. No cambiar la versión del parent.
- Código (clases, métodos, paquetes) en inglés. Mensajes de error devueltos por la API en español.
- Sin Lombok.
- Dinero siempre con `BigDecimal` (no aplica todavía en este paso).
- Los commits los hace Claude **solo si el usuario lo pide expresamente**. Si no, se dejan los cambios preparados y se le indica el comando.
- Mensajes de commit `<tipo>: <descripción>` sin `Co-Authored-By` ni firmas.
- El cambio local de `.gitignore` (ignora `CLAUDE.md` y `/.claude`) es del usuario: **no se incluye en ningún commit** salvo que lo pida.
- En PowerShell, los argumentos `-D` de Maven van entre comillas: `.\mvnw.cmd test "-Dtest=ModularityTests"`.
- Antes de cada commit: `.\mvnw.cmd test` en verde.

## Review Focus

1. **JSON mal formado** en un POST → 400 con `application/problem+json`, nunca 500. Test en la Tarea 4.
2. **Error inesperado (500)** → el mensaje interno de la excepción **no** aparece en la respuesta. Test en la Tarea 4.
3. **URL inexistente** → 404 en formato ProblemDetail, no la página de error HTML de Spring. Test en la Tarea 4.
4. **Validación con varios campos** → cada error trae `field` y `message` para que el frontend lo pinte junto al campo. Test en la Tarea 4.
5. **Arrancar sin indicar perfil** (por ejemplo, Run desde IntelliJ) → usa `dev` y conecta a PostgreSQL en vez de fallar por falta de datasource. Se comprueba a mano en la Tarea 6.

---

## File Structure

```text
pom.xml                                                        (modificar: BOM Modulith, springdoc)
README.md                                                      (crear)
docs/ROADMAP.md                                                (ya existe, se commitea)
src/main/resources/application.yaml                            (modificar: perfil por defecto dev)
src/main/java/com/pedropalazon/sales_manager_backend/
├── common/controller/HealthController.java                    (borrar → se mueve)
└── shared/
    ├── ResourceNotFoundException.java                         (crear, API pública)
    ├── BusinessRuleException.java                             (crear, API pública)
    └── web/
        ├── HealthController.java                              (movido)
        ├── GlobalExceptionHandler.java                        (crear)
        └── OpenApiConfig.java                                 (crear)
src/test/java/com/pedropalazon/sales_manager_backend/
├── ModularityTests.java                                       (crear)
├── common/controller/HealthControllerTest.java                (borrar → se mueve)
└── shared/web/
    ├── HealthControllerTest.java                              (movido)
    ├── GlobalExceptionHandlerTest.java                        (crear)
    └── OpenApiDocumentationTest.java                          (crear)
```

---

### Task 1: Rama y primer commit (limpieza + roadmap)

**Files:**
- Ya preparado: `AGENTS.md` e `IMPLEMENTATION_PLAN.md` borrados (staged); `docs/ROADMAP.md` y este plan creados.

**Interfaces:**
- Consumes: estado actual de `feature/product-crud` (commit `56b8976`).
- Produces: rama `chore/modular-base` donde viven todas las tareas siguientes.

- [ ] **Step 1: Crear la rama desde la actual**

Los cambios del working tree viajan con el cambio de rama.

```bash
git switch -c chore/modular-base
git status --short
```

Esperado: `D  AGENTS.md`, `D  IMPLEMENTATION_PLAN.md`, ` M .gitignore`, `?? docs/`.

- [ ] **Step 2: Comprobar que los tests pasan antes de tocar nada**

Run: `.\mvnw.cmd test`
Esperado: `BUILD SUCCESS` (`contextLoads` y `HealthControllerTest`).

- [ ] **Step 3: Commit (solo con autorización del usuario)**

```bash
git add docs/ROADMAP.md docs/superpowers/plans/2026-10-14-paso-0-modular-base.md
git commit -m "chore: remove previous plan files and add roadmap"
```

Los borrados ya están staged. `.gitignore` **no** se añade.

---

### Task 2: Spring Modulith y test de modularidad

**Files:**
- Modify: `pom.xml`
- Create: `src/test/java/com/pedropalazon/sales_manager_backend/ModularityTests.java`

**Interfaces:**
- Produces: `ModularityTests`, que todas las tareas posteriores (y los pasos 1–8) deben mantener en verde.

- [ ] **Step 1: Escribir el test**

```java
package com.pedropalazon.sales_manager_backend;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(SalesManagerBackendApplication.class);

    @Test
    void givenApplicationModules_whenVerified_thenNoModuleBoundaryIsViolated() {
        modules.verify();
    }

    @Test
    void givenApplicationModules_whenDocumented_thenDiagramsAreWritten() {
        // Genera diagramas PlantUML y fichas de cada módulo en target/spring-modulith-docs
        new Documenter(modules).writeDocumentation();
    }
}
```

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `.\mvnw.cmd test "-Dtest=ModularityTests"`
Esperado: FAIL de compilación, `package org.springframework.modulith.core does not exist`.

- [ ] **Step 3: Añadir Spring Modulith al `pom.xml`**

En `<properties>`:

```xml
<java.version>21</java.version>
<spring-modulith.version>2.1.1</spring-modulith.version>
```

Después de `</dependencies>` y antes de `<build>`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-bom</artifactId>
            <version>${spring-modulith.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Dentro de `<dependencies>`, junto a las demás de test:

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

`spring-modulith-starter-core` (eventos entre módulos) no se añade todavía: se añadirá cuando un módulo publique el primer evento.

- [ ] **Step 4: Ejecutarlo y ver que pasa**

Run: `.\mvnw.cmd test "-Dtest=ModularityTests"`
Esperado: PASS. En `target/spring-modulith-docs` aparecen ficheros `.puml` y `.adoc` de los módulos actuales (`common`, `product`).

Si Maven no resuelve 2.1.1 contra Boot 4.1.0, probar la última `2.1.x` disponible y anotar la versión usada en el resumen.

- [ ] **Step 5: Suite completa**

Run: `.\mvnw.cmd test`
Esperado: `BUILD SUCCESS`.

- [ ] **Step 6: Commit (solo con autorización del usuario)**

```bash
git add pom.xml src/test/java/com/pedropalazon/sales_manager_backend/ModularityTests.java
git commit -m "chore: add spring modulith and modularity test"
```

---

### Task 3: Mover el health endpoint al módulo `shared`

**Files:**
- Delete: `src/main/java/com/pedropalazon/sales_manager_backend/common/controller/HealthController.java`
- Create: `src/main/java/com/pedropalazon/sales_manager_backend/shared/web/HealthController.java`
- Delete: `src/test/java/com/pedropalazon/sales_manager_backend/common/controller/HealthControllerTest.java`
- Create: `src/test/java/com/pedropalazon/sales_manager_backend/shared/web/HealthControllerTest.java`

**Interfaces:**
- Produces: paquete `shared.web` (interno de `shared`), donde viven las piezas web transversales de las tareas 4 y 5. `GET /api/health` mantiene su contrato.

- [ ] **Step 1: Mover con git para conservar el historial**

```bash
mkdir -p src/main/java/com/pedropalazon/sales_manager_backend/shared/web src/test/java/com/pedropalazon/sales_manager_backend/shared/web
git mv src/main/java/com/pedropalazon/sales_manager_backend/common/controller/HealthController.java src/main/java/com/pedropalazon/sales_manager_backend/shared/web/HealthController.java
git mv src/test/java/com/pedropalazon/sales_manager_backend/common/controller/HealthControllerTest.java src/test/java/com/pedropalazon/sales_manager_backend/shared/web/HealthControllerTest.java
```

- [ ] **Step 2: Cambiar la declaración de paquete en ambos ficheros**

En los dos ficheros movidos, sustituir:

```java
package com.pedropalazon.sales_manager_backend.common.controller;
```

por:

```java
package com.pedropalazon.sales_manager_backend.shared.web;
```

El resto del contenido no cambia.

- [ ] **Step 3: Borrar las carpetas `common` vacías**

```bash
rmdir src/main/java/com/pedropalazon/sales_manager_backend/common/controller src/main/java/com/pedropalazon/sales_manager_backend/common
rmdir src/test/java/com/pedropalazon/sales_manager_backend/common/controller src/test/java/com/pedropalazon/sales_manager_backend/common
```

- [ ] **Step 4: Ejecutar los tests**

Run: `.\mvnw.cmd test`
Esperado: `BUILD SUCCESS`. `HealthControllerTest` pasa en su nuevo paquete y `ModularityTests` ya detecta los módulos `shared` y `product`.

- [ ] **Step 5: Commit (solo con autorización del usuario)**

```bash
git add -A src/main/java/com/pedropalazon/sales_manager_backend src/test/java/com/pedropalazon/sales_manager_backend
git commit -m "refactor: move health endpoint to shared module"
```

---

### Task 4: Manejo global de errores con ProblemDetail

**Files:**
- Create: `src/main/java/com/pedropalazon/sales_manager_backend/shared/ResourceNotFoundException.java`
- Create: `src/main/java/com/pedropalazon/sales_manager_backend/shared/BusinessRuleException.java`
- Create: `src/main/java/com/pedropalazon/sales_manager_backend/shared/web/GlobalExceptionHandler.java`
- Test: `src/test/java/com/pedropalazon/sales_manager_backend/shared/web/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces (API pública de `shared`, la usarán todos los módulos):
  - `new ResourceNotFoundException(String message)` → HTTP 404, título "Recurso no encontrado", `detail` = mensaje.
  - `new BusinessRuleException(String message)` → HTTP 409, título "Operación no permitida", `detail` = mensaje.
  - Validación `@Valid` → HTTP 400, título "Datos no válidos", propiedad `errors: [{ "field": "...", "message": "..." }]`.
  - Cualquier otra excepción → HTTP 500, título "Error inesperado" y un `detail` genérico, sin filtrar el mensaje interno.

- [ ] **Step 1: Escribir el test**

```java
package com.pedropalazon.sales_manager_backend.shared.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.FailingController.class)
@Import(GlobalExceptionHandlerTest.FailingController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenMissingResource_whenRequested_thenReturnsNotFoundProblem() throws Exception {
        mockMvc.perform(get("/test-errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("Pedido 7 no encontrado"));
    }

    @Test
    void givenBrokenBusinessRule_whenRequested_thenReturnsConflictProblem() throws Exception {
        mockMvc.perform(get("/test-errors/business-rule"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Operación no permitida"))
                .andExpect(jsonPath("$.detail").value("El pedido ya está entregado"));
    }

    @Test
    void givenUnexpectedError_whenRequested_thenReturnsGenericProblemWithoutInternalMessage() throws Exception {
        mockMvc.perform(get("/test-errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Error inesperado"))
                .andExpect(content().string(not(containsString("secreto interno"))));
    }

    @Test
    void givenInvalidFields_whenPosted_thenReturnsBadRequestWithOneErrorPerField() throws Exception {
        mockMvc.perform(post("/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Datos no válidos"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.errors[?(@.field == 'price')].message").value("El precio no puede ser negativo"));
    }

    @Test
    void givenMalformedJson_whenPosted_thenReturnsBadRequestProblem() throws Exception {
        mockMvc.perform(post("/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void givenUnknownUrl_whenRequested_thenReturnsNotFoundProblem() throws Exception {
        mockMvc.perform(get("/test-errors/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @RestController
    @RequestMapping("/test-errors")
    static class FailingController {

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Pedido 7 no encontrado");
        }

        @GetMapping("/business-rule")
        void businessRule() {
            throw new BusinessRuleException("El pedido ya está entregado");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("secreto interno");
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody TestRequest request) {
        }
    }

    public record TestRequest(
            @NotBlank(message = "El nombre es obligatorio") String name,
            @PositiveOrZero(message = "El precio no puede ser negativo") BigDecimal price) {
    }
}
```

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `.\mvnw.cmd test "-Dtest=GlobalExceptionHandlerTest"`
Esperado: FAIL de compilación, `cannot find symbol: class ResourceNotFoundException`.

- [ ] **Step 3: Crear las excepciones públicas de `shared`**

`ResourceNotFoundException.java`:

```java
package com.pedropalazon.sales_manager_backend.shared;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

`BusinessRuleException.java`:

```java
package com.pedropalazon.sales_manager_backend.shared;

public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Crear `GlobalExceptionHandler`**

```java
package com.pedropalazon.sales_manager_backend.shared.web;

import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Extiende ResponseEntityExceptionHandler para que los errores propios de Spring MVC
// (JSON mal formado, URL inexistente, método no soportado...) también salgan como ProblemDetail.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleResourceNotFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", exception.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException exception) {
        return problem(HttpStatus.CONFLICT, "Operación no permitida", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unexpected error while processing request", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error inesperado",
                "Ha ocurrido un error inesperado. Inténtalo de nuevo más tarde.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Datos no válidos", "Hay campos con valores no válidos.");
        List<FieldValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldValidationError(error.getField(),
                        Objects.requireNonNullElse(error.getDefaultMessage(), "Valor no válido")))
                .toList();
        body.setProperty("errors", errors);
        return handleExceptionInternal(exception, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    public record FieldValidationError(String field, String message) {
    }
}
```

- [ ] **Step 5: Ejecutarlo y ver que pasa**

Run: `.\mvnw.cmd test "-Dtest=GlobalExceptionHandlerTest"`
Esperado: PASS (6 tests).

Si `givenUnknownUrl...` devuelve 404 pero sin `application/problem+json`, añadir en `src/main/resources/application.yaml`, bajo `spring:`, `mvc: problemdetails: enabled: true` y volver a ejecutar. No cambiar el test.

- [ ] **Step 6: Suite completa**

Run: `.\mvnw.cmd test`
Esperado: `BUILD SUCCESS`, con `ModularityTests` en verde (`shared.web` usa la raíz de `shared`, que es pública).

- [ ] **Step 7: Commit (solo con autorización del usuario)**

```bash
git add src/main/java/com/pedropalazon/sales_manager_backend/shared src/test/java/com/pedropalazon/sales_manager_backend/shared/web/GlobalExceptionHandlerTest.java
git commit -m "feat: add global error handling with problem details"
```

---

### Task 5: Documentación OpenAPI y Swagger UI

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/pedropalazon/sales_manager_backend/shared/web/OpenApiConfig.java`
- Test: `src/test/java/com/pedropalazon/sales_manager_backend/shared/web/OpenApiDocumentationTest.java`

**Interfaces:**
- Produces: `GET /v3/api-docs` (JSON OpenAPI) y `GET /swagger-ui.html`. Los controladores de los pasos siguientes aparecen ahí automáticamente.

- [ ] **Step 1: Escribir el test**

```java
package com.pedropalazon.sales_manager_backend.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenRunningApplication_whenApiDocsRequested_thenReturnsOpenApiWithTitleAndHealthPath() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("SalesController API"))
                .andExpect(jsonPath("$.paths['/api/health']").exists());
    }
}
```

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `.\mvnw.cmd test "-Dtest=OpenApiDocumentationTest"`
Esperado: FAIL, `/v3/api-docs` responde 404 (ProblemDetail).

- [ ] **Step 3: Añadir springdoc al `pom.xml`**

En `<properties>`:

```xml
<springdoc.version>3.1.1</springdoc.version>
```

En `<dependencies>`, después de `spring-boot-starter-webmvc`:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc.version}</version>
</dependency>
```

Si 3.1.1 no arranca con Boot 4.1.0, probar la última `3.0.x` y anotar la versión usada.

- [ ] **Step 4: Crear `OpenApiConfig`**

```java
package com.pedropalazon.sales_manager_backend.shared.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI salesControllerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SalesController API")
                .description("API REST para gestionar ventas de camisetas, cobros y pedidos al fabricante")
                .version("v1"));
    }
}
```

- [ ] **Step 5: Ejecutarlo y ver que pasa**

Run: `.\mvnw.cmd test "-Dtest=OpenApiDocumentationTest"`
Esperado: PASS.

- [ ] **Step 6: Suite completa**

Run: `.\mvnw.cmd test`
Esperado: `BUILD SUCCESS`.

- [ ] **Step 7: Commit (solo con autorización del usuario)**

```bash
git add pom.xml src/main/java/com/pedropalazon/sales_manager_backend/shared/web/OpenApiConfig.java src/test/java/com/pedropalazon/sales_manager_backend/shared/web/OpenApiDocumentationTest.java
git commit -m "chore: add openapi documentation"
```

---

### Task 6: Perfil por defecto, README y prueba real contra PostgreSQL

**Files:**
- Modify: `src/main/resources/application.yaml`
- Create: `README.md`

**Interfaces:**
- Produces: la app arranca con `dev` si no se indica perfil, y el README explica cómo levantarla.

- [ ] **Step 1: Perfil por defecto `dev`**

`src/main/resources/application.yaml` queda así (más el bloque `mvc` si la Tarea 4 lo necesitó):

```yaml
spring:
  application:
    name: sales-manager-backend
  profiles:
    default: dev
  jpa:
    open-in-view: false

server:
  port: 8080
```

`default` solo se aplica si no hay ningún perfil activo. Los tests siguen usando `test` porque lo fijan con `@ActiveProfiles("test")`.

- [ ] **Step 2: Escribir `README.md`**

````markdown
# SalesController

Backend para gestionar la venta de camisetas: catálogo con precios, clientes, pedidos, cobros, lotes al fabricante (con su Excel) e informes de beneficio.

Arquitectura, modelo de dominio y pasos de desarrollo: [docs/ROADMAP.md](docs/ROADMAP.md).

## Requisitos

- JDK 21 (`java -version` debe mostrar 21).
- Docker Desktop arrancado.
- No hace falta instalar Maven: se usa el wrapper `mvnw.cmd`.

## Arrancar en local (Windows, PowerShell)

1. Levantar PostgreSQL:

   ```powershell
   docker compose up -d
   ```

   Crea la base de datos `sales_manager` (usuario y contraseña `sales_manager`, solo para desarrollo) en el puerto 5432. Se pueden cambiar con las variables `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` y `POSTGRES_PORT`.

2. Arrancar la aplicación (usa el perfil `dev` por defecto):

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   Desde IntelliJ basta con ejecutar `SalesManagerBackendApplication`.

3. Comprobar que responde:

   - Health: <http://localhost:8080/api/health>
   - Swagger UI: <http://localhost:8080/swagger-ui.html>

4. Parar PostgreSQL (los datos se conservan en el volumen `postgres_data`):

   ```powershell
   docker compose down
   ```

## Tests

```powershell
.\mvnw.cmd test
```

Los tests usan H2 en memoria (perfil `test`) y no necesitan Docker.

`ModularityTests` comprueba que ningún módulo usa clases internas de otro y genera diagramas de los módulos en `target/spring-modulith-docs`.

## Estructura

Cada paquete bajo `com.pedropalazon.sales_manager_backend` es un módulo de negocio (`shared`, `catalog`, `customers`, `sales`, …). Lo que está en la raíz del paquete es su API pública. Lo que está en subpaquetes es interno.
````

- [ ] **Step 3: Suite completa y verify**

Run: `.\mvnw.cmd verify`
Esperado: `BUILD SUCCESS`.

- [ ] **Step 4: Prueba manual contra PostgreSQL real**

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

En otra terminal:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
Invoke-RestMethod http://localhost:8080/v3/api-docs | Select-Object -ExpandProperty info
(Invoke-WebRequest http://localhost:8080/swagger-ui.html).StatusCode
```

Esperado: `status = UP`, `title = SalesController API` y `200`. En el log de arranque aparece Flyway conectado a PostgreSQL sin errores.

Parar la app con Ctrl+C. `docker compose down`.

- [ ] **Step 5: Commit (solo con autorización del usuario)**

```bash
git add README.md src/main/resources/application.yaml
git commit -m "docs: add readme with local setup"
```

---

### Task 7: Cierre del Paso 0

- [ ] **Step 1:** `.\mvnw.cmd verify` en verde y `git status` limpio, salvo el `.gitignore` del usuario.
- [ ] **Step 2:** Resumir al usuario los commits y archivos tocados, y cómo probarlo.
- [ ] **Step 3:** Preguntar si quiere `git push -u origin chore/modular-base` y abrir el PR a `main`. Claude no lo hace sin su autorización.
