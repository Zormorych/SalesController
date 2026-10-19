# Paso 9 — Stock y reventa · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prendas en stock para revender (dadas de alta a mano o camisetas que el cliente ya no quiere), su venta con beneficio y su reflejo en los informes.

**Architecture:**
- Nuevo módulo Modulith `resale` (Swagger "Stock y reventa", `/api/resale/items`) → `sales` y `purchasing`, solo por sus APIs públicas. `reports` → `resale`.
- Una camiseta pasada a stock cambia de estado en `sales`: `STOCKED_PAID` (sigue siendo una venta) o `STOCKED_REFUNDED` (cuenta como cancelada).
- La prenda de stock se crea en `resale` en la misma transacción.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Modulith 2.1, JPA/Hibernate 7, Flyway, H2 (tests) y PostgreSQL 17.

**Spec:** [docs/superpowers/specs/2026-10-19-resale-design.md](../specs/2026-10-19-resale-design.md)

## Global Constraints

- `BigDecimal` con escala 2 (`HALF_UP`). Fechas con `LocalDate`. Sin Lombok. Sin ciclos entre módulos (`ModularityTests`).
- Código en inglés. Mensajes de la API y documentación en español. Errores: `ResourceNotFoundException` → 404, `BusinessRuleException` → 409, validación → 400.
- Sin borrado físico.
- Migración nueva: `V8__create_resale_items.sql`. La columna `status` de las líneas es `VARCHAR(20)`: los estados nuevos caben sin migrarla.
- Rama `feature/resale`. Commit + push por tarea, sin firmas en los mensajes.
- Sin Postman: se prueba con Swagger y con tests.

## Review Focus

1. **Pedido con una sola camiseta pasada a stock con devolución:** el pedido queda `CANCELLED`, la línea sigue `STOCKED_REFUNDED` (no `CANCELLED`) y lo pagado aparece en "por devolver". Test en Task 1.
2. **Reparto del envío del lote:** pasar a stock (con cualquier opción) no cambia la parte de envío de las demás camisetas del lote. Test en Task 2.
3. **Doble cómputo:**
   - Con `customerPays = true`, la venta y el coste siguen en el resumen de pedidos y la prenda entra a 0,00.
   - Con `false`, salen del resumen y del lote, y la prenda entra con coste + envío.
   - Test en Task 5.
4. **Pasar a stock dos veces la misma camiseta, o una no recibida:** 409, sin crear la prenda. Test en Task 4.
5. **Vender una prenda ya vendida:** 409. Deshacer la venta de una disponible: 409. Test en Task 3.

---

### Task 1: Camisetas pasadas a stock en `sales`

**Files:**
- Modify:
  - `sales/LineStatus.java`
  - `sales/SalesOrderApi.java`
  - `sales/internal/SalesOrderLine.java`
  - `sales/internal/SalesOrder.java`
  - `sales/internal/SalesOrderView.java` (`LineCounts`)
  - `sales/internal/SalesOrderService.java` (`cancel`)
  - `sales/internal/SalesOrderApiService.java`
  - `sales/internal/SalesOrderLineRepository.java`
- Test:
  - `sales/internal/SalesOrderProgressTest.java`
  - Nuevo `sales/internal/SalesOrderStockApiTest.java` (`@SpringBootTest @Transactional`, usando `SalesOrderApi`).

**Interfaces:**
- Produces:
  - `LineStatus.STOCKED_PAID` y `LineStatus.STOCKED_REFUNDED`.
  - `OrderLineSnapshot SalesOrderApi.moveLineToStock(Long lineId, boolean customerPays)`, con `Propagation.MANDATORY`. Devuelve la línea ya en su estado nuevo.
  - `SalesOrderView.LineCounts(pending, ordered, received, delivered, cancelled, stocked)`, donde `stocked` cuenta los dos estados.

- [ ] **Step 1: Tests que fallan**
  - En `SalesOrderProgressTest`:
    - Recibida + `STOCKED_PAID` → `READY_TO_DELIVER`.
    - Entregada + `STOCKED_PAID` → `COMPLETED`.
    - Una `STOCKED_REFUNDED` no suma en `totalSale()` y no cuenta para el progreso.
    - Una `STOCKED_PAID` sí suma en `totalSale()`.
    - Los `LineCounts` existentes ganan un sexto valor `0`.
  - En `SalesOrderStockApiTest`:
    - `givenReceivedLine_whenMovedToStockWithCustomerPaying_thenLineIsStockedPaidAndOrderTotalIsUnchanged`.
    - `givenReceivedLine_whenMovedToStockWithRefund_thenOrderTotalDropsByItsSale`.
    - `givenOnlyActiveLine_whenMovedToStockWithRefund_thenOrderIsCancelledAndLineStaysStockedRefunded`.
    - `givenPendingOrOrderedLine_whenMovedToStock_thenConflictAndNothingChanges`: 409 con "no está recibida".
    - `givenStockedRefundedLine_whenSearchingReportLines_thenItIsLeftOutButStockedPaidIsIncluded`: `findActiveLinesOrderedBetween` y `countActiveLinesByStatus` no incluyen `STOCKED_REFUNDED`; `STOCKED_PAID` sí aparece en el rango.
  - Para llegar a `RECEIVED`, los tests usan `markLinesOrdered` + `receiveLines` dentro de la transacción del test.
- [ ] **Step 2:** `./mvnw -B -ntp test -Dtest='SalesOrderProgressTest,SalesOrderStockApiTest'` falla (no compila).
- [ ] **Step 3: Implementar**
  - `SalesOrderLine`:
    - `moveToStock(boolean customerPays)`.
    - `countsAsSale()`: `status` no es `CANCELLED` ni `STOCKED_REFUNDED`. Sustituye a `!isCancelled()` en `SalesOrder.activeLines()`.
  - `SalesOrder.progress()`: `STOCKED_PAID` cuenta como `DELIVERED` para `COMPLETED` y como recibida para `READY_TO_DELIVER`.
  - `SalesOrder.cancel()`: solo pasa a `CANCELLED` las líneas `PENDING`; las `STOCKED_REFUNDED` quedan igual.
  - `SalesOrderService.cancel`: la guarda permite `PENDING`, `CANCELLED` y `STOCKED_REFUNDED`.
  - `SalesOrderApiService.moveLineToStock`:
    - Reutiliza `changeStatus(List.of(lineId), RECEIVED, "La línea %d no está recibida: solo una camiseta ya recibida puede pasar a stock", …)`.
    - Si `!customerPays` y el pedido se queda sin líneas que cuenten como venta, llama a `order.cancel()`.
    - `findActiveLinesOrderedBetween` y `countActiveLinesByStatus` excluyen también `STOCKED_REFUNDED`. `findOrderedBetween` pasa de `l.status <> :excludedStatus` a `l.status not in :excludedStatuses`.
- [ ] **Step 4:** el mismo comando pasa. Después, `./mvnw -B -ntp verify` en verde, porque el cambio de `LineCounts` afecta a otros tests.
- [ ] **Step 5:** commit `feat: move received shirts to stock from sales orders`.

### Task 2: Envío y totales de lote con camisetas en stock (`purchasing`)

**Files:**
- Modify:
  - `purchasing/PurchasingApi.java`
  - `purchasing/internal/PurchasingApiService.java`
  - `purchasing/internal/FactoryBatchView.java`
  - `purchasing/internal/FactoryBatchRepository.java`
- Test: `purchasing/internal/PurchasingApiServiceTest.java`

**Interfaces:**
- Consumes: `LineStatus.STOCKED_PAID` y `LineStatus.STOCKED_REFUNDED` (Task 1).
- Produces: `BigDecimal PurchasingApi.shippingShareOf(Long lineId)`. Es la parte de envío de la línea en su lote no descartado; `0.00` si no está en ninguno.

- [ ] **Step 1: Tests que fallan**
  - `givenBatchOfThreeWithOneMovedToStock_whenGettingShares_thenSplitIsUnchanged`:
    - Lote recibido de 3 camisetas con 4,50 €. Una pasa a `STOCKED_REFUNDED` y otra a `STOCKED_PAID`.
    - Las tres siguen con 1,50 en `shippingSharesByLine()`, y `shippingShareOf(lineId)` da 1,50.
  - `givenStockedRefundedLine_whenGettingBatchTotals_thenItsSaleCostAndShippingAreLeftOut`:
    - `shirts` 2 y `shipping` 3,00.
    - La `STOCKED_PAID` sí cuenta.
  - `givenLineWithoutBatch_whenGettingShareOf_thenZero`: `0.00`.
- [ ] **Step 2:** `./mvnw -B -ntp test -Dtest=PurchasingApiServiceTest` falla.
- [ ] **Step 3: Implementar**
  - `FactoryBatchView.details`:
    - El reparto sigue contando todas las líneas no `CANCELLED`.
    - `Totals` (venta, coste, envío, `activeLines`) excluye además `STOCKED_REFUNDED`.
    - La línea mantiene su `shippingShare` en `lines`.
  - `shippingShareOf`: `FactoryBatchRepository` gana `Optional<FactoryBatch> findActiveContainingLine(Long lineId, FactoryBatchStatus excluded)`, con `select b from FactoryBatch b join b.lineIds l where l = :lineId and b.status <> :excluded`, y se usa el detalle de ese lote.
- [ ] **Step 4:** el test pasa y `./mvnw -B -ntp verify` está en verde.
- [ ] **Step 5:** commit `feat: keep shipping split when shirts move to stock`.

### Task 3: Módulo `resale`: prendas dadas de alta a mano

**Files:**
- Create:
  - `src/main/resources/db/migration/V8__create_resale_items.sql`
  - `resale/internal/ResaleItem.java`
  - `ResaleItemStatus.java` (`AVAILABLE`, `SOLD`)
  - `ResaleItemOrigin.java` (`MANUAL`, `ORDER_LINE`)
  - `ResaleItemRepository.java`
  - `ResaleItemService.java`
  - `ResaleItemView.java`
  - `resale/internal/web/ResaleItemController.java`
  - `ResaleItemRequest.java`
  - `SellResaleItemRequest.java`
- Test: `resale/internal/web/ResaleItemApiTest.java`

**Interfaces:**
- Produces:
  - Tabla `resale_items`:
    - `id`, `name VARCHAR(160) NOT NULL`, `item_size VARCHAR(40)`, `notes VARCHAR(500)`.
    - `origin VARCHAR(20) NOT NULL`, `sales_order_line_id BIGINT UNIQUE REFERENCES sales_order_lines(id)`.
    - `purchase_cost NUMERIC(10,2) NOT NULL CHECK >= 0`, `purchase_date DATE NOT NULL`, `asking_price NUMERIC(10,2) CHECK >= 0`.
    - `status VARCHAR(20) NOT NULL`, `sale_price NUMERIC(10,2) CHECK >= 0`, `sale_date DATE`.
    - `created_at`, `updated_at`.
    - Índice por `status`. La columna no se llama `size` (palabra reservada en H2/PostgreSQL).
  - `ResaleItemView.Item(Long id, String name, String size, String notes, ResaleItemOrigin origin, Long salesOrderLineId, BigDecimal purchaseCost, LocalDate purchaseDate, BigDecimal askingPrice, ResaleItemStatus status, BigDecimal salePrice, LocalDate saleDate, BigDecimal profit)`, con `profit` null si no está vendida.
  - `ResaleItemService`:
    - `create(ResaleItemDraft)`.
    - `update(Long id, ResaleItemDraft)`.
    - `sell(Long id, BigDecimal salePrice, LocalDate saleDate)`.
    - `unsell(Long id)`.
    - `get(Long id)`.
    - `list(ResaleItemStatus statusOrNull)`, de la más reciente a la más antigua.
    - Todos devuelven `ResaleItemView.Item`.
    - `ResaleItemDraft(String name, String size, String notes, BigDecimal purchaseCost, LocalDate purchaseDate, BigDecimal askingPrice)`. Sin `purchaseDate`, se usa hoy.
  - Endpoints:
    - `POST /api/resale/items` (201).
    - `PUT /api/resale/items/{id}`.
    - `POST /api/resale/items/{id}/sell`.
    - `POST /api/resale/items/{id}/unsell`.
    - `GET /api/resale/items/{id}`.
    - `GET /api/resale/items?status=`.
  - Tag de Swagger: `@Tag(name = "Stock y reventa", …)`.

- [ ] **Step 1: Tests que fallan** (`ResaleItemApiTest`, MockMvc)
  - `givenVintedJacket_whenCreatedAndSold_thenProfitIsSalePriceMinusCost`: alta con coste 11,70 y anuncio 20,00 → `AVAILABLE`, `profit` null. Venta por 17,70 → `SOLD`, `profit` 6,00.
  - `givenSoldItem_whenSoldAgain_thenConflict` (409) y `givenAvailableItem_whenUnsold_thenConflict` (409).
  - `givenSoldItem_whenUnsold_thenAvailableWithoutSaleData`.
  - `givenNoPurchaseOrSaleDate_whenCreatingAndSelling_thenTodayIsUsed`.
  - `givenBlankNameOrNegativeCost_whenCreating_thenBadRequestOnField`: 400 con `errors[0].field`.
  - `givenItems_whenListingByStatus_thenOnlyThatStatus`.
  - `givenUnknownId_whenGetting_thenNotFound`: 404.
- [ ] **Step 2:** `./mvnw -B -ntp test -Dtest=ResaleItemApiTest` falla.
- [ ] **Step 3: Implementar**
  - Patrones de `payments`: entidad con `@PrePersist`/`@PreUpdate`, servicio `@Transactional`, controlador package-private y records de petición con Bean Validation:
    - `name` `@NotBlank @Size(max=160)`.
    - `purchaseCost` y `salePrice` `@NotNull @PositiveOrZero`.
    - `askingPrice` `@PositiveOrZero`.
  - Mensajes en español:
    - "La prenda %d ya está vendida".
    - "La prenda %d no está vendida".
    - "No existe la prenda con id %d".
- [ ] **Step 4:** el test pasa y `./mvnw -B -ntp verify` está en verde (incluye `ModularityTests`).
- [ ] **Step 5:** commit `feat: add resale items bought to resell`.

### Task 4: Pasar a stock una camiseta de un pedido

**Files:**
- Modify:
  - `resale/internal/ResaleItemService.java`
  - `resale/internal/ResaleItem.java` (constructor desde línea)
  - `resale/internal/web/ResaleItemController.java`
- Create: `resale/internal/web/MoveOrderLineToStockRequest.java`
- Test: `resale/internal/web/ResaleFromOrderLineApiTest.java`

**Interfaces:**
- Consumes:
  - `SalesOrderApi.moveLineToStock(Long, boolean)` (Task 1).
  - `PurchasingApi.shippingShareOf(Long)` (Task 2).
- Produces:
  - `ResaleItemView.Item ResaleItemService.moveFromOrderLine(Long lineId, boolean customerPays, BigDecimal askingPrice)`.
  - `POST /api/resale/items/from-order-line` (201), con cuerpo `{lineId, customerPays (por defecto true), askingPrice?}`.

- [ ] **Step 1: Tests que fallan** (camiseta de 17,00 / 7,70 + "Nombre + número" 2,00 / 1,20 con texto "WILLIAMS JR 17", en un lote de 3 con 4,50 € de envío, enviado y recibido)
  - `givenReceivedShirt_whenMovedToStockWithCustomerPaying_thenItemCostsZero`:
    - `origin` `ORDER_LINE`, `purchaseCost` 0,00.
    - `name` "Camiseta Player · Selección Española blanca · WILLIAMS JR 17", `size` "M".
    - El pedido sigue con su total.
  - `givenReceivedShirt_whenMovedToStockWithRefund_thenItemCostsLineCostPlusShippingShare`: `purchaseCost` 10,40 (7,70 + 1,20 + 1,50).
  - `givenShirtAlreadyMoved_whenMovedAgain_thenConflictAndNoSecondItem`: 409 y una sola prenda con ese `salesOrderLineId`.
  - `givenShirtNotReceived_whenMovedToStock_thenConflictAndNoItem`: 409.
  - `givenUnknownLine_whenMovedToStock_thenNotFound`: 404.
  - `givenCustomizationWithoutText_whenMovedToStock_thenItsNameIsUsed`: un parche sin texto aparece como "Parche".
- [ ] **Step 2:** `./mvnw -B -ntp test -Dtest=ResaleFromOrderLineApiTest` falla.
- [ ] **Step 3: Implementar `moveFromOrderLine`** (`@Transactional`)
  - Llama a `moveLineToStock`. Su 409 por estado protege también de pasarla dos veces, porque ya no está `RECEIVED`.
  - Coste: `customerPays ? 0.00 : totalCost + shippingShareOf(lineId)`.
  - Nombre: `productName · kitName`, más ` · ` y las personalizaciones (`customText`, o el nombre de la personalización si no hay texto), unidas por `, `.
  - Talla: `size.getLabel()`. Fecha de compra: hoy.
- [ ] **Step 4:** el test pasa y `./mvnw -B -ntp verify` está en verde.
- [ ] **Step 5:** commit `feat: move unwanted order shirts to resale stock`.

### Task 5: Reventa en los informes

**Files:**
- Create:
  - `resale/ResaleApi.java`
  - `resale/ResaleSales.java`
  - `resale/StockValue.java`
  - `resale/internal/ResaleApiService.java`
- Modify:
  - `resale/internal/ResaleItemRepository.java`
  - `reports/internal/SalesSummary.java`
  - `reports/internal/PendingWork.java`
  - `reports/internal/ReportService.java`
- Test:
  - `resale/internal/ResaleApiServiceTest.java`
  - `reports/internal/web/ReportApiTest.java`

**Interfaces:**
- Produces:
  - `ResaleSales ResaleApi.soldBetween(LocalDate from, LocalDate to)`, con `record ResaleSales(long items, BigDecimal sale, BigDecimal cost, BigDecimal profit)` por **fecha de venta**.
  - `StockValue ResaleApi.stock()`, con `record StockValue(long items, BigDecimal cost)` para las disponibles.
  - `SalesSummary` gana al final `ResaleSales resale, BigDecimal totalProfit`.
  - `PendingWork` gana al final `long itemsInStock, BigDecimal stockValue`.

- [ ] **Step 1: Tests que fallan**
  - `ResaleApiServiceTest`: una prenda vendida el 31/10 entra en octubre y no en noviembre; las disponibles suman en `stock()`; sin datos, todo a 0,00.
  - `ReportApiTest`, sobre el escenario existente (A, B, C, D y el lote de A+B con 4,50 €). Las prendas se crean dentro de cada test, no en el `@BeforeEach`.
    - `givenVintedItems_whenGettingReports_thenResaleProfitAndStockAppear`:
      - Una prenda 11,70 → 17,70 vendida el 15/10 y otra disponible de 9,00.
      - Resumen de octubre: `resale.items` 1, `resale.profit` 6,00, `totalProfit` 27,80 (21,80 + 6,00).
      - Pendiente: `itemsInStock` 1, `stockValue` 9,00.
    - `givenShirtsMovedToStock_whenGettingReports_thenNothingCountsTwice`:
      - Se recibe el lote entero (`batchService.receive(batchId, List.of())`). La Fan de A (15,00 / 6,50) pasa con `customerPays = false` y la de B con `true`.
      - Resumen de octubre: `shirts` 2, `sale` 32,00, `cost` 14,20, `shipping` 3,00, `profit` 14,80.
      - Lotes: `shirts` 2, `sale` 32,00, `shipping` 3,00, `profit` 14,80.
      - Pendiente:
        - `shirtsToDeliver` 1.
        - `ordersToCollect` 1 y `amountToCollect` 15,00 (B sigue debiéndose).
        - `amountToRefund` 13,00 (A ahora suma 17,00 con 20,00 pagados, más los 10,00 de D).
        - `itemsInStock` 2 y `stockValue` 8,00 (6,50 + 1,50 de envío; la de B vale 0,00).
- [ ] **Step 2:** `./mvnw -B -ntp test -Dtest='ResaleApiServiceTest,ReportApiTest'` falla.
- [ ] **Step 3: Implementar**
  - Consultas agregadas JPQL en `ResaleItemRepository`, con `coalesce(sum(...), 0)` y `setScale(2)` como en `PaymentApiService`.
  - `ReportService` añade los campos nuevos. `reports` depende de `resale`.
- [ ] **Step 4:** los tests pasan y `./mvnw -B -ntp verify` está en verde.
- [ ] **Step 5:** commit `feat: add resale figures to reports`.

### Task 6: Cierre

- [ ] Roadmap: el Paso 9 tal como quedó (endpoints y estados `STOCKED_PAID` / `STOCKED_REFUNDED`). En la sección 2, el modelo `ResaleItem` y los estados de línea nuevos.
- [ ] Prueba manual en Docker (`APP_PORT=8081`) con un script Node que cubra:
  - Una prenda de Vinted 11,70 → 17,70.
  - Una camiseta recibida pasada a stock con cada opción.
  - Los informes.
  - Después, borrar los datos de prueba y reiniciar las secuencias.
- [ ] Revisión final de toda la rama con un subagente; corregir con TDD lo Critical e Important.
- [ ] PR a `main` con `gh` y asociarlo a la sesión.
