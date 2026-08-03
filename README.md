# ForwardMeasure Database Migrations

Provider-neutral database migration orchestration with explicit application-owned migration plans.

The project deliberately separates migration policy from migration technology:

- `forwardmeasure-database-migrations-api` defines immutable requests, plans, results, status and validation contracts.
- `forwardmeasure-database-migrations-jdbc` owns safe connection target selection and restoration.
- `forwardmeasure-database-migrations-liquibase` implements the contracts using Liquibase.
- Integration tests consume the independently reusable `forwardmeasure-testcontainers-junit-jupiter` PostgreSQL fixture.
- `forwardmeasure-database-migrations-bom` aligns consumer dependency versions.

Applications own their root changelog and pass its resource path explicitly. This library does not scan for changelogs, create or drop schemas, decide which tenants to migrate, or mark unknown databases as already migrated.

See [the architecture](docs/architecture.md).
