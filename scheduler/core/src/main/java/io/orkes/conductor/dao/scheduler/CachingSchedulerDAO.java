/*
 * Copyright 2026 Conductor Authors.
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
package io.orkes.conductor.dao.scheduler;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.netflix.conductor.common.run.SearchResult;

import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;
import io.orkes.conductor.scheduler.model.WorkflowScheduleModel;

/**
 * Decorating {@link SchedulerDAO} that layers a {@link SchedulerCacheDAO} on top of a delegate DAO.
 * Follows the same integration pattern as Orkes Conductor:
 *
 * <ul>
 *   <li>{@code updateSchedule} — write-through (cache + DB)
 *   <li>{@code findScheduleByName} — read cache first, fall back to DB on miss
 *   <li>{@code deleteWorkflowSchedule} — invalidate cache, then delete from DB
 *   <li>{@code getNextRunTimeInEpoch} — cache <b>only</b> (DB fallback when no cache)
 *   <li>{@code setNextRunTimeInEpoch} — cache <b>only</b> (DB fallback when no cache)
 * </ul>
 *
 * <p>All other methods (execution records, bulk queries, search) delegate directly.
 */
public class CachingSchedulerDAO implements SchedulerDAO {

    private final SchedulerDAO delegate;
    private final SchedulerCacheDAO cache;

    public CachingSchedulerDAO(SchedulerDAO delegate, SchedulerCacheDAO cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public void updateSchedule(WorkflowScheduleModel workflowSchedule) {
        cache.updateSchedule(workflowSchedule);
        delegate.updateSchedule(workflowSchedule);
    }

    @Override
    public WorkflowScheduleModel findScheduleByName(String name) {
        WorkflowScheduleModel cached = cache.findScheduleByName(name);
        if (cached != null) {
            return cached;
        }
        return delegate.findScheduleByName(name);
    }

    @Override
    public void deleteWorkflowSchedule(String name) {
        cache.deleteWorkflowSchedule(name);
        delegate.deleteWorkflowSchedule(name);
    }

    @Override
    public long getNextRunTimeInEpoch(String scheduleName) {
        return cache.getNextRunTimeInEpoch(scheduleName);
    }

    @Override
    public void setNextRunTimeInEpoch(String name, long toEpochMilli) {
        cache.setNextRunTimeInEpoch(name, toEpochMilli);
    }

    // -- Pure delegation (no caching) -----------------------------------------

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecutionModel executionModel) {
        delegate.saveExecutionRecord(executionModel);
    }

    @Override
    public WorkflowScheduleExecutionModel readExecutionRecord(String executionId) {
        return delegate.readExecutionRecord(executionId);
    }

    @Override
    public void removeExecutionRecord(String executionId) {
        delegate.removeExecutionRecord(executionId);
    }

    @Override
    public List<WorkflowScheduleModel> findAllSchedules(String workflowName) {
        return delegate.findAllSchedules(workflowName);
    }

    @Override
    public List<String> getPendingExecutionRecordIds() {
        return delegate.getPendingExecutionRecordIds();
    }

    @Override
    public List<WorkflowScheduleModel> getAllSchedules() {
        return delegate.getAllSchedules();
    }

    @Override
    public Map<String, WorkflowScheduleModel> findAllByNames(Set<String> workflowScheduleNames) {
        return delegate.findAllByNames(workflowScheduleNames);
    }

    @Override
    public SearchResult<WorkflowScheduleModel> searchSchedules(
            String workflowName,
            String scheduleName,
            Boolean paused,
            String freeText,
            int start,
            int size,
            List<String> sortOptions) {
        return delegate.searchSchedules(
                workflowName, scheduleName, paused, freeText, start, size, sortOptions);
    }
}
