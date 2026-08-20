package com.forwardmeasure.database.migration.api;

/** Executes and inspects immutable migration plans for one provider. */
public interface DatabaseMigrationEngine {

  String provider();

  MigrationValidation validate(MigrationRequest request);

  MigrationStatus status(MigrationRequest request);

  MigrationResult migrate(MigrationRequest request);
}
