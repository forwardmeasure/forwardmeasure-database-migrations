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

/** Validation result for migration content against a target database. */
public record MigrationValidation(
    String planId, DatabaseTarget target, Instant validatedAt, boolean valid, List<String> errors) {

  public MigrationValidation {
    Objects.requireNonNull(planId, "planId");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(validatedAt, "validatedAt");
    errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
    if (valid && !errors.isEmpty()) {
      throw new IllegalArgumentException("A valid result cannot contain errors");
    }
    if (!valid && errors.isEmpty()) {
      throw new IllegalArgumentException("An invalid result must contain an error");
    }
  }

  public static MigrationValidation valid(
      String planId, DatabaseTarget target, Instant validatedAt) {
    return new MigrationValidation(planId, target, validatedAt, true, List.of());
  }

  public static MigrationValidation invalid(
      String planId, DatabaseTarget target, Instant validatedAt, List<String> errors) {
    return new MigrationValidation(planId, target, validatedAt, false, errors);
  }
}
