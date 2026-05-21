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
package io.orkes.conductor.scheduler.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.netflix.conductor.common.run.SearchResult;

import io.orkes.conductor.dao.scheduler.SchedulerDAO;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;
import io.orkes.conductor.scheduler.model.WorkflowScheduleModel;

/** Simple in-memory SchedulerDAO for OSS unit tests. Keyed by name/id. */
public class InMemorySchedulerDAO implements SchedulerDAO {

    private final Map<String, WorkflowScheduleModel> schedules = new ConcurrentHashMap<>();
    private final Map<String, WorkflowScheduleExecutionModel> executionRecords =
            new ConcurrentHashMap<>();
    private final Map<String, Long> nextRunTimes = new ConcurrentHashMap<>();

    public void clear() {
        schedules.clear();
        executionRecords.clear();
        nextRunTimes.clear();
    }

    @Override
    public void updateSchedule(WorkflowScheduleModel workflowSchedule) {
        schedules.put(workflowSchedule.getName(), workflowSchedule);
    }

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecutionModel executionModel) {
        executionRecords.put(executionModel.getExecutionId(), executionModel);
    }

    @Override
    public WorkflowScheduleExecutionModel readExecutionRecord(String executionId) {
        return executionRecords.get(executionId);
    }

    @Override
    public void removeExecutionRecord(String executionId) {
        executionRecords.remove(executionId);
    }

    @Override
    public WorkflowScheduleModel findScheduleByName(String name) {
        return schedules.get(name);
    }

    @Override
    public List<WorkflowScheduleModel> findAllSchedules(String workflowName) {
        return schedules.values().stream()
                .filter(
                        s ->
                                workflowName == null
                                        || workflowName.equals(
                                                s.getStartWorkflowRequest().getName()))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteWorkflowSchedule(String name) {
        schedules.remove(name);
    }

    @Override
    public List<String> getPendingExecutionRecordIds() {
        return executionRecords.values().stream()
                .map(WorkflowScheduleExecutionModel::getExecutionId)
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkflowScheduleModel> getAllSchedules() {
        return new ArrayList<>(schedules.values());
    }

    @Override
    public Map<String, WorkflowScheduleModel> findAllByNames(Set<String> workflowScheduleNames) {
        Map<String, WorkflowScheduleModel> result = new HashMap<>();
        for (String name : workflowScheduleNames) {
            WorkflowScheduleModel s = schedules.get(name);
            if (s != null) {
                result.put(name, s);
            }
        }
        return result;
    }

    @Override
    public long getNextRunTimeInEpoch(String scheduleName) {
        return nextRunTimes.getOrDefault(scheduleName, -1L);
    }

    @Override
    public void setNextRunTimeInEpoch(String name, long toEpochMilli) {
        nextRunTimes.put(name, toEpochMilli);
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
        List<WorkflowScheduleModel> filtered =
                schedules.values().stream()
                        .filter(
                                s ->
                                        workflowName == null
                                                || workflowName.equals(
                                                        s.getStartWorkflowRequest().getName()))
                        .filter(s -> scheduleName == null || s.getName().contains(scheduleName))
                        .filter(s -> paused == null || s.isPaused() == paused)
                        .collect(Collectors.toList());

        if (sortOptions != null && !sortOptions.isEmpty()) {
            String sortOption = sortOptions.get(0);
            String[] parts = sortOption.split(":");
            String field = parts[0];
            boolean asc = parts.length < 2 || "ASC".equalsIgnoreCase(parts[1]);
            if ("name".equals(field)) {
                filtered.sort(
                        asc
                                ? Comparator.comparing(WorkflowScheduleModel::getName)
                                : Comparator.comparing(WorkflowScheduleModel::getName).reversed());
            }
        }

        int total = filtered.size();
        int end = Math.min(start + size, total);
        List<WorkflowScheduleModel> page =
                start < total ? filtered.subList(start, end) : Collections.emptyList();
        return new SearchResult<>(total, page);
    }
}
