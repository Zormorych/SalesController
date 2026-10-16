# Paso 7 — Recepción y entrega · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recibir los lotes del fabricante (incluido el caso de camisetas que no llegan), entregar las camisetas a los clientes y mostrar el progreso de cada pedido.

**Architecture:**
- `sales` gana los estados `RECEIVED` y `DELIVERED`, dos operaciones nuevas en su API pública (`markLinesReceived` y `returnLinesToPending`), endpoints de entrega y un progreso calculado del pedido.
- `purchasing` gana la recepción del lote: estado `RECEIVED`, `receivedAt` y una tabla `factory_batch_missing_lines` con el **historial** de camisetas que no llegaron.
- Las camisetas que faltan dejan de pertenecer al lote, así quedan libres para otro lote.

**Spec:** [docs/ROADMAP.md](../../ROADMAP.md), "Paso 7". Test obligatorio pedido por el usuario: en un lote de 10 camisetas llegan 9 (el fabricante no avisa de la que falta).

## Global Constraints

- Sin borrado de datos de negocio: lo que no llega se registra.
- `ModularityTests` sin ciclos: `purchasing` → `sales`.
- Bloqueos como en los pasos 4 y 5: el lote se bloquea en `purchasing` y los pedidos en `sales`.
- Sin Postman: los endpoints se prueban con Swagger y curl.
- Rama `feature/receive-deliver` desde `origin/main`. Commit + push por tarea.

## Decisiones de diseño

- **Estados de línea:**
  - `PENDING` → `ORDERED` → `RECEIVED` → `DELIVERED`.
  - `ORDERED` → `PENDING` cuando la camiseta no llega.
  - `PENDING` → `CANCELLED`, como hasta ahora.
- **Cancelar un pedido:** se bloquea (409) si alguna camiseta no está `PENDING` ni `CANCELLED`, porque ya se ha fabricado.
- **Recibir:**
  - `POST /api/purchasing/batches/{id}/receive` con `{ "missingLineIds": [...] }`. Si `missingLineIds` es `null` o vacío, ha llegado todo.
  - El lote debe estar `SENT`:
    - `OPEN` → 409 "todavía no se ha enviado".
    - `RECEIVED` → 409 "ya se ha recibido".
    - `CANCELLED` → 409.
  - Cada id de `missingLineIds` debe ser una camiseta del lote (404 si no).
  - Las que llegan → `RECEIVED`. Las que faltan → `PENDING`: salen del lote y quedan en `missingLineIds` del lote.
- **Detalle del lote:** las camisetas del lote más `missingLines` (camisetas que faltaron, con su estado actual) y `receivedAt`. El envío y los totales se calculan solo con las camisetas del lote.
- **Entregar:**
  - `POST /api/sales/orders/{id}/lines/{lineId}/deliver`, solo si la camiseta está `RECEIVED`.
  - `POST /api/sales/orders/{id}/deliver`, que entrega todas las recibidas. Si no hay ninguna, 409.
- **Progreso** (`SalesOrderProgress`, calculado sobre las camisetas no canceladas):
  - `CANCELLED` si el pedido está cancelado.
  - `COMPLETED` si todas están entregadas.
  - `READY_TO_DELIVER` si todas están recibidas o entregadas.
  - `PENDING` si todas están pendientes.
  - `IN_PROGRESS` en cualquier otro caso.
  - Va en el detalle y en el listado, junto con `lineCounts {pending, ordered, received, delivered, cancelled}`.
- **Camiseta pedida que el cliente ya no quiere:** sigue bloqueado. Queda como decisión del usuario (stock o pérdida).

## Review Focus

1. **Lote de 10 con 9 recibidas** → 9 `RECEIVED`, 1 vuelve a pendientes de pedir, aparece en `missingLines` del lote y se puede meter en otro lote. Test en la Tarea 3.
2. **Entregar una camiseta no recibida** → 409. Test en la Tarea 2.
3. **Progreso** → refleja cada combinación (todo pendiente, mixto, listo para entregar, completado). Test en la Tarea 1.
4. **Recibir un lote no enviado o ya recibido** → 409. Test en la Tarea 3.
5. **Camiseta que no llega** → su pedido vuelve a permitir editarla, porque está `PENDING`. El envío del lote no se le asigna. Test en la Tarea 3.

---

### Task 1: Estados de recepción en `sales` y progreso del pedido

- `LineStatus` gana `RECEIVED` y `DELIVERED`.
- `SalesOrderLine` gana `markReceived()`, `returnToPending()` y `markDelivered()`.
- `SalesOrderApi` gana `markLinesReceived(Collection<Long>)` y `returnLinesToPending(Collection<Long>)`.
  - Ambas son `MANDATORY` y de todo o nada.
  - Las líneas deben estar `ORDERED`; si no, 409 "La línea N no está pedida al fabricante".
  - Comparten con `markLinesOrdered` el bloqueo y la recarga de los pedidos.
- `SalesOrder.progress()` y `lineCounts()`, expuestos en `SalesOrderView.Details` y `Summary`.
- La guarda de `cancel` pasa a "alguna camiseta no PENDING ni CANCELLED".
- Tests:
  - Unitario de progreso: las 5 combinaciones, y que lo cancelado no cuenta.
  - API de líneas: recibir y devolver a pendientes, con los casos de estado incorrecto.
- Commit `feat: track received shirts and order progress`.

### Task 2: Entregas

- `SalesOrderService.deliverLine(orderId, lineId)` y `deliverAll(orderId)`.
- Endpoints `POST …/lines/{lineId}/deliver` y `POST …/deliver`.
- Tests (API): entregar una recibida, entregar el pedido completo hasta `COMPLETED`, una no recibida → 409, una ya entregada → 409, un pedido sin recibidas → 409.
  - Los datos se preparan pasando por el lote con los servicios: crear lote → enviar → recibir.
- Commit `feat: deliver received shirts to customers`.

### Task 3: Recepción del lote con camisetas que no llegan

- `V7`: `factory_batches.received_at` y la tabla `factory_batch_missing_lines` (`factory_batch_id`, `sales_order_line_id`, PK compuesta, FKs).
- `FactoryBatchStatus.RECEIVED` y `FactoryBatch.markReceived(missingLineIds, when)`.
- `FactoryBatchService.receive(id, missingLineIds)` con bloqueo del lote.
- Endpoint y `ReceiveFactoryBatchRequest`.
- `FactoryBatchView`: `receivedAt` y `missingLines` en el detalle; `missingCount` en el resumen.
- Tests:
  - **El obligatorio: 10 camisetas, llegan 9.**
  - Recibir todo.
  - Lote `OPEN`, `RECEIVED` o `CANCELLED` → 409.
  - Id ajeno al lote → 404.
  - La que falta se puede editar en su pedido y meter en otro lote.
  - La que falta no recibe envío.
- Commit `feat: receive factory batches recording missing shirts`.

### Task 4: Cierre

- Roadmap.
- Prueba manual en Docker con curl: lote de 3, falta 1, entregar el resto.
- Revisión final, PR y asociarlo a la sesión.
