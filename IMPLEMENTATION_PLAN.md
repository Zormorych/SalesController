# Implementation plan

Este documento resume el orden recomendado para construir el backend de forma limpia, fase por fase y con una rama por tarea.

## Enfoque general

No crear todas las entidades del sistema de golpe.

Trabajar siempre una funcionalidad completa de punta a punta:

```text
Entidad Java
Migracion SQL
Repository
Service
Controller REST
Tests
```

La base de datos no debe dejarse para el final. En cada fase que persista datos, crear la entidad JPA y la migracion Flyway correspondiente en la misma rama.

## Orden recomendado

```text
1. chore/project-setup
2. feature/product-crud
3. feature/order-crud
4. feature/order-search-status
5. feature/factory-excel-export
6. feature/order-item-status-flow
7. feature/dashboard-summary
```

## Fase 0: Project setup

Rama:

```text
chore/project-setup
```

Objetivo:

Preparar una base tecnica estable para que el backend arranque, conecte con base de datos y pueda probarse.

Tareas:

- Revisar `pom.xml`.
- Confirmar dependencias: Web, JPA, Validation, PostgreSQL y Flyway.
- Configurar `application.yaml`.
- Crear `application-dev.yaml`.
- Crear `application-test.yaml`.
- Crear `docker-compose.yml` para PostgreSQL.
- Crear endpoint `GET /api/health`.
- Crear test de contexto.
- Documentar arranque en `README.md`.

Validacion:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

## Fase 1: Product CRUD

Rama:

```text
feature/product-crud
```

Objetivo:

Tener productos persistidos y CRUD basico.

Carpetas:

```text
product/entity
product/repository
product/service
product/controller
```

Tareas:

- Crear `Product` entity.
- Crear migracion Flyway `V1__create_products.sql`.
- Crear `ProductRepository`.
- Crear `ProductService`.
- Crear `ProductController`.
- Validar nombre obligatorio.
- Validar precios mayores o iguales que cero.
- Usar `BigDecimal` para dinero.
- No borrar fisicamente productos: usar `active`.
- Anadir tests de service y controller.

Endpoints minimos:

```text
GET /api/products
GET /api/products/{id}
POST /api/products
PUT /api/products/{id}
PATCH /api/products/{id}/activate
PATCH /api/products/{id}/deactivate
```

## Fase 2: Order CRUD

Rama:

```text
feature/order-crud
```

Objetivo:

Crear pedidos con lineas de producto.

Entidades:

```text
SalesOrder
SalesOrderItem
```

Nota:

Aunque la carpeta se llame `order`, evitaria llamar a la entidad principal `Order` porque es un nombre demasiado generico. `SalesOrder` y `SalesOrderItem` son mas claros.

Tareas:

- Crear `SalesOrder`.
- Crear `SalesOrderItem`.
- Crear enums de estado.
- Crear migraciones Flyway.
- Relacionar `SalesOrderItem` con `Product`.
- Anadir `buyer` como `String` en `SalesOrderItem`.
- Anadir talla, personalizacion y cantidad.
- Anadir precio de compra unitario y precio de venta unitario.
- Copiar snapshot del nombre y referencia del producto.
- Calcular subtotal, coste y beneficio.
- Crear repositories.
- Crear services.
- Crear controllers.
- Anadir tests de calculo y validacion.

Endpoints minimos:

```text
POST /api/orders
GET /api/orders
GET /api/orders/{id}
POST /api/orders/{id}/items
PUT /api/orders/{id}/items/{itemId}
DELETE /api/orders/{id}/items/{itemId}
PATCH /api/orders/{id}/confirm
PATCH /api/orders/{id}/cancel
```

## Fase 3: Order search and status

Rama:

```text
feature/order-search-status
```

Objetivo:

Consultar pedidos y controlar su estado de forma comoda.

Tareas:

- Filtro por estado.
- Filtro por comprador.
- Filtro por fecha.
- Filtro por numero de pedido.
- Paginacion.
- Validar transiciones de estado.
- Impedir editar pedidos cancelados o entregados.
- Anadir tests de busqueda y estado.

## Fase 4: Factory Excel export

Rama:

```text
feature/factory-excel-export
```

Objetivo:

Generar el Excel que se enviara a fabrica.

Ubicacion inicial:

```text
order/service
order/controller
```

Si crece, anadir:

```text
order/export
```

Tareas:

- Listar lineas pendientes de fabrica.
- Agrupar por producto/modelo.
- Mantener detalle por comprador.
- Generar Excel `.xlsx`.
- Crear endpoint para descargar Excel.
- Marcar lineas como enviadas a fabrica.
- Evitar exportar dos veces una linea ya enviada.
- Anadir tests de agrupacion y exportacion.

Endpoints minimos:

```text
GET /api/orders/factory/pending-items
POST /api/orders/factory/export
```

## Fase 5: Order item status flow

Rama:

```text
feature/order-item-status-flow
```

Objetivo:

Controlar recibido y entregado.

Tareas:

- Marcar linea como recibida.
- Marcar linea como entregada.
- Impedir entregar si no esta recibida.
- Impedir recibir si no fue enviada a fabrica.
- Actualizar estado general del pedido si aplica.
- Anadir tests de transiciones.

## Fase 6: Dashboard summary

Rama:

```text
feature/dashboard-summary
```

Objetivo:

Crear un resumen simple del negocio.

Tareas:

- Total vendido.
- Coste total.
- Beneficio total.
- Pedidos del mes.
- Lineas pendientes de fabrica.
- Lineas pendientes de entrega.
- Anadir tests de calculo.

Endpoint minimo:

```text
GET /api/dashboard/summary
```

## Regla por cada rama

En cada tarea:

```text
1. Crear rama desde main.
2. Hacer una sola funcionalidad.
3. Anadir migracion si toca base de datos.
4. Anadir tests.
5. Ejecutar .\mvnw.cmd test.
6. Ejecutar .\mvnw.cmd verify si toca persistencia o configuracion.
7. Commit.
8. Push.
9. Pull Request contra main.
```

## Primer paso recomendado

Empezar por `feature/product-crud`.

Producto es la base del dominio. Si `Product` queda bien hecho de punta a punta, despues se puede repetir el mismo patron para pedidos.
