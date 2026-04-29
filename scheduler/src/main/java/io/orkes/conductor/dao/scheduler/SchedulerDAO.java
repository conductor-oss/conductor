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

public interface SchedulerDAO {

    void updateSchedule(WorkflowScheduleModel workflowSchedule);

    void saveExecutionRecord(WorkflowScheduleExecutionModel executionModel);

    WorkflowScheduleExecutionModel readExecutionRecord(String orgId, String executionId);

    void removeExecutionRecord(String orgId, String executionId);

    WorkflowScheduleModel findScheduleByName(String orgId, String name);

    List<WorkflowScheduleModel> findAllSchedules(String orgId, String workflowName);

    void deleteWorkflowSchedule(String orgId, String name);

    List<String> getPendingExecutionRecordIds(String orgId);

    List<WorkflowScheduleModel> getAllSchedules(String orgId);

    Map<String, WorkflowScheduleModel> findAllByNames(
            String orgId, Set<String> workflowScheduleNames);

    long getNextRunTimeInEpoch(String orgId, String scheduleName);

    void setNextRunTimeInEpoch(String orgId, String name, long toEpochMilli);

    SearchResult<WorkflowScheduleModel> searchSchedules(
            String orgId,
            String workflowName,
            String scheduleName,
            Boolean paused,
            String freeText,
            int start,
            int size,
            List<String> sortOptions);
}
