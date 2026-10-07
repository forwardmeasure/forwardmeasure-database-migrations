package com.forwardmeasure.database.migration.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.database.migration.api.DatabaseTarget;
import com.forwardmeasure.database.migration.api.MigrationPlan;
import com.forwardmeasure.database.migration.api.MigrationRequest;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcTargetScopeTest {
  @Test
  void realConnectionRestoresTheOriginalSchemaBeforeClosing() throws Exception {
    try (var postgres = new PostgreSqlTestContainer().start()) {
      postgres.createSchema("migration_target");
      var request =
          new MigrationRequest(
              postgres.dataSource(),
              DatabaseTarget.catalogAndSchema(postgres.databaseName(), "migration_target"),
              MigrationPlan.liquibase("plan", "root.xml"));
      var scope = JdbcTargetScope.open(request.dataSource(), request.target());
      var connection = scope.connection();
      try (scope;
          var statement = connection.createStatement()) {
        try (var row = statement.executeQuery("select current_schema()")) {
          assertTrue(row.next());
          assertEquals("migration_target", row.getString(1));
        }
        scope.restore();
        assertEquals("public", connection.getSchema());
        assertFalse(connection.isClosed());
        scope.restore();
      }
      assertTrue(connection.isClosed());
      scope.close();
      assertThrows(IllegalStateException.class, scope::connection);
      try (var unchanged = JdbcTargetScope.open(postgres.dataSource(), DatabaseTarget.database())) {
        assertEquals("public", unchanged.connection().getSchema());
      }
    }
  }

  @Test
  void allCleanupFailuresAreSuppressedWithoutLosingTheFirstFailure() throws Exception {
    var driver = new DriverBoundary();
    var scope = JdbcTargetScope.open(driver.source(), DatabaseTarget.schema("target"));
    var catalog = new SQLException("restore catalog");
    var schema = new SQLException("restore schema");
    var close = new SQLException("close connection");
    driver.failures.put("setCatalog", catalog);
    driver.failures.put("setSchema", schema);
    driver.failures.put("close", close);
    var thrown = assertThrows(SQLException.class, scope::close);
    assertSame(catalog, thrown);
    assertEquals(java.util.List.of(schema, close), java.util.List.of(thrown.getSuppressed()));
    assertTrue(driver.closeAttempted);
    assertThrows(IllegalStateException.class, scope::connection);
    scope.close();
  }

  @Test
  void schemaOnlyAndCloseOnlyFailuresStillCloseTheScope() throws Exception {
    for (String operation : java.util.List.of("setSchema", "close")) {
      var driver = new DriverBoundary();
      var scope = JdbcTargetScope.open(driver.source(), DatabaseTarget.database());
      var error = new SQLException(operation);
      driver.failures.put(operation, error);
      assertSame(error, assertThrows(SQLException.class, scope::close));
      assertTrue(driver.closeAttempted);
      assertThrows(IllegalStateException.class, scope::connection);
    }
  }

  @Test
  void partiallyOpenedConnectionsAreClosedAndTheOriginalFailureIsPreserved() {
    for (String operation : java.util.List.of("getCatalog", "setSchema")) {
      var driver = new DriverBoundary();
      var original = new SQLException("select target failed");
      var cleanup = new SQLException("close failed");
      driver.failures.put(operation, original);
      driver.failures.put("close", cleanup);
      var thrown =
          assertThrows(
              SQLException.class,
              () -> JdbcTargetScope.open(driver.source(), DatabaseTarget.schema("target")));
      assertSame(original, thrown);
      assertTrue(driver.closeAttempted);
      assertTrue(thrown.getSuppressed().length > 0);
    }
  }

  @Test
  void externallyClosedConnectionCanBeCleanedUpRepeatedly() throws Exception {
    var driver = new DriverBoundary();
    var scope = JdbcTargetScope.open(driver.source(), DatabaseTarget.database());
    scope.connection().close();
    scope.restore();
    scope.close();
    scope.close();
  }

  /**
   * Controlled JDBC driver failures; real SQL/schema behavior is verified against PostgreSQL above.
   */
  static final class DriverBoundary {
    final Map<String, SQLException> failures = new HashMap<>();
    boolean closed;
    boolean closeAttempted;
    final Connection connection =
        (Connection)
            Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                  String name = method.getName();
                  if (name.equals("close")) closeAttempted = true;
                  // Fail once, permitting restoration to run after a failed initial target
                  // selection.
                  var failure = failures.remove(name);
                  if (failure != null) throw failure;
                  return switch (name) {
                    case "getCatalog" -> "original_database";
                    case "getSchema" -> "original_schema";
                    case "setCatalog", "setSchema" -> null;
                    case "isClosed" -> closed;
                    case "close" -> {
                      closed = true;
                      yield null;
                    }
                    default -> throw new UnsupportedOperationException(name);
                  };
                });

    DataSource source() {
      return (DataSource)
          Proxy.newProxyInstance(
              DataSource.class.getClassLoader(),
              new Class<?>[] {DataSource.class},
              (proxy, method, args) -> {
                if (method.getName().equals("getConnection")) return connection;
                throw new UnsupportedOperationException(method.getName());
              });
    }
  }
}
