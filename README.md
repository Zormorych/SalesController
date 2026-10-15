# SalesController

[![CI](https://github.com/Zormorych/SalesController/actions/workflows/ci.yml/badge.svg)](https://github.com/Zormorych/SalesController/actions/workflows/ci.yml)

Backend para gestionar la venta de camisetas: catálogo con precios, clientes, pedidos, cobros, lotes al fabricante (con su Excel) e informes de beneficio.

Arquitectura, modelo de dominio y pasos de desarrollo: [docs/ROADMAP.md](docs/ROADMAP.md).

## Arrancar con Docker (todo el proyecto, en cualquier equipo)

Solo hace falta **Git** y **Docker Desktop** arrancado. No hay que instalar Java, Maven ni PostgreSQL: Docker compila el backend y levanta la base de datos.

1. Descargar el código:

   ```powershell
   git clone https://github.com/Zormorych/SalesController.git
   cd SalesController
   ```

2. (Opcional) Cambiar puertos o contraseñas: copiar `.env.example` como `.env` y editarlo. Por ejemplo, `APP_PORT=8081` si el 8080 ya está ocupado.

   ```powershell
   Copy-Item .env.example .env
   ```

3. Construir y arrancar todo:

   ```powershell
   docker compose up -d --build
   ```

   La primera vez tarda unos minutos (descarga imágenes y dependencias). El backend espera a que PostgreSQL esté listo y Flyway crea las tablas.

4. Comprobar que responde: <http://localhost:8080/swagger-ui.html> (o el puerto de `APP_PORT`).

| Para… | Comando |
|---|---|
| Ver el estado (`healthy` = funcionando) | `docker compose ps` |
| Ver los logs del backend | `docker compose logs -f app` |
| Parar todo (los datos se conservan) | `docker compose down` |
| Actualizar a la última versión del código | `git pull` y después `docker compose up -d --build` |
| Borrar también los datos (¡irreversible!) | `docker compose down -v` |

### Pasar los datos de un equipo a otro

En el equipo de origen, con PostgreSQL arrancado, se hace una copia en `backups/backup.sql` (esta carpeta no se sube a git):

```powershell
New-Item -ItemType Directory -Force backups
docker compose exec -T postgres pg_dump -U sales_manager -d sales_manager --clean --if-exists -f /tmp/backup.sql
docker compose cp postgres:/tmp/backup.sql backups/backup.sql
```

En el equipo de destino, se copia `backups/backup.sql` a la misma carpeta del proyecto y se restaura con el backend parado:

```powershell
docker compose up -d postgres
docker compose cp backups/backup.sql postgres:/tmp/backup.sql
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U sales_manager -d sales_manager -f /tmp/backup.sql
docker compose up -d --build
```

La restauración **sustituye** los datos que hubiera en el destino. Si se han cambiado usuario o base de datos en `.env`, hay que usar esos valores en lugar de `sales_manager`.

## Desarrollar en local (IntelliJ + PostgreSQL en Docker)

Requisitos: JDK 21 (`java -version` debe mostrar 21) y Docker Desktop. No hace falta instalar Maven: se usa el wrapper `mvnw.cmd`.

1. Levantar solo PostgreSQL (el backend se ejecuta desde IntelliJ o con Maven):

   ```powershell
   docker compose up -d postgres
   ```

   Crea la base de datos `sales_manager` (usuario y contraseña `sales_manager`, solo para desarrollo) en el puerto 5432. Se pueden cambiar con las variables `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` y `POSTGRES_PORT`.

2. Arrancar la aplicación (usa el perfil `dev` por defecto):

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   Desde IntelliJ basta con ejecutar `SalesManagerBackendApplication`.

   Si el puerto 8080 está ocupado por otra aplicación, se puede cambiar con la variable `SERVER_PORT`:

   ```powershell
   $env:SERVER_PORT = '8081'; .\mvnw.cmd spring-boot:run
   ```

   En IntelliJ: *Run → Edit Configurations → Environment variables* → `SERVER_PORT=8081`.

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

## Integración continua

En cada push a cualquier rama GitHub Actions ejecuta el pipeline con JDK 21 (sus checks aparecen también en el PR de esa rama). También se puede lanzar a mano con **Run workflow** en la pestaña Actions. Va por etapas:

```text
Compilar ──┬── Tests ────────────────┬── Empaquetar (.jar)
           └── Arquitectura (módulos) ┴── Imagen Docker (comprueba que el Dockerfile construye)
```

Si una etapa falla, las siguientes no se ejecutan. El resultado se ve en la pestaña **Actions** del repositorio y en el propio PR. Cada ejecución deja como artefactos descargables los informes de tests (`test-reports`), los diagramas de módulos (`module-diagrams`) y el `.jar`.

Workflow: [.github/workflows/ci.yml](.github/workflows/ci.yml).

## Estructura

Cada paquete bajo `com.pedropalazon.sales_manager_backend` es un módulo de negocio (`shared`, `catalog`, `customers`, `sales`, …). Lo que está en la raíz del paquete es su API pública. Lo que está en subpaquetes es interno.
