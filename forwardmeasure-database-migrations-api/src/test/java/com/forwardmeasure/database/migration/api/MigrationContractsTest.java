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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MigrationContractsTest {
  private final Instant now = Instant.parse("2026-10-07T00:00:00Z");
  private final DatabaseTarget target = DatabaseTarget.schema("tenant_a");

  @Test
  void repeatableChangesCountAsAppliedAndPendingUntilReapplied() {
    var changes = new ArrayList<MigrationChange>();
    for (var state : MigrationChangeState.values()) {
      changes.add(
          new MigrationChange(
              state.name(),
              "author",
              "changelog.xml",
              null,
              Optional.of("checksum"),
              Optional.of(now),
              state));
    }
    var status = new MigrationStatus("plan", target, now, changes);
    changes.clear();
    assertEquals(2, status.appliedCount());
    assertEquals(2, status.pendingCount());
    assertEquals(1, status.filteredCount());
    assertFalse(status.current());
    assertEquals("", status.changes().getFirst().description());
    assertThrows(UnsupportedOperationException.class, () -> status.changes().clear());
    var current =
        new MigrationStatus(
            "plan",
            target,
            now,
            List.of(
                new MigrationChange(
                    "one",
                    "author",
                    "change.xml",
                    "created table",
                    Optional.empty(),
                    Optional.empty(),
                    MigrationChangeState.APPLIED)));
    assertTrue(current.current());
    var result = new MigrationResult("plan", target, now, now.plusSeconds(3), 1, current);
    assertEquals(Duration.ofSeconds(3), result.duration());
    assertThrows(
        IllegalArgumentException.class,
        () -> new MigrationResult("plan", target, now, now, -1, current));
  }

  @Test
  void validationCannotContradictItsErrorList() {
    assertTrue(MigrationValidation.valid("plan", target, now).valid());
    var errors = new ArrayList<>(List.of("checksum mismatch"));
    var invalid = MigrationValidation.invalid("plan", target, now, errors);
    errors.clear();
    assertFalse(invalid.valid());
    assertEquals(List.of("checksum mismatch"), invalid.errors());
    assertThrows(
        IllegalArgumentException.class,
        () -> new MigrationValidation("plan", target, now, true, List.of("error")));
    assertThrows(
        IllegalArgumentException.class,
        () -> MigrationValidation.invalid("plan", target, now, List.of()));
  }

  @Test
  void plansSnapshotSelectionInputsAndRejectEmptyOrBlankResources() {
    var resources = new ArrayList<>(List.of("changes.xml"));
    var contexts = new HashSet<>(Set.of("prod"));
    var labels = new HashSet<>(Set.of("release"));
    var parameters = new HashMap<>(Map.of("schema", "tenant_a"));
    var plan = new MigrationPlan("plan", "liquibase", resources, contexts, labels, parameters);
    resources.clear();
    contexts.clear();
    labels.clear();
    parameters.clear();
    assertEquals(List.of("changes.xml"), plan.resources());
    assertEquals(Set.of("prod"), plan.contexts());
    assertEquals(Set.of("release"), plan.labels());
    assertEquals(Map.of("schema", "tenant_a"), plan.parameters());
    assertEquals(List.of("root.xml"), MigrationPlan.liquibase("plan", "root.xml").resources());
    assertThrows(IllegalArgumentException.class, () -> MigrationPlan.liquibase(" ", "root.xml"));
    assertThrows(IllegalArgumentException.class, () -> MigrationPlan.liquibase("plan", " "));
    assertThrows(
        IllegalArgumentException.class,
        () -> new MigrationPlan("plan", "liquibase", List.of(), Set.of(), Set.of(), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MigrationPlan(
                "plan", "liquibase", List.of("root"), Set.of(" "), Set.of(), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MigrationPlan(
                "plan", "liquibase", List.of("root"), Set.of(), Set.of(" "), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MigrationPlan(
                "plan", "liquibase", List.of("root"), Set.of(), Set.of(), Map.of(" ", "value")));
  }

  @Test
  void databaseTargetsDistinguishDefaultsAndRejectControlCharacters() {
    assertEquals("catalog=<default>, schema=<default>", DatabaseTarget.database().displayName());
    assertEquals(
        "catalog=db, schema=tenant_a",
        DatabaseTarget.catalogAndSchema("db", "tenant_a").displayName());
    for (String invalid : List.of(" ", "bad\nname", "bad\u0000name")) {
      assertThrows(IllegalArgumentException.class, () -> DatabaseTarget.schema(invalid));
      assertThrows(
          IllegalArgumentException.class, () -> DatabaseTarget.catalogAndSchema(invalid, "schema"));
    }
  }

  @Test
  void migrationFailuresRetainOperationTargetAndOriginalCause() {
    var cause = new java.sql.SQLException("connection lost");
    var failure = new MigrationException(MigrationOperation.MIGRATE, "plan", target, cause);
    assertSame(cause, failure.getCause());
    assertSame(target, failure.target());
    assertEquals(MigrationOperation.MIGRATE, failure.operation());
    assertEquals("plan", failure.planId());
    assertTrue(failure.getMessage().contains("schema=tenant_a"));
    assertThrows(
        NullPointerException.class,
        () -> new MigrationRequest(null, target, MigrationPlan.liquibase("plan", "root.xml")));
  }
}
