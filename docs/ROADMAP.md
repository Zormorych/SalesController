# Roadmap — SalesController

Backend (Spring Boot 4.1, Java 21, PostgreSQL) para gestionar la venta de camisetas y otros productos deportivos, organizado como un **ERP modular**: funcionalidades parecidas en el mismo módulo, funcionalidades distintas en módulos distintos.

Objetivos:

1. **Práctico:** dejar de montar a mano el Excel del pedido al fabricante y llevar control de clientes, cobros, costes y beneficio.
2. **Aprender:** arquitectura modular, API REST, tests y, más adelante, frontend separado, seguridad y despliegue.

---

## 1. Arquitectura

### Monolito modular con Spring Modulith

- Un solo proyecto Maven y un solo `.jar`.
- Cada **módulo de negocio es un paquete directo** bajo `com.pedropalazon.sales_manager_backend`.
- Lo que está en la raíz del paquete del módulo es su **API pública**. Lo que está en subpaquetes (`internal`, etc.) es privado.
- Un test (`ModularityTests`) verifica con Spring Modulith que ningún módulo accede a las clases internas de otro ni hay dependencias circulares. Si se rompe un límite, el build falla.
- Los módulos se comunican **llamando a la API pública** del otro módulo o **publicando eventos** de Spring cuando el que emite no debe conocer al que reacciona.

### Módulos

| Módulo (paquete) | Responsabilidad | Depende de |
|---|---|---|
| `shared` | Errores HTTP comunes, utilidades de dinero, configuración (OpenAPI, CORS). Más adelante, seguridad. | — |
| `catalog` | Productos (precio de venta y coste) y personalizaciones (precio y coste). Tallas. | `shared` |
| `customers` | Clientes: nombre, teléfono de WhatsApp, notas. | `shared` |
| `sales` | Pedidos de cliente, líneas, personalizaciones de línea, estados, totales y beneficio. | `catalog`, `customers` |
| `payments` | Cobros (pagos parciales) de un pedido: pagado y pendiente. | `sales` |
| `purchasing` | Lotes al fabricante: agrupar líneas pendientes, generar el Excel, recepción. | `sales` |
| `reports` | Informes de solo lectura: ventas, costes y beneficio por periodo, pedido o lote. | `sales`, `payments`, `purchasing` |

### Estructura interna de cada módulo

```text
sales/
├── SalesOrderApi.java          ← API pública (interfaz o servicio) que usan otros módulos
├── SalesOrderLineSummary.java  ← DTOs públicos que expone a otros módulos
└── internal/
    ├── SalesOrder.java         ← entidades JPA
    ├── SalesOrderRepository.java
    ├── SalesOrderService.java
    └── web/
        ├── SalesOrderController.java
        └── CreateSalesOrderRequest.java
```

Solo se crean las clases que hagan falta: nada de carpetas vacías.

### Reglas transversales

- **Dinero:** siempre `BigDecimal` (escala 2, `RoundingMode.HALF_UP`). Nunca `double` ni `float`.
- **Fechas:** `LocalDate` y `LocalDateTime`.
- **Código en inglés** (clases, tablas, columnas). Documentación y mensajes de la API en español.
- **Snapshots:** al añadir un producto o una personalización a una línea se **copian** su nombre, precio y coste. Cambiar precios en el catálogo no altera los pedidos antiguos.
- **Precios configurables:** ningún precio queda en el código. Todo se crea y edita desde la API.
- **Sin borrado físico** de datos de negocio: se desactiva o se cancela.
- **Migraciones:** Flyway, una por cambio de esquema (`V1__...`, `V2__...`). Hibernate en `validate` o `none`.
- **Errores:** respuestas `ProblemDetail` (RFC 9457) con los errores de validación por campo, para que el frontend pueda mostrarlos.
- **Tests:** unitarios para los cálculos y las transiciones de estado, `@DataJpaTest` para las consultas, `@WebMvcTest` para la API y `ModularityTests` para los límites entre módulos.

---

## 2. Modelo de dominio

```text
catalog
  Product           id, name, salePrice, purchaseCost, active
                    ej.: "Camiseta Fan" 15,00 / 6,50 · "Camiseta Player" 17,00 / ? · "Chándal" 50,00 / 34,00
  Customization     id, name, salePrice, purchaseCost, active
                    ej.: "Nombre + número" 2,00 / ? · "Parche" 1,00 / ?
  ClothingSize (enum) S, M, L, XL, XXL, KIDS_16 … KIDS_28   (común a todos los productos; no se llama
                    Size para no chocar con la anotación de validación @Size)

customers
  Customer          id, name, phone (normalizado y único, opcional), notes

sales
  SalesOrder        id, orderNumber (SO-2026-000001), customer, orderDate, status, notes
  SalesOrderLine    id, order, productId, productName*, kitName (texto libre: "Selección Española blanca 2 estrellas"),
                    size, unitSalePrice*, unitPurchaseCost*, status
  LineCustomization id, line, customizationId, customizationName*, value ("WILLIAMS JR 17"),
                    salePrice*, purchaseCost*
  (* = snapshot copiado del catálogo y editable: descuento, amigo → 0 €)

payments
  Payment           id, salesOrderId, amount, paymentDate, status (REGISTERED, VOIDED), notes

purchasing
  FactoryBatch      id, batchNumber, createdAt, sentAt, status (OPEN, SENT, CANCELLED; RECEIVED en el Paso 7), shippingCost, notes
  (cada SalesOrderLine pertenece como mucho a un lote)
  (shippingCost es editable; el valor por defecto, normalmente 4,00 €, lo propone el frontend, no el backend)
```

**Estado de una línea:** `PENDING` → `ORDERED` (está en un lote enviado, desde el Paso 5) → `RECEIVED` → `DELIVERED`. Solo una línea `PENDING` se puede cancelar: una camiseta ya pedida al fabricante se fabrica y se paga igual, así que ni ella ni su pedido se pueden cancelar (qué hacer con ella se decide en el Paso 7).

**Estado del pedido:** se deriva del estado de sus líneas, salvo `CANCELLED`, que es explícito. Hasta el Paso 7 el pedido solo distingue `OPEN` y `CANCELLED`, y las líneas `PENDING`, `ORDERED` y `CANCELLED`.

**Cálculos** (no se guardan en BD, se calculan):

- Venta de la línea = `unitSalePrice` + Σ `salePrice` de sus personalizaciones.
- Coste de la línea = `unitPurchaseCost` + Σ `purchaseCost` de sus personalizaciones.
- Beneficio de la línea = venta − coste.
- Envío repartido = `shippingCost` del lote ÷ número de líneas no canceladas del lote, a partes iguales por camiseta. Solo se aplica a las líneas que están en un lote.
- Beneficio real de la línea = beneficio de la línea − envío repartido.
- Total del pedido = Σ de las líneas no canceladas. Pendiente de cobro = total − Σ pagos.

---

## 3. Forma de trabajar

- Cada paso va en **su propia rama**, creada desde `main`, con **commits pequeños**: uno por cada punto de la lista de commits del paso.
- Formato de commit: `<tipo>: <descripción>` con tipo `feat`, `fix`, `refactor`, `test`, `docs` o `chore`.
- Antes de cada commit: `.\mvnw.cmd test` en verde.
- Al terminar un paso: push de la rama → Pull Request a `main` → revisión → merge. Después, siguiente paso.
- Claude no hace commit ni push salvo que se le pida expresamente.

---

## 4. Pasos

### Paso 0 — Base modular · rama `chore/modular-base`

Partimos del trabajo ya hecho en `feature/product-crud`: docker-compose, perfiles `dev` y `test`, H2 para tests y `HealthController`.

Commits:

1. `chore: remove previous plan files and add roadmap` — borra `AGENTS.md` e `IMPLEMENTATION_PLAN.md` y añade este documento.
2. `chore: add spring modulith and modularity test` — dependencias de Spring Modulith y `ModularityTests`.
3. `refactor: move health endpoint to shared module` — `HealthController` pasa a `shared`.
4. `feat: add global error handling with problem details` — `@RestControllerAdvice` con 404, 400 de validación, 409 de regla de negocio y 500 genérico.
5. `chore: add openapi documentation` — springdoc y Swagger UI en `/swagger-ui.html`.
6. `docs: add readme with local setup` — cómo arrancar PostgreSQL con Docker y la app con el perfil `dev` en Windows.

Hecho cuando: la app arranca contra PostgreSQL, Swagger responde y pasan `ModularityTests` y el resto de tests.

### Paso 1 — Catálogo · rama `feature/catalog`

Commits:

1. `refactor: reshape product as catalog item with prices` — `Product` con `name`, `salePrice`, `purchaseCost` y `active`, migración `V1`, repositorio. Se eliminan `teamName`, `ProductType` e `imagePath`: la equipación va como texto en la línea y la app no guarda imágenes.
2. `feat: add product REST endpoints` — `/api/catalog/products`: listar (activos o todos), ver, crear, editar, activar y desactivar. Nombre único sin distinguir mayúsculas. Precios con 2 decimales como máximo.
3. `feat: add customization catalog` — lo mismo en `/api/catalog/customizations`.
4. `feat: add size catalog` — enum `ClothingSize` y `GET /api/catalog/sizes`.

### Paso 2 — Clientes · rama `feature/customers`

1. `feat: add customer entity and migration` — `Customer` (nombre, teléfono, notas), migración `V3`, repositorio con búsqueda.
2. `feat: add customer REST endpoints` — `/api/customers`: listar (con `?search=` por nombre o por dígitos del teléfono), ver, crear y editar. Teléfono guardado sin separadores y único: "+34 600-123-456" y "+34600123456" son el mismo. No se borran clientes.

### Paso 3 — Pedidos de cliente · rama `feature/sales-orders`

1. `feat: expose catalog and customer lookups to other modules` — APIs públicas `CatalogApi` y `CustomerApi`: `sales` no toca clases internas de otros módulos.
2. `feat: add sales order and line entities with migration` — `SalesOrder` → `SalesOrderLine` → `LineCustomization`, migración `V4`, número `SO-<año>-<secuencia>` con una secuencia de BD, totales calculados.
3. `feat: create orders copying catalog prices with manual overrides` — `POST/GET /api/sales/orders`. Los precios se copian del catálogo; si llegan en la petición (incluido 0) se usan esos.
4. `feat: list, edit and cancel orders and lines` — listado (`?customerId=`), editar cabecera, añadir/sustituir/cancelar líneas y cancelar el pedido. Solo pedidos abiertos y líneas `PENDING`; no se puede cancelar la última línea (se cancela el pedido). Un producto o personalización desactivados no se pueden añadir, pero una línea que ya los tenía se puede seguir editando (por ejemplo, para cambiar la talla).

### Paso 4 — Cobros · rama `feature/payments`

1. `feat: expose sales order totals to other modules` — API pública `SalesOrderApi`: cobros consulta número, estado y total del pedido sin tocar el interior de ventas.
2. `feat: add payment entity and migration` — `Payment`, migración `V5`, `shared.MoneyFormat` para importes en mensajes ("17,00 €").
3. `feat: register payments and show paid and pending amounts` — `GET/POST /api/sales/orders/{id}/payments`: cobrado y pendiente. Importe > 0; no se cobra más de lo pendiente ni en pedidos cancelados. Si se cancela un pedido ya cobrado, el pendiente sale negativo (a devolver).
4. `feat: void payments registered by mistake` — `POST …/payments/{paymentId}/void`: el pago queda anulado (`VOIDED`) y deja de sumar.

Lo cobrado se sirve desde el módulo de cobros y no en el detalle del pedido: `sales` no puede depender de `payments` (sería un ciclo entre módulos).

### Paso 5 — Lotes al fabricante · rama `feature/factory-batches`

1. `feat: expose order lines to other modules and allow marking them as ordered` — `SalesOrderApi` gana líneas pendientes, consulta de líneas y "marcar como pedidas" (`ORDERED`); `LineStatus` pasa a ser API pública.
2. `feat: add factory batch entity and migration` — `FactoryBatch` (número `FB-<año>-<secuencia>`), migración `V6` con `UNIQUE` por camiseta, reparto del envío por céntimos (4,00 € entre 3 → 1,34 + 1,33 + 1,33).
3. `feat: create factory batches from pending lines with shipping cost` — `/api/purchasing/pending-lines` y `/api/purchasing/batches`: crear, listar, ver (venta, coste, envío repartido y beneficio por camiseta y del lote) y corregir el envío.
4. `feat: add, remove, send and cancel factory batch lines` — añadir/quitar camisetas mientras está abierto, enviar (las camisetas pasan a `ORDERED` y ya no se editan) y descartar (libera sus camisetas).

### Paso 6 — Excel del fabricante · rama `feature/factory-excel`

1. `feat: generate the factory batch excel` — Apache POI. Un bloque por camiseta, 3 por fila: **arriba un marco blanco** de 12 filas ("Pega aquí la foto") y **justo debajo** equipación, tipo de producto, talla y personalización (`Nombre + número "WILLIAMS JR 17"; Parche` o "Sin personalizar"). Mucho espacio entre camisetas (columnas de 15 caracteres y 5 filas entre filas de bloques). Camisetas iguales juntas (por equipación, tipo y talla). Apaisado al 85 %, con un salto de página por cada fila de 3 bloques para que ningún marco se separe de sus datos.
   El Excel **no lleva precios, costes, clientes ni cálculos**. Eso va en los informes (Paso 8).
2. `feat: add excel download endpoint for factory batches` — `GET /api/purchasing/batches/{id}/excel` descarga `FB-….xlsx`. Con el lote abierto o enviado; sin las camisetas canceladas; un lote descartado da 409.

### Paso 7 — Recepción y entrega · rama `feature/receive-deliver`

1. `feat: mark batch as received` — al recibir un lote se marca **cada camiseta**: recibida o **no llegada**. Puede faltar alguna sin que el fabricante avise (por ejemplo, 1 de 10). Las recibidas pasan a `RECEIVED`; las no llegadas no se dan por recibidas y vuelven a quedar pendientes de pedir, para reclamarlas o meterlas en el siguiente lote, sin perder el registro de en qué lote faltaron. El lote queda recibido aunque falte alguna.
   - Test obligatorio: lote de 10 camisetas en el que llegan 9 → 9 `RECEIVED`, 1 vuelve a pendientes y aparece como no llegada en el detalle del lote.
2. `feat: mark lines as delivered`
3. `feat: derive order status from its lines`
4. `feat: decide what happens to an ordered shirt the customer no longer wants` — hoy está bloqueado (no se puede cancelar una camiseta ya pedida); opciones: pasarla a stock o darla por perdida manteniendo su coste.
5. `test: cover status transitions`

### Paso 8 — Informes · rama `feature/reports`

1. `feat: sales, cost and profit summary by date range` — el coste incluye el envío repartido.
2. `feat: profit by batch` — incluye el envío del lote.
3. `feat: pending counts (to order, to receive, to deliver, to collect)`
4. `test: cover report calculations`

---

## 5. Después del MVP

- **Catálogo de equipaciones:** guardar "Selección Española blanca 2 estrellas" para elegirla de una lista en vez de escribirla.
- **Frontend Angular** en un repositorio aparte, consumiendo esta API.
- **Seguridad:** Spring Security con login y JWT.
- **Despliegue:** Dockerfile, perfil `prod`, front y back desplegados, HTTPS y copias de seguridad.

---

## 6. Decisiones

- [x] Spring Modulith con los módulos de la sección 1.
- [x] Envío por lote: importe editable que se reparte a partes iguales entre las camisetas no canceladas del lote. El valor por defecto (normalmente 4,00 €) lo pone el frontend.
- [ ] Coste real de "Camiseta Player" y de cada personalización. Es un dato del catálogo que se mete desde la API, así que no bloquea.
