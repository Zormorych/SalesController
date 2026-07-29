# AGENTS.md — Gestor de pedidos de camisetas

## 1. Objetivo del proyecto

Construir una aplicación web para gestionar un pequeño negocio de venta de camisetas personalizadas.

Actualmente el negocio se gestiona con hojas de cálculo:

1. Se apunta cada venta con el comprador, modelo, talla, personalización, coste y precio de venta.
2. Se calcula manualmente la ganancia.
3. Para pedir las camisetas a la fábrica, se recopilan imágenes de los modelos y se escriben al lado las tallas y personalizaciones solicitadas.

La aplicación debe sustituir progresivamente ese proceso y permitir:

- Registrar el comprador como dato de cada línea de pedido.
- Registrar modelos de camisetas con una imagen.
- Crear pedidos con una o varias camisetas.
- Guardar talla, personalización, cantidad, precio de compra y precio de venta.
- Calcular automáticamente costes, ingresos y beneficio.
- Consultar qué camisetas están pendientes de pedir a la fábrica.
- Crear pedidos agrupados para la fábrica.
- Generar una vista limpia e imprimible con la imagen del modelo, talla, personalización y cantidad.
- Controlar el estado de cada camiseta hasta su entrega.
- Consultar un resumen de ventas y beneficios.

El proyecto debe construirse **fase por fase**. No se debe desarrollar todo de una sola vez.

---

## 2. Forma de trabajar de Codex

Antes de modificar código:

1. Lee este archivo completo.
2. Revisa el estado actual del repositorio.
3. Identifica la fase activa.
4. Explica brevemente qué vas a implementar.
5. No avances a otra fase sin una petición explícita del usuario.

Al terminar cada tarea:

1. Ejecuta los tests.
2. Ejecuta la compilación.
3. Corrige los errores encontrados.
4. Resume los archivos creados o modificados.
5. Explica cómo probar manualmente la funcionalidad.
6. Indica posibles decisiones pendientes.
7. Detente y espera la siguiente instrucción.

### Reglas importantes

- Implementa únicamente la fase solicitada.
- No generes funcionalidades futuras “por si acaso”.
- No cambies decisiones importantes de arquitectura sin explicarlo.
- No elimines código funcional sin justificarlo.
- No ocultes errores con bloques `try/catch` vacíos.
- No desactives tests para conseguir que la compilación pase.
- No uses valores monetarios de tipo `double` o `float`.
- Usa `BigDecimal` para precios, costes e importes.
- Usa `LocalDate` o `LocalDateTime` para fechas.
- El código, nombres de clases, métodos, variables y columnas estarán en inglés.
- Los textos visibles de la interfaz estarán en español.
- Evita abreviaturas poco claras.
- Prefiere código sencillo antes que una arquitectura excesivamente compleja.
- No uses Lombok.
- Añade comentarios solo cuando expliquen una decisión que no sea evidente.
- Usa Git con commits pequeños y descriptivos, pero no hagas commits sin autorización.
- No añadas autenticación durante el MVP salvo que se solicite expresamente.

### Git, ramas y Pull Requests

Todo cambio de código debe realizarse mediante una rama independiente y terminar en un Pull Request abierto para revisión del usuario.

#### Rama principal

- La rama principal del repositorio es `main`.
- Nunca modificar código directamente sobre `main`.
- Nunca hacer commits directamente sobre `main`.
- Nunca hacer `push` directo a `main`.
- Nunca hacer `force push`.
- Nunca fusionar un Pull Request.
- El usuario será siempre quien revise y fusione el Pull Request desde GitHub.

#### Comprobaciones antes de trabajar

Antes de modificar cualquier archivo:

1. Ejecutar `git status`.
2. Comprobar la rama actual con `git branch --show-current`.
3. Comprobar que existe el remoto `origin` con `git remote -v`.
4. Actualizar las referencias remotas con:

```bash
git fetch origin
```

5. Verificar que no hay cambios locales del usuario sin guardar.

Si existen cambios locales que no pertenecen a la tarea:

- No eliminarlos.
- No sobrescribirlos.
- No incluirlos en el commit.
- Informar al usuario antes de continuar si impiden trabajar de forma segura.

#### Creación de ramas

Cada tarea debe desarrollarse en una rama nueva creada desde `main`.

Antes de crearla:

```bash
git checkout main
git pull --ff-only origin main
```

Usar nombres en minúsculas, en inglés y con palabras separadas por guiones.

Convenciones:

```text
feature/<feature-name>
fix/<bug-name>
refactor/<refactor-name>
test/<test-name>
docs/<documentation-name>
chore/<maintenance-name>
```

Ejemplos:

```text
feature/create-product-model
feature/create-sales-order
fix/duplicate-factory-order-items
refactor/order-profit-calculation
```

Para una funcionalidad nueva:

```bash
git checkout -b feature/<feature-name>
```

Reglas:

- Una rama debe contener una sola feature o corrección claramente delimitada.
- No reutilizar una rama ya fusionada.
- No mezclar cambios de distintas fases.
- No incluir refactors no relacionados con la tarea.
- No cambiar el nombre de la rama durante la tarea salvo que sea estrictamente necesario.

#### Implementación

Antes de escribir código:

1. Leer completamente `AGENTS.md`.
2. Revisar el código existente relacionado con la tarea.
3. Explicar brevemente el plan.
4. Crear la rama correspondiente.
5. Implementar únicamente el alcance solicitado.

Durante la implementación:

- Mantener cambios pequeños y coherentes.
- No desarrollar funcionalidades de fases futuras.
- No modificar archivos no relacionados.
- Añadir o actualizar tests.
- No desactivar tests existentes.
- No ocultar errores para conseguir que la compilación pase.

#### Verificación

Antes de crear el commit, ejecutar como mínimo en Windows:

```powershell
.\mvnw.cmd test
```

Cuando la tarea afecte a integración, persistencia o configuración, ejecutar también:

```powershell
.\mvnw.cmd verify
```

No crear el Pull Request si:

- El proyecto no compila.
- Hay tests fallando.
- Hay errores conocidos sin documentar.
- La funcionalidad está incompleta.
- Se han incluido cambios ajenos a la tarea.

Si algún test no puede ejecutarse por una dependencia externa, explicar claramente:

- Qué test no se pudo ejecutar.
- Por qué no pudo ejecutarse.
- Qué se verificó de forma alternativa.
- Qué tendrá que comprobar el usuario.

#### Revisión antes del commit

Antes de crear el commit:

```bash
git status
git diff
git diff --staged
```

Comprobar que:

- Solo se incluyen archivos relacionados con la tarea.
- No se incluyen contraseñas, tokens ni archivos `.env`.
- No se incluyen carpetas generadas.
- No se incluyen datos personales reales.
- No se incluyen cambios accidentales del IDE.
- No se incluyen archivos binarios innecesarios.

#### Commits

Crear commits claros y pequeños.

Formato recomendado:

```text
<type>: <short description>
```

Tipos permitidos:

```text
feat
fix
refactor
test
docs
chore
```

Ejemplos:

```text
feat: add product model creation
fix: prevent duplicate factory order items
test: add profit calculation tests
docs: document local database setup
```

No usar mensajes genéricos como:

```text
changes
updates
fix
final
work done
```

No modificar commits ya publicados mediante `--amend` salvo autorización expresa del usuario.

#### Publicación de la rama

Cuando la implementación esté terminada y verificada:

```bash
git push -u origin <branch-name>
```

No publicar cambios incompletos salvo que el usuario lo solicite.

#### Creación del Pull Request

Después de publicar la rama, crear un Pull Request contra `main`:

```bash
gh pr create \
  --base main \
  --head <branch-name> \
  --title "<pull-request-title>" \
  --body "<pull-request-description>"
```

En PowerShell puede utilizarse en una sola línea:

```powershell
gh pr create --base main --head <branch-name> --title "<pull-request-title>" --body "<pull-request-description>"
```

El título debe ser breve y describir el resultado.

Ejemplos:

```text
Add product model management
Implement sales order profit calculation
```

El cuerpo del Pull Request debe contener:

```markdown
## Summary

Descripción breve del objetivo de la tarea.

## Changes

- Cambio principal realizado.
- Segundo cambio relevante.
- Tests añadidos o modificados.

## Validation

- `.\mvnw.cmd test`
- `.\mvnw.cmd verify`, cuando corresponda.
- Pruebas manuales realizadas.

## How to test

Pasos concretos para comprobar la funcionalidad.

## Notes

Decisiones relevantes, limitaciones o trabajo futuro que no forma parte de este PR.
```

#### Gestión del Pull Request

Después de abrir el Pull Request:

- Dejarlo abierto.
- No aprobarlo.
- No fusionarlo.
- No cerrarlo.
- No borrar la rama.
- No crear releases.
- No modificar `main`.
- Mostrar al usuario la URL del Pull Request.
- Esperar a que el usuario lo revise desde GitHub.

Si el usuario pide cambios después de revisar el PR:

1. Continuar trabajando sobre la misma rama.
2. Aplicar únicamente los cambios solicitados.
3. Ejecutar nuevamente los tests.
4. Crear un nuevo commit.
5. Hacer `push` a la misma rama.
6. No abrir otro Pull Request para la misma tarea.

El Pull Request existente debe actualizarse automáticamente con los nuevos commits.

#### Fallos al crear el Pull Request

Si no se puede crear el Pull Request por falta de autenticación, permisos, conexión o ausencia de GitHub CLI:

- No afirmar que el PR fue creado.
- Mostrar el error encontrado.
- Confirmar si la rama fue publicada correctamente.
- Proporcionar el comando exacto que queda por ejecutar.
- No fusionar ni modificar otras ramas para intentar evitar el problema.

#### Finalización de la tarea

Una tarea con cambios de código solo se considera terminada cuando:

- La implementación está completa.
- El proyecto compila.
- Los tests correspondientes pasan.
- Los cambios están en una rama distinta de `main`.
- Los commits están publicados en GitHub.
- Existe un Pull Request abierto contra `main`.
- Se ha proporcionado al usuario la URL del Pull Request.

La respuesta final debe seguir este formato:

```text
Rama:
Commits:
Implementado:
Tests ejecutados:
Resultado de los tests:
Pull Request:
Pendiente de revisión:
```

---

## 3. Stack tecnológico

### Backend

- Java 21.
- Spring Boot.
- Maven.
- Spring Web MVC.
- Spring Data JPA.
- Bean Validation.
- PostgreSQL.
- Flyway para migraciones.
- JUnit 5.
- Mockito.
- H2 únicamente para tests, si resulta conveniente.

### Frontend

El frontend será una aplicación Angular independiente que consumirá la API REST del backend.

- Angular.
- TypeScript.
- HTML5.
- CSS propio o una librería visual elegida explícitamente.
- Cliente HTTP contra endpoints REST del backend.

No usar inicialmente React o Vue. La prioridad es terminar una aplicación funcional con Angular y un backend Spring Boot sencillo, bien separado y fácil de probar.

### Entorno local

- PostgreSQL mediante `docker-compose.yml`.
- Maven Wrapper incluido.
- Configuración sensible mediante variables de entorno.
- No guardar contraseñas reales en Git.

---

## 4. Nombre y estructura inicial

Nombre provisional de la aplicación:

`sales-manager`

Paquete base:

`com.pedropalazon.sales_manager_backend`

Usar una estructura por funcionalidad:

```text
src/main/java/com/pedropalazon/sales_manager_backend
├── common
├── product
├── order
└── dashboard
```

Cada funcionalidad podrá contener, según sea necesario:

```text
entity
repository
service
controller
```

No crear carpetas vacías ni capas sin una necesidad real. Los DTOs o clases de petición/respuesta solo se añadirán cuando sean necesarios y, al principio, podrán vivir cerca del `controller` correspondiente antes de crear una carpeta específica.

Recursos del backend:

```text
src/main/resources
├── db/migration
├── application.yml
├── application-dev.yml
└── application-test.yml
```

Las imágenes subidas por el usuario no deben guardarse definitivamente dentro del classpath. Durante el desarrollo se utilizará una carpeta externa configurable, por ejemplo:

```text
./data/uploads
```

---

## 5. Alcance del MVP

El MVP es una aplicación para un único administrador y debe cubrir estas funciones:

### Catálogo de modelos

- Crear un modelo de camiseta.
- Editar un modelo.
- Desactivarlo sin borrarlo físicamente.
- Guardar nombre.
- Guardar referencia de fábrica opcional.
- Guardar descripción opcional.
- Guardar imagen.
- Guardar coste de compra predeterminado.
- Guardar precio de venta predeterminado.
- Ver listado de modelos activos e inactivos.

### Comprador en líneas de pedido

- No crear una entidad ni un módulo de clientes durante el MVP.
- Guardar el comprador como texto obligatorio en cada línea de pedido.
- Permitir filtrar pedidos o líneas por comprador.
- Mantener el comprador histórico aunque después se corrija el texto en nuevas líneas.

### Pedidos de clientes

- Crear un pedido.
- Añadir una o varias camisetas.
- Elegir modelo.
- Indicar comprador en cada línea.
- Indicar talla.
- Indicar personalización.
- Indicar cantidad.
- Copiar inicialmente los precios predeterminados del modelo.
- Permitir modificar el coste y el precio de venta dentro del pedido.
- Calcular el beneficio por línea.
- Calcular los totales del pedido.
- Editar un pedido mientras no esté entregado o cancelado.
- Cancelar sin borrar físicamente.
- Consultar su estado.

### Pedidos a fábrica

- Mostrar camisetas pendientes de pedir.
- Seleccionar camisetas.
- Crear un pedido a fábrica.
- Evitar incluir dos veces la misma línea.
- Mostrar una vista agrupada por modelo.
- Incluir imagen, referencia, talla, personalización y cantidad.
- Permitir imprimir la vista desde el navegador.
- Marcar el pedido como enviado.
- Marcar camisetas como recibidas.

### Entregas

- Marcar camisetas como entregadas al comprador.
- Mantener el historial de estados.
- Mostrar claramente qué falta por pedir, recibir o entregar.

### Resumen económico

- Total vendido.
- Coste total.
- Beneficio total.
- Datos del mes actual.
- Número de camisetas pendientes de pedir.
- Número de camisetas pendientes de entregar.

---

## 6. Modelo de dominio inicial

Este modelo puede ajustarse si se detecta una necesidad real, pero cualquier cambio importante debe explicarse antes de aplicarlo.

### ProductModel

Representa un modelo de camiseta disponible para vender.

```text
id
name
supplierReference
description
imagePath
defaultPurchasePrice
defaultSalePrice
createdAt
updatedAt
active
```

Reglas:

- `name` es obligatorio.
- Los precios no pueden ser negativos.
- La imagen es opcional al crear el modelo, pero debe mostrarse un marcador visual cuando no exista.
- Un modelo desactivado no aparecerá por defecto al crear nuevas ventas.
- Los pedidos antiguos deben conservar el nombre y los precios con los que fueron vendidos.

### SalesOrder

Representa el pedido realizado por un comprador.

```text
id
orderNumber
orderDate
status
notes
createdAt
updatedAt
```

Estados propuestos:

```java
DRAFT
CONFIRMED
IN_PROGRESS
COMPLETED
CANCELLED
```

Reglas:

- `orderNumber` debe ser único y legible.
- Formato sugerido: `SO-2026-000001`.
- Un pedido necesita al menos una línea para confirmarse.
- Un pedido cancelado no participa en ingresos ni beneficios.
- El estado general puede derivarse del estado de sus líneas cuando sea posible.

### SalesOrderItem

Representa una camiseta o grupo de camisetas iguales dentro de una venta.

```text
id
salesOrder
productModel
productNameSnapshot
supplierReferenceSnapshot
buyer
size
personalization
quantity
unitPurchasePrice
unitSalePrice
status
createdAt
updatedAt
```

Estados propuestos:

```java
PENDING_FACTORY
SENT_TO_FACTORY
RECEIVED
DELIVERED
CANCELLED
```

Reglas:

- `size` es obligatoria.
- `buyer` es obligatorio.
- `personalization` puede estar vacía.
- `quantity` debe ser mayor que cero.
- Los precios unitarios no pueden ser negativos.
- Al crear la línea, copiar el nombre y referencia del producto como snapshot.
- Beneficio unitario:

```text
unitSalePrice - unitPurchasePrice
```

- Beneficio total de la línea:

```text
(unitSalePrice - unitPurchasePrice) × quantity
```

No guardar el beneficio como columna salvo que exista una razón técnica justificada. Debe calcularse a partir de los precios.

### Preparación para fábrica

No crear un paquete `factory` durante el MVP. La preparación del pedido a fábrica y la exportación Excel vivirán dentro de `order` hasta que exista una razón clara para separarlas.

```text
order/export
```

La primera versión puede funcionar sin una entidad específica para pedidos a fábrica: las líneas de pedido tendrán estado y se podrán consultar las que estén pendientes de enviar.

Estados de línea relacionados con fábrica:

```java
PENDING_FACTORY
SENT_TO_FACTORY
RECEIVED
DELIVERED
```

Más adelante, si se necesita historial de cada envío a fábrica, se podrá añadir una entidad específica.

Reglas:

- Una línea no debe exportarse dos veces si ya fue marcada como enviada a fábrica.
- Al recibir las camisetas, actualizar el estado de la línea.
- No modificar automáticamente una línea entregada.

---

## 7. Tratamiento del dinero

Usar siempre:

```java
BigDecimal
```

Aplicar estas reglas:

- Escala de dos decimales para importes mostrados y persistidos.
- Redondeo `RoundingMode.HALF_UP` cuando sea necesario.
- Moneda de la aplicación: EUR.
- Interfaz con formato español, por ejemplo `19,95 €`.
- No utilizar `new BigDecimal(19.95)`.
- Usar `new BigDecimal("19.95")` en tests y datos de ejemplo.
- Los cálculos económicos deben probarse con tests unitarios.

---

## 8. API, peticiones y validaciones

El backend expondrá endpoints REST para que Angular cree, edite y consulte datos.

Usar clases de petición/respuesta separadas de las entidades JPA cuando la API no coincida exactamente con la entidad.

Ejemplo:

```text
CreateProductRequest
UpdateProductRequest
CreateOrderRequest
CreateOrderItemRequest
OrderResponse
```

Validaciones mínimas:

- Campos obligatorios.
- Longitudes máximas.
- Cantidad mayor que cero.
- Precios iguales o mayores que cero.
- Formato y tamaño de imagen.
- No aceptar IDs inexistentes.
- No aceptar modelos inactivos en nuevas líneas.
- No permitir transiciones de estado inválidas.
- Devolver errores de validación estructurados para que Angular pueda mostrarlos junto al campo correspondiente.

No confiar únicamente en validaciones del frontend. Toda regla importante debe validarse también en backend.

---

## 9. Diseño de la interfaz

La interfaz debe ser clara, sencilla y adaptable a móvil.

### Navegación principal

- Inicio.
- Ventas.
- Nueva venta.
- Modelos.
- Pedidos a fábrica.
- Informes.

### Dashboard

Mostrar tarjetas con:

- Ventas del mes.
- Costes del mes.
- Beneficio del mes.
- Camisetas pendientes de fábrica.
- Camisetas pendientes de entrega.

Mostrar también:

- Últimos pedidos.
- Acceso rápido a “Nueva venta”.
- Acceso rápido a “Preparar pedido a fábrica”.

### Listados

Todos los listados deben:

- Usar tablas o componentes claros y legibles.
- Ser legibles en móvil.
- Mostrar estados mediante badges.
- Tener estado vacío con un mensaje útil.
- Permitir abrir el detalle.
- Evitar acciones destructivas sin confirmación.

### Pantalla de venta

Debe permitir añadir varias líneas.

Para la primera versión, puede resolverse con cualquiera de estas opciones:

1. Crear el pedido y después añadir líneas desde su pantalla de detalle.
2. Crear el pedido y sus líneas desde una única pantalla Angular.

Elegir primero la opción más simple y fiable. No introducir lógica compleja en Angular antes de que el flujo básico funcione.

### Vista del pedido a fábrica

Debe estar optimizada para imprimir y compartir.

Cada modelo debe aparecer en un bloque con:

- Imagen grande.
- Nombre del modelo.
- Referencia de fábrica.
- Tabla con talla.
- Personalización.
- Cantidad.
- Número de pedido interno opcional.

Ejemplo visual aproximado:

```text
------------------------------------------------
[ IMAGEN ]  Camiseta Córdoba Local 2026
            Ref: COR-LOCAL-26

            Talla | Personalización | Cantidad
            M     | PEDRO 10        | 1
            L     | Sin personalizar| 2
            XL    | ALBA 7          | 1
------------------------------------------------
```

Crear estilos CSS específicos para impresión mediante:

```css
@media print
```

Durante la impresión se deben ocultar:

- Menú.
- Botones.
- Filtros.
- Elementos de navegación.

---

## 10. Persistencia y base de datos

Usar PostgreSQL en desarrollo.

Crear:

```text
docker-compose.yml
```

Con:

- Base de datos.
- Usuario.
- Contraseña de desarrollo.
- Volumen persistente.
- Puerto configurable.

Usar Flyway desde el inicio.

Convenciones:

- Tablas y columnas en `snake_case`.
- Claves primarias numéricas.
- Restricciones `NOT NULL` cuando proceda.
- Índices para números de pedido, estados y fechas.
- Restricciones únicas para números de pedido.
- No usar `ddl-auto=create` como mecanismo definitivo.
- En desarrollo podrá utilizarse `ddl-auto=validate`.

Perfiles sugeridos:

```text
application.yml
application-dev.yml
application-test.yml
```

No registrar contraseñas, tokens o datos personales completos en los logs.

---

## 11. Imágenes

Para el MVP:

- Permitir JPG, JPEG, PNG y WEBP.
- Establecer un tamaño máximo configurable.
- Generar un nombre de archivo seguro y único.
- No usar directamente el nombre enviado por el navegador.
- Evitar rutas relativas manipulables.
- Guardar únicamente la ruta en la base de datos.
- Servir las imágenes mediante un controlador o configuración segura.
- Eliminar una imagen antigua solo cuando la actualización haya terminado correctamente.
- Mostrar una imagen por defecto cuando no exista.

No guardar imágenes como `byte[]` en PostgreSQL durante el MVP.

---

## 12. Manejo de errores

Crear un manejo de errores coherente:

- Recurso no encontrado.
- Operación no permitida.
- Validación de negocio.
- Error al guardar una imagen.
- Error inesperado.

La interfaz debe mostrar mensajes comprensibles en español.

No mostrar al usuario:

- Stack traces.
- SQL.
- Nombres internos de clases.
- Rutas locales del servidor.

Registrar suficiente información técnica en backend para diagnosticar el problema.

---

## 13. Pruebas

Cada fase debe incluir pruebas proporcionales a su funcionalidad.

### Tests unitarios

Probar principalmente:

- Cálculo de beneficios.
- Cálculo de totales.
- Creación de números de pedido.
- Transiciones de estado.
- Validaciones de negocio.
- Agrupación de líneas para el pedido a fábrica.

### Tests de repositorio

Probar:

- Búsqueda por estado.
- Búsqueda por fechas.
- Búsqueda por comprador cuando se implemente.
- Líneas pendientes de fábrica.
- Prevención de duplicados.
- Totales o proyecciones, cuando se implementen.

### Tests API

Probar como mínimo:

- Endpoints de listado.
- Peticiones válidas.
- Peticiones inválidas.
- Respuestas correctas tras guardar.
- Respuestas de éxito y error.
- Acciones de cambio de estado.

### Reglas para los tests

- Usar nombres descriptivos.
- Seguir patrón `given_when_then` o nombres equivalentes claros.
- No depender del orden de ejecución.
- No compartir datos mutables entre tests.
- No usar la base de datos real del usuario.
- No reducir la cobertura eliminando tests que fallen.

Comandos que deben funcionar:

```bash
./mvnw test
./mvnw verify
```

En Windows:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

---

## 14. Seguridad y privacidad

Durante el MVP la aplicación será para uso privado de una sola persona.

Aun sin autenticación:

- Validar todas las entradas.
- Escapar contenido mostrado.
- Configurar CORS solo para los orígenes necesarios durante el desarrollo.
- Restringir las subidas de archivos.
- No exponer rutas del sistema.
- No almacenar datos innecesarios de compradores.
- No incluir datos reales en fixtures, tests o repositorio.

La autenticación con Spring Security será una fase posterior antes de publicar la aplicación en Internet.

---

## 15. Fases de implementación

## Fase 0 — Preparación y esqueleto

Objetivo:

Crear un proyecto que arranque correctamente y tenga una página inicial mínima.

Tareas:

- Crear proyecto Maven con Java 21.
- Añadir dependencias necesarias.
- Incluir Maven Wrapper.
- Crear estructura base.
- Configurar PostgreSQL con Docker Compose.
- Configurar Flyway.
- Crear migración inicial vacía o tabla de control si procede.
- Crear endpoint de salud o bienvenida para comprobar que el backend responde.
- Documentar cómo se conectará Angular al backend.
- Añadir una prueba de contexto.
- Crear `README.md` con instrucciones de arranque.
- Crear `.gitignore`.
- Crear `.env.example` si se usan variables de entorno.

Criterios de aceptación:

- PostgreSQL arranca con Docker.
- La aplicación arranca sin errores.
- Flyway ejecuta las migraciones.
- El endpoint de salud o bienvenida responde correctamente.
- `mvn test` pasa.
- El README explica cómo arrancarlo en Windows.

Detenerse al finalizar esta fase.

---

## Fase 1 — Catálogo de modelos

Objetivo:

Gestionar los modelos de camisetas que se venden.

Tareas:

- Crear entidad `ProductModel`.
- Crear migración.
- Crear repositorio.
- Crear servicio.
- Crear clases de petición/respuesta si son necesarias.
- Crear listado.
- Crear alta.
- Crear edición.
- Crear activación y desactivación.
- Añadir precios predeterminados.
- Añadir subida de imagen.
- Añadir validaciones.
- Añadir tests.

Criterios de aceptación:

- Se puede crear un modelo.
- Se puede subir una imagen válida.
- Se rechazan archivos no permitidos.
- Se puede editar.
- Se puede desactivar.
- Los precios se muestran como euros.
- Los tests pasan.

Detenerse al finalizar esta fase.

---

## Fase 2 — Ventas y líneas de pedido

Objetivo:

Registrar pedidos con líneas de producto y comprador como texto.

Tareas:

- Crear `SalesOrder`.
- Crear `SalesOrderItem`.
- Crear enums de estado.
- Crear migración.
- Crear repositorio.
- Crear servicio.
- Generar números de pedido.
- Crear pedido.
- Añadir líneas con comprador, modelo, talla, personalización, cantidad, coste y precio de venta.
- Editar líneas mientras sea válido.
- Eliminar líneas únicamente mientras sea válido.
- Confirmar pedido.
- Mostrar detalle.
- Mostrar coste, venta y beneficio.
- Añadir tests de cálculo y validación.

Criterios de aceptación:

- Se crea un pedido.
- Se añaden varias camisetas.
- Cada línea tiene comprador obligatorio.
- El coste y precio predeterminados se copian desde el modelo.
- Los precios pueden modificarse en la línea.
- El beneficio se calcula correctamente.
- No se puede confirmar un pedido vacío.
- Los tests pasan.

Detenerse al finalizar esta fase.

---

## Fase 3 — Listado y seguimiento de ventas

Objetivo:

Consultar pedidos y controlar su estado.

Tareas:

- Crear listado de pedidos.
- Añadir filtros por comprador, fecha, estado y número.
- Añadir paginación.
- Crear badges de estado.
- Permitir cancelar.
- Impedir cambios no válidos.
- Mostrar totales del pedido.
- Añadir tests.

Criterios de aceptación:

- Los filtros funcionan.
- La paginación funciona.
- Cancelar excluye el pedido de los totales económicos.
- No se modifican pedidos completados o cancelados.
- Los tests pasan.

Detenerse al finalizar esta fase.

---

## Fase 4 — Preparación y exportación a fábrica

Objetivo:

Sustituir el Excel manual enviado a fábrica.

Tareas:

- Listar líneas pendientes de enviar a fábrica.
- Permitir seleccionar líneas.
- Agrupar por modelo, talla y personalización cuando proceda.
- Mantener el detalle por comprador.
- Generar Excel `.xlsx` para fábrica.
- Marcar líneas exportadas como enviadas a fábrica.
- Evitar exportar dos veces una línea ya enviada.
- Añadir tests de agrupación y generación de datos del Excel.

Criterios de aceptación:

- Se ven todas las camisetas pendientes.
- El Excel contiene resumen para fábrica.
- El Excel conserva el detalle por comprador.
- Una línea enviada a fábrica no se exporta otra vez por defecto.
- Los tests pasan.

Detenerse al finalizar esta fase.

---

## Fase 5 — Recepción y entrega

Objetivo:

Controlar qué camisetas se han recibido y entregado.

Tareas:

- Marcar líneas como recibidas.
- Marcar líneas como entregadas.
- Impedir entregar una línea no recibida.
- Mostrar claramente qué falta por pedir, recibir o entregar.
- Actualizar estado general de la venta si procede.
- Añadir tests de transiciones.

Criterios de aceptación:

- Una línea enviada a fábrica puede marcarse como recibida.
- Una línea recibida puede marcarse como entregada.
- Una línea no recibida no puede entregarse.
- El estado general refleja el progreso.
- Los tests pasan.

Detenerse al finalizar esta fase.

## Fase 6 — Dashboard e informes

Objetivo:

Mostrar la situación actual del negocio.

Tareas:

- Ventas del mes.
- Costes del mes.
- Beneficio del mes.
- Unidades vendidas.
- Líneas pendientes de fábrica.
- Líneas pendientes de entrega.
- Filtros por rango de fechas.
- Excluir pedidos cancelados.
- Añadir tests de cálculos.

Criterios de aceptación:

- Los importes coinciden con los pedidos.
- Los cancelados no se contabilizan.
- Los datos pueden filtrarse por fechas.
- Los tests pasan.

Detenerse al finalizar esta fase.

---

## Fase 7 — Exportación y copias de seguridad

Objetivo:

Facilitar la extracción y protección de los datos.

Tareas posibles:

- Exportar ventas a CSV.
- Exportar líneas pendientes a CSV.
- Exportar resumen económico.
- Documentar copia de seguridad de PostgreSQL.
- Documentar restauración.
- Añadir tests de formato CSV.

El CSV debe usar una codificación y separadores compatibles con Excel en configuración española. Debe verificarse manualmente antes de cerrar la fase.

Detenerse al finalizar esta fase.

---

## Fase 8 — Preparación para publicación

No implementar hasta que el MVP funcione localmente.

Tareas posibles:

- Spring Security.
- Usuario administrador.
- Protección de rutas.
- Configuración de producción.
- Almacenamiento persistente de imágenes.
- HTTPS.
- Logs.
- Copias de seguridad automáticas.
- Dockerfile.
- Despliegue.
- Política de privacidad básica.

---

## 16. Funcionalidades fuera del MVP

No implementar durante las primeras fases:

- Aplicación móvil.
- React o Vue.
- Pagos online.
- Facturación fiscal.
- Integración automática con la fábrica.
- WhatsApp automático.
- Múltiples usuarios y permisos.
- Notificaciones push.
- Inteligencia artificial.
- Microservicios.
- Kafka.
- Kubernetes.
- Arquitectura hexagonal completa.
- Event sourcing.
- Eliminación física de datos históricos.

Estas ideas podrán revisarse después de terminar el MVP.

---

## 17. Datos de demostración

Solo cuando se solicite, crear datos ficticios como:

### Modelos

- Camiseta Córdoba Local 2026.
- Camiseta Córdoba Visitante 2026.
- Camiseta España Local 2026.

### Nombres de comprador

- Cliente Demo Uno.
- Cliente Demo Dos.

### Personalizaciones

- `PEDRO 10`.
- `ALBA 7`.
- `Sin personalizar`.

Nunca usar datos personales reales en el repositorio.

---

## 18. Definición de terminado

Una tarea no está terminada hasta que:

- El código compila.
- Los tests pasan.
- La funcionalidad puede probarse desde la interfaz.
- Las validaciones backend existen.
- Los errores se muestran de forma comprensible.
- No quedan secretos en el repositorio.
- Se actualizan las migraciones necesarias.
- Se actualiza el README cuando cambia el proceso de arranque.
- Codex resume cómo probarlo.
- No se ha avanzado a la fase siguiente.

---

## 19. Plantilla de respuesta esperada de Codex

Al empezar una fase:

```text
Fase actual:
Objetivo:
Archivos que revisaré:
Cambios previstos:
Riesgos o decisiones:
```

Al terminar:

```text
Implementado:
Archivos principales:
Tests ejecutados:
Resultado:
Cómo probarlo:
Decisiones tomadas:
Siguiente paso recomendado:
```

La respuesta debe ser concreta y educativa. Explicar las decisiones relevantes sin describir línea por línea todo el código generado.

---

## 20. Primeras instrucciones para ejecutar con Codex

### Primera orden

Usar esta orden cuando el repositorio todavía esté vacío:

```text
Lee AGENTS.md completo. Implementa únicamente la Fase 0. Antes de modificar archivos, revisa el directorio y dime el plan concreto. Después crea el proyecto, ejecuta los tests y verifica que arranca. No avances a la Fase 1.
```

### Segunda orden

Cuando la Fase 0 funcione:

```text
Lee AGENTS.md y revisa el código actual. Implementa únicamente la Fase 1, catálogo de modelos. Divide el trabajo en cambios pequeños, añade pruebas y no avances a la Fase 2.
```

### Orden para corregir errores

```text
Revisa el error actual sin avanzar de fase. Encuentra la causa raíz, aplica el cambio mínimo necesario, ejecuta los tests relacionados y explica qué estaba fallando.
```

### Orden para revisar antes de continuar

```text
Revisa la fase actual como si fuera una code review. Busca errores funcionales, validaciones ausentes, problemas de seguridad, código innecesariamente complejo y tests insuficientes. Corrige solo problemas de esta fase y vuelve a ejecutar los tests.
```

### Orden para continuar de fase

```text
La fase anterior está aprobada. Lee AGENTS.md, revisa el estado actual e implementa únicamente la siguiente fase. No desarrolles funcionalidades de fases posteriores.
```

---

## 21. Decisiones iniciales asumidas

Salvo que el usuario indique lo contrario:

- La aplicación se utilizará inicialmente en un ordenador personal.
- Habrá un único administrador.
- La moneda será el euro.
- Los compradores pueden pedir varias camisetas.
- Cada camiseta puede tener talla y personalización distintas.
- Una línea puede representar varias unidades únicamente cuando modelo, talla, personalización y precios sean iguales.
- Los modelos tendrán una imagen reutilizable en los pedidos a fábrica.
- El pedido a fábrica se compartirá mediante una página imprimible; PDF automático se deja para una mejora posterior.
- Los pedidos y líneas se cancelan, no se eliminan físicamente.
- El frontend inicial será Angular.
- PostgreSQL será la base de datos de desarrollo.
