# Paso 3 — Pedidos de cliente · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Módulo `sales` para registrar pedidos de cliente con varias camisetas. Cada línea lleva producto, equipación, talla y personalizaciones. Los precios se copian del catálogo y se pueden ajustar a mano, y el pedido calcula sus totales de venta, coste y beneficio.

**Architecture:**
- Nuevo módulo Modulith `sales`, que depende de `catalog` y `customers` **solo a través de su API pública**:
  - `catalog.CatalogApi` y `catalog.CatalogItemSnapshot`.
  - `customers.CustomerApi` y `customers.CustomerSummary`.
  - Sus implementaciones viven en el `internal` de cada módulo.
- Entre módulos el pedido guarda **ids**, no relaciones JPA. En la base de datos sí hay claves foráneas: es la misma BD y nunca se borra nada.
- Dentro de `sales`:
  - `SalesOrder` 1→N `SalesOrderLine` 1→N `LineCustomization`, con cascade y orphanRemoval.
  - Los totales se calculan en las entidades y no se guardan.
  - El servicio devuelve *views* (records) construidas dentro de la transacción, porque `open-in-view` está desactivado.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring Modulith 2.1.1, Data JPA + Flyway (`V4`), H2 en modo PostgreSQL para tests y PostgreSQL 17 en dev y Docker.

**Spec:** [docs/ROADMAP.md](../../ROADMAP.md), sección 2 (`sales`) y "Paso 3".

## Global Constraints

- Dinero en `BigDecimal` con escala 2 (`NUMERIC(10, 2)`). Nada de `double` ni `float`.
- Fechas: `orderDate` es `LocalDate`; los timestamps son `LocalDateTime`.
- Código, tablas y columnas en inglés. Mensajes de la API en español.
- Sin Lombok. **Sin borrado físico:** las líneas y los pedidos se cancelan.
- `ModularityTests` en verde: `sales` solo usa la raíz de `catalog`, de `customers` y de `shared`.
- Rama `feature/sales-orders` desde `origin/main`. Commit + push por tarea, sin `Co-Authored-By`. El `.gitignore` local del usuario no se commitea.
- Maven desde Bash con `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"`.

## Decisiones de diseño

- **Número de pedido:** `SO-<año de orderDate>-<secuencia de 6 dígitos>`. La secuencia es global (`sales_order_number_seq`) y no se reinicia cada año. Es más simple, y el número sigue siendo único y legible.
- **Estados:**
  - Pedido: `OPEN` o `CANCELLED`, guardado en BD. El estado derivado de las líneas llegará en el Paso 7.
  - Línea: `PENDING` o `CANCELLED`. `ORDERED`, `RECEIVED` y `DELIVERED` llegarán con los lotes (Pasos 5 y 7).
- **Precios en la petición:**
  - `unitSalePrice`, `unitPurchaseCost`, `salePrice` y `purchaseCost` son opcionales.
  - Si vienen `null`, se copia el valor del catálogo. Si vienen con valor (incluido `0`), se usa ese valor.
- **Catálogo:** un producto o una personalización desactivados no se pueden añadir (409). Uno inexistente da 404.
- **Edición:**
  - Solo se editan pedidos `OPEN` y líneas `PENDING`.
  - No se puede cancelar la última línea no cancelada de un pedido (409): hay que cancelar el pedido.
  - Cancelar un pedido cancela todas sus líneas.
- **Totales:**
  - Venta de la línea = `unitSalePrice` + Σ personalizaciones. El coste se calcula igual.
  - El pedido suma solo las líneas no canceladas.
  - Un pedido cancelado mantiene sus líneas visibles, con totales 0.

## Review Focus

1. **Precio a 0 en la petición** (personalización gratis para un amigo) → se guarda `0.00`, no se sustituye por el precio del catálogo. Test en la Tarea 3.
2. **Cambiar el precio en el catálogo después de crear el pedido** → el pedido mantiene los precios y el nombre que se copiaron (snapshot). Test en la Tarea 3.
3. **Línea cancelada** → no cuenta en los totales, pero sigue apareciendo en el detalle con estado `CANCELLED`. Test en la Tarea 4.
4. **Error de validación en una línea anidada** → el campo sale como `lines[0].kitName`, para que el frontend lo ponga en su sitio. Test en la Tarea 3.
5. **Talla inexistente** (`"size":"XXXL"`) → 400 en español, no 500. Test en la Tarea 3.

---

## File Structure

```text
src/main/java/com/pedropalazon/sales_manager_backend/
├── catalog/
│   ├── CatalogApi.java                        (API pública)
│   ├── CatalogItemSnapshot.java               (API pública)
│   └── internal/CatalogApiService.java        (implementación)
├── customers/
│   ├── CustomerApi.java · CustomerSummary.java (API pública)
│   └── internal/CustomerApiService.java       (implementación)
└── sales/internal/
    ├── SalesOrder.java · SalesOrderLine.java · LineCustomization.java
    ├── SalesOrderStatus.java · LineStatus.java
    ├── SalesOrderRepository.java              (+ siguiente número de la secuencia)
    ├── SalesOrderService.java
    ├── SalesOrderView.java                    (records de lectura: detalle, línea, personalización, totales, resumen)
    ├── OrderLineDraft.java                    (datos de entrada de una línea, ya sin anotaciones web)
    └── web/
        ├── SalesOrderController.java          (/api/sales/orders)
        └── CreateSalesOrderRequest.java · UpdateSalesOrderRequest.java · OrderLineRequest.java · LineCustomizationRequest.java
src/main/resources/db/migration/V4__create_sales_orders.sql
src/main/resources/application.yaml            (hibernate.default_batch_fetch_size)
src/test/java/.../catalog/internal/CatalogApiServiceTest.java
src/test/java/.../customers/internal/CustomerApiServiceTest.java
src/test/java/.../sales/internal/SalesOrderTotalsTest.java   (unitario, sin Spring)
src/test/java/.../sales/internal/SalesOrderRepositoryTest.java
src/test/java/.../sales/internal/web/SalesOrderApiTest.java
```

---

### Task 1: APIs públicas de `catalog` y `customers`

**Interfaces (Produces):**

```java
// catalog
public interface CatalogApi {
    CatalogItemSnapshot getActiveProduct(Long productId);             // 404 si no existe, 409 si está desactivado
    CatalogItemSnapshot getActiveCustomization(Long customizationId);  // ídem
}
public record CatalogItemSnapshot(Long id, String name, BigDecimal salePrice, BigDecimal purchaseCost) {}

// customers
public interface CustomerApi {
    CustomerSummary getCustomer(Long customerId);                          // 404 si no existe
    Map<Long, CustomerSummary> findCustomers(Collection<Long> customerIds); // los que no existan se omiten
}
public record CustomerSummary(Long id, String name, String phone) {}
```

Mensajes:
- "El producto 'X' está desactivado".
- "La personalización 'X' está desactivada".
- Los 404 reutilizan los de `ProductService`, `CustomizationService` y `CustomerService`.

Tests (`@SpringBootTest` + `@Transactional`, perfil `test`):
- `CatalogApiServiceTest`:
  - Un producto activo devuelve un snapshot con nombre y precios.
  - Un producto desactivado da `BusinessRuleException` con el mensaje.
  - Un id inexistente da `ResourceNotFoundException`.
  - Una personalización desactivada da `BusinessRuleException`.
- `CustomerApiServiceTest`:
  - `getCustomer` devuelve el resumen.
  - Un id inexistente da `ResourceNotFoundException`.
  - `findCustomers` devuelve un mapa con los existentes y omite los ids inexistentes.

Pasos: test que falla (compilación) → implementación → test en verde → suite → commit `feat: expose catalog and customer lookups to other modules` → push.

---

### Task 2: Entidades de pedido, migración `V4`, totales y número de pedido

**Interfaces (Produces):**
- `SalesOrder(String orderNumber, Long customerId, LocalDate orderDate, String notes)`, con:
  - `addLine(SalesOrderLine)`, `updateHeader(Long customerId, LocalDate orderDate, String notes)` y `cancel()`.
  - `totalSale()`, `totalCost()` y `totalProfit()`, que suman solo las líneas no canceladas (0 si el pedido está cancelado).
  - `activeLineCount()`, `getLines()`, `getStatus()` y los getters de cabecera.
- `SalesOrderLine(Long productId, String productName, String kitName, ClothingSize size, BigDecimal unitSalePrice, BigDecimal unitPurchaseCost)`, con:
  - `addCustomization(LineCustomization)`, `replaceWith(otra línea sin persistir)` y `cancel()`.
  - `totalSale()`, `totalCost()`, `profit()` e `isCancelled()`.
- `LineCustomization(Long customizationId, String customizationName, String customText, BigDecimal salePrice, BigDecimal purchaseCost)`.
- `SalesOrderRepository extends JpaRepository<SalesOrder, Long>`, con:
  - `long nextOrderSequence()`: consulta nativa `select nextval('sales_order_number_seq')`.
  - `List<SalesOrder> findAllByOrderByOrderDateDescIdDesc()` y `List<SalesOrder> findByCustomerIdOrderByOrderDateDescIdDesc(Long)`.
- `SalesOrder.formatOrderNumber(int year, long sequence)` → `"SO-2026-000001"`.

Migración `V4__create_sales_orders.sql`:
- Secuencia `sales_order_number_seq`.
- Tabla `sales_orders`: `order_number` único, FK `customer_id` → `customers`, `order_date` DATE, `status` VARCHAR(20), `notes` VARCHAR(500) y timestamps. Índices por cliente y por fecha.
- Tabla `sales_order_lines`:
  - Columnas: FK `sales_order_id`, FK `product_id` → `products`, `product_name` VARCHAR(120), `kit_name` VARCHAR(160), `clothing_size` VARCHAR(20), `unit_sale_price` y `unit_purchase_cost` NUMERIC(10,2) con CHECK ≥ 0, y `status` VARCHAR(20).
  - Índices por pedido y por estado.
- Tabla `line_customizations`: FK `sales_order_line_id`, FK `customization_id` → `customizations`, `customization_name` VARCHAR(120), `custom_text` VARCHAR(120) que admite NULL, y `sale_price` y `purchase_cost` NUMERIC(10,2) con CHECK ≥ 0.
- Se usan `clothing_size` y `custom_text` en vez de `size` y `value` para evitar palabras reservadas de H2 y PostgreSQL.

`application.yaml`: `spring.jpa.properties.hibernate.default_batch_fetch_size: 50`. Evita N+1 al cargar las líneas y las personalizaciones de varios pedidos.

Tests:
- `SalesOrderTotalsTest` (unitario, sin Spring):
  - Una línea de 15,00/6,50 con "Nombre + número" de 2,00/1,20 da venta 17,00, coste 7,70 y beneficio 9,30.
  - El pedido suma dos líneas.
  - Una línea cancelada no cuenta.
  - Un pedido cancelado tiene totales 0.
  - `formatOrderNumber(2026, 1)` da `"SO-2026-000001"`.
- `SalesOrderRepositoryTest` (`@DataJpaTest`):
  - Guardar un pedido con línea y personalización y recargarlo conserva los snapshots y la talla.
  - Dos llamadas a `nextOrderSequence()` devuelven valores crecientes.

Commit `feat: add sales order and line entities with migration`.

---

### Task 3: Crear y consultar pedidos copiando precios del catálogo

**Endpoints:**
- `POST /api/sales/orders` → 201 + `Location` y el detalle.
- `GET /api/sales/orders/{id}` → detalle.

**Petición `CreateSalesOrderRequest`:**

```json
{ "customerId": 1, "orderDate": "2026-10-15", "notes": "...",
  "lines": [ { "productId": 1, "kitName": "Selección Española blanca 2 estrellas", "size": "M",
               "unitSalePrice": null, "unitPurchaseCost": null,
               "customizations": [ { "customizationId": 1, "customText": "WILLIAMS JR 17",
                                     "salePrice": 0, "purchaseCost": null } ] } ] }
```

- `customerId` y `lines` son obligatorios, con al menos una línea.
- `orderDate` es opcional y por defecto es hoy.
- `kitName` es obligatorio, con 160 caracteres como máximo. `size` es obligatorio.
- Los precios siguen la misma validación que el catálogo (≥ 0, 8 enteros y 2 decimales).
- `customText` admite 120 caracteres como máximo.

**Respuesta (`SalesOrderView.Details`):**
- Cabecera: `id`, `orderNumber`, `orderDate`, `status` y `notes`.
- `customer`: `{id, name, phone}`.
- `lines[]`: `{id, productId, productName, kitName, size, sizeLabel, unitSalePrice, unitPurchaseCost, status, customizations[], totalSale, totalCost, profit}`.
- `totals`: `{sale, cost, profit, activeLines}`.

**Tests (`SalesOrderApiTest`, `@SpringBootTest` + `@Transactional`):** los datos se crean por API (producto, personalización y cliente).
- **Camino feliz:** copia los precios del catálogo, `orderNumber` empieza por `SO-2026-` y los totales cuadran (17,00 / 7,70 / 9,30).
- **Override:** `salePrice: 0` en la personalización se guarda 0 y los totales bajan (15,00 / 7,70 / 7,30).
- **Snapshot:** se edita el precio del producto en el catálogo después del pedido y el `GET` del pedido mantiene 15,00 y el nombre original.
- **Sin líneas** → 400 con el campo `lines`.
- **Falta `kitName`** → 400 con el campo `lines[0].kitName`.
- **Talla `XXXL`** → 400 con el título "Petición no válida".
- **Producto desactivado** → 409 "El producto 'Camiseta Fan' está desactivado".
- **Cliente inexistente** → 404 "No existe el cliente con id 999999".
- **Pedido inexistente** → 404 "No existe el pedido con id 999999".

Commit `feat: create orders copying catalog prices with manual overrides`.

---

### Task 4: Listar, editar y cancelar pedidos y líneas

**Endpoints:**
- `GET /api/sales/orders?customerId=` → resúmenes `{id, orderNumber, orderDate, status, customerId, customerName, activeLines, totalSale, totalProfit}`, del más reciente al más antiguo.
- `PUT /api/sales/orders/{id}` → cabecera (`customerId`, `orderDate`, `notes`). Solo pedidos `OPEN`.
- `POST /api/sales/orders/{id}/lines` → añade una línea (mismo formato que en la creación). Solo pedidos `OPEN`. Responde 201 con el detalle.
- `PUT /api/sales/orders/{id}/lines/{lineId}` → sustituye la línea: producto, precios (si vienen `null`, se vuelven a copiar del catálogo) y personalizaciones. Solo líneas `PENDING` de pedidos `OPEN`.
- `POST /api/sales/orders/{id}/lines/{lineId}/cancel` → la línea pasa a `CANCELLED`. Da 409 si es la última no cancelada.
- `POST /api/sales/orders/{id}/cancel` → el pedido y todas sus líneas pasan a `CANCELLED`.

**Mensajes:**
- "El pedido SO-… está cancelado y no se puede modificar".
- "La línea N ya no está pendiente y no se puede modificar".
- "No se puede cancelar la última línea del pedido: cancela el pedido entero".
- "No existe la línea N en el pedido SO-…".

**Tests (`SalesOrderApiTest`):**
- El listado filtra por cliente y ordena del más reciente al más antiguo.
- Editar la cabecera cambia la fecha y las notas.
- Añadir una línea suma a los totales.
- Editar una línea cambia el producto y la talla y vuelve a copiar los precios.
- **Cancelar una línea:** sale `CANCELLED` en el detalle y los totales la excluyen.
- Cancelar la última línea → 409.
- Cancelar el pedido → `CANCELLED`, todas sus líneas canceladas y totales 0.
- Editar un pedido cancelado → 409.
- Una línea de otro pedido → 404.

Commit `feat: list, edit and cancel orders and lines`.

---

### Task 5: Cierre del Paso 3

- [ ] Actualizar el Paso 3 de `docs/ROADMAP.md` con los commits reales y las reglas. Commit `docs: update roadmap for sales orders step`.
- [ ] Prueba manual con `docker compose up -d --build` (`APP_PORT=8081`):
  - Crear producto, personalización, cliente y un pedido con dos líneas.
  - Comprobar los totales y que Flyway aplica `V4`.
  - Borrar los datos de prueba y `docker compose down`.
- [ ] Revisión final de toda la rama con un revisor independiente. Corregir los Critical e Important con TDD.
- [ ] PR `feature/sales-orders` → `main` y asociarlo a la sesión.
