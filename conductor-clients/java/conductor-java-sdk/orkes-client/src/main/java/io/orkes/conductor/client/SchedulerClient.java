/*
 * Copyright 2022 Conductor Authors.
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
package io.orkes.conductor.client;

import java.util.List;

import io.orkes.conductor.client.model.SaveScheduleRequest;
import io.orkes.conductor.client.model.SearchResultWorkflowScheduleExecution;
import io.orkes.conductor.client.model.TagObject;
import io.orkes.conductor.client.model.WorkflowSchedule;

public interface SchedulerClient {
    void deleteSchedule(String name);

    List<WorkflowSchedule> getAllSchedules(String workflowName);

    List<Long> getNextFewSchedules(String cronExpression, Long scheduleStartTime, Long scheduleEndTime, Integer limit);

    WorkflowSchedule getSchedule(String name);

    void pauseAllSchedules();

    void pauseSchedule(String name);

    void requeueAllExecutionRecords();

    void resumeAllSchedules();

    void resumeSchedule(String name);

    void saveSchedule(SaveScheduleRequest saveScheduleRequest);

    SearchResultWorkflowScheduleExecution search(Integer start, Integer size, String sort, String freeText, String query);

    void setSchedulerTags(List<TagObject> body, String name);

    void deleteSchedulerTags(List<TagObject> body, String name);

    List<TagObject> getSchedulerTags(String name);

    }
