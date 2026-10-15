# Paso 1 — Catálogo · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Módulo `catalog` con productos y personalizaciones (precio de venta y coste editables desde la API, activar/desactivar sin borrar) y el listado de tallas.

**Architecture:** El paquete `product` se sustituye por el módulo Modulith `catalog`. Su API pública (raíz del paquete) solo expone el enum `ClothingSize`. Todo lo demás vive en `catalog.internal`:
- `CatalogItem`: superclase JPA con los campos comunes.
- Un repositorio, un servicio y un controlador genéricos que heredan `Product` y `Customization`, para no duplicar la lógica.

Cada entidad tiene su tabla y su migración Flyway, y Hibernate valida el esquema al arrancar.

**Tech Stack:** Java 21, Spring Boot 4.1.0 (Web MVC, Data JPA, Validation, Flyway), Spring Modulith 2.1.1, springdoc 3.1.1, H2 en modo PostgreSQL para tests y PostgreSQL 17 en dev.

**Spec:** [docs/ROADMAP.md](../../ROADMAP.md), secciones 1, 2 (catálogo) y "Paso 1".

## Global Constraints

- Dinero siempre `BigDecimal`, escala 2 (`NUMERIC(10, 2)`), `RoundingMode.HALF_UP`. Nunca `double` ni `float`.
- Fechas con `LocalDateTime`.
- Código, tablas y columnas en inglés (`snake_case` en BD). Mensajes de la API en español.
- Sin Lombok. Sin borrado físico: se desactiva.
- Migraciones Flyway versionadas (`V1__...`, `V2__...`). Hibernate en `ddl-auto: validate` en `dev` y `test`.
- Errores con ProblemDetail a través de `shared` (`ResourceNotFoundException` → 404, `BusinessRuleException` → 409).
- `ModularityTests` en verde: ningún otro módulo usa `catalog.internal`.
- Rama `feature/catalog` desde `origin/main`. Commit + push al terminar cada tarea, sin `Co-Authored-By`. El `.gitignore` local del usuario no se commitea.
- Comandos Maven desde Bash: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"` y `./mvnw ...`.

## Review Focus

1. **Nombre duplicado con otras mayúsculas o espacios** (`"  camiseta fan "` cuando existe `"Camiseta Fan"`) → 409, no se crea un duplicado. Test en la Tarea 2.
2. **Precio con 3 decimales o demasiado grande** (`15.999`, `123456789.00`) → 400 con error en el campo, sin redondeo silencioso. Test en la Tarea 2.
3. **Editar un producto manteniendo su propio nombre** → 200, no es un "duplicado de sí mismo". Test en la Tarea 2.
4. **Producto desactivado** → desaparece del listado por defecto, pero sigue accesible por id y con `includeInactive=true`. Se puede reactivar. Test en la Tarea 2.
5. **Id no numérico** (`/api/catalog/products/abc`) → 400 en español, no 500. Test en la Tarea 2.

---

## File Structure

```text
src/main/java/com/pedropalazon/sales_manager_backend/
├── product/                                        (borrar entero)
├── shared/web/GlobalExceptionHandler.java          (modificar: DataIntegrityViolationException → 409)
└── catalog/
    ├── ClothingSize.java                           (API pública: tallas)
    └── internal/
        ├── CatalogItem.java                        (@MappedSuperclass)
        ├── CatalogItemRepository.java              (@NoRepositoryBean genérico)
        ├── CatalogItemService.java                 (servicio genérico abstracto)
        ├── Product.java · ProductRepository.java · ProductService.java
        ├── Customization.java · CustomizationRepository.java · CustomizationService.java
        └── web/
            ├── CatalogItemRequest.java · CatalogItemResponse.java
            ├── CatalogItemController.java          (controlador genérico abstracto)
            ├── ProductController.java · CustomizationController.java
            └── SizeController.java · SizeResponse.java
src/main/resources/
├── db/migration/V1__create_products.sql
├── db/migration/V2__create_customizations.sql
├── application-dev.yaml                            (ddl-auto: validate)
└── application-test.yaml                           (ddl-auto: validate)
src/test/java/com/pedropalazon/sales_manager_backend/
├── catalog/internal/ProductRepositoryTest.java
├── catalog/internal/web/ProductApiTest.java
├── catalog/internal/web/CustomizationApiTest.java
├── catalog/internal/web/SizeApiTest.java
└── shared/web/GlobalExceptionHandlerTest.java      (modificar)
docs/ROADMAP.md                                     (modificar: ClothingSize y endpoints)
```

---

### Task 1: `Product` como artículo de catálogo con precios, migración y repositorio

**Files:**
- Delete: `src/main/java/com/pedropalazon/sales_manager_backend/product/entity/Product.java`, `.../product/entity/ProductType.java`
- Create: `catalog/internal/CatalogItem.java`, `catalog/internal/Product.java`, `catalog/internal/CatalogItemRepository.java`, `catalog/internal/ProductRepository.java`
- Create: `src/main/resources/db/migration/V1__create_products.sql`
- Modify: `application-dev.yaml`, `application-test.yaml`
- Test: `src/test/java/com/pedropalazon/sales_manager_backend/catalog/internal/ProductRepositoryTest.java`

**Interfaces:**
- Produces:
  - `CatalogItem`, con `getId()`, `getName()`, `getSalePrice()`, `getPurchaseCost()`, `isActive()`, `getCreatedAt()`, `getUpdatedAt()`, `update(String name, BigDecimal salePrice, BigDecimal purchaseCost)`, `activate()` y `deactivate()`. El nombre se guarda con `trim()` y los importes con escala 2.
  - `Product(String name, BigDecimal salePrice, BigDecimal purchaseCost)`.
  - `CatalogItemRepository<T extends CatalogItem>`, con `findAllByOrderByNameAsc()`, `findAllByActiveTrueOrderByNameAsc()`, `existsByNameIgnoreCase(String)` y `existsByNameIgnoreCaseAndIdNot(String, Long)`.
  - `ProductRepository extends CatalogItemRepository<Product>`.

- [ ] **Step 1: Crear la rama**

```bash
git fetch origin
git switch -c feature/catalog origin/main
```

- [ ] **Step 2: Escribir el test del repositorio**

```java
package com.pedropalazon.sales_manager_backend.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

// Usa el H2 en modo PostgreSQL del perfil test, con las migraciones Flyway reales
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTest {

    @Autowired
    private ProductRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void givenNewProduct_whenSaved_thenNamePricesAndTimestampsArePersisted() {
        Product saved = repository.save(new Product("  Camiseta Fan ", new BigDecimal("15"), new BigDecimal("6.5")));
        entityManager.flush();
        entityManager.clear();

        Product found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("Camiseta Fan");
        assertThat(found.getSalePrice()).isEqualTo(new BigDecimal("15.00"));
        assertThat(found.getPurchaseCost()).isEqualTo(new BigDecimal("6.50"));
        assertThat(found.isActive()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void givenActiveAndInactiveProducts_whenFindingActive_thenReturnsOnlyActiveSortedByName() {
        repository.save(new Product("Chándal", new BigDecimal("50.00"), new BigDecimal("34.00")));
        Product inactive = new Product("Cortavientos", new BigDecimal("30.00"), new BigDecimal("18.00"));
        inactive.deactivate();
        repository.save(inactive);
        repository.save(new Product("Camiseta Fan", new BigDecimal("15.00"), new BigDecimal("6.50")));

        assertThat(repository.findAllByActiveTrueOrderByNameAsc())
                .extracting(Product::getName)
                .containsExactly("Camiseta Fan", "Chándal");
        assertThat(repository.findAllByOrderByNameAsc())
                .extracting(Product::getName)
                .containsExactly("Camiseta Fan", "Chándal", "Cortavientos");
    }

    @Test
    void givenExistingProduct_whenCheckingNameIgnoringCase_thenOnlyOtherProductsCount() {
        Product product = repository.save(new Product("Camiseta Fan", new BigDecimal("15.00"), new BigDecimal("6.50")));

        assertThat(repository.existsByNameIgnoreCase("CAMISETA FAN")).isTrue();
        assertThat(repository.existsByNameIgnoreCaseAndIdNot("camiseta fan", product.getId())).isFalse();
        assertThat(repository.existsByNameIgnoreCase("Camiseta Player")).isFalse();
    }
}
```

- [ ] **Step 3: Ejecutarlo y ver que falla**

Run: `./mvnw -B -ntp test -Dtest=ProductRepositoryTest`
Esperado: FAIL de compilación, `cannot find symbol: class ProductRepository` o `Product`.

- [ ] **Step 4: Borrar el paquete `product` antiguo**

```bash
git rm -q -r src/main/java/com/pedropalazon/sales_manager_backend/product
```

- [ ] **Step 5: Crear `CatalogItem`**

```java
package com.pedropalazon.sales_manager_backend.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

// Campos comunes a todo lo que se vende con precio y coste: productos y personalizaciones.
@MappedSuperclass
public abstract class CatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal salePrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal purchaseCost;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected CatalogItem() {
    }

    protected CatalogItem(String name, BigDecimal salePrice, BigDecimal purchaseCost) {
        update(name, salePrice, purchaseCost);
    }

    public void update(String name, BigDecimal salePrice, BigDecimal purchaseCost) {
        this.name = name.trim();
        this.salePrice = salePrice.setScale(2, RoundingMode.HALF_UP);
        this.purchaseCost = purchaseCost.setScale(2, RoundingMode.HALF_UP);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public BigDecimal getPurchaseCost() {
        return purchaseCost;
    }

    public boolean isActive() {
        return active;
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

- [ ] **Step 6: Crear `Product`**

```java
package com.pedropalazon.sales_manager_backend.catalog.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product extends CatalogItem {

    protected Product() {
    }

    public Product(String name, BigDecimal salePrice, BigDecimal purchaseCost) {
        super(name, salePrice, purchaseCost);
    }
}
```

- [ ] **Step 7: Crear los repositorios**

`CatalogItemRepository.java`:

```java
package com.pedropalazon.sales_manager_backend.catalog.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface CatalogItemRepository<T extends CatalogItem> extends JpaRepository<T, Long> {

    List<T> findAllByOrderByNameAsc();

    List<T> findAllByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
```

`ProductRepository.java`:

```java
package com.pedropalazon.sales_manager_backend.catalog.internal;

public interface ProductRepository extends CatalogItemRepository<Product> {
}
```

- [ ] **Step 8: Crear la migración `V1__create_products.sql`**

```sql
CREATE TABLE products (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name          VARCHAR(120)   NOT NULL,
    sale_price    NUMERIC(10, 2) NOT NULL,
    purchase_cost NUMERIC(10, 2) NOT NULL,
    active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP      NOT NULL,
    updated_at    TIMESTAMP      NOT NULL,
    CONSTRAINT uk_products_name UNIQUE (name),
    CONSTRAINT ck_products_sale_price CHECK (sale_price >= 0),
    CONSTRAINT ck_products_purchase_cost CHECK (purchase_cost >= 0)
);
```

- [ ] **Step 9: Hibernate valida el esquema en `dev` y `test`**

En `application-dev.yaml` y en `application-test.yaml`, cambiar `ddl-auto: none` por:

```yaml
      ddl-auto: validate
```

Así, si una entidad no coincide con las migraciones, la app no arranca y los tests fallan.

- [ ] **Step 10: Ejecutar el test y ver que pasa**

Run: `./mvnw -B -ntp test -Dtest=ProductRepositoryTest`
Esperado: PASS (3 tests). En el log, Flyway aplica `V1__create_products`.

Si `@DataJpaTest` no ejecuta Flyway (falla con "missing table [products]"), añadir `@ImportAutoConfiguration(FlywayAutoConfiguration.class)` al test y anotarlo como ruling.

- [ ] **Step 11: Suite completa**

Run: `./mvnw -B -ntp test`
Esperado: `BUILD SUCCESS`. `ModularityTests` ve los módulos `catalog` y `shared`.

- [ ] **Step 12: Commit y push**

```bash
git add -A src/main/java/com/pedropalazon/sales_manager_backend src/main/resources src/test/java/com/pedropalazon/sales_manager_backend/catalog
git commit -m "refactor: reshape product as catalog item with prices"
git push -u origin feature/catalog
```

---

### Task 2: Servicio y endpoints REST de productos

**Files:**
- Create: `catalog/internal/CatalogItemService.java`, `catalog/internal/ProductService.java`
- Create: `catalog/internal/web/CatalogItemRequest.java`, `CatalogItemResponse.java`, `CatalogItemController.java`, `ProductController.java`
- Modify: `shared/web/GlobalExceptionHandler.java` (`DataIntegrityViolationException` → 409)
- Test: `catalog/internal/web/ProductApiTest.java`, `shared/web/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `CatalogItem`, `Product`, `CatalogItemRepository`, `ProductRepository` (Tarea 1). `ResourceNotFoundException` y `BusinessRuleException` (`shared`).
- Produces:
  - `CatalogItemService<T>` con `list(boolean includeInactive)`, `get(Long id)`, `create(String, BigDecimal, BigDecimal)`, `update(Long, String, BigDecimal, BigDecimal)`, `activate(Long)` y `deactivate(Long)`. Las subclases implementan `newItem(...)`.
  - `CatalogItemRequest(name, salePrice, purchaseCost)` y `CatalogItemResponse(id, name, salePrice, purchaseCost, active)`.
  - `CatalogItemController<T>`, que las subclases montan sobre su ruta.
  - Endpoints bajo `/api/catalog/products`:
    - `GET` (con `?includeInactive=true` opcional) y `GET /{id}`.
    - `POST` → 201 con cabecera `Location`.
    - `PUT /{id}`.
    - `POST /{id}/activate` y `POST /{id}/deactivate`.

- [ ] **Step 1: Escribir el test de API**

```java
package com.pedropalazon.sales_manager_backend.catalog.internal.web;

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

// @Transactional: cada test deshace sus cambios en BD al terminar
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductApiTest {

    private static final String PRODUCTS = "/api/catalog/products";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenValidProduct_whenCreated_thenReturnsCreatedWithLocationAndBody() throws Exception {
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Camiseta Fan", "15.00", "6.50")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(PRODUCTS + "/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Camiseta Fan"))
                .andExpect(jsonPath("$.salePrice").value(15.0))
                .andExpect(jsonPath("$.purchaseCost").value(6.5))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void givenExistingName_whenCreatingWithOtherCaseAndSpaces_thenReturnsConflict() throws Exception {
        create("Camiseta Fan", "15.00", "6.50");

        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("  camiseta fan ", "16.00", "7.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe un producto llamado 'camiseta fan'"));
    }

    @Test
    void givenPriceWithThreeDecimals_whenCreated_thenReturnsBadRequestWithFieldError() throws Exception {
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Camiseta Fan", "15.999", "6.50")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'salePrice')].message")
                        .value("El precio de venta admite como máximo 8 enteros y 2 decimales"));
    }

    @Test
    void givenTooLargePrice_whenCreated_thenReturnsBadRequest() throws Exception {
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Camiseta Fan", "123456789.00", "6.50")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("salePrice"));
    }

    @Test
    void givenEmptyBody_whenCreated_thenReturnsOneErrorPerRequiredField() throws Exception {
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.errors[?(@.field == 'purchaseCost')].message").value("El coste es obligatorio"));
    }

    @Test
    void givenUnknownId_whenRequested_thenReturnsNotFound() throws Exception {
        mockMvc.perform(get(PRODUCTS + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el producto con id 999999"));
    }

    @Test
    void givenNonNumericId_whenRequested_thenReturnsBadRequestInSpanish() throws Exception {
        mockMvc.perform(get(PRODUCTS + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"));
    }

    @Test
    void givenExistingProduct_whenUpdatedKeepingItsName_thenReturnsNewPrices() throws Exception {
        long id = create("Camiseta Player", "17.00", "8.00");

        mockMvc.perform(put(PRODUCTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Camiseta Player", "18.00", "8.75")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Camiseta Player"))
                .andExpect(jsonPath("$.salePrice").value(18.0))
                .andExpect(jsonPath("$.purchaseCost").value(8.75));
    }

    @Test
    void givenTwoProducts_whenRenamingOneToTheOthersName_thenReturnsConflict() throws Exception {
        create("Camiseta Fan", "15.00", "6.50");
        long playerId = create("Camiseta Player", "17.00", "8.00");

        mockMvc.perform(put(PRODUCTS + "/" + playerId).contentType(MediaType.APPLICATION_JSON)
                        .content(json("CAMISETA FAN", "17.00", "8.00")))
                .andExpect(status().isConflict());
    }

    @Test
    void givenDeactivatedProduct_whenListing_thenOnlyAppearsWhenIncludingInactive() throws Exception {
        create("Camiseta Fan", "15.00", "6.50");
        long tracksuitId = create("Chándal", "50.00", "34.00");

        mockMvc.perform(post(PRODUCTS + "/" + tracksuitId + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get(PRODUCTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(contains("Camiseta Fan")));
        mockMvc.perform(get(PRODUCTS).param("includeInactive", "true"))
                .andExpect(jsonPath("$[*].name").value(contains("Camiseta Fan", "Chándal")));
        mockMvc.perform(get(PRODUCTS + "/" + tracksuitId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void givenDeactivatedProduct_whenActivated_thenIsActiveAgain() throws Exception {
        long id = create("Chándal", "50.00", "34.00");
        mockMvc.perform(post(PRODUCTS + "/" + id + "/deactivate")).andExpect(status().isOk());

        mockMvc.perform(post(PRODUCTS + "/" + id + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    private long create(String name, String salePrice, String purchaseCost) throws Exception {
        String body = mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(json(name, salePrice, purchaseCost)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String json(String name, String salePrice, String purchaseCost) {
        return """
                {"name":"%s","salePrice":%s,"purchaseCost":%s}""".formatted(name, salePrice, purchaseCost);
    }
}
```

- [ ] **Step 2: Añadir al test de errores el caso de conflicto de datos**

En `GlobalExceptionHandlerTest`, añadir el test:

```java
    @Test
    void givenDataIntegrityViolation_whenRequested_thenReturnsConflictWithoutDatabaseDetails() throws Exception {
        mockMvc.perform(get("/test-errors/data-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Operación no permitida"))
                .andExpect(jsonPath("$.detail").value("Los datos entran en conflicto con otros ya guardados."))
                .andExpect(content().string(not(containsString("uk_products_name"))));
    }
```

En `FailingController`, añadir el endpoint:

```java
        @GetMapping("/data-conflict")
        void dataConflict() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint uk_products_name");
        }
```

Y el import: `import org.springframework.dao.DataIntegrityViolationException;`.

- [ ] **Step 3: Ejecutar los tests y ver que fallan**

Run: `./mvnw -B -ntp test "-Dtest=ProductApiTest,GlobalExceptionHandlerTest"`
Esperado:
- `ProductApiTest`: todos los tests fallan con 404, porque no hay controlador.
- `givenDataIntegrityViolation...`: falla con 500 en lugar de 409.

- [ ] **Step 4: Manejar `DataIntegrityViolationException` en `GlobalExceptionHandler`**

Añadir el import `org.springframework.dao.DataIntegrityViolationException` y el método, junto a `handleBusinessRule`:

```java
    // Red de seguridad: si una restricción de BD (único, check...) salta, es un conflicto, no un error del servidor
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation", exception);
        return problem(HttpStatus.CONFLICT, "Operación no permitida",
                "Los datos entran en conflicto con otros ya guardados.");
    }
```

- [ ] **Step 5: Crear `CatalogItemService`**

```java
package com.pedropalazon.sales_manager_backend.catalog.internal;

import com.pedropalazon.sales_manager_backend.shared.BusinessRuleException;
import com.pedropalazon.sales_manager_backend.shared.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

// Lógica común de productos y personalizaciones: listar, crear, editar y activar/desactivar.
public abstract class CatalogItemService<T extends CatalogItem> {

    private final CatalogItemRepository<T> repository;
    private final String notFoundMessage;
    private final String duplicateNameMessage;

    protected CatalogItemService(CatalogItemRepository<T> repository, String notFoundMessage,
            String duplicateNameMessage) {
        this.repository = repository;
        this.notFoundMessage = notFoundMessage;
        this.duplicateNameMessage = duplicateNameMessage;
    }

    protected abstract T newItem(String name, BigDecimal salePrice, BigDecimal purchaseCost);

    @Transactional(readOnly = true)
    public List<T> list(boolean includeInactive) {
        return includeInactive
                ? repository.findAllByOrderByNameAsc()
                : repository.findAllByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public T get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(notFoundMessage.formatted(id)));
    }

    @Transactional
    public T create(String name, BigDecimal salePrice, BigDecimal purchaseCost) {
        ensureNameIsFree(name, null);
        return repository.save(newItem(name, salePrice, purchaseCost));
    }

    @Transactional
    public T update(Long id, String name, BigDecimal salePrice, BigDecimal purchaseCost) {
        T item = get(id);
        ensureNameIsFree(name, id);
        item.update(name, salePrice, purchaseCost);
        return item;
    }

    @Transactional
    public T activate(Long id) {
        T item = get(id);
        item.activate();
        return item;
    }

    @Transactional
    public T deactivate(Long id) {
        T item = get(id);
        item.deactivate();
        return item;
    }

    private void ensureNameIsFree(String name, Long currentId) {
        String trimmedName = name.trim();
        boolean taken = currentId == null
                ? repository.existsByNameIgnoreCase(trimmedName)
                : repository.existsByNameIgnoreCaseAndIdNot(trimmedName, currentId);
        if (taken) {
            throw new BusinessRuleException(duplicateNameMessage.formatted(trimmedName));
        }
    }
}
```

- [ ] **Step 6: Crear `ProductService`**

```java
package com.pedropalazon.sales_manager_backend.catalog.internal;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class ProductService extends CatalogItemService<Product> {

    public ProductService(ProductRepository repository) {
        super(repository, "No existe el producto con id %d", "Ya existe un producto llamado '%s'");
    }

    @Override
    protected Product newItem(String name, BigDecimal salePrice, BigDecimal purchaseCost) {
        return new Product(name, salePrice, purchaseCost);
    }
}
```

- [ ] **Step 7: Crear los DTOs web**

`CatalogItemRequest.java`:

```java
package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CatalogItemRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String name,

        @NotNull(message = "El precio de venta es obligatorio")
        @PositiveOrZero(message = "El precio de venta no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El precio de venta admite como máximo 8 enteros y 2 decimales")
        BigDecimal salePrice,

        @NotNull(message = "El coste es obligatorio")
        @PositiveOrZero(message = "El coste no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El coste admite como máximo 8 enteros y 2 decimales")
        BigDecimal purchaseCost) {
}
```

`CatalogItemResponse.java`:

```java
package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import com.pedropalazon.sales_manager_backend.catalog.internal.CatalogItem;
import java.math.BigDecimal;

public record CatalogItemResponse(Long id, String name, BigDecimal salePrice, BigDecimal purchaseCost,
        boolean active) {

    static CatalogItemResponse from(CatalogItem item) {
        return new CatalogItemResponse(item.getId(), item.getName(), item.getSalePrice(), item.getPurchaseCost(),
                item.isActive());
    }
}
```

- [ ] **Step 8: Crear `CatalogItemController` y `ProductController`**

`CatalogItemController.java`:

```java
package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import com.pedropalazon.sales_manager_backend.catalog.internal.CatalogItem;
import com.pedropalazon.sales_manager_backend.catalog.internal.CatalogItemService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Endpoints comunes; cada subclase los monta en su propia ruta con @RequestMapping.
abstract class CatalogItemController<T extends CatalogItem> {

    private final CatalogItemService<T> service;

    protected CatalogItemController(CatalogItemService<T> service) {
        this.service = service;
    }

    @GetMapping
    List<CatalogItemResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(includeInactive).stream().map(CatalogItemResponse::from).toList();
    }

    @GetMapping("/{id}")
    CatalogItemResponse get(@PathVariable Long id) {
        return CatalogItemResponse.from(service.get(id));
    }

    @PostMapping
    ResponseEntity<CatalogItemResponse> create(@Valid @RequestBody CatalogItemRequest request) {
        T item = service.create(request.name(), request.salePrice(), request.purchaseCost());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(item.getId())
                .toUri();
        return ResponseEntity.created(location).body(CatalogItemResponse.from(item));
    }

    @PutMapping("/{id}")
    CatalogItemResponse update(@PathVariable Long id, @Valid @RequestBody CatalogItemRequest request) {
        return CatalogItemResponse.from(
                service.update(id, request.name(), request.salePrice(), request.purchaseCost()));
    }

    @PostMapping("/{id}/activate")
    CatalogItemResponse activate(@PathVariable Long id) {
        return CatalogItemResponse.from(service.activate(id));
    }

    @PostMapping("/{id}/deactivate")
    CatalogItemResponse deactivate(@PathVariable Long id) {
        return CatalogItemResponse.from(service.deactivate(id));
    }
}
```

`ProductController.java`:

```java
package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import com.pedropalazon.sales_manager_backend.catalog.internal.Product;
import com.pedropalazon.sales_manager_backend.catalog.internal.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/products")
@Tag(name = "Catálogo - Productos", description = "Productos que se venden, con su precio de venta y su coste")
class ProductController extends CatalogItemController<Product> {

    ProductController(ProductService service) {
        super(service);
    }
}
```

- [ ] **Step 9: Ejecutar los tests y ver que pasan**

Run: `./mvnw -B -ntp test "-Dtest=ProductApiTest,GlobalExceptionHandlerTest"`
Esperado: PASS (11 + 11 tests).

- [ ] **Step 10: Suite completa**

Run: `./mvnw -B -ntp test`
Esperado: `BUILD SUCCESS`.

- [ ] **Step 11: Commit y push**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: add product REST endpoints"
git push
```

---

### Task 3: Catálogo de personalizaciones

**Files:**
- Create: `catalog/internal/Customization.java`, `CustomizationRepository.java`, `CustomizationService.java`
- Create: `catalog/internal/web/CustomizationController.java`
- Create: `src/main/resources/db/migration/V2__create_customizations.sql`
- Test: `catalog/internal/web/CustomizationApiTest.java`

**Interfaces:**
- Consumes: `CatalogItem`, `CatalogItemRepository`, `CatalogItemService`, `CatalogItemController`, `CatalogItemRequest` y `CatalogItemResponse` (Tareas 1 y 2).
- Produces: los mismos endpoints que productos, bajo `/api/catalog/customizations`.

- [ ] **Step 1: Escribir el test de API**

```java
package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class CustomizationApiTest {

    private static final String CUSTOMIZATIONS = "/api/catalog/customizations";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenValidCustomization_whenCreated_thenReturnsCreatedWithPrices() throws Exception {
        mockMvc.perform(post(CUSTOMIZATIONS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Nombre + número", "2.00", "1.20")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nombre + número"))
                .andExpect(jsonPath("$.salePrice").value(2.0))
                .andExpect(jsonPath("$.purchaseCost").value(1.2))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void givenExistingName_whenCreatingDuplicate_thenReturnsConflict() throws Exception {
        create("Parche", "1.00", "0.50");

        mockMvc.perform(post(CUSTOMIZATIONS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("parche", "1.50", "0.60")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una personalización llamada 'parche'"));
    }

    @Test
    void givenUnknownId_whenRequested_thenReturnsNotFound() throws Exception {
        mockMvc.perform(get(CUSTOMIZATIONS + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe la personalización con id 999999"));
    }

    @Test
    void givenDeactivatedCustomization_whenListing_thenIsHiddenByDefault() throws Exception {
        create("Nombre + número", "2.00", "1.20");
        long patchId = create("Parche", "1.00", "0.50");
        mockMvc.perform(post(CUSTOMIZATIONS + "/" + patchId + "/deactivate")).andExpect(status().isOk());

        mockMvc.perform(get(CUSTOMIZATIONS))
                .andExpect(jsonPath("$[*].name").value(contains("Nombre + número")));
        mockMvc.perform(get(CUSTOMIZATIONS).param("includeInactive", "true"))
                .andExpect(jsonPath("$[*].name").value(contains("Nombre + número", "Parche")));
    }

    @Test
    void givenProductWithSameName_whenCreatingCustomization_thenIsAllowed() throws Exception {
        mockMvc.perform(post("/api/catalog/products").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Parche", "1.00", "0.50")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(CUSTOMIZATIONS).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Parche", "1.00", "0.50")))
                .andExpect(status().isCreated());
    }

    private long create(String name, String salePrice, String purchaseCost) throws Exception {
        String body = mockMvc.perform(post(CUSTOMIZATIONS).contentType(MediaType.APPLICATION_JSON)
                        .content(json(name, salePrice, purchaseCost)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String json(String name, String salePrice, String purchaseCost) {
        return """
                {"name":"%s","salePrice":%s,"purchaseCost":%s}""".formatted(name, salePrice, purchaseCost);
    }
}
```

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `./mvnw -B -ntp test -Dtest=CustomizationApiTest`
Esperado: FAIL. Los tests de personalizaciones reciben 404 porque no hay ruta. `givenProductWithSameName...` falla en el segundo POST.

- [ ] **Step 3: Migración `V2__create_customizations.sql`**

```sql
CREATE TABLE customizations (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name          VARCHAR(120)   NOT NULL,
    sale_price    NUMERIC(10, 2) NOT NULL,
    purchase_cost NUMERIC(10, 2) NOT NULL,
    active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP      NOT NULL,
    updated_at    TIMESTAMP      NOT NULL,
    CONSTRAINT uk_customizations_name UNIQUE (name),
    CONSTRAINT ck_customizations_sale_price CHECK (sale_price >= 0),
    CONSTRAINT ck_customizations_purchase_cost CHECK (purchase_cost >= 0)
);
```

- [ ] **Step 4: Entidad, repositorio y servicio**

`Customization.java`:

```java
package com.pedropalazon.sales_manager_backend.catalog.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "customizations")
public class Customization extends CatalogItem {

    protected Customization() {
    }

    public Customization(String name, BigDecimal salePrice, BigDecimal purchaseCost) {
        super(name, salePrice, purchaseCost);
    }
}
```

`CustomizationRepository.java`:

```java
package com.pedropalazon.sales_manager_backend.catalog.internal;

public interface CustomizationRepository extends CatalogItemRepository<Customization> {
}
```

`CustomizationService.java`:

```java
package com.pedropalazon.sales_manager_backend.catalog.internal;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class CustomizationService extends CatalogItemService<Customization> {

    public CustomizationService(CustomizationRepository repository) {
        super(repository, "No existe la personalización con id %d", "Ya existe una personalización llamada '%s'");
    }

    @Override
    protected Customization newItem(String name, BigDecimal salePrice, BigDecimal purchaseCost) {
        return new Customization(name, salePrice, purchaseCost);
    }
}
```

- [ ] **Step 5: Controlador**

```java
package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import com.pedropalazon.sales_manager_backend.catalog.internal.Customization;
import com.pedropalazon.sales_manager_backend.catalog.internal.CustomizationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/customizations")
@Tag(name = "Catálogo - Personalizaciones",
        description = "Personalizaciones (nombre y número, parches...) con su precio de venta y su coste")
class CustomizationController extends CatalogItemController<Customization> {

    CustomizationController(CustomizationService service) {
        super(service);
    }
}
```

- [ ] **Step 6: Ejecutarlo y ver que pasa**

Run: `./mvnw -B -ntp test -Dtest=CustomizationApiTest`
Esperado: PASS (5 tests).

- [ ] **Step 7: Suite completa, commit y push**

Run: `./mvnw -B -ntp test` → `BUILD SUCCESS`.

```bash
git add -A src/main src/test
git commit -m "feat: add customization catalog"
git push
```

---

### Task 4: Tallas

**Files:**
- Create: `catalog/ClothingSize.java` (API pública), `catalog/internal/web/SizeController.java`, `catalog/internal/web/SizeResponse.java`
- Modify: `docs/ROADMAP.md` (`Size` → `ClothingSize`, endpoints del catálogo)
- Test: `catalog/internal/web/SizeApiTest.java`

**Interfaces:**
- Produces:
  - `catalog.ClothingSize`, con 12 valores (`S`, `M`, `L`, `XL`, `XXL`, `KIDS_16` … `KIDS_28`) y `getLabel()`. Las líneas de pedido lo usarán en el Paso 3.
  - `GET /api/catalog/sizes`, que devuelve `[{ "code": "S", "label": "S" }, …, { "code": "KIDS_16", "label": "16 (niño)" }, …]`.

- [ ] **Step 1: Escribir el test**

```java
package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SizeController.class)
class SizeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenSizeCatalog_whenListed_thenReturnsAdultAndKidsSizesInOrder() throws Exception {
        mockMvc.perform(get("/api/catalog/sizes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].code").value("S"))
                .andExpect(jsonPath("$[0].label").value("S"))
                .andExpect(jsonPath("$[4].code").value("XXL"))
                .andExpect(jsonPath("$[5].code").value("KIDS_16"))
                .andExpect(jsonPath("$[5].label").value("16 (niño)"))
                .andExpect(jsonPath("$[11].code").value("KIDS_28"));
    }
}
```

- [ ] **Step 2: Ejecutarlo y ver que falla**

Run: `./mvnw -B -ntp test -Dtest=SizeApiTest`
Esperado: FAIL de compilación, `cannot find symbol: class SizeController`.

- [ ] **Step 3: Crear `ClothingSize`**

```java
package com.pedropalazon.sales_manager_backend.catalog;

// Tallas comunes a todos los productos (camisetas, chándales...). Se llama ClothingSize y no Size
// para no chocar con la anotación de validación jakarta.validation.constraints.Size.
public enum ClothingSize {

    S("S"),
    M("M"),
    L("L"),
    XL("XL"),
    XXL("XXL"),
    KIDS_16("16 (niño)"),
    KIDS_18("18 (niño)"),
    KIDS_20("20 (niño)"),
    KIDS_22("22 (niño)"),
    KIDS_24("24 (niño)"),
    KIDS_26("26 (niño)"),
    KIDS_28("28 (niño)");

    private final String label;

    ClothingSize(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
```

- [ ] **Step 4: Crear `SizeResponse` y `SizeController`**

`SizeResponse.java`:

```java
package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;

public record SizeResponse(String code, String label) {

    static SizeResponse from(ClothingSize size) {
        return new SizeResponse(size.name(), size.getLabel());
    }
}
```

`SizeController.java`:

```java
package com.pedropalazon.sales_manager_backend.catalog.internal.web;

import com.pedropalazon.sales_manager_backend.catalog.ClothingSize;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/sizes")
@Tag(name = "Catálogo - Tallas", description = "Tallas disponibles para cualquier producto")
class SizeController {

    @GetMapping
    List<SizeResponse> list() {
        return Arrays.stream(ClothingSize.values()).map(SizeResponse::from).toList();
    }
}
```

- [ ] **Step 5: Ejecutarlo y ver que pasa**

Run: `./mvnw -B -ntp test -Dtest=SizeApiTest`
Esperado: PASS.

- [ ] **Step 6: Actualizar `docs/ROADMAP.md`**

En la sección 2, cambiar la línea:

```text
  Size (enum)       S, M, L, XL, XXL, KIDS_16 … KIDS_28   (común a todos los productos)
```

por:

```text
  ClothingSize (enum) S, M, L, XL, XXL, KIDS_16 … KIDS_28   (común a todos los productos; no se llama
                    Size para no chocar con la anotación de validación @Size)
```

En el Paso 1, sustituir la lista de commits por los commits reales de este plan:

```markdown
1. `refactor: reshape product as catalog item with prices` — `Product` con `name`, `salePrice`, `purchaseCost` y `active`, migración `V1`, repositorio. Se eliminan `teamName`, `ProductType` e `imagePath`.
2. `feat: add product REST endpoints` — `/api/catalog/products`: listar (activos o todos), ver, crear, editar, activar y desactivar. Nombre único sin distinguir mayúsculas. Precios con 2 decimales como máximo.
3. `feat: add customization catalog` — lo mismo en `/api/catalog/customizations`.
4. `feat: add size catalog` — enum `ClothingSize` y `GET /api/catalog/sizes`.
```

- [ ] **Step 7: `verify`, commit y push**

Run: `./mvnw -B -ntp clean verify` → `BUILD SUCCESS`.

```bash
git add -A src/main src/test docs/ROADMAP.md
git commit -m "feat: add size catalog"
git push
```

---

### Task 5: Cierre del Paso 1

- [ ] **Step 1:** Prueba manual contra PostgreSQL. `docker compose up -d` y arrancar la app. Después:
  - Crear "Camiseta Fan" (15,00 / 6,50) y "Nombre + número" (2,00 / X) desde Swagger o `curl`.
  - Listar los productos y las tallas.
  - Comprobar en el log que Flyway aplica `V1` y `V2` y que Hibernate valida el esquema.
  - Parar la app y `docker compose down`.
- [ ] **Step 2:** Revisión final de toda la rama, con un revisor independiente.
- [ ] **Step 3:** Abrir el PR `feature/catalog` → `main` con `gh pr create` y asociarlo a la sesión.
