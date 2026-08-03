package com.forwardmeasure.database.migration.api;

import java.util.Objects;

/** Provider-contained operational migration failure. */
public final class MigrationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final MigrationOperation operation;
    private final String planId;
    private final DatabaseTarget target;

    public MigrationException(
            MigrationOperation operation,
            String planId,
            DatabaseTarget target,
            Throwable cause) {
        super("Failed to " + operation.name().toLowerCase()
                + " migration plan " + planId + " against " + target.displayName(), cause);
        this.operation = Objects.requireNonNull(operation, "operation");
        this.planId = Objects.requireNonNull(planId, "planId");
        this.target = Objects.requireNonNull(target, "target");
    }

    public MigrationOperation operation() {
        return operation;
    }

    public String planId() {
        return planId;
    }

    public DatabaseTarget target() {
        return target;
    }
}

