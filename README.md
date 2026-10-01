# Parking Lot API

Production-oriented Spring Boot REST API for reserving one-hour parking spaces. The lot has 100 physical spaces and permits at most 80 concurrent active reservations, leaving 20% for operational use.

## Technology

- Java 21 and Spring Boot 4.1.1
- PostgreSQL 18
- Spring Data JPA with explicit transactional locking
- Lombok for focused boilerplate reduction
- Flyway SQL migrations
- OpenAPI/Swagger UI
- Actuator and Prometheus metrics
- JUnit, Mockito, and PostgreSQL Testcontainers

## Prerequisites

- Java 21 or newer (the project compiles to Java 21)
- Docker with Docker Compose

## Run with Docker Compose

The local defaults work without an environment file:

```bash
docker compose up --build
```

To override them, copy `.env.example` to `.env` and set your own values before starting Compose.

The service is available at `http://localhost:8080`. PostgreSQL is reachable only by the application on the private Compose network, and its data is retained in the `parking-postgres-data` Docker volume.

Useful endpoints:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`
- Readiness: `http://localhost:8080/actuator/health/readiness`
- Liveness: `http://localhost:8080/actuator/health/liveness`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus`

Stop the services without deleting the database:

```bash
docker compose down
```

To deliberately delete local database data as well:

```bash
docker compose down --volumes
```

## Run from the JVM

Use a PostgreSQL instance reachable from the host, then run the application:

```bash
./mvnw spring-boot:run
```

The default JVM connection is:

```text
jdbc:postgresql://localhost:5432/parking
username: parking
password: parking
```

Override it with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.

## API

### Create a reservation

`parkingSpaceNumber` is optional. When omitted, the lowest-numbered space available for the complete interval is selected.

```bash
curl --request POST http://localhost:8080/api/v1/reservations \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: 7910da8f-cd45-4f88-9058-593fdc40a876' \
  --data '{
    "licensePlate": "CA1234AB",
    "startTime": "2026-10-02T10:15:00+03:00",
    "parkingSpaceNumber": 42
  }'
```

Successful response (`201 Created`):

```json
{
  "id": "99ccf10c-2b2e-48d2-852d-bdde19a555f4",
  "licensePlate": "CA1234AB",
  "parkingSpaceNumber": 42,
  "startTime": "2026-10-02T07:15:00Z",
  "endTime": "2026-10-02T08:15:00Z",
  "status": "ACTIVE",
  "createdAt": "2026-10-01T12:00:00Z",
  "updatedAt": "2026-10-01T12:00:00Z"
}
```

Retrying the identical request with the same `Idempotency-Key` returns the original reservation. Reusing that key for a different payload returns `409 Conflict`.

### Get a reservation

The `Location` header returned during creation points to this endpoint:

```bash
curl http://localhost:8080/api/v1/reservations/99ccf10c-2b2e-48d2-852d-bdde19a555f4
```

It returns the current reservation representation, including whether it is `ACTIVE` or `CANCELLED`. An unknown reservation returns `404 Not Found`.

### Cancel a reservation

```bash
curl --request DELETE \
  http://localhost:8080/api/v1/reservations/99ccf10c-2b2e-48d2-852d-bdde19a555f4
```

Cancellation returns `204 No Content` and is idempotent. An unknown reservation returns `404 Not Found`.

### Errors

Errors use RFC 9457 Problem Details and include stable `errorCode` and `correlationId` properties:

```json
{
  "type": "urn:parking-lot:problem:space_unavailable",
  "title": "Conflict",
  "status": 409,
  "detail": "The requested parking space is unavailable for this interval",
  "instance": "/api/v1/reservations",
  "errorCode": "SPACE_UNAVAILABLE",
  "correlationId": "74c6d51d-434d-4381-88a0-8dd760ff8f35"
}
```

Clients may provide a safe `X-Correlation-ID` header; otherwise the service creates one and returns it in the response.

## Reservation rules

- Start times must be in the future and include a UTC offset.
- All timestamps are normalized and persisted as UTC instants.
- Every interval is exactly one hour and uses half-open boundaries: `[start, end)`.
- Adjacent reservations are allowed; for example, `10:00–11:00` and `11:00–12:00` do not overlap.
- A requested space must be between 1 and 100 and must be available for the full interval.
- Automatic allocation is deterministic and selects the lowest available number.
- At most 80 active reservations may overlap at any instant.
- License plates are trimmed, uppercased, required, and limited to 20 characters.

## Concurrency and data integrity

Reservation creation acquires the transaction-scoped PostgreSQL advisory lock `pg_advisory_xact_lock(1)`. While holding that database lock, one transaction:

1. Checks the idempotency key.
2. Counts overlapping active reservations.
3. Selects and validates a space.
4. Inserts the reservation and idempotency record.

This serializes allocation for this one fixed lot and ensures concurrent requests cannot exceed the limit. PostgreSQL releases the advisory lock automatically on commit or rollback. A PostgreSQL GiST exclusion constraint independently prevents overlapping active reservations for the same space. Foreign keys, unique constraints, and a database check for the exact one-hour duration provide additional defense in depth.

The lock ID is an application convention rather than a row identifier. Every reservation-creation path must acquire the same lock. A future multi-lot system could use one stable advisory-lock ID per lot.

## Database migrations

Flyway runs migrations automatically at startup. Hibernate uses `ddl-auto=validate` and never creates or updates production tables. The initial migration:

- Enables PostgreSQL `btree_gist`.
- Creates the spaces, reservations, and idempotency tables.
- Adds indexes, foreign keys, checks, and the overlap exclusion constraint.
- Creates spaces 1–100. The fixed limit of 80 active overlapping reservations is defined by the service.

Never edit an applied migration. Add a new versioned migration under `src/main/resources/db/migration`.

## Tests

Run unit tests only:

```bash
./mvnw test
```

Run the complete suite, including PostgreSQL Testcontainers integration and concurrency tests:

```bash
./mvnw verify
```

Docker must be running for `verify`. Integration tests use PostgreSQL 18, run the real Flyway migrations, verify database constraints and HTTP behavior, and submit 81 concurrent requests to prove the 80-reservation limit.

## Configuration

| Environment variable | Default | Purpose |
|---|---:|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/parking` | JDBC connection URL |
| `DB_USERNAME` | `parking` | Database user |
| `DB_PASSWORD` | `parking` | Database password |
| `DB_POOL_MAX_SIZE` | `20` | Maximum Hikari connections |
| `DB_POOL_MIN_IDLE` | `2` | Minimum idle connections |
| `DB_CONNECTION_TIMEOUT_MS` | `3000` | Pool connection timeout |
| `DB_QUERY_TIMEOUT_MS` | `5000` | Hibernate query timeout |
| `SERVER_PORT` | `8080` | HTTP port |
| `SHUTDOWN_TIMEOUT` | `20s` | Graceful shutdown window in the `prod` profile |

Do not commit `.env` or real credentials. In a deployed environment, inject secrets with the platform's secret manager.

## Build the production image

```bash
docker build --tag parking-lot:local .
```

The multi-stage image builds the executable JAR and runs it as a non-root user with a readiness health check.
