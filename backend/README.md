# PeachHacks Backend

Spring Boot 4 (Java 21, Maven) service for PeachHacks, served at `api.peachhacks.com`. It currently only establishes the PostgreSQL connection; no schema is created or managed (`ddl-auto: none`). The public site (`web/`) and the upcoming participant platform (`platform/`) will call it.

## Requirements

- JDK 21
- Docker (local Postgres and the integration test)

Maven is not required; use the bundled wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

## Run locally

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
curl localhost:8080/actuator/health
```

The `local` profile starts Postgres from `compose.yaml` automatically (Docker must be running) and wires the datasource to it. `/actuator/health` reports `components.db.status`; `/actuator/health/liveness` and `/actuator/health/readiness` are also exposed.

To use a different Postgres, start without the `local` profile and set the datasource variables below.

## Tests

```bash
./mvnw test
```

The context/health test uses Testcontainers (`postgres:16-alpine`) and is skipped automatically when Docker is not available.

## Container image

```bash
docker build -t peachhacks-backend .
docker run --rm -p 8080:8080 --env-file .env peachhacks-backend
```

The image runs as a non-root user with the `prod` profile and listens on `$PORT` (default 8080).

## Configuration

Standard Spring Boot environment variables; see `.env.example`.

| Variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL` | JDBC URL (default `jdbc:postgresql://localhost:5432/peachhacks`) |
| `SPRING_DATASOURCE_USERNAME` | Database user (default `peachhacks`) |
| `SPRING_DATASOURCE_PASSWORD` | Database password (default `peachhacks`) |
| `SPRING_PROFILES_ACTIVE` | `prod` in deployed environments (set by the Dockerfile) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins in `prod` |
| `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE` | Connection pool size (5 in `prod`) |
| `PORT` | HTTP port (default 8080) |

The URL must be in JDBC form (`jdbc:postgresql://...`), not the `postgres://user:pass@...` form providers display; put the user and password in their own variables.

## Deploy to Railway

1. Create a service from this repo and set **Root Directory** to `/backend`.
2. Set the config-as-code path to `/backend/railway.toml` (Railway resolves it from the repo root). It builds with the `Dockerfile` and health-checks `/actuator/health`.
3. Set the datasource variables (below) and optionally `CORS_ALLOWED_ORIGINS`. `PORT` is injected by Railway.
4. Point `api.peachhacks.com` at the service under Settings > Networking > Custom Domain.

**Railway Postgres:** add a Postgres service and reference its variables on the backend service:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
```

**Supabase:** take the host, user and password from Project Settings > Database (Connect).

```
SPRING_DATASOURCE_URL=jdbc:postgresql://aws-0-<region>.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0
SPRING_DATASOURCE_USERNAME=postgres.<project-ref>
SPRING_DATASOURCE_PASSWORD=<database-password>
```

- The transaction pooler (port 6543, user `postgres.<project-ref>`) is IPv4-reachable, which Railway needs. `prepareThreshold=0` is required because transaction-mode pooling does not support server-side prepared statements.
- The direct connection (`db.<project-ref>.supabase.co:5432`, user `postgres`) is IPv6 only unless the IPv4 add-on is enabled; use it for long-lived sessions or schema work.
- Keep the pool small on the Supabase free tier.
