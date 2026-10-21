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

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Complete migration state observed at a point in time. */
public record MigrationStatus(
    String planId, DatabaseTarget target, Instant inspectedAt, List<MigrationChange> changes) {

  public MigrationStatus {
    Objects.requireNonNull(planId, "planId");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(inspectedAt, "inspectedAt");
    changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
  }

  public long appliedCount() {
    return changes.stream()
        .filter(
            change ->
                change.state() == MigrationChangeState.APPLIED
                    || change.state() == MigrationChangeState.APPLIED_AND_PENDING)
        .count();
  }

  public long pendingCount() {
    return changes.stream()
        .filter(
            change ->
                change.state() == MigrationChangeState.PENDING
                    || change.state() == MigrationChangeState.APPLIED_AND_PENDING)
        .count();
  }

  public long filteredCount() {
    return count(MigrationChangeState.FILTERED);
  }

  public boolean current() {
    return pendingCount() == 0;
  }

  private long count(MigrationChangeState state) {
    return changes.stream().filter(change -> change.state() == state).count();
  }
}
