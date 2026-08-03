package com.forwardmeasure.database.migration.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable, provider-addressed migration content and selection options. */
public record MigrationPlan(
        String id,
        String provider,
        List<String> resources,
        Set<String> contexts,
        Set<String> labels,
        Map<String, String> parameters) {

    public MigrationPlan {
        id = required(id, "id");
        provider = required(provider, "provider");
        resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
        contexts = Set.copyOf(Objects.requireNonNull(contexts, "contexts"));
        labels = Set.copyOf(Objects.requireNonNull(labels, "labels"));
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("resources must not be empty");
        }
        resources.forEach(resource -> required(resource, "resource"));
        contexts.forEach(context -> required(context, "context"));
        labels.forEach(label -> required(label, "label"));
        parameters.forEach((key, value) -> {
            required(key, "parameter key");
            Objects.requireNonNull(value, "parameter value");
        });
    }

    public static MigrationPlan liquibase(String id, String rootChangelog) {
        return new MigrationPlan(
                id,
                "liquibase",
                List.of(rootChangelog),
                Set.of(),
                Set.of(),
                Map.of());
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

