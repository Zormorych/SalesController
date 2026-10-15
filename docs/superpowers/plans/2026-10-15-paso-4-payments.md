# Paso 4 — Cobros · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Registrar los cobros (también parciales) de cada pedido y saber cuánto se ha cobrado y cuánto queda pendiente.

**Architecture:**
- Nuevo módulo Modulith `payments`, que depende de `sales` a través de una API pública nueva: `sales.SalesOrderApi` → `SalesOrderSnapshot`.
- `sales` **no** conoce a `payments`. Si lo hiciera habría un ciclo, que `ModularityTests` prohíbe. Por eso lo cobrado y lo pendiente se sirven desde `payments`, en `/api/sales/orders/{orderId}/payments`, y no en el detalle del pedido.
- `Payment` guarda `salesOrderId` (id de otro módulo) con una FK en la base de datos.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring Modulith 2.1.1, Data JPA + Flyway (`V5`), H2 en modo PostgreSQL para tests y PostgreSQL 17 en Docker. Newman para la colección de Postman.

**Spec:** [docs/ROADMAP.md](../../ROADMAP.md), sección 2 (`payments`) y "Paso 4".

## Global Constraints

- Dinero en `BigDecimal` con escala 2. Fechas con `LocalDate` (`paymentDate`) y `LocalDateTime` (timestamps).
- Código en inglés y mensajes en español. Los importes en mensajes se formatean como `15,00 €` con `shared.MoneyFormat`.
- Sin Lombok y sin borrado físico: un pago erróneo se **anula** (`VOIDED`) y deja de sumar.
- `ModularityTests` en verde, sin ciclos: `payments` → `sales` → `catalog`/`customers`.
- Rama `feature/payments` desde `origin/main`. Commit + push por tarea, sin `Co-Authored-By`. El `.gitignore` local del usuario no se commitea.
- Cada paso añade sus endpoints a la colección de Postman y la verifica con Newman.

## Decisiones de diseño

- **Importe:** debe ser mayor que 0, con 8 enteros y 2 decimales como máximo. `paymentDate` es opcional y por defecto es hoy.
- **Sobrecobro:** no se acepta un pago que haga que lo cobrado supere el total del pedido. Responde 409 con "El pago de X supera lo pendiente del pedido SO-… (Y)", o "El pedido SO-… ya está pagado" si no queda nada pendiente.
- **Pedido cancelado:** no admite pagos nuevos (409). Si se cancela un pedido que ya tenía pagos, su total pasa a 0 y el pendiente sale **negativo**. Eso significa que hay dinero a devolver, y se muestra tal cual.
- **Anular:** `POST …/payments/{paymentId}/void`. Anular un pago ya anulado da 409. Un pago de otro pedido da 404.
- **Resumen** (`PaymentSummary`): `orderId`, `orderNumber`, `orderStatus`, `orderTotal`, `paidAmount`, `pendingAmount` y `payments[]`, con `{id, amount, paymentDate, notes, status}` ordenados por fecha. Los anulados aparecen con `status = VOIDED` pero no suman.

## Review Focus

1. **Pago que deja el pedido justo pagado** (pendiente 0) → se acepta. Solo se rechaza si supera lo pendiente, y no hay error por redondeo. Test en la Tarea 3.
2. **Pago anulado** → no suma en lo cobrado y libera ese importe para un pago nuevo. Test en la Tarea 4.
3. **Pedido cancelado con pagos** → el pendiente es negativo (a devolver) y no se aceptan pagos nuevos. Test en la Tarea 3.
4. **Importe 0 o negativo** → 400 en el campo `amount`. Test en la Tarea 3.
5. **Pedido inexistente** → 404 tanto al cobrar como al consultar. Test en la Tarea 3.

---

### Task 1: API pública de `sales`

- `sales.SalesOrderApi`, con `SalesOrderSnapshot getOrder(Long orderId)`, que da 404 si no existe.
- `sales.SalesOrderSnapshot(Long id, String orderNumber, Long customerId, boolean cancelled, BigDecimal totalSale)`.
- La implementación, `sales.internal.SalesOrderApiService`, es *package-private*.
- Tests (`SalesOrderApiServiceTest`, `@SpringBootTest` + `@Transactional`):
  - Un pedido abierto devuelve su total.
  - Un pedido cancelado devuelve `cancelled=true` y total 0.
  - Un id inexistente da `ResourceNotFoundException`.
  - Para crear los datos se usan los servicios internos de cada módulo, ya que los tests no pasan por Modulith.
- Commit `feat: expose sales order totals to other modules`.

### Task 2: Entidad `Payment`, migración `V5` y `MoneyFormat`

- `shared.MoneyFormat.euros(BigDecimal)` → `"15,00 €"`, en formato español y con escala 2. Test unitario.
- `payments.internal.Payment(Long salesOrderId, BigDecimal amount, LocalDate paymentDate, String notes)`, con `voidPayment()`, `isVoided()` y `PaymentStatus {REGISTERED, VOIDED}`.
- `PaymentRepository.findBySalesOrderIdOrderByPaymentDateAscIdAsc(Long)`.
- `V5__create_payments.sql`:
  - Tabla `payments`.
  - FK `sales_order_id` → `sales_orders`.
  - `amount` NUMERIC(10,2) con CHECK > 0.
  - `payment_date` DATE, `status` VARCHAR(20), `notes` VARCHAR(500) y timestamps.
  - Índice por pedido.
- Test `@DataJpaTest`: guardar y recargar conserva el importe, la fecha y las notas; anular persiste `VOIDED`.
- Commit `feat: add payment entity and migration`.

### Task 3: Registrar pagos y ver lo cobrado y lo pendiente

- `PaymentService.register(orderId, amount, paymentDate, notes)` y `summary(orderId)`.
- `PaymentController` en `/api/sales/orders/{orderId}/payments`:
  - `GET` → resumen.
  - `POST` → 201 con el resumen.
- `RegisterPaymentRequest`:
  - `amount`: `@NotNull`, `@Positive`, `@Digits(8,2)`.
  - `paymentDate`: opcional.
  - `notes`: `@Size(500)`.
- Tests (`PaymentApiTest`). Pedido de 17,00 creado por API:
  - Un pago de 10 deja cobrado 10 y pendiente 7.
  - Un pago de 7 más deja pendiente 0 (Review Focus 1).
  - Un pago extra de 0,01 → 409 "El pedido SO-… ya está pagado".
  - En un pedido nuevo, un pago de 20 → 409 "El pago de 20,00 € supera lo pendiente del pedido SO-… (17,00 €)".
  - Un importe de 0 o -5 → 400 en el campo `amount`.
  - Un pedido inexistente → 404 en `GET` y en `POST`.
  - Un pedido cancelado → `POST` da 409, y si tenía un pago de 10, el resumen da total 0, cobrado 10 y pendiente -10.
  - Sin fecha → la fecha del pago es hoy.
- Commit `feat: register payments and show paid and pending amounts`.

### Task 4: Anular pagos

- `PaymentService.voidPayment(orderId, paymentId)`.
- `POST /api/sales/orders/{orderId}/payments/{paymentId}/void` → 200 con el resumen.
- Tests:
  - Anular un pago de 10 deja cobrado 0, el pago se ve con `VOIDED` y se puede volver a cobrar 17.
  - Anular dos veces → 409.
  - Un pago de otro pedido → 404.
- Commit `feat: void payments registered by mistake`.

### Task 5: Cierre

- Roadmap: actualizar el Paso 4 con los commits reales y las reglas.
- Postman: carpeta "6. Cobros" con este flujo:
  - Crear pedido.
  - Pago parcial.
  - Error de sobrecobro.
  - Anular.
  - Pagar el resto.
  - Resumen.
- Verificar con Newman contra Docker y borrar después los datos de prueba.
- Revisión final independiente, PR y asociarlo a la sesión.
