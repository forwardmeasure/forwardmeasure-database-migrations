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
import java.util.Objects;
import java.util.Optional;

/** Provider-neutral description and observed state of one migration change. */
public record MigrationChange(
    String id,
    String author,
    String resource,
    String description,
    Optional<String> checksum,
    Optional<Instant> lastAppliedAt,
    MigrationChangeState state) {

  public MigrationChange {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(author, "author");
    Objects.requireNonNull(resource, "resource");
    description = description == null ? "" : description;
    Objects.requireNonNull(checksum, "checksum");
    Objects.requireNonNull(lastAppliedAt, "lastAppliedAt");
    Objects.requireNonNull(state, "state");
  }
}
