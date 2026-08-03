package com.forwardmeasure.database.migration.api;

import java.util.Objects;
import javax.sql.DataSource;

/** All inputs required to execute or inspect one migration plan. */
public record MigrationRequest(
        DataSource dataSource,
        DatabaseTarget target,
        MigrationPlan plan) {

    public MigrationRequest {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(plan, "plan");
    }
}

