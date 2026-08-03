package com.forwardmeasure.database.migration.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Complete migration state observed at a point in time. */
public record MigrationStatus(
        String planId,
        DatabaseTarget target,
        Instant inspectedAt,
        List<MigrationChange> changes) {

    public MigrationStatus {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(inspectedAt, "inspectedAt");
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
    }

    public long appliedCount() {
        return changes.stream()
                .filter(change -> change.state() == MigrationChangeState.APPLIED
                        || change.state() == MigrationChangeState.APPLIED_AND_PENDING)
                .count();
    }

    public long pendingCount() {
        return changes.stream()
                .filter(change -> change.state() == MigrationChangeState.PENDING
                        || change.state() == MigrationChangeState.APPLIED_AND_PENDING)
                .count();
    }

    public long filteredCount() {
        return count(MigrationChangeState.FILTERED);
    }

    public boolean current() {
        return pendingCount() == 0;
    }

    private long count(MigrationChangeState state) {
        return changes.stream().filter(change -> change.state() == state).count();
    }
}
