/*
 * Copyright 2024 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.scheduler.dao;

import java.util.List;

import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;

/**
 * Data access interface for workflow schedules and their execution history.
 *
 * <p>This interface is shared between OSS and Orkes Conductor. Orkes injects multi-tenancy (orgId)
 * within the DAO implementation layer; OSS uses a single-tenant schema with no orgId.
 */
public interface SchedulerDAO {

    // -------------------------------------------------------------------------
    // Schedule CRUD
    // -------------------------------------------------------------------------

    /** Persists or updates a schedule. */
    void updateSchedule(WorkflowSchedule schedule);

    /**
     * Returns the schedule with the given name, or {@code null} if not found.
     *
     * @param name schedule name
     */
    WorkflowSchedule findScheduleByName(String name);

    /** Returns all schedules. */
    List<WorkflowSchedule> getAllSchedules();

    /**
     * Returns all schedules that trigger a particular workflow.
     *
     * @param workflowName the workflow definition name to filter by
     */
    List<WorkflowSchedule> findAllSchedules(String workflowName);

    /**
     * Permanently removes a schedule.
     *
     * @param name schedule name
     */
    void deleteWorkflowSchedule(String name);

    // -------------------------------------------------------------------------
    // Execution tracking
    // -------------------------------------------------------------------------

    /** Saves an execution record (POLLED → EXECUTED/FAILED lifecycle). */
    void saveExecutionRecord(WorkflowScheduleExecution execution);

    /**
     * Returns the execution record with the given ID.
     *
     * @param executionId UUID assigned when the record was created
     */
    WorkflowScheduleExecution readExecutionRecord(String executionId);

    /**
     * Deletes an execution record (used during cleanup).
     *
     * @param executionId UUID of the record to remove
     */
    void removeExecutionRecord(String executionId);

    /**
     * Returns the IDs of all execution records currently in the POLLED state. Used by the scheduler
     * to detect and clean up stale entries.
     */
    List<String> getPendingExecutionRecordIds();

    /**
     * Returns recent execution records for a given schedule, ordered by execution time descending.
     *
     * @param scheduleName schedule to query
     * @param limit maximum number of records to return
     */
    List<WorkflowScheduleExecution> getExecutionRecords(String scheduleName, int limit);

    // -------------------------------------------------------------------------
    // Next-run time management
    // -------------------------------------------------------------------------

    /**
     * Returns the cached next-run epoch millis for the given schedule.
     *
     * @param scheduleName schedule name
     * @return epoch millis, or {@code -1} if not set
     */
    long getNextRunTimeInEpoch(String scheduleName);

    /**
     * Caches the next-run epoch millis for the given schedule.
     *
     * @param scheduleName schedule name
     * @param epochMillis epoch millis of the next planned execution
     */
    void setNextRunTimeInEpoch(String scheduleName, long epochMillis);
}
