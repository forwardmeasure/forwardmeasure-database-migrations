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

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Outcome of a successful, possibly idempotent migration operation. */
public record MigrationResult(
    String planId,
    DatabaseTarget target,
    Instant startedAt,
    Instant completedAt,
    long appliedChangeCount,
    MigrationStatus status) {

  public MigrationResult {
    Objects.requireNonNull(planId, "planId");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(completedAt, "completedAt");
    Objects.requireNonNull(status, "status");
    if (appliedChangeCount < 0) {
      throw new IllegalArgumentException("appliedChangeCount must not be negative");
    }
  }

  public Duration duration() {
    return Duration.between(startedAt, completedAt);
  }
}
