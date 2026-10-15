# Paso 5 — Lotes al fabricante · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Agrupar las camisetas pendientes de pedir en lotes al fabricante, con un coste de envío editable repartido entre ellas, y marcar las líneas como pedidas al enviar el lote.

**Architecture:**
- Nuevo módulo Modulith `purchasing` → `sales` (API pública) → `catalog`/`customers`.
- **`purchasing` guarda la pertenencia de cada línea a un lote** en `factory_batch_lines`, con `UNIQUE` por línea. `sales` no conoce los lotes.
- `sales` amplía `SalesOrderApi` con:
  - Líneas pendientes.
  - Consultar líneas por id (`OrderLineSnapshot`).
  - `markLinesOrdered`, que pasa las líneas a `ORDERED` (estado nuevo).
- `LineStatus` pasa a la API pública de `sales`, porque `purchasing` necesita leerlo.

**Spec:** [docs/ROADMAP.md](../../ROADMAP.md), secciones 2 (`purchasing` y reparto de envío) y "Paso 5".

## Global Constraints

- `BigDecimal` con escala 2. Mensajes en español con `MoneyFormat`. Sin Lombok.
- `ModularityTests` sin ciclos.
- Rama `feature/factory-batches` desde `origin/main`. Commit + push por tarea. El `.gitignore` local del usuario no se commitea.
- Al cerrar: colección de Postman + Newman contra Docker.

## Decisiones de diseño

- **Número de lote:** `FB-<año>-<secuencia de 4 dígitos>` (`factory_batch_number_seq`).
- **Estados:** `OPEN` → `SENT`, y `OPEN` → `CANCELLED`. `RECEIVED` llegará en el Paso 7.
- **Crear un lote:** `lineIds` (al menos uno), `shippingCost` (obligatorio, ≥ 0) y `notes`. Cada línea debe existir (404), estar `PENDING` en un pedido abierto (409) y no estar ya en otro lote (409).
- **Mientras el lote está `OPEN`:**
  - Se pueden añadir líneas y quitar líneas.
  - Se puede editar el envío y las notas. Esto se permite también en `SENT`, porque el coste real del envío suele conocerse después.
- **Enviar** (`OPEN` → `SENT`):
  - Exige al menos una línea.
  - Ninguna línea del lote puede estar cancelada; si la hay, 409 con aviso para quitarla.
  - Marca las líneas como `ORDERED` en `sales` y guarda `sentAt`.
- **Cancelar** (solo `OPEN`): el lote queda `CANCELLED` y **libera sus líneas**, que vuelven a salir como pendientes.
- **Líneas pendientes** = líneas `PENDING` de pedidos abiertos que no están en ningún lote. Se ordenan por fecha de pedido.
- **Reparto del envío:**
  - `shippingCost` ÷ líneas no canceladas del lote, por céntimos.
  - Los céntimos que sobran van a las primeras líneas, así que la suma siempre es exacta.
  - Si el lote no tiene líneas activas, no se reparte nada.
- **Detalle del lote:**
  - Por línea: pedido, cliente, producto, equipación, talla, personalizaciones, venta, coste, envío asignado y beneficio después del envío.
  - Totales: venta, coste, envío y beneficio.

## Review Focus

1. **Reparto de 4,00 € entre 3 camisetas** → 1,34 + 1,33 + 1,33. La suma es exactamente 4,00. Test en la Tarea 2.
2. **Línea ya en otro lote** → 409 y no se duplica. Test en la Tarea 3.
3. **Lote cancelado** → sus líneas vuelven a aparecer como pendientes. Test en la Tarea 4.
4. **Lote enviado** → sus líneas pasan a `ORDERED` y ya no se pueden editar en el pedido. Test en la Tarea 4.
5. **Línea cancelada dentro de un lote abierto** → el envío no se le asigna, y enviar el lote da 409 hasta quitarla. Test en la Tarea 4.

---

### Task 1: API de líneas en `sales`

- `LineStatus` pasa a `sales` (raíz) y gana `ORDERED`.
- `sales.OrderLineSnapshot` con:
  - `lineId`, `orderId`, `orderNumber`, `orderDate`, `customerId`, `customerName`.
  - `productId`, `productName`, `kitName`, `size` (`ClothingSize`), `status` (`LineStatus`).
  - `customizations` (`List<Customization(name, customText)>`), `totalSale` y `totalCost`.
- `SalesOrderApi`:
  - `List<OrderLineSnapshot> findPendingLines()`.
  - `List<OrderLineSnapshot> getLines(Collection<Long> lineIds)`: 404 "No existen las líneas …" si falta alguna.
  - `void markLinesOrdered(Collection<Long> lineIds)`: `MANDATORY`; 409 si alguna no está `PENDING` o su pedido está cancelado.
- Tests: `SalesOrderLinesApiTest` (`@SpringBootTest` + `@Transactional`), con servicios internos para crear los datos.

### Task 2: `ShippingAllocation`, entidad `FactoryBatch` y `V6`

- `purchasing.internal.ShippingAllocation.split(BigDecimal total, int parts)` → `List<BigDecimal>` por céntimos. Test unitario: 4,00/3, 4,00/4, 0/2, 0,01/3 y `parts = 0` (lista vacía).
- `FactoryBatch(batchNumber, shippingCost, notes)`, con:
  - `@ElementCollection Set<Long> lineIds`, en la tabla `factory_batch_lines`.
  - `status`, `sentAt`.
  - `addLines`, `removeLine`, `updateDetails`, `markSent`, `cancel`.
- `FactoryBatchRepository`:
  - `nextBatchSequence()`.
  - `findAllByOrderByIdDesc()`.
  - `@Query` con todos los ids de línea asignados a algún lote.
- `V6__create_factory_batches.sql`:
  - Secuencia `factory_batch_number_seq`.
  - Tabla `factory_batches`: `batch_number` único, `status`, `shipping_cost` NUMERIC(10,2) con CHECK ≥ 0, `notes`, `sent_at` y timestamps.
  - Tabla `factory_batch_lines`: PK (`factory_batch_id`, `sales_order_line_id`), **`UNIQUE` (`sales_order_line_id`)** y FKs a `factory_batches` y `sales_order_lines`.
- Test de repositorio:
  - Guardar y recargar conserva las líneas.
  - Dos lotes con la misma línea → `DataIntegrityViolationException` al hacer flush.

### Task 3: Crear y consultar lotes

- `GET /api/purchasing/pending-lines`.
- `POST /api/purchasing/batches` → 201.
- `GET /api/purchasing/batches` (resúmenes) y `GET /api/purchasing/batches/{id}` (detalle).
- `PUT /api/purchasing/batches/{id}` → envío y notas.
- Tests (`FactoryBatchApiTest`):
  - Lo pendiente excluye las líneas que ya están en un lote.
  - Crear un lote de 3 líneas con 4,00 € de envío da el reparto 1,34/1,33/1,33 y totales correctos.
  - Una línea ya en lote → 409.
  - Una línea inexistente → 404.
  - Una línea de un pedido cancelado → 409.
  - `lineIds` vacío → 400.
  - Envío negativo → 400.
  - Editar el envío recalcula el reparto.

### Task 4: Añadir y quitar líneas, enviar y cancelar

- `POST /api/purchasing/batches/{id}/lines` (`lineIds`).
- `POST /api/purchasing/batches/{id}/lines/{lineId}/remove`.
- `POST /api/purchasing/batches/{id}/send`.
- `POST /api/purchasing/batches/{id}/cancel`.
- Tests:
  - **Enviar:** el estado pasa a `SENT` con `sentAt`, las líneas quedan `ORDERED` en el pedido, editarlas da 409 y dejan de salir como pendientes.
  - Añadir o quitar líneas en un lote enviado → 409.
  - **Cancelar:** el estado pasa a `CANCELLED` y las líneas vuelven a pendientes.
  - Cancelar un lote enviado → 409.
  - **Línea cancelada en un lote abierto:** el envío se reparte entre las demás y enviar da 409 hasta quitarla.

### Task 5: Cierre

- Roadmap.
- Postman: carpeta "7. Compras - Lotes" + Newman contra Docker, y borrar después los datos de prueba.
- Revisión final, PR y asociarlo a la sesión.
