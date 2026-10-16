# Paso 6 — Excel del fabricante · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Descargar un `.xlsx` por lote, listo para el fabricante. Lleva un bloque por camiseta, con un marco blanco arriba para pegar la captura y debajo la equipación, el tipo, la talla y la personalización.

**Architecture:**
- En el módulo `purchasing`, `FactoryBatchExcel` es una función pura: recibe las líneas (`OrderLineSnapshot`) y devuelve `byte[]`, generados con Apache POI (XSSF).
- `FactoryBatchService.excel(id)` valida el lote y devuelve `ExcelFile(fileName, content)`.
- `GET /api/purchasing/batches/{id}/excel` sirve el fichero como descarga.

**Spec:** [docs/ROADMAP.md](../../ROADMAP.md), "Paso 6". Lo que pidió el usuario:
- Solo nombre de la camiseta, talla, personalización (si la tiene) y el hueco para la foto **justo encima**.
- **Sin precios ni cálculos.**

## Global Constraints

- Nada de precios, costes, clientes ni números de pedido en el Excel.
- Textos en español. Sin Lombok.
- `ModularityTests` en verde.
- Rama `feature/factory-excel` desde `origin/main`. Commit + push por tarea.
- Al cerrar: Postman + Newman contra Docker, y un fichero de ejemplo para que el usuario lo vea.

## Decisiones de diseño

- **Cabecera** (fila 1): "Pedido al fabricante FB-…", con la fecha y el número de camisetas.
- **Bloques:**
  - 3 por fila, cada uno en su propia columna (ancho ≈ 40 caracteres), con una columna estrecha de separación entre bloques.
  - Arriba, un **marco** de 12 filas combinadas: fondo blanco, borde grueso y el texto "Pega aquí la foto" en gris claro.
  - Debajo, 4 filas: `Equipación`, `Tipo`, `Talla` y `Personalización`.
  - Después, una fila vacía antes de la siguiente fila de bloques.
- **Personalización:** cada una como "Nombre: texto" si tiene texto, o solo el nombre ("Parche"). Si hay varias, se unen con " + ". Si no hay ninguna, "Sin personalizar".
- **Orden:** por equipación, tipo de producto y orden de talla (S…XXL y luego niño), para que las camisetas iguales queden juntas.
- **Página:** apaisada, ajustada a 1 página de ancho y márgenes estrechos.
- **Qué se exporta y cuándo:**
  - Solo las camisetas no canceladas.
  - Lote `CANCELLED` → 409. Lote sin camisetas activas → 409.
  - Se puede generar con el lote `OPEN` o `SENT`.
- **Nombre del fichero:** `<batchNumber>.xlsx`.

## Review Focus

1. **Ningún precio o importe** aparece en ninguna celda (venta, coste, "€"). Test en la Tarea 1.
2. **Camiseta sin personalización** → "Sin personalizar", no una celda vacía. Test en la Tarea 1.
3. **Más de 3 camisetas** → pasan a la siguiente fila de bloques sin solaparse. Test en la Tarea 1.
4. **Camiseta cancelada dentro del lote** → no aparece en el Excel. Test en la Tarea 2.
5. **Nombres con "ñ" y acentos** → se ven bien en el `.xlsx`. Test en la Tarea 1.

---

### Task 1: Generador `FactoryBatchExcel` + dependencia POI

- `pom.xml`: `org.apache.poi:poi-ooxml` 5.5.1, con la propiedad `poi.version`.
- `FactoryBatchExcel.generate(String batchNumber, LocalDate date, List<OrderLineSnapshot> lines)` → `byte[]`.
- Tests (`FactoryBatchExcelTest`, unitario), leyendo el resultado con POI:
  - La cabecera contiene el número de lote y "4 camisetas".
  - Por cada camiseta hay un bloque con sus 4 textos y el marco encima, con borde y región combinada de 12 filas.
  - Las camisetas están ordenadas por equipación y talla.
  - La 4ª camiseta va a la segunda fila de bloques.
  - Personalización: "Nombre + número: WILLIAMS JR 17 + Parche" y "Sin personalizar".
  - Ninguna celda contiene "€" ni importes.
  - "Selección España niño" con ñ se conserva.
  - La hoja está en horizontal y ajustada a 1 página de ancho.
- Commit `feat: generate the factory batch excel`.

### Task 2: Endpoint de descarga

- `FactoryBatchService.excel(Long id)` → `ExcelFile(String fileName, byte[] content)`.
- `GET /api/purchasing/batches/{id}/excel`:
  - `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
  - `Content-Disposition: attachment; filename="FB-….xlsx"`.
- Tests (API):
  - 200 con las cabeceras correctas, y el contenido se abre con POI y contiene la equipación.
  - Una línea cancelada no sale.
  - Un lote descartado → 409.
  - Un lote inexistente → 404.
- Commit `feat: add excel download endpoint for factory batches`.

### Task 3: Cierre

- Roadmap.
- Postman: petición "Descargar Excel del lote" en la carpeta 7.
- Newman contra Docker.
- Generar un Excel de ejemplo con datos de demostración y enviárselo al usuario.
- Revisión final, PR y asociarlo a la sesión.
