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
package org.conductoross.conductor.scheduler.model;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

/**
 * Represents a scheduled workflow execution.
 *
 * <p>The {@code orgId} field is retained for convergence with Orkes Conductor's data model. In OSS,
 * {@code orgId} is always set to {@link #DEFAULT_ORG_ID} ("default"). Orkes Conductor can extend
 * this model by injecting the actual org from request context.
 */
public class WorkflowSchedule {

    public static final String DEFAULT_ORG_ID = "default";

    /** Always "default" in OSS; used by Orkes for multi-tenancy. */
    private String orgId = DEFAULT_ORG_ID;

    /** Unique name for this schedule. */
    private String name;

    /** 6-field cron expression (second precision), e.g. "0 0 9 * * MON-FRI". */
    private String cronExpression;

    /** Timezone for cron evaluation, e.g. "America/New_York". Defaults to UTC. */
    private String zoneId = "UTC";

    /** When true, the schedule will not trigger new executions. */
    private boolean paused;

    /** Optional explanation for why the schedule is paused. */
    private String pausedReason;

    /** The workflow to start when this schedule fires. */
    private StartWorkflowRequest startWorkflowRequest;

    /** Epoch millis; if set, do not trigger before this time. */
    private Long scheduleStartTime;

    /** Epoch millis; if set, do not trigger after this time. */
    private Long scheduleEndTime;

    /**
     * When true and the server restarts after downtime, execute all missed schedule instances
     * sequentially before resuming normal cadence.
     */
    private boolean runCatchupScheduleInstances;

    /** Epoch millis when this schedule was created. */
    private Long createTime;

    /** Epoch millis when this schedule was last updated. */
    private Long updatedTime;

    /** Optional: who created this schedule. Not enforced in OSS. */
    private String createdBy;

    /** Optional: who last updated this schedule. Not enforced in OSS. */
    private String updatedBy;

    /** Optional description of the schedule. */
    private String description;

    /** Cached epoch millis of the next planned execution. Updated after each trigger. */
    private Long nextRunTime;

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public String getPausedReason() {
        return pausedReason;
    }

    public void setPausedReason(String pausedReason) {
        this.pausedReason = pausedReason;
    }

    public StartWorkflowRequest getStartWorkflowRequest() {
        return startWorkflowRequest;
    }

    public void setStartWorkflowRequest(StartWorkflowRequest startWorkflowRequest) {
        this.startWorkflowRequest = startWorkflowRequest;
    }

    public Long getScheduleStartTime() {
        return scheduleStartTime;
    }

    public void setScheduleStartTime(Long scheduleStartTime) {
        this.scheduleStartTime = scheduleStartTime;
    }

    public Long getScheduleEndTime() {
        return scheduleEndTime;
    }

    public void setScheduleEndTime(Long scheduleEndTime) {
        this.scheduleEndTime = scheduleEndTime;
    }

    public boolean isRunCatchupScheduleInstances() {
        return runCatchupScheduleInstances;
    }

    public void setRunCatchupScheduleInstances(boolean runCatchupScheduleInstances) {
        this.runCatchupScheduleInstances = runCatchupScheduleInstances;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(Long updatedTime) {
        this.updatedTime = updatedTime;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getNextRunTime() {
        return nextRunTime;
    }

    public void setNextRunTime(Long nextRunTime) {
        this.nextRunTime = nextRunTime;
    }
}
