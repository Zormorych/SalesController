# Paso 2 — Clientes · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Módulo `customers` con alta, edición, consulta y búsqueda de clientes (nombre, teléfono de WhatsApp y notas).

**Architecture:** Nuevo módulo Modulith `customers`. De momento todo vive en `customers.internal`; la API pública que use `sales` llegará en el Paso 3.
- **Teléfono:** se guarda normalizado, sin espacios, guiones, puntos ni paréntesis, para poder buscarlo y detectar duplicados aunque se escriba con otro formato.
- **Búsqueda:** una sola caja que busca por nombre (sin distinguir mayúsculas) o por los dígitos del teléfono.
- **Borrado:** no hay ni borrado ni desactivación (YAGNI). Los clientes no se borran.

**Tech Stack:** Java 21, Spring Boot 4.1.0 (Web MVC, Data JPA, Validation, Flyway), Spring Modulith 2.1.1, H2 en modo PostgreSQL para tests y PostgreSQL 17 en dev y Docker.

**Spec:** [docs/ROADMAP.md](../../ROADMAP.md), sección 2 (`customers`) y "Paso 2".

## Global Constraints

- Código, tablas y columnas en inglés (`snake_case` en BD). Mensajes de la API en español.
- Fechas con `LocalDateTime`. Sin Lombok. Sin borrado físico.
- Migración Flyway `V3__create_customers.sql`. Hibernate en `validate`.
- Errores por `shared`: `ResourceNotFoundException` → 404, `BusinessRuleException` → 409.
- `ModularityTests` en verde.
- Rama `feature/customers` desde `origin/main`. Commit + push por tarea, sin `Co-Authored-By`. El `.gitignore` local del usuario no se commitea.
- Maven desde Bash con `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"`.

## Review Focus

1. **Mismo teléfono con otro formato** (`+34 600-123-456` frente a `+34600123456`) → 409 que nombra al cliente que ya lo tiene. Test en la Tarea 2.
2. **Buscar un texto sin dígitos** (`"ana"`) → no devuelve a todos los clientes que tienen teléfono, solo a los que coinciden por nombre. Tests en las Tareas 1 y 2.
3. **Teléfono vacío (`""`) enviado por el frontend** → se guarda como "sin teléfono", no da 400. Varios clientes sin teléfono pueden convivir. Test en la Tarea 2.
4. **Editar un cliente manteniendo su teléfono** → 200, no es un "duplicado de sí mismo". Test en la Tarea 2.
5. **Notas en blanco** → se guardan como `null`. Test en la Tarea 1.

---

## File Structure

```text
src/main/java/com/pedropalazon/sales_manager_backend/customers/internal/
├── Customer.java                 (entidad + normalización del teléfono)
├── CustomerRepository.java       (búsqueda por nombre o dígitos del teléfono)
├── CustomerService.java
└── web/
    ├── CustomerRequest.java · CustomerResponse.java
    └── CustomerController.java   (/api/customers)
src/main/resources/db/migration/V3__create_customers.sql
src/test/java/com/pedropalazon/sales_manager_backend/customers/internal/
├── CustomerRepositoryTest.java
└── web/CustomerApiTest.java
```

---

### Task 1: Entidad `Customer`, migración y repositorio con búsqueda

**Files:**
- Create: `customers/internal/Customer.java`, `customers/internal/CustomerRepository.java`
- Create: `src/main/resources/db/migration/V3__create_customers.sql`
- Test: `customers/internal/CustomerRepositoryTest.java`

**Interfaces:**
- Produces:
  - `Customer(String name, String phone, String notes)`, con `update(...)`, `getId()`, `getName()`, `getPhone()`, `getNotes()`, `getCreatedAt()` y `getUpdatedAt()`.
  - `public static String Customer.normalizePhone(String)`. Si el valor es `null` o está en blanco devuelve `null`; si no, quita espacios, `-`, `.`, `(` y `)`.
  - `CustomerRepository`, con:
    - `findAllByOrderByNameAsc()`.
    - `findByPhone(String)`, que devuelve `Optional<Customer>`.
    - `search(String text, String digits)`. Busca por nombre que contenga `text`, sin distinguir mayúsculas, **o** por teléfono que contenga `digits`, solo si `digits` no está vacío. Ordena por nombre.

- [ ] **Step 1: Crear la rama**

```bash
git fetch origin
git switch -c feature/customers origin/main
```

- [ ] **Step 2: Escribir el test del repositorio**

```java
package com.pedropalazon.sales_manager_backend.customers.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void givenFormattedPhoneAndBlankNotes_whenSaved_thenPhoneIsNormalizedAndNotesAreNull() {
        Customer saved = repository.save(new Customer("  Ana Pérez ", "+34 600-123.456", "   "));
        entityManager.flush();
        entityManager.clear();

        Customer found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("Ana Pérez");
        assertThat(found.getPhone()).isEqualTo("+34600123456");
        assertThat(found.getNotes()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void givenCustomers_whenSearchingByNamePart_thenMatchesIgnoringCaseSortedByName() {
        repository.save(new Customer("Mariano López", "600111222", null));
        repository.save(new Customer("Ana Pérez", "600333444", null));
        repository.save(new Customer("Juan García", "600555666", null));

        assertThat(repository.search("AN", ""))
                .extracting(Customer::getName)
                .containsExactly("Ana Pérez", "Juan García", "Mariano López");
        assertThat(repository.search("pérez", ""))
                .extracting(Customer::getName)
                .containsExactly("Ana Pérez");
    }

    @Test
    void givenCustomers_whenSearchingByPhoneDigits_thenMatchesThePhone() {
        repository.save(new Customer("Ana Pérez", "+34 600 333 444", null));
        repository.save(new Customer("Juan García", "600555666", null));

        assertThat(repository.search("333 444", "333444"))
                .extracting(Customer::getName)
                .containsExactly("Ana Pérez");
    }

    @Test
    void givenTextWithoutDigits_whenSearching_thenDoesNotMatchEveryPhone() {
        repository.save(new Customer("Ana Pérez", "600333444", null));
        repository.save(new Customer("Juan García", "600555666", null));

        assertThat(repository.search("zzz", "")).isEmpty();
    }

    @Test
    void givenSavedCustomer_whenFindingByNormalizedPhone_thenFindsIt() {
        Customer saved = repository.save(new Customer("Ana Pérez", "600 333 444", null));

        assertThat(repository.findByPhone("600333444")).map(Customer::getId).contains(saved.getId());
    }
}
```

- [ ] **Step 3: Ejecutarlo y ver que falla**

Run: `./mvnw -B -ntp test -Dtest=CustomerRepositoryTest`
Esperado: FAIL de compilación, `cannot find symbol: class CustomerRepository`.

- [ ] **Step 4: Migración `V3__create_customers.sql`**

```sql
CREATE TABLE customers (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    phone      VARCHAR(30),
    notes      VARCHAR(500),
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    -- Varios clientes pueden no tener teléfono: UNIQUE permite varios NULL
    CONSTRAINT uk_customers_phone UNIQUE (phone)
);

CREATE INDEX ix_customers_name ON customers (name);
```

- [ ] **Step 5: Entidad `Customer`**

```java
package com.pedropalazon.sales_manager_backend.customers.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Customer() {
    }

    public Customer(String name, String phone, String notes) {
        update(name, phone, notes);
    }

    public void update(String name, String phone, String notes) {
        this.name = name.trim();
        this.phone = normalizePhone(phone);
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    // El teléfono se guarda sin separadores para poder buscarlo y detectar duplicados
    // aunque se escriba con otro formato ("+34 600-123-456" y "+34600123456" son el mismo)
    public static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.replaceAll("[\\s.()-]", "");
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getNotes() {
        return notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 6: Repositorio**

```java
package com.pedropalazon.sales_manager_backend.customers.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findAllByOrderByNameAsc();

    Optional<Customer> findByPhone(String phone);

    // Si el texto no tiene dígitos (digits = ''), no se compara el teléfono: si no, coincidiría con todos
    @Query("""
            select c from Customer c
            where lower(c.name) like lower(concat('%', :text, '%'))
               or (:digits <> '' and c.phone like concat('%', :digits, '%'))
            order by c.name""")
    List<Customer> search(@Param("text") String text, @Param("digits") String digits);
}
```

- [ ] **Step 7: Ejecutarlo y ver que pasa**

Run: `./mvnw -B -ntp test -Dtest=CustomerRepositoryTest`
Esperado: PASS (5 tests). Flyway aplica `V3`.

- [ ] **Step 8: Suite completa, commit y push**

Run: `./mvnw -B -ntp test` → `BUILD SUCCESS` (`ModularityTests` ve el módulo `customers`).

```bash
git add -A src/main src/test docs/superpowers/plans/2026-10-15-paso-2-customers.md
git commit -m "feat: add customer entity and migration"
git push -u origin feature/customers
```

---

### Task 2: Servicio y endpoints REST de clientes

**Files:**
- Create: `customers/internal/CustomerService.java`
- Create: `customers/internal/web/CustomerRequest.java`, `CustomerResponse.java`, `CustomerController.java`
- Test: `customers/internal/web/CustomerApiTest.java`

**Interfaces:**
- Consumes: `Customer`, `Customer.normalizePhone`, `CustomerRepository` (Tarea 1). `ResourceNotFoundException` y `BusinessRuleException` (`shared`).
- Produces:
  - `CustomerService`, con `list(String search)`, `get(Long)`, `create(String name, String phone, String notes)` y `update(Long, String, String, String)`.
  - Endpoints bajo `/api/customers`:
    - `GET` (`?search=` opcional) y `GET /{id}`.
    - `POST` → 201 con `Location`.
    - `PUT /{id}`.
  - `CustomerResponse(id, name, phone, notes)`.

- [ ] **Step 1: Escribir el test de API**

```java
package com.pedropalazon.sales_manager_backend.customers.internal.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerApiTest {

    private static final String CUSTOMERS = "/api/customers";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenValidCustomer_whenCreated_thenReturnsCreatedWithNormalizedPhone() throws Exception {
        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Ana Pérez", "+34 600-123-456", "Pide siempre talla M")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(CUSTOMERS + "/")))
                .andExpect(jsonPath("$.name").value("Ana Pérez"))
                .andExpect(jsonPath("$.phone").value("+34600123456"))
                .andExpect(jsonPath("$.notes").value("Pide siempre talla M"));
    }

    @Test
    void givenEmptyPhone_whenCreatingTwoCustomers_thenBothAreCreatedWithoutPhone() throws Exception {
        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON).content(json("Ana Pérez", "", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").doesNotExist());
        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON).content(json("Juan García", "", null)))
                .andExpect(status().isCreated());
    }

    @Test
    void givenExistingPhone_whenCreatingWithOtherFormat_thenReturnsConflictNamingTheOwner() throws Exception {
        create("Ana Pérez", "+34600123456");

        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Otra Persona", "+34 600 123 456", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El teléfono +34600123456 ya pertenece al cliente Ana Pérez"));
    }

    @Test
    void givenInvalidPhone_whenCreated_thenReturnsBadRequestOnPhone() throws Exception {
        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON).content(json("Ana Pérez", "abc", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("phone"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("El teléfono solo puede tener dígitos, espacios, guiones, puntos, paréntesis y un + inicial"));
    }

    @Test
    void givenMissingName_whenCreated_thenReturnsBadRequestOnName() throws Exception {
        mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON).content(json(" ", "600111222", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("El nombre es obligatorio"));
    }

    @Test
    void givenCustomers_whenSearchingByNameOrPhone_thenReturnsMatchesSortedByName() throws Exception {
        create("Mariano López", "600111222");
        create("Ana Pérez", "600333444");
        create("Juan García", "600555666");

        mockMvc.perform(get(CUSTOMERS).param("search", "ana"))
                .andExpect(jsonPath("$[*].name").value(contains("Ana Pérez", "Mariano López")));
        mockMvc.perform(get(CUSTOMERS).param("search", "555 666"))
                .andExpect(jsonPath("$[*].name").value(contains("Juan García")));
        mockMvc.perform(get(CUSTOMERS))
                .andExpect(jsonPath("$[*].name").value(contains("Ana Pérez", "Juan García", "Mariano López")));
    }

    @Test
    void givenExistingCustomer_whenUpdatedKeepingItsPhone_thenReturnsUpdatedData() throws Exception {
        long id = create("Ana Pérez", "600333444");

        mockMvc.perform(put(CUSTOMERS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Ana Pérez Ruiz", "600 333 444", "Cliente habitual")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Pérez Ruiz"))
                .andExpect(jsonPath("$.phone").value("600333444"))
                .andExpect(jsonPath("$.notes").value("Cliente habitual"));
    }

    @Test
    void givenTwoCustomers_whenGivingOneTheOthersPhone_thenReturnsConflict() throws Exception {
        create("Ana Pérez", "600333444");
        long juanId = create("Juan García", "600555666");

        mockMvc.perform(put(CUSTOMERS + "/" + juanId).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Juan García", "600-333-444", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void givenUnknownId_whenRequested_thenReturnsNotFound() throws Exception {
        mockMvc.perform(get(CUSTOMERS + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el cliente con id 999999"));
    }

    private long create(String name, String phone) throws Exception {
        String body = mockMvc.perform(post(CUSTOMERS).contentType(MediaType.APPLICATION_JSON)
                        .content(json(name, phone, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String json(String name, String phone, String notes) {
        return """
                {"name":%s,"phone":%s,"notes":%s}""".formatted(quote(name), quote(phone), quote(notes));
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
```

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `./mvnw -B -ntp test -Dtest=CustomerApiTest`
Esperado: FAIL. Los tests reciben 404 porque no hay controlador.

- [ ] **Step 3: Servicio**

```java
package com.pedropalazon.sales_manager_backend.customers.internal;

import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Customer> list(String search) {
        if (search == null || search.isBlank()) {
            return repository.findAllByOrderByNameAsc();
        }
        String text = search.trim();
        return repository.search(text, text.replaceAll("\\D", ""));
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el cliente con id %d".formatted(id)));
    }

    @Transactional
    public Customer create(String name, String phone, String notes) {
        ensurePhoneIsFree(phone, null);
        return repository.save(new Customer(name, phone, notes));
    }

    @Transactional
    public Customer update(Long id, String name, String phone, String notes) {
        Customer customer = get(id);
        ensurePhoneIsFree(phone, id);
        customer.update(name, phone, notes);
        return customer;
    }

    private void ensurePhoneIsFree(String phone, Long currentId) {
        String normalizedPhone = Customer.normalizePhone(phone);
        if (normalizedPhone == null) {
            return;
        }
        repository.findByPhone(normalizedPhone)
                .filter(owner -> !owner.getId().equals(currentId))
                .ifPresent(owner -> {
                    throw new BusinessRuleException("El teléfono %s ya pertenece al cliente %s"
                            .formatted(normalizedPhone, owner.getName()));
                });
    }
}
```

- [ ] **Step 4: DTOs y controlador**

`CustomerRequest.java`:

```java
package com.pedropalazon.sales_manager_backend.customers.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String name,

        // Vacío = sin teléfono (el frontend puede mandar "" en vez de null)
        @Pattern(regexp = "^$|^\\+?[0-9 .()-]{6,25}$",
                message = "El teléfono solo puede tener dígitos, espacios, guiones, puntos, paréntesis y un + inicial")
        String phone,

        @Size(max = 500, message = "Las notas no pueden superar 500 caracteres")
        String notes) {
}
```

`CustomerResponse.java`:

```java
package com.pedropalazon.sales_manager_backend.customers.internal.web;

import com.pedropalazon.sales_manager_backend.customers.internal.Customer;

public record CustomerResponse(Long id, String name, String phone, String notes) {

    static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getPhone(), customer.getNotes());
    }
}
```

`CustomerController.java`:

```java
package com.pedropalazon.sales_manager_backend.customers.internal.web;

import com.pedropalazon.sales_manager_backend.customers.internal.Customer;
import com.pedropalazon.sales_manager_backend.customers.internal.CustomerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Clientes", description = "Clientes con su teléfono de WhatsApp y notas")
class CustomerController {

    private final CustomerService service;

    CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    List<CustomerResponse> list(@RequestParam(required = false) String search) {
        return service.list(search).stream().map(CustomerResponse::from).toList();
    }

    @GetMapping("/{id}")
    CustomerResponse get(@PathVariable Long id) {
        return CustomerResponse.from(service.get(id));
    }

    @PostMapping
    ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        Customer customer = service.create(request.name(), request.phone(), request.notes());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(customer.getId())
                .toUri();
        return ResponseEntity.created(location).body(CustomerResponse.from(customer));
    }

    @PutMapping("/{id}")
    CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return CustomerResponse.from(service.update(id, request.name(), request.phone(), request.notes()));
    }
}
```

- [ ] **Step 5: Ejecutarlo y ver que pasa**

Run: `./mvnw -B -ntp test -Dtest=CustomerApiTest`
Esperado: PASS (9 tests).

Si `$.phone` sale como `null` en lugar de no aparecer, cambiar en el test `doesNotExist()` por `value(nullValue())` y anotarlo como ruling. Es la serialización por defecto de Jackson y no un error de la app.

- [ ] **Step 6: `clean verify`, commit y push**

Run: `./mvnw -B -ntp clean verify` → `BUILD SUCCESS`.

```bash
git add -A src/main src/test
git commit -m "feat: add customer REST endpoints"
git push
```

---

### Task 3: Cierre del Paso 2

- [ ] **Step 1:** Actualizar el Paso 2 de `docs/ROADMAP.md` con los commits reales y las reglas: teléfono normalizado y único, y búsqueda por nombre o teléfono. Commit `docs: update roadmap for customers step`.
- [ ] **Step 2:** Prueba manual con Docker:
  - `docker compose up -d --build` (con `APP_PORT=8081`).
  - Crear un cliente, buscarlo y comprobar el 409 por teléfono.
  - Comprobar que Flyway aplica `V3`.
  - Borrar los datos de prueba y `docker compose down`.
- [ ] **Step 3:** Revisión final de toda la rama con un revisor independiente.
- [ ] **Step 4:** PR `feature/customers` → `main` con `gh pr create` y asociarlo a la sesión.
