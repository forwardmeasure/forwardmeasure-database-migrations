package com.forwardmeasure.database.migration.liquibase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.database.migration.api.DatabaseTarget;
import com.forwardmeasure.database.migration.api.MigrationException;
import com.forwardmeasure.database.migration.api.MigrationOperation;
import com.forwardmeasure.database.migration.api.MigrationPlan;
import com.forwardmeasure.database.migration.api.MigrationRequest;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@WithPostgreSqlContainer
class LiquibaseFailureContractTest {
  private static final String CHANGELOG = "db/changelog/provider-contract.xml";

  @Test
  void missingValidationResourceIsOperationalFailureAndMultipleRootsAreRejected(
      PostgreSqlTestContainer database) {
    var engine = new LiquibaseMigrationEngine();
    var missing = request(database.dataSource(), DatabaseTarget.database(), "missing.xml");
    var failed = assertThrows(MigrationException.class, () -> engine.validate(missing));
    assertEquals(MigrationOperation.VALIDATE, failed.operation());
    var many =
        new MigrationPlan(
            "multiple",
            "liquibase",
            List.of(CHANGELOG, "second.xml"),
            Set.of(),
            Set.of(),
            Map.of());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            engine.status(
                new MigrationRequest(database.dataSource(), DatabaseTarget.database(), many)));
  }

  @Test
  void connectionAcquisitionFailureRetainsOperationAndCause() {
    var cause = new SQLException("database unavailable");
    var source =
        (DataSource)
            Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] {DataSource.class},
                (proxy, method, args) -> {
                  throw cause;
                });
    var failure =
        assertThrows(
            MigrationException.class,
            () ->
                new LiquibaseMigrationEngine()
                    .status(request(source, DatabaseTarget.database(), CHANGELOG)));
    assertSame(cause, failure.getCause());
    assertEquals(MigrationOperation.STATUS, failure.operation());
  }

  @Test
  void successfulMigrationWithFailedCleanupMustStillReportFailure(PostgreSqlTestContainer database)
      throws Exception {
    String schema = schema(database);
    for (boolean failSchema : List.of(false, true)) {
      try (var driver = new DriverFaults(database.dataSource(), failSchema, true, false)) {
        var failure =
            assertThrows(
                MigrationException.class,
                () ->
                    new LiquibaseMigrationEngine()
                        .migrate(
                            request(
                                driver.source(),
                                DatabaseTarget.catalogAndSchema(database.databaseName(), schema),
                                CHANGELOG)));
        assertEquals(MigrationOperation.MIGRATE, failure.operation());
        assertTrue(contains(failure, "controlled close failure"));
        if (failSchema) assertTrue(contains(failure, "controlled restore failure"));
        assertTrue(driver.closeAttempts > 0);
      }
    }
  }

  @Test
  void cleanupFailureIsSuppressedOnTheOriginalMigrationFailure(PostgreSqlTestContainer database)
      throws Exception {
    try (var driver = new DriverFaults(database.dataSource(), true, false, false)) {
      var failure =
          assertThrows(
              MigrationException.class,
              () ->
                  new LiquibaseMigrationEngine()
                      .migrate(
                          request(
                              driver.source(),
                              DatabaseTarget.schema(schema(database)),
                              "missing.xml")));
      assertTrue(contains(failure, "missing.xml"));
      assertTrue(contains(failure, "controlled restore failure"));
      assertTrue(failure.getCause().getSuppressed().length > 0);
    }
  }

  @Test
  void unexpectedDriverRuntimeFailureStillClosesAcquiredConnection(PostgreSqlTestContainer database)
      throws Exception {
    try (var driver = new DriverFaults(database.dataSource(), false, false, true)) {
      var failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  new LiquibaseMigrationEngine()
                      .status(request(driver.source(), DatabaseTarget.database(), CHANGELOG)));
      assertSame(driver.runtime, failure);
      assertTrue(driver.connections.getFirst().isClosed());
    }
  }

  @Test
  void checksumDriftIsReportedAsInvalidWithoutReapplyingChanges(PostgreSqlTestContainer database)
      throws Exception {
    String schema = schema(database);
    var request = request(database.dataSource(), DatabaseTarget.schema(schema), CHANGELOG);
    var engine = new LiquibaseMigrationEngine();
    engine.migrate(request);
    try (var connection = database.dataSource().getConnection();
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          "UPDATE \""
              + schema
              + "\".databasechangelog SET md5sum = '9:"
              + "0".repeat(32)
              + "' WHERE id = '001-create-widget'");
    }
    var validation = engine.validate(request);
    assertFalse(validation.valid());
    assertTrue(validation.errors().stream().anyMatch(error -> error.contains("001-create-widget")));
  }

  private static String schema(PostgreSqlTestContainer database) {
    String schema = "fault_" + UUID.randomUUID().toString().replace("-", "");
    database.createSchema(schema);
    return schema;
  }

  private static MigrationRequest request(
      DataSource source, DatabaseTarget target, String resource) {
    return new MigrationRequest(
        source,
        target,
        new MigrationPlan(
            "fault-contract",
            "liquibase",
            List.of(resource),
            Set.of("seed"),
            Set.of("standard"),
            Map.of("widgetName", "contract")));
  }

  private static boolean contains(Throwable failure, String text) {
    if (failure.getMessage() != null && failure.getMessage().contains(text)) return true;
    for (var suppressed : failure.getSuppressed()) if (contains(suppressed, text)) return true;
    return failure.getCause() != null && contains(failure.getCause(), text);
  }

  /** Fault injection at the JDBC boundary; Liquibase and PostgreSQL execute normally. */
  static final class DriverFaults implements AutoCloseable {
    final DataSource delegate;
    final boolean failSchema;
    final boolean failClose;
    final boolean failMetadata;
    final IllegalStateException runtime = new IllegalStateException("controlled metadata failure");
    final List<Connection> connections = new ArrayList<>();
    int closeAttempts;

    DriverFaults(DataSource delegate, boolean failSchema, boolean failClose, boolean failMetadata) {
      this.delegate = delegate;
      this.failSchema = failSchema;
      this.failClose = failClose;
      this.failMetadata = failMetadata;
    }

    DataSource source() {
      return (DataSource)
          Proxy.newProxyInstance(
              DataSource.class.getClassLoader(),
              new Class<?>[] {DataSource.class},
              (proxy, method, args) -> {
                if (!method.getName().equals("getConnection"))
                  throw new UnsupportedOperationException(method.getName());
                var connection = delegate.getConnection();
                connections.add(connection);
                return Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (ignored, call, values) -> {
                      if (call.getName().equals("getMetaData") && failMetadata) throw runtime;
                      if (call.getName().equals("setSchema")
                          && failSchema
                          && "public".equals(values[0]))
                        throw new SQLException("controlled restore failure");
                      if (call.getName().equals("close")) {
                        closeAttempts++;
                        if (failClose) throw new SQLException("controlled close failure");
                      }
                      try {
                        return call.invoke(connection, values);
                      } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                      }
                    });
              });
    }

    public void close() throws SQLException {
      for (var connection : connections) connection.close();
    }
  }
}
