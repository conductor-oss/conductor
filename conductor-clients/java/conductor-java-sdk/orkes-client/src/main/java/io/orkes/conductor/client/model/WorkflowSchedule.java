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

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
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

    private List<Tag> tags = null;

    private String pausedReason = null;

    private String description = null;

    public WorkflowSchedule createTime(Long createTime) {
        this.createTime = createTime;
        return this;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public WorkflowSchedule createdBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public WorkflowSchedule cronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
        return this;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public WorkflowSchedule name(String name) {
        this.name = name;
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public WorkflowSchedule paused(Boolean paused) {
        this.paused = paused;
        return this;
    }

    public Boolean isPaused() {
        return paused;
    }

    public void setPaused(Boolean paused) {
        this.paused = paused;
    }

    public WorkflowSchedule runCatchupScheduleInstances(Boolean runCatchupScheduleInstances) {
        this.runCatchupScheduleInstances = runCatchupScheduleInstances;
        return this;
    }

    public Boolean isRunCatchupScheduleInstances() {
        return runCatchupScheduleInstances;
    }

    public void setRunCatchupScheduleInstances(Boolean runCatchupScheduleInstances) {
        this.runCatchupScheduleInstances = runCatchupScheduleInstances;
    }

    public WorkflowSchedule scheduleEndTime(Long scheduleEndTime) {
        this.scheduleEndTime = scheduleEndTime;
        return this;
    }

    public Long getScheduleEndTime() {
        return scheduleEndTime;
    }

    public void setScheduleEndTime(Long scheduleEndTime) {
        this.scheduleEndTime = scheduleEndTime;
    }

    public WorkflowSchedule scheduleStartTime(Long scheduleStartTime) {
        this.scheduleStartTime = scheduleStartTime;
        return this;
    }

    public Long getScheduleStartTime() {
        return scheduleStartTime;
    }

    public void setScheduleStartTime(Long scheduleStartTime) {
        this.scheduleStartTime = scheduleStartTime;
    }

    public WorkflowSchedule startWorkflowRequest(StartWorkflowRequest startWorkflowRequest) {
        this.startWorkflowRequest = startWorkflowRequest;
        return this;
    }

    public StartWorkflowRequest getStartWorkflowRequest() {
        return startWorkflowRequest;
    }

    public void setStartWorkflowRequest(StartWorkflowRequest startWorkflowRequest) {
        this.startWorkflowRequest = startWorkflowRequest;
    }

    public WorkflowSchedule updatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        return this;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public WorkflowSchedule updatedTime(Long updatedTime) {
        this.updatedTime = updatedTime;
        return this;
    }

    public Long getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(Long updatedTime) {
        this.updatedTime = updatedTime;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public WorkflowSchedule zoneId(String zoneId) {
        this.zoneId = zoneId;
        return this;
    }

    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    public WorkflowSchedule tags(List<Tag> tags) {
        this.tags = tags;
        return this;
    }

    public String getPausedReason() {
        return pausedReason;
    }

    public void setPausedReason(String pausedReason) {
        this.pausedReason = pausedReason;
    }

    public WorkflowSchedule pausedReason(String pausedReason) {
        this.pausedReason = pausedReason;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public WorkflowSchedule description(String description) {
        this.description = description;
        return this;
    }
}