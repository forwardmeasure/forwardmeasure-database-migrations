package com.forwardmeasure.database.migration.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Provider-neutral description and observed state of one migration change. */
public record MigrationChange(
    String id,
    String author,
    String resource,
    String description,
    Optional<String> checksum,
    Optional<Instant> lastAppliedAt,
    MigrationChangeState state) {

  public MigrationChange {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(author, "author");
    Objects.requireNonNull(resource, "resource");
    description = description == null ? "" : description;
    Objects.requireNonNull(checksum, "checksum");
    Objects.requireNonNull(lastAppliedAt, "lastAppliedAt");
    Objects.requireNonNull(state, "state");
  }
}
