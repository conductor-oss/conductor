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

import org.conductoross.conductor.scheduler.model.WorkflowSchedule;

/**
 * Optional cache layer for {@link SchedulerDAO}.
 *
 * <p>Mirrors the {@code SchedulerCacheDAO} pattern from Orkes Conductor. Implementations (e.g.
 * Redis) provide fast in-memory reads for the hot path; the authoritative SQL {@link SchedulerDAO}
 * remains the source of truth.
 *
 * <p>All methods are keyed by schedule name only (no orgId — OSS is single-tenant).
 */
public interface SchedulerCacheDAO {

    /** Stores or updates a schedule in the cache. */
    void updateSchedule(WorkflowSchedule schedule);

    /**
     * Returns the cached schedule, or {@code null} if not present.
     *
     * @param name schedule name
     */
    WorkflowSchedule findScheduleByName(String name);

    /**
     * Returns {@code true} if the schedule exists in the cache.
     *
     * @param name schedule name
     */
    boolean exists(String name);

    /**
     * Removes the schedule from the cache.
     *
     * @param name schedule name
     */
    void deleteWorkflowSchedule(String name);

    /**
     * Returns the cached next-run epoch millis, or {@code -1} if not set.
     *
     * @param scheduleName schedule name
     */
    long getNextRunTimeInEpoch(String scheduleName);

    /**
     * Stores the next-run epoch millis in the cache.
     *
     * @param scheduleName schedule name
     * @param epochMillis epoch millis of the next planned execution
     */
    void setNextRunTimeInEpoch(String scheduleName, long epochMillis);
}
