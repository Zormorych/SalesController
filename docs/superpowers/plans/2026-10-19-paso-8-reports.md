# Paso 8 — Informes · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Informes de solo lectura con tres vistas: venta, coste, envío y beneficio de un periodo; beneficio por lote; y lo que falta por pedir, recibir, entregar, cobrar o devolver.

**Architecture:**
- Nuevo módulo Modulith `reports` → `sales`, `purchasing` y `payments`, solo a través de sus APIs públicas.
- APIs nuevas:
  - `SalesOrderApi`: `findActiveLinesOrderedBetween`, `countActiveLinesByStatus` y `findAllOrders`.
  - `purchasing.PurchasingApi`: `shippingSharesByLine` y `findBatchTotals`. `FactoryBatchStatus` pasa a la API pública de `purchasing`.
  - `payments.PaymentApi`: `paidAmountsByOrder` y `collectedBetween`.
- Las implementaciones son *package-private*, dentro del `internal` de cada módulo.

**Spec:** [docs/ROADMAP.md](../../ROADMAP.md), sección 2 (cálculos) y "Paso 8".

## Global Constraints

- `BigDecimal` con escala 2. Fechas con `LocalDate`. Sin Lombok. Sin ciclos entre módulos.
- Sin Postman: se prueba con Swagger y con tests.
- Rama `feature/reports` desde `origin/main`. Commit + push por tarea.

## Decisiones de diseño

- **Resumen** (`GET /api/reports/summary?from&to`):
  - Por defecto, del día 1 al último del mes actual. Si `from` es posterior a `to`, 400.
  - Entran las camisetas no canceladas de pedidos no cancelados cuya **fecha de pedido** está en el rango.
  - **Envío:** el reparto de su lote, si está en un lote no descartado; 0 si aún no está en ninguno.
  - **Beneficio** = venta − coste − envío.
  - **Cobrado:** pagos no anulados con fecha de pago en el rango.
- **Lotes** (`GET /api/reports/batches`): lotes no descartados, del más reciente al más antiguo, con camisetas, venta, coste, envío y beneficio. Son los mismos números que el detalle del lote.
- **Pendiente** (`GET /api/reports/pending`):
  - `shirtsToOrder`, `shirtsToReceive` y `shirtsToDeliver`: camisetas `PENDING`, `ORDERED` y `RECEIVED` de pedidos no cancelados.
  - Por cada pedido, pendiente = total − cobrado:
    - `amountToCollect` y `ordersToCollect`: los pedidos no cancelados con pendiente > 0.
    - `amountToRefund`: lo cobrado de más (pendiente < 0), por ejemplo en pedidos cancelados ya cobrados.

## Review Focus

1. **El envío del resumen** coincide con el reparto del lote (mismo cálculo por céntimos). Las camisetas sin lote cuentan 0.
2. **Pedido cancelado** → fuera de venta, coste y beneficio. Si estaba cobrado, aparece en `amountToRefund`.
3. **Pago anulado** → no cuenta en lo cobrado ni en el pendiente.
4. **Pedido de septiembre cobrado en octubre** → no entra en la venta de octubre, pero el cobro sí entra en "cobrado" de octubre.
5. **Rango invertido** → 400 en español.

---

### Task 1: APIs de informe en `sales`

- `List<OrderLineSnapshot> findActiveLinesOrderedBetween(LocalDate from, LocalDate to)`, con una consulta JPQL sobre las líneas que hace `join fetch` del pedido.
- `Map<LineStatus, Long> countActiveLinesByStatus()`: líneas de pedidos no cancelados.
- `List<SalesOrderSnapshot> findAllOrders()`.
- Tests (`SalesOrderReportApiTest`): el rango excluye lo cancelado y lo que queda fuera de fechas; los recuentos; todos los pedidos con su total (0 si están cancelados).
- Commit `feat: expose sales data for reports`.

### Task 2: APIs de `payments` y `purchasing`

- `payments.PaymentApi` + `PaymentApiService`:
  - `paidAmountsByOrder()`: suma de pagos no anulados por pedido.
  - `collectedBetween(from, to)`.
- `purchasing.PurchasingApi` + `PurchasingApiService`:
  - `shippingSharesByLine()`: para cada lote no descartado se usa `FactoryBatchView.details` y se toma el reparto de cada línea.
  - `findBatchTotals()`: records `FactoryBatchTotals`.
  - `FactoryBatchStatus` se mueve a `purchasing` (raíz).
- Tests:
  - `PaymentApiServiceTest`: un pago anulado no cuenta; el filtro por fecha.
  - `PurchasingApiServiceTest`: el reparto de 4,50 € entre 3 camisetas y los totales de los lotes; un lote descartado no aparece.
- Commit `feat: expose payment and batch figures for reports`.

### Task 3: Módulo `reports`

- `ReportService` y `ReportController` en `/api/reports`, con los records `SalesSummary` y `PendingWork`.
- Clave de `messages.properties` para `ResponseStatusException` → "Petición no válida".
- Test (`ReportApiTest`, `@SpringBootTest` + `@Transactional`), con datos creados con los servicios de cada módulo:
  - **Pedido A** (05/10): 2 camisetas (17,00/7,70 y 15,00/6,50).
  - **Pedido B** (20/10): 15,00/6,50.
  - **Pedido C** (28/09): 15,00/6,50, cobrado con 15 € el 01/10.
  - **Pedido D** (10/10): cobrado con 10 € el 11/10 y después cancelado.
  - **Lote** con las camisetas de A y B y 4,50 € de envío, ya enviado.
  - **Pagos:** 20 € en A el 06/10, y un pago anulado de 5 € en B.
  - **Resultados esperados:**
    - Resumen de octubre: 2 pedidos, 3 camisetas, venta 47,00, coste 20,70, envío 4,50, beneficio 21,80 y cobrado 45,00.
    - Pendiente: por pedir 1, por recibir 3, por entregar 0, por cobrar 27,00 en 2 pedidos y por devolver 10,00.
    - Lotes: 1 lote con 3 camisetas y beneficio 21,80.
    - Rango invertido → 400.
- Commit `feat: add sales, batch and pending work reports`.

### Task 4: Cierre

- Roadmap.
- Prueba manual en Docker con curl.
- Revisión final, PR y asociarlo a la sesión.
