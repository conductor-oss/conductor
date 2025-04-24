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
package io.orkes.conductor.client.model;

import java.util.List;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

import io.orkes.conductor.common.metadata.tags.Tag;

import lombok.*;

@EqualsAndHashCode
@ToString
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowSchedule {

    private Long createTime = null;

    private String createdBy = null;

    private String cronExpression = null;

    private String name = null;

    private Boolean paused = null;

    private Boolean runCatchupScheduleInstances = null;

    private Long scheduleEndTime = null;

    private Long scheduleStartTime = null;

    private StartWorkflowRequest startWorkflowRequest = null;

    private String updatedBy = null;

    private Long updatedTime = null;

    private String zoneId;

    private List<Tag> tags;

    private String pausedReason;

    private String description;

    public WorkflowSchedule createTime(Long createTime) {
        this.createTime = createTime;
        return this;
    }

    public WorkflowSchedule createdBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public WorkflowSchedule cronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
        return this;
    }

    public WorkflowSchedule name(String name) {
        this.name = name;
        return this;
    }

    public WorkflowSchedule paused(Boolean paused) {
        this.paused = paused;
        return this;
    }

    public Boolean isPaused() {
        return paused;
    }

    public WorkflowSchedule runCatchupScheduleInstances(Boolean runCatchupScheduleInstances) {
        this.runCatchupScheduleInstances = runCatchupScheduleInstances;
        return this;
    }

    public Boolean isRunCatchupScheduleInstances() {
        return runCatchupScheduleInstances;
    }

    public WorkflowSchedule scheduleEndTime(Long scheduleEndTime) {
        this.scheduleEndTime = scheduleEndTime;
        return this;
    }

    public WorkflowSchedule scheduleStartTime(Long scheduleStartTime) {
        this.scheduleStartTime = scheduleStartTime;
        return this;
    }

    public WorkflowSchedule startWorkflowRequest(StartWorkflowRequest startWorkflowRequest) {
        this.startWorkflowRequest = startWorkflowRequest;
        return this;
    }

    public WorkflowSchedule updatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        return this;
    }

    public WorkflowSchedule updatedTime(Long updatedTime) {
        this.updatedTime = updatedTime;
        return this;
    }

    public WorkflowSchedule zoneId(String zoneId) {
        this.zoneId = zoneId;
        return this;
    }
}