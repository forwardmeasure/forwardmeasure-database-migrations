package com.forwardmeasure.database.migration.liquibase;

import com.forwardmeasure.database.migration.api.DatabaseMigrationEngine;
import com.forwardmeasure.database.migration.api.MigrationChange;
import com.forwardmeasure.database.migration.api.MigrationChangeState;
import com.forwardmeasure.database.migration.api.MigrationException;
import com.forwardmeasure.database.migration.api.MigrationOperation;
import com.forwardmeasure.database.migration.api.MigrationRequest;
import com.forwardmeasure.database.migration.api.MigrationResult;
import com.forwardmeasure.database.migration.api.MigrationStatus;
import com.forwardmeasure.database.migration.api.MigrationValidation;
import com.forwardmeasure.database.migration.jdbc.JdbcTargetScope;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.change.CheckSum;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.ChangeSetStatus;
import liquibase.changelog.visitor.DefaultChangeExecListener;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.exception.ValidationFailedException;
import liquibase.resource.ClassLoaderResourceAccessor;

/** Liquibase implementation of the provider-neutral migration contract. */
public final class LiquibaseMigrationEngine implements DatabaseMigrationEngine {

    public static final String PROVIDER = "liquibase";

    private final ClassLoader classLoader;
    private final Clock clock;

    public LiquibaseMigrationEngine() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public LiquibaseMigrationEngine(ClassLoader classLoader) {
        this(classLoader, Clock.systemUTC());
    }

    LiquibaseMigrationEngine(ClassLoader classLoader, Clock clock) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public MigrationValidation validate(MigrationRequest request) {
        return execute(request, MigrationOperation.VALIDATE, liquibase -> {
            Instant validatedAt = clock.instant();
            try {
                liquibase.validate();
                return MigrationValidation.valid(
                        request.plan().id(), request.target(), validatedAt);
            } catch (LiquibaseException exception) {
                ValidationFailedException validationFailure =
                        findValidationFailure(exception);
                if (validationFailure == null) {
                    throw exception;
                }
                LinkedHashSet<String> errors = new LinkedHashSet<>();
                if (validationFailure.getMessage() != null
                        && !validationFailure.getMessage().isBlank()) {
                    errors.add(validationFailure.getMessage());
                }
                errors.addAll(validationFailure.getInvalidMD5Sums());
                if (errors.isEmpty()) {
                    errors.add("Liquibase validation failed");
                }
                return MigrationValidation.invalid(
                        request.plan().id(),
                        request.target(),
                        validatedAt,
                        List.copyOf(errors));
            }
        });
    }

    @Override
    public MigrationStatus status(MigrationRequest request) {
        return execute(
                request,
                MigrationOperation.STATUS,
                liquibase -> inspect(request, liquibase));
    }

    @Override
    public MigrationResult migrate(MigrationRequest request) {
        Instant startedAt = clock.instant();
        return execute(request, MigrationOperation.MIGRATE, liquibase -> {
            DefaultChangeExecListener listener = new DefaultChangeExecListener();
            liquibase.setChangeExecListener(listener);
            liquibase.update(contexts(request), labels(request));
            MigrationStatus after = inspect(request, liquibase);
            return new MigrationResult(
                    request.plan().id(),
                    request.target(),
                    startedAt,
                    clock.instant(),
                    listener.getDeployedChangeSets().size(),
                    after);
        });
    }

    private MigrationStatus inspect(
            MigrationRequest request, Liquibase liquibase)
            throws LiquibaseException {
        List<MigrationChange> changes = new ArrayList<>();
        for (ChangeSetStatus status : liquibase.getChangeSetStatuses(
                contexts(request), labels(request))) {
            ChangeSet changeSet = status.getChangeSet();
            CheckSum checksum = status.getStoredCheckSum() != null
                    ? status.getStoredCheckSum()
                    : status.getCurrentCheckSum();
            MigrationChangeState state;
            if (status.getWillRun() && status.getPreviouslyRan()) {
                state = MigrationChangeState.APPLIED_AND_PENDING;
            } else if (status.getWillRun()) {
                state = MigrationChangeState.PENDING;
            } else if (status.getPreviouslyRan()) {
                state = MigrationChangeState.APPLIED;
            } else {
                state = MigrationChangeState.FILTERED;
            }
            changes.add(new MigrationChange(
                    changeSet.getId(),
                    changeSet.getAuthor(),
                    changeSet.getFilePath(),
                    status.getDescription(),
                    Optional.ofNullable(checksum).map(CheckSum::toString),
                    Optional.ofNullable(status.getDateLastExecuted())
                            .map(date -> date.toInstant()),
                    state));
        }
        return new MigrationStatus(
                request.plan().id(), request.target(), clock.instant(), changes);
    }

    private <T> T execute(
            MigrationRequest request,
            MigrationOperation operation,
            LiquibaseWork<T> work) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(work, "work");
        validatePlan(request);

        JdbcTargetScope scope = null;
        Liquibase liquibase = null;
        Throwable failure = null;
        try {
            scope = JdbcTargetScope.open(request.dataSource(), request.target());
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(
                            new JdbcConnection(scope.connection()));
            request.target().catalog().ifPresent(catalog -> {
                try {
                    database.setDefaultCatalogName(catalog);
                    database.setLiquibaseCatalogName(catalog);
                } catch (LiquibaseException exception) {
                    throw new DatabaseConfigurationException(exception);
                }
            });
            request.target().schema().ifPresent(schema -> {
                try {
                    database.setDefaultSchemaName(schema);
                    database.setLiquibaseSchemaName(schema);
                } catch (LiquibaseException exception) {
                    throw new DatabaseConfigurationException(exception);
                }
            });
            liquibase = new Liquibase(
                    request.plan().resources().getFirst(),
                    new ClassLoaderResourceAccessor(classLoader),
                    database);
            request.plan().parameters().forEach(liquibase::setChangeLogParameter);
            return work.execute(liquibase);
        } catch (DatabaseConfigurationException exception) {
            failure = exception.getCause();
            throw migrationException(operation, request, exception.getCause());
        } catch (SQLException | LiquibaseException exception) {
            failure = exception;
            throw migrationException(operation, request, exception);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            cleanup(scope, liquibase, failure, operation, request);
        }
    }

    private void validatePlan(MigrationRequest request) {
        if (!provider().equals(request.plan().provider())) {
            throw new IllegalArgumentException(
                    "Migration plan provider " + request.plan().provider()
                            + " cannot be executed by " + provider());
        }
        if (request.plan().resources().size() != 1) {
            throw new IllegalArgumentException(
                    "Liquibase plans require exactly one explicit root changelog");
        }
    }

    private Contexts contexts(MigrationRequest request) {
        return new Contexts(request.plan().contexts());
    }

    private LabelExpression labels(MigrationRequest request) {
        return new LabelExpression(request.plan().labels());
    }

    private MigrationException migrationException(
            MigrationOperation operation,
            MigrationRequest request,
            Throwable cause) {
        return new MigrationException(
                operation, request.plan().id(), request.target(), cause);
    }

    private void cleanup(
            JdbcTargetScope scope,
            Liquibase liquibase,
            Throwable operationFailure,
            MigrationOperation operation,
            MigrationRequest request) {
        if (scope == null) {
            return;
        }

        Exception cleanupFailure = null;
        try {
            scope.restore();
        } catch (SQLException exception) {
            cleanupFailure = exception;
        }
        try {
            if (liquibase != null) {
                liquibase.close();
            }
        } catch (LiquibaseException exception) {
            cleanupFailure = merge(cleanupFailure, exception);
        }
        try {
            scope.close();
        } catch (SQLException exception) {
            cleanupFailure = merge(cleanupFailure, exception);
        }

        if (cleanupFailure == null) {
            return;
        }
        if (operationFailure != null) {
            operationFailure.addSuppressed(cleanupFailure);
            return;
        }
        throw migrationException(operation, request, cleanupFailure);
    }

    private Exception merge(Exception existing, Exception additional) {
        if (existing == null) {
            return additional;
        }
        existing.addSuppressed(additional);
        return existing;
    }

    private ValidationFailedException findValidationFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ValidationFailedException validationFailure) {
                return validationFailure;
            }
            current = current.getCause();
        }
        return null;
    }

    @FunctionalInterface
    private interface LiquibaseWork<T> {
        T execute(Liquibase liquibase) throws LiquibaseException;
    }

    private static final class DatabaseConfigurationException
            extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private DatabaseConfigurationException(LiquibaseException cause) {
            super(cause);
        }

        @Override
        public synchronized LiquibaseException getCause() {
            return (LiquibaseException) super.getCause();
        }
    }
}
