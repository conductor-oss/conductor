/*
 * Copyright 2022 Orkes, Inc.
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

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class WorkflowScheduleExecutionModel {

    private String executionId = null;

    private Long executionTime = null;

    private String reason = null;

    private String scheduleName = null;

    private Long scheduledTime = null;

    private String stackTrace = null;

    private StartWorkflowRequest startWorkflowRequest = null;

    public enum StateEnum {
        POLLED("POLLED"),
        FAILED("FAILED"),
        EXECUTED("EXECUTED");

        private final String value;

        StateEnum(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }

        public static StateEnum fromValue(String input) {
            for (StateEnum b : StateEnum.values()) {
                if (b.value.equals(input)) {
                    return b;
                }
            }
            return null;
        }
    }

    private StateEnum state = null;

    private String workflowId = null;

    private String workflowName = null;

    public WorkflowScheduleExecutionModel executionId(String executionId) {
        this.executionId = executionId;
        return this;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public WorkflowScheduleExecutionModel executionTime(Long executionTime) {
        this.executionTime = executionTime;
        return this;
    }

    public Long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(Long executionTime) {
        this.executionTime = executionTime;
    }

    public WorkflowScheduleExecutionModel reason(String reason) {
        this.reason = reason;
        return this;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public WorkflowScheduleExecutionModel scheduleName(String scheduleName) {
        this.scheduleName = scheduleName;
        return this;
    }

    public String getScheduleName() {
        return scheduleName;
    }

    public void setScheduleName(String scheduleName) {
        this.scheduleName = scheduleName;
    }

    public WorkflowScheduleExecutionModel scheduledTime(Long scheduledTime) {
        this.scheduledTime = scheduledTime;
        return this;
    }

    public Long getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(Long scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public WorkflowScheduleExecutionModel stackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
        return this;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public WorkflowScheduleExecutionModel startWorkflowRequest(
            StartWorkflowRequest startWorkflowRequest) {
        this.startWorkflowRequest = startWorkflowRequest;
        return this;
    }

    public StartWorkflowRequest getStartWorkflowRequest() {
        return startWorkflowRequest;
    }

    public void setStartWorkflowRequest(StartWorkflowRequest startWorkflowRequest) {
        this.startWorkflowRequest = startWorkflowRequest;
    }

    public WorkflowScheduleExecutionModel state(StateEnum state) {
        this.state = state;
        return this;
    }

    public StateEnum getState() {
        return state;
    }

    public void setState(StateEnum state) {
        this.state = state;
    }

    public WorkflowScheduleExecutionModel workflowId(String workflowId) {
        this.workflowId = workflowId;
        return this;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public WorkflowScheduleExecutionModel workflowName(String workflowName) {
        this.workflowName = workflowName;
        return this;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }
}
