package com.forwardmeasure.database.migration.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Validation result for migration content against a target database. */
public record MigrationValidation(
    String planId, DatabaseTarget target, Instant validatedAt, boolean valid, List<String> errors) {

  public MigrationValidation {
    Objects.requireNonNull(planId, "planId");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(validatedAt, "validatedAt");
    errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
    if (valid && !errors.isEmpty()) {
      throw new IllegalArgumentException("A valid result cannot contain errors");
    }
    if (!valid && errors.isEmpty()) {
      throw new IllegalArgumentException("An invalid result must contain an error");
    }
  }

  public static MigrationValidation valid(
      String planId, DatabaseTarget target, Instant validatedAt) {
    return new MigrationValidation(planId, target, validatedAt, true, List.of());
  }

  public static MigrationValidation invalid(
      String planId, DatabaseTarget target, Instant validatedAt, List<String> errors) {
    return new MigrationValidation(planId, target, validatedAt, false, errors);
  }
}
