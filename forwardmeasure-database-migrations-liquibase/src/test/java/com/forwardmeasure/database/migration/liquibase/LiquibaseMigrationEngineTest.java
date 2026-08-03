package com.forwardmeasure.database.migration.liquibase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.database.migration.api.DatabaseTarget;
import com.forwardmeasure.database.migration.api.MigrationException;
import com.forwardmeasure.database.migration.api.MigrationChangeState;
import com.forwardmeasure.database.migration.api.MigrationPlan;
import com.forwardmeasure.database.migration.api.MigrationRequest;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGPoolingDataSource;

@WithPostgreSqlContainer(databaseName = "migration_contract")
class LiquibaseMigrationEngineTest {

    private static final String CHANGELOG = "db/changelog/provider-contract.xml";

    @Test
    void validatesInspectsMigratesAndIsIdempotentAcrossSchemas(
            PostgreSqlTestContainer database) throws Exception {
        createSchema(database, "tenant_alpha");
        createSchema(database, "tenant_beta");
        LiquibaseMigrationEngine engine = new LiquibaseMigrationEngine();
        MigrationRequest alpha = request(database, "tenant_alpha");

        assertTrue(engine.validate(alpha).valid());
        assertEquals(2, engine.status(alpha).pendingCount());
        assertEquals(1, engine.status(alpha).filteredCount());

        var first = engine.migrate(alpha);

        assertEquals(2, first.appliedChangeCount());
        assertTrue(first.status().current());
        assertTrue(tableExists(database, "tenant_alpha", "migration_widget"));
        assertEquals(1, rowCount(database, "tenant_alpha", "migration_widget"));
        assertFalse(tableExists(database, "tenant_alpha", "premium_widget"));
        assertFalse(tableExists(database, "tenant_beta", "migration_widget"));

        var second = engine.migrate(alpha);

        assertEquals(0, second.appliedChangeCount());
        assertEquals(1, rowCount(database, "tenant_alpha", "migration_widget"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void restoresSchemaBeforeReturningPooledConnection(
            PostgreSqlTestContainer database) throws Exception {
        createSchema(database, "tenant_pool");
        PGPoolingDataSource pooled = new PGPoolingDataSource();
        pooled.setUrl(database.hostJdbcUrl());
        pooled.setUser(database.username());
        pooled.setPassword(database.password());
        pooled.setDataSourceName("migration-test-" + UUID.randomUUID());
        pooled.setMaxConnections(1);
        try {
            MigrationRequest request = new MigrationRequest(
                    pooled, DatabaseTarget.schema("tenant_pool"), plan());
            new LiquibaseMigrationEngine().migrate(request);

            try (var connection = pooled.getConnection()) {
                assertEquals("public", connection.getSchema());
            }
        } finally {
            pooled.close();
        }
    }

    @Test
    void reportsInvalidChangelogWithoutApplyingIt(
            PostgreSqlTestContainer database) throws Exception {
        createSchema(database, "tenant_invalid");
        MigrationPlan invalidPlan = MigrationPlan.liquibase(
                "invalid-plan", "db/changelog/invalid-duplicate.xml");
        MigrationRequest request = new MigrationRequest(
                database.dataSource(),
                DatabaseTarget.schema("tenant_invalid"),
                invalidPlan);

        var validation = new LiquibaseMigrationEngine().validate(request);

        assertFalse(validation.valid());
        assertFalse(validation.errors().isEmpty());
        assertFalse(tableExists(database, "tenant_invalid", "first_table"));
    }

    @Test
    void failsClosedForMissingResourceOrWrongProvider(
            PostgreSqlTestContainer database) throws Exception {
        createSchema(database, "tenant_failure");
        LiquibaseMigrationEngine engine = new LiquibaseMigrationEngine();
        MigrationRequest missing = new MigrationRequest(
                database.dataSource(),
                DatabaseTarget.schema("tenant_failure"),
                MigrationPlan.liquibase("missing", "does/not/exist.xml"));
        MigrationPlan wrongProvider = new MigrationPlan(
                "wrong", "flyway", List.of(CHANGELOG), Set.of(), Set.of(), Map.of());

        assertThrows(MigrationException.class, () -> engine.status(missing));
        assertThrows(IllegalArgumentException.class, () -> engine.status(
                new MigrationRequest(
                        database.dataSource(),
                        DatabaseTarget.schema("tenant_failure"),
                        wrongProvider)));
    }

    @Test
    @SuppressWarnings("deprecation")
    void restoresPooledConnectionAfterProviderFailure(
            PostgreSqlTestContainer database) throws Exception {
        createSchema(database, "tenant_failed_pool");
        PGPoolingDataSource pooled = new PGPoolingDataSource();
        pooled.setUrl(database.hostJdbcUrl());
        pooled.setUser(database.username());
        pooled.setPassword(database.password());
        pooled.setDataSourceName("migration-failure-test-" + UUID.randomUUID());
        pooled.setMaxConnections(1);
        try {
            MigrationRequest request = new MigrationRequest(
                    pooled,
                    DatabaseTarget.schema("tenant_failed_pool"),
                    MigrationPlan.liquibase("missing", "does/not/exist.xml"));

            assertThrows(
                    MigrationException.class,
                    () -> new LiquibaseMigrationEngine().migrate(request));

            try (var connection = pooled.getConnection()) {
                assertEquals("public", connection.getSchema());
            }
        } finally {
            pooled.close();
        }
    }

    @Test
    void reportsActualRunAlwaysExecutions(
            PostgreSqlTestContainer database) throws Exception {
        createSchema(database, "tenant_run_always");
        MigrationRequest request = new MigrationRequest(
                database.dataSource(),
                DatabaseTarget.schema("tenant_run_always"),
                MigrationPlan.liquibase(
                        "run-always", "db/changelog/run-always.xml"));
        LiquibaseMigrationEngine engine = new LiquibaseMigrationEngine();

        var first = engine.migrate(request);
        var second = engine.migrate(request);

        assertEquals(2, first.appliedChangeCount());
        assertEquals(1, second.appliedChangeCount());
        assertEquals(2, rowCount(database,
                "tenant_run_always", "migration_execution_log"));
        assertTrue(second.status().changes().stream().anyMatch(change ->
                change.state() == MigrationChangeState.APPLIED_AND_PENDING));
    }

    private MigrationRequest request(
            PostgreSqlTestContainer database, String schema) {
        return new MigrationRequest(
                database.dataSource(), DatabaseTarget.schema(schema), plan());
    }

    private MigrationPlan plan() {
        return new MigrationPlan(
                "provider-contract",
                LiquibaseMigrationEngine.PROVIDER,
                List.of(CHANGELOG),
                Set.of("seed"),
                Set.of("standard"),
                Map.of("widgetName", "from-parameter"));
    }

    private void createSchema(
            PostgreSqlTestContainer database, String schema) throws Exception {
        try (var connection = database.dataSource().getConnection();
                var statement = connection.createStatement()) {
            statement.execute("create schema if not exists \"" + schema + "\"");
        }
    }

    private boolean tableExists(
            PostgreSqlTestContainer database,
            String schema,
            String table) throws Exception {
        try (var connection = database.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "select exists (select 1 from information_schema.tables"
                                + " where table_schema = ? and table_name = ?)")) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private long rowCount(
            PostgreSqlTestContainer database,
            String schema,
            String table) throws Exception {
        String sql = "select count(*) from \"" + schema + "\".\"" + table + "\"";
        try (var connection = database.dataSource().getConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
