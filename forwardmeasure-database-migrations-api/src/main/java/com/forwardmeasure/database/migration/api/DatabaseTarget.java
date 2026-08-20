package com.forwardmeasure.database.migration.api;

import java.util.Objects;
import java.util.Optional;

/** The optional JDBC catalog and schema selected for one operation. */
public record DatabaseTarget(Optional<String> catalog, Optional<String> schema) {

  public DatabaseTarget {
    catalog = validate(catalog, "catalog");
    schema = validate(schema, "schema");
  }

  public static DatabaseTarget database() {
    return new DatabaseTarget(Optional.empty(), Optional.empty());
  }

  public static DatabaseTarget schema(String schema) {
    return new DatabaseTarget(Optional.empty(), Optional.of(schema));
  }

  public static DatabaseTarget catalogAndSchema(String catalog, String schema) {
    return new DatabaseTarget(Optional.of(catalog), Optional.of(schema));
  }

  public String displayName() {
    return catalog.map(value -> "catalog=" + value).orElse("catalog=<default>")
        + ", "
        + schema.map(value -> "schema=" + value).orElse("schema=<default>");
  }

  private static Optional<String> validate(Optional<String> value, String name) {
    Objects.requireNonNull(value, name);
    return value.map(
        item -> {
          if (item.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
          }
          if (item.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must not contain control characters");
          }
          return item;
        });
  }
}
