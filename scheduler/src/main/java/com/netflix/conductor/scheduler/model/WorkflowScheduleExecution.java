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
package com.netflix.conductor.scheduler.model;

/**
 * Records a single scheduled execution attempt.
 *
 * <p>The {@code orgId} field is retained for convergence with Orkes Conductor. In OSS it is always
 * {@link WorkflowSchedule#DEFAULT_ORG_ID}.
 */
public class WorkflowScheduleExecution {

    public enum ExecutionState {
        /** The schedule was polled from the queue; workflow start has not been confirmed yet. */
        POLLED,
        /** The workflow was started successfully. */
        EXECUTED,
        /** Workflow start failed; see {@code reason} for details. */
        FAILED
    }

    /** Always "default" in OSS. */
    private String orgId = WorkflowSchedule.DEFAULT_ORG_ID;

    /** Unique ID for this execution record. */
    private String executionId;

    /** Name of the schedule that triggered this execution. */
    private String scheduleName;

    /** Conductor workflow instance ID, populated once the workflow is started. */
    private String workflowId;

    /** Epoch millis of when this execution was scheduled to fire. */
    private Long scheduledTime;

    /** Epoch millis of when the execution was actually processed. */
    private Long executionTime;

    /** Current state of this execution. */
    private ExecutionState state;

    /** Error message or explanation if state is FAILED. */
    private String reason;

    /** Timezone that was in effect when this execution fired. */
    private String zoneId;

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getScheduleName() {
        return scheduleName;
    }

    public void setScheduleName(String scheduleName) {
        this.scheduleName = scheduleName;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public Long getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(Long scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public Long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(Long executionTime) {
        this.executionTime = executionTime;
    }

    public ExecutionState getState() {
        return state;
    }

    public void setState(ExecutionState state) {
        this.state = state;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }
}
