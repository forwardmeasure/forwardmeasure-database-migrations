package com.forwardmeasure.database.migration.api;

/** Provider-neutral state of one declared migration change. */
public enum MigrationChangeState {
  APPLIED,
  APPLIED_AND_PENDING,
  PENDING,
  FILTERED
}
