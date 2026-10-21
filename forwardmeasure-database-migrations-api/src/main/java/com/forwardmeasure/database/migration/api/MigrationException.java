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

/** Provider-contained operational migration failure. */
public final class MigrationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final MigrationOperation operation;
  private final String planId;
  private final DatabaseTarget target;

  public MigrationException(
      MigrationOperation operation, String planId, DatabaseTarget target, Throwable cause) {
    super(
        "Failed to "
            + operation.name().toLowerCase()
            + " migration plan "
            + planId
            + " against "
            + target.displayName(),
        cause);
    this.operation = Objects.requireNonNull(operation, "operation");
    this.planId = Objects.requireNonNull(planId, "planId");
    this.target = Objects.requireNonNull(target, "target");
  }

  public MigrationOperation operation() {
    return operation;
  }

  public String planId() {
    return planId;
  }

  public DatabaseTarget target() {
    return target;
  }
}
