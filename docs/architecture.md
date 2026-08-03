# Architecture

## Ownership boundary

The application owns migration content and operational policy: root changelogs, schema creation, tenant selection, scheduling, retries and authorization. The library owns deterministic execution against an already-addressable JDBC target.

## Layers

1. The API accepts a `DataSource`, validated target, immutable migration plan, contexts, labels and changelog parameters.
2. The JDBC layer acquires one connection, records its original catalog and schema, selects the requested target, and restores the original state before returning a pooled connection.
3. A provider adapter resolves the explicitly named migration resource and implements validation, status inspection and migration.
4. `forwardmeasure-testcontainers` proves provider behaviour against a real PostgreSQL container without embedding another container lifecycle in this project.

## Safety decisions

- No implicit classpath scanning or library-selected application changelog.
- No schema create/drop operations.
- No automatic `changelogSync` or baseline-on-error behaviour.
- No framework lifecycle, CDI, Spring or Micronaut coupling.
- Provider exceptions are contained behind the API.
- A target's original JDBC catalog/schema is restored on success and failure.

Flyway can be added later as a peer provider without changing consumers of the API.
