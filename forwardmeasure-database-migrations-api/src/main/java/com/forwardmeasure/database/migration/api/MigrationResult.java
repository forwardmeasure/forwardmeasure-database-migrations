package com.forwardmeasure.database.migration.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Outcome of a successful, possibly idempotent migration operation. */
public record MigrationResult(
        String planId,
        DatabaseTarget target,
        Instant startedAt,
        Instant completedAt,
        long appliedChangeCount,
        MigrationStatus status) {

    public MigrationResult {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        Objects.requireNonNull(status, "status");
        if (appliedChangeCount < 0) {
            throw new IllegalArgumentException("appliedChangeCount must not be negative");
        }
    }

    public Duration duration() {
        return Duration.between(startedAt, completedAt);
    }
}

