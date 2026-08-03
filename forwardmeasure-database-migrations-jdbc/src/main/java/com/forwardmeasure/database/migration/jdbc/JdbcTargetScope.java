package com.forwardmeasure.database.migration.jdbc;

import com.forwardmeasure.database.migration.api.DatabaseTarget;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * Owns one JDBC connection and restores its original catalog and schema before
 * the connection can be returned to a pool.
 */
public final class JdbcTargetScope implements AutoCloseable {

    private final Connection connection;
    private final String originalCatalog;
    private final String originalSchema;
    private boolean restored;
    private boolean closed;

    private JdbcTargetScope(
            Connection connection,
            String originalCatalog,
            String originalSchema) {
        this.connection = connection;
        this.originalCatalog = originalCatalog;
        this.originalSchema = originalSchema;
    }

    public static JdbcTargetScope open(
            DataSource dataSource, DatabaseTarget target) throws SQLException {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(target, "target");

        Connection connection = dataSource.getConnection();
        JdbcTargetScope scope = null;
        try {
            scope = new JdbcTargetScope(
                    connection, connection.getCatalog(), connection.getSchema());
            if (target.catalog().isPresent()) {
                connection.setCatalog(target.catalog().orElseThrow());
            }
            if (target.schema().isPresent()) {
                connection.setSchema(target.schema().orElseThrow());
            }
            return scope;
        } catch (SQLException | RuntimeException | Error failure) {
            cleanupFailedOpen(scope, connection, failure);
            throw failure;
        }
    }

    public Connection connection() {
        if (closed) {
            throw new IllegalStateException("JDBC target scope is closed");
        }
        return connection;
    }

    /** Restores the original connection target without closing the connection. */
    public void restore() throws SQLException {
        if (restored || connection.isClosed()) {
            restored = true;
            return;
        }

        SQLException failure = null;
        try {
            connection.setCatalog(originalCatalog);
        } catch (SQLException exception) {
            failure = exception;
        }
        try {
            connection.setSchema(originalSchema);
        } catch (SQLException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        restored = failure == null;
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }

        SQLException failure = null;
        try {
            restore();
        } catch (SQLException exception) {
            failure = exception;
        }
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        } finally {
            closed = true;
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void cleanupFailedOpen(
            JdbcTargetScope scope,
            Connection connection,
            Throwable failure) {
        try {
            if (scope != null) {
                scope.close();
            } else {
                connection.close();
            }
        } catch (SQLException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}

