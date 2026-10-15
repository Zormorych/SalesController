# SalesController

Backend para gestionar la venta de camisetas: catálogo con precios, clientes, pedidos, cobros, lotes al fabricante (con su Excel) e informes de beneficio.

Arquitectura, modelo de dominio y pasos de desarrollo: [docs/ROADMAP.md](docs/ROADMAP.md).

## Requisitos

- JDK 21 (`java -version` debe mostrar 21).
- Docker Desktop arrancado.
- No hace falta instalar Maven: se usa el wrapper `mvnw.cmd`.

## Arrancar en local (Windows, PowerShell)

1. Levantar PostgreSQL:

   ```powershell
   docker compose up -d
   ```

   Crea la base de datos `sales_manager` (usuario y contraseña `sales_manager`, solo para desarrollo) en el puerto 5432. Se pueden cambiar con las variables `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` y `POSTGRES_PORT`.

2. Arrancar la aplicación (usa el perfil `dev` por defecto):

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   Desde IntelliJ basta con ejecutar `SalesManagerBackendApplication`.

3. Comprobar que responde:

   - Health: <http://localhost:8080/api/health>
   - Swagger UI: <http://localhost:8080/swagger-ui.html>

4. Parar PostgreSQL (los datos se conservan en el volumen `postgres_data`):

   ```powershell
   docker compose down
   ```

## Tests

```powershell
.\mvnw.cmd test
```

Los tests usan H2 en memoria (perfil `test`) y no necesitan Docker.

`ModularityTests` comprueba que ningún módulo usa clases internas de otro y genera diagramas de los módulos en `target/spring-modulith-docs`.

## Estructura

Cada paquete bajo `com.pedropalazon.sales_manager_backend` es un módulo de negocio (`shared`, `catalog`, `customers`, `sales`, …). Lo que está en la raíz del paquete es su API pública. Lo que está en subpaquetes es interno.
