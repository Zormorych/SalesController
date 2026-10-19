# Stock y reventa · Diseño

## Para qué

Además de las camisetas que pide al fabricante para sus clientes, el usuario compra prendas sueltas (por ejemplo en Vinted) para revenderlas más caras. Hoy las apunta a mano. Quiere tenerlas en la app:

- La da de alta con lo que le costó.
- La anuncia a un precio.
- Al venderla apunta el precio final, que a menudo es más bajo porque le regatean.
- Quiere ver cuánto le ha sacado.

Ejemplo real: la compra a 11,70, la anuncia a 20,00 y la vende por 17,70. Beneficio 6,00.

Además se cierra una decisión pendiente del roadmap. Si un cliente ya no quiere una camiseta que ya se pidió al fabricante, **pasa a stock** en lugar de darse por perdida, y se revende igual que una prenda de Vinted.

En la API y en la documentación el apartado se llama **"Stock y reventa"**. En el código es el módulo `resale`.

## Objetivos

- Dar de alta prendas a mano con nombre, talla, coste, fecha de compra y precio anunciado.
- Cambiar el precio anunciado mientras no se venda, por ejemplo para rebajarla.
- Venderla con un precio final y una fecha. La app calcula el beneficio (precio final − coste).
- Ver el stock: lo disponible y lo vendido.
- Pasar a stock una camiseta recibida que el cliente ya no quiere, sin cobrársela al cliente.
- Ver la reventa en los informes:
  - Lo vendido en un periodo y su beneficio.
  - Cuánto dinero hay parado en stock.

## Fuera de alcance

- A quién se vende una prenda de stock. La venta de stock va aparte de los pedidos de clientes: no lleva cliente, ni cobros en varias veces, ni sale en el Excel del fabricante.
- Fotos de las prendas.
- Devoluciones de prendas ya entregadas o vendidas.
- Dar una prenda por perdida, regalarla o quedársela. Se podrá añadir más adelante.
- Deshacer el paso a stock de una camiseta de pedido. Si el cliente cambia de idea, se le vende como cualquier prenda de stock.

## Lo que no debe pasar

- Que el coste o el beneficio de una camiseta pasada a stock cuente dos veces: una en el pedido o el lote y otra en el stock.
- Que el cliente siga debiendo una camiseta pasada a stock cuando se ha decidido devolverle el dinero.

## Decisiones del usuario

- Una camiseta pedida que el cliente ya no quiere va a stock, no se da por perdida. En stock vale −coste ("me la como") hasta que se revenda.
- Al pasarla a stock se elige si el cliente la paga igualmente (lo normal, porque cobra antes de pedir) o si se le devuelve el dinero (caso puntual).
- Las prendas de stock se dan de alta a mano con su coste y se venden indicando el precio final.
- La venta de stock va aparte de los pedidos de clientes.
- Nombre del apartado: "Stock y reventa".

## Diseño

### Módulo `resale`

- Swagger: grupo "Stock y reventa".
- Ruta: `/api/resale/items`.
- Depende de `sales` (para pasar una camiseta a stock) y de `purchasing` (para conocer su parte del envío), solo a través de sus APIs públicas.
- `reports` pasa a depender también de `resale`.
- No crea ciclos entre módulos.

### Prenda de stock (`ResaleItem`)

| Campo | Notas |
|---|---|
| `name` | Obligatorio. "Chaqueta Nike vintage". |
| `size` | Texto libre y opcional: en Vinted hay tallas que no son de camiseta, como "42" o "W32". |
| `notes` | Opcional. |
| `origin` | `MANUAL` o `ORDER_LINE`. |
| `salesOrderLineId` | Solo si viene de un pedido. Único: una camiseta no puede pasar dos veces a stock. |
| `purchaseCost` | Obligatorio, ≥ 0. |
| `purchaseDate` | Por defecto, hoy. |
| `askingPrice` | Precio anunciado. Opcional, ≥ 0. |
| `status` | `AVAILABLE` o `SOLD`. |
| `salePrice`, `saleDate` | Se rellenan al vender. |

- Beneficio calculado = `salePrice − purchaseCost`. Solo existe si está vendida.
- Dinero en `BigDecimal` con escala 2, como en el resto de la app.
- Necesita una migración nueva, `V8`.

### Operaciones

- **Alta a mano:** `POST /api/resale/items`.
- **Editar** nombre, talla, notas, coste, fecha de compra y precio anunciado: `PUT /api/resale/items/{id}`.
  - Se puede en cualquier estado, para corregir errores.
  - No cambia el estado ni los datos de venta.
- **Vender:** `POST /api/resale/items/{id}/sell` con `salePrice` (obligatorio, ≥ 0) y `saleDate` (por defecto, hoy).
  - Si ya está vendida, 409.
- **Deshacer la venta:** `POST /api/resale/items/{id}/unsell`. Vuelve a `AVAILABLE` y borra los datos de venta. Sirve para corregir una venta apuntada por error.
- **Listar:** `GET /api/resale/items?status=`. Incluye el beneficio de las vendidas.
- **Detalle:** `GET /api/resale/items/{id}`.
- **Sin borrado físico** (regla general de la app). Un alta equivocada se corrige editándola.

### Pasar a stock una camiseta de un pedido

`POST /api/resale/items/from-order-line` con `lineId`, `customerPays` (por defecto `true`) y, opcionalmente, `askingPrice`.

- **Solo se aceptan camisetas `RECEIVED`.** Si está pedida pero aún no ha llegado, se espera a que llegue y entonces se pasa. Así no hay que tocar la recepción de lotes. Las de otros estados dan 409.
- La línea pasa a un estado nuevo, `MOVED_TO_STOCK`, y guarda cuál de las dos opciones se eligió.
- La prenda de stock se crea con:
  - Nombre: producto + equipación + personalizaciones. Por ejemplo "Camiseta Fan · Selección Española blanca · WILLIAMS JR 17".
  - Talla: la etiqueta de la talla.
  - Fecha de compra: hoy.
  - El coste depende de la opción (ver abajo).

**Dos opciones (decisión del usuario).** Casi siempre cobra antes de pedir y casi nunca devuelve el dinero.

| | `customerPays = true` (por defecto) | `customerPays = false` (caso puntual) |
|---|---|---|
| Significado | El cliente la paga igualmente; el usuario se queda la camiseta y el dinero. | Se le devuelve al cliente lo que hubiera pagado. |
| En el pedido | **Sigue contando como vendida**: suma en el total, en el resumen de ventas y en los totales del lote. Para el progreso cuenta como terminada, igual que una entregada. | Cuenta **igual que una cancelada**: no suma en el total, ni en el resumen, ni en los totales del lote, y no cuenta en el progreso. Lo cobrado de más aparece en "por devolver". |
| Coste de la prenda en stock | **0,00 €**: su coste ya está en la venta al cliente. Al revenderla, todo es beneficio extra. | Coste de la línea (con personalizaciones) **+ su parte del envío del lote** en ese momento. |
| Pedido sin camisetas activas | No aplica: la camiseta sigue siendo una venta. | El pedido **se cancela solo** en la misma operación (un cliente con una sola camiseta que ya no la quiere). Las líneas `MOVED_TO_STOCK` se quedan como están; no pasan a `CANCELLED`. |

- **Envío del lote:** el reparto **no cambia** en ningún caso. La camiseta viajó en el lote y sigue contando para repartir el envío, así que las demás no pagan más.
  - Con `customerPays = false`, su parte del envío pasa al coste de la prenda en stock y deja de contar en los totales del lote. El envío del informe de lotes puede quedar por debajo del envío del lote.
- **Nada cuenta dos veces:**
  - Con `true`, el coste se queda en la venta al cliente y la prenda vale 0.
  - Con `false`, el coste se va entero a la prenda.
- Todo en una transacción, con el pedido bloqueado como en el resto de cambios de estado de las líneas (`changeStatus` de `SalesOrderApiService`).

### Informes

- **`GET /api/reports/summary`** gana:
  - Un bloque `resale` con las prendas vendidas con **fecha de venta** en el periodo: número, venta, coste y beneficio.
  - Un `totalProfit` = beneficio de pedidos + beneficio de reventa.
  - Las camisetas pasadas a stock cuentan en la parte de pedidos solo con `customerPays = true`.
- **`GET /api/reports/pending`**:
  - Gana `itemsInStock` (prendas disponibles) y `stockValue` (suma de sus costes).
  - Una camiseta pasada a stock no cuenta como "por entregar".
- **`GET /api/reports/batches`**:
  - Las camisetas pasadas a stock con `customerPays = false` no cuentan, igual que las canceladas, pero sin cambiar el reparto del envío.
  - Con `true` cuentan como cualquier otra.

## A criterio de quien lo construya

- El nombre exacto de clases, endpoints secundarios y DTOs, siguiendo los patrones del proyecto.
- Cómo calcular en `FactoryBatchView` que una camiseta cuenta para repartir el envío pero no en los totales.
- La paginación del listado. Con el volumen actual no hace falta.
- Dónde aparece `MOVED_TO_STOCK` en el recuento de camisetas por estado del pedido (`lineCounts`). Sigue saliendo en el Excel de su lote, porque se pidió.
- El nombre de la prenda cuando una personalización no tiene texto (por ejemplo, un parche): se usa el nombre de la personalización.
- Lo cobrado por la reventa no entra en `collected` del resumen, que es lo cobrado de pedidos. Va en el bloque `resale`.
- Que pasar una camiseta a stock cambie el resumen de su mes es lo esperado: desde ese momento ya no es una venta a un cliente.

## Mis decisiones (revisables)

- Solo pasan a stock camisetas ya recibidas.
- Con `customerPays = false`, si un pedido se queda sin camisetas activas, se cancela solo.
- El coste en stock incluye la parte del envío y queda fijo en ese momento. Si luego cambia el envío del lote, se puede editar a mano.
- Se puede deshacer una venta. No se puede deshacer el paso a stock.
- La talla es texto libre.
- `totalProfit` en el resumen.
