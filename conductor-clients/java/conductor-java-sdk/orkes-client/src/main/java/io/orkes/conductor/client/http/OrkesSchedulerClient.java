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
package io.orkes.conductor.client.http;

import java.util.List;

import com.netflix.conductor.client.http.ConductorClient;

import io.orkes.conductor.client.SchedulerClient;
import io.orkes.conductor.client.model.SaveScheduleRequest;
import io.orkes.conductor.client.model.SearchResultWorkflowScheduleExecution;
import io.orkes.conductor.client.model.TagObject;
import io.orkes.conductor.client.model.WorkflowSchedule;

public class OrkesSchedulerClient implements SchedulerClient {

    private final SchedulerResource schedulerResource;

    public OrkesSchedulerClient(ConductorClient apiClient) {
        this.schedulerResource = new SchedulerResource(apiClient);
    }

    @Override
    public void deleteSchedule(String name) {
        schedulerResource.deleteSchedule(name);
    }

    @Override
    public List<WorkflowSchedule> getAllSchedules(String workflowName) {
        return schedulerResource.getAllSchedules(workflowName);
    }

    @Override
    public List<Long> getNextFewSchedules(String cronExpression,
                                          Long scheduleStartTime,
                                          Long scheduleEndTime,
                                          Integer limit) {
        return schedulerResource.getNextFewSchedules(
                cronExpression, scheduleStartTime, scheduleEndTime, limit);
    }

    @Override
    public WorkflowSchedule getSchedule(String name) {
        return schedulerResource.getSchedule(name);
    }

    @Override
    public void pauseAllSchedules() {
        schedulerResource.pauseAllSchedules();
    }

    @Override
    public void pauseSchedule(String name) {
        schedulerResource.pauseSchedule(name);
    }

    @Override
    public void requeueAllExecutionRecords() {
        schedulerResource.requeueAllExecutionRecords();
    }

    @Override
    public void resumeAllSchedules() {
        schedulerResource.resumeAllSchedules();
    }

    @Override
    public void resumeSchedule(String name) {
        schedulerResource.resumeSchedule(name);
    }

    @Override
    public void saveSchedule(SaveScheduleRequest saveScheduleRequest) {
        schedulerResource.saveSchedule(saveScheduleRequest);
    }

    @Override
    public SearchResultWorkflowScheduleExecution search(Integer start, Integer size, String sort, String freeText, String query) {
        return schedulerResource.search(start, size, sort, freeText, query);
    }

    @Override
    public void setSchedulerTags(List<TagObject> body, String name) {
        schedulerResource.putTagForSchedule(name, body);
    }

    @Override
    public void deleteSchedulerTags(List<TagObject> body, String name) {
        schedulerResource.deleteTagForSchedule(name, body);
    }

    @Override
    public List<TagObject> getSchedulerTags(String name) {
        return schedulerResource.getTagsForSchedule(name);
    }
}
