/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
