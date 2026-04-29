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

/**
 * Simple in-memory SchedulerDAO for OSS unit tests. Keyed by orgId + name/id to simulate
 * multi-tenant storage.
 */
public class InMemorySchedulerDAO implements SchedulerDAO {

    private final Map<String, WorkflowScheduleModel> schedules = new ConcurrentHashMap<>();
    private final Map<String, WorkflowScheduleExecutionModel> executionRecords =
            new ConcurrentHashMap<>();
    private final Map<String, Long> nextRunTimes = new ConcurrentHashMap<>();

    private String key(String orgId, String name) {
        return orgId + ":" + name;
    }

    public void clear() {
        schedules.clear();
        executionRecords.clear();
        nextRunTimes.clear();
    }

    @Override
    public void updateSchedule(WorkflowScheduleModel workflowSchedule) {
        schedules.put(
                key(workflowSchedule.getOrgId(), workflowSchedule.getName()), workflowSchedule);
    }

    @Override
    public void saveExecutionRecord(WorkflowScheduleExecutionModel executionModel) {
        executionRecords.put(
                key(executionModel.getOrgId(), executionModel.getExecutionId()), executionModel);
    }

    @Override
    public WorkflowScheduleExecutionModel readExecutionRecord(String orgId, String executionId) {
        return executionRecords.get(key(orgId, executionId));
    }

    @Override
    public void removeExecutionRecord(String orgId, String executionId) {
        executionRecords.remove(key(orgId, executionId));
    }

    @Override
    public WorkflowScheduleModel findScheduleByName(String orgId, String name) {
        return schedules.get(key(orgId, name));
    }

    @Override
    public List<WorkflowScheduleModel> findAllSchedules(String orgId, String workflowName) {
        return schedules.values().stream()
                .filter(s -> orgId.equals(s.getOrgId()))
                .filter(
                        s ->
                                workflowName == null
                                        || workflowName.equals(
                                                s.getStartWorkflowRequest().getName()))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteWorkflowSchedule(String orgId, String name) {
        schedules.remove(key(orgId, name));
    }

    @Override
    public List<String> getPendingExecutionRecordIds(String orgId) {
        return executionRecords.entrySet().stream()
                .filter(e -> e.getKey().startsWith(orgId + ":"))
                .map(e -> e.getValue().getExecutionId())
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkflowScheduleModel> getAllSchedules(String orgId) {
        return schedules.values().stream()
                .filter(s -> orgId.equals(s.getOrgId()))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, WorkflowScheduleModel> findAllByNames(
            String orgId, Set<String> workflowScheduleNames) {
        Map<String, WorkflowScheduleModel> result = new HashMap<>();
        for (String name : workflowScheduleNames) {
            WorkflowScheduleModel s = schedules.get(key(orgId, name));
            if (s != null) {
                result.put(name, s);
            }
        }
        return result;
    }

    @Override
    public long getNextRunTimeInEpoch(String orgId, String scheduleName) {
        return nextRunTimes.getOrDefault(key(orgId, scheduleName), -1L);
    }

    @Override
    public void setNextRunTimeInEpoch(String orgId, String name, long toEpochMilli) {
        nextRunTimes.put(key(orgId, name), toEpochMilli);
    }

    @Override
    public SearchResult<WorkflowScheduleModel> searchSchedules(
            String orgId,
            String workflowName,
            String scheduleName,
            Boolean paused,
            String freeText,
            int start,
            int size,
            List<String> sortOptions) {
        List<WorkflowScheduleModel> filtered =
                schedules.values().stream()
                        .filter(s -> orgId.equals(s.getOrgId()))
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
