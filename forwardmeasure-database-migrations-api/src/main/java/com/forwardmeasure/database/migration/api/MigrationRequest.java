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
import javax.sql.DataSource;

/** All inputs required to execute or inspect one migration plan. */
public record MigrationRequest(DataSource dataSource, DatabaseTarget target, MigrationPlan plan) {

  public MigrationRequest {
    Objects.requireNonNull(dataSource, "dataSource");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(plan, "plan");
  }
}
