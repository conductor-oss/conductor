/*
 * Copyright 2020 Orkes, Inc.
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
package com.netflix.conductor.common.run;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.common.utils.SummaryUtil;

/**
 * Captures workflow summary info to be indexed in Elastic Search.
 */
public class WorkflowSummary {

    /**
     * The time should be stored as GMT
     */
    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    private String workflowType;

    private int version;

    private String workflowId;

    private String correlationId;

    private String startTime;

    private String updateTime;

    private String endTime;

    private Workflow.WorkflowStatus status;

    private String input;

    private String output;

    private String reasonForIncompletion;

    private long executionTime;

    private String event;

    private String failedReferenceTaskNames = "";

    private String externalInputPayloadStoragePath;

    private String externalOutputPayloadStoragePath;

    private int priority;

    private Set<String> failedTaskNames = new HashSet<>();

    private String createdBy;

    public WorkflowSummary() {
    }

    public WorkflowSummary(Workflow workflow) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(GMT);
        this.workflowType = workflow.getWorkflowName();
        this.version = workflow.getWorkflowVersion();
        this.workflowId = workflow.getWorkflowId();
        this.priority = workflow.getPriority();
        this.correlationId = workflow.getCorrelationId();
        if (workflow.getCreateTime() != null) {
            this.startTime = sdf.format(new Date(workflow.getCreateTime()));
        }
        if (workflow.getEndTime() > 0) {
            this.endTime = sdf.format(new Date(workflow.getEndTime()));
        }
        if (workflow.getUpdateTime() != null) {
            this.updateTime = sdf.format(new Date(workflow.getUpdateTime()));
        }
        this.status = workflow.getStatus();
        if (workflow.getInput() != null) {
            this.input = SummaryUtil.serializeInputOutput(workflow.getInput());
        }
        if (workflow.getOutput() != null) {
            this.output = SummaryUtil.serializeInputOutput(workflow.getOutput());
        }
        this.reasonForIncompletion = workflow.getReasonForIncompletion();
        if (workflow.getEndTime() > 0) {
            this.executionTime = workflow.getEndTime() - workflow.getStartTime();
        }
        this.event = workflow.getEvent();
        this.failedReferenceTaskNames = workflow.getFailedReferenceTaskNames().stream().collect(Collectors.joining(","));
        this.failedTaskNames = workflow.getFailedTaskNames();
        if (StringUtils.isNotBlank(workflow.getExternalInputPayloadStoragePath())) {
            this.externalInputPayloadStoragePath = workflow.getExternalInputPayloadStoragePath();
        }
        if (StringUtils.isNotBlank(workflow.getExternalOutputPayloadStoragePath())) {
            this.externalOutputPayloadStoragePath = workflow.getExternalOutputPayloadStoragePath();
        }
    }

    /**
     * @return the workflowType
     */
    public String getWorkflowType() {
        return workflowType;
    }

    /**
     * @return the version
     */
    public int getVersion() {
        return version;
    }

    /**
     * @return the workflowId
     */
    public String getWorkflowId() {
        return workflowId;
    }

    /**
     * @return the correlationId
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * @return the startTime
     */
    public String getStartTime() {
        return startTime;
    }

    /**
     * @return the endTime
     */
    public String getEndTime() {
        return endTime;
    }

    /**
     * @return the status
     */
    public WorkflowStatus getStatus() {
        return status;
    }

    /**
     * @return the input
     */
    public String getInput() {
        return input;
    }

    public long getInputSize() {
        return input != null ? input.length() : 0;
    }

    /**
     * @return the output
     */
    public String getOutput() {
        return output;
    }

    public long getOutputSize() {
        return output != null ? output.length() : 0;
    }

    /**
     * @return the reasonForIncompletion
     */
    public String getReasonForIncompletion() {
        return reasonForIncompletion;
    }

    /**
     * @return the executionTime
     */
    public long getExecutionTime() {
        return executionTime;
    }

    /**
     * @return the updateTime
     */
    public String getUpdateTime() {
        return updateTime;
    }

    /**
     * @return The event
     */
    public String getEvent() {
        return event;
    }

    /**
     * @param event The event
     */
    public void setEvent(String event) {
        this.event = event;
    }

    public String getFailedReferenceTaskNames() {
        return failedReferenceTaskNames;
    }

    public void setFailedReferenceTaskNames(String failedReferenceTaskNames) {
        this.failedReferenceTaskNames = failedReferenceTaskNames;
    }

    public Set<String> getFailedTaskNames() {
        return failedTaskNames;
    }

    public void setFailedTaskNames(Set<String> failedTaskNames) {
        this.failedTaskNames = failedTaskNames;
    }

    public void setWorkflowType(String workflowType) {
        this.workflowType = workflowType;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public void setStatus(WorkflowStatus status) {
        this.status = status;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public void setReasonForIncompletion(String reasonForIncompletion) {
        this.reasonForIncompletion = reasonForIncompletion;
    }

    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }

    /**
     * @return the external storage path of the workflow input payload
     */
    public String getExternalInputPayloadStoragePath() {
        return externalInputPayloadStoragePath;
    }

    /**
     * @param externalInputPayloadStoragePath the external storage path where the workflow input
     *     payload is stored
     */
    public void setExternalInputPayloadStoragePath(String externalInputPayloadStoragePath) {
        this.externalInputPayloadStoragePath = externalInputPayloadStoragePath;
    }

    /**
     * @return the external storage path of the workflow output payload
     */
    public String getExternalOutputPayloadStoragePath() {
        return externalOutputPayloadStoragePath;
    }

    /**
     * @param externalOutputPayloadStoragePath the external storage path where the workflow output
     *     payload is stored
     */
    public void setExternalOutputPayloadStoragePath(String externalOutputPayloadStoragePath) {
        this.externalOutputPayloadStoragePath = externalOutputPayloadStoragePath;
    }

    /**
     * @return the priority to define on tasks
     */
    public int getPriority() {
        return priority;
    }

    /**
     * @param priority priority of tasks (between 0 and 99)
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WorkflowSummary that = (WorkflowSummary) o;
        return getVersion() == that.getVersion() && getExecutionTime() == that.getExecutionTime() && getPriority() == that.getPriority() && getWorkflowType().equals(that.getWorkflowType()) && getWorkflowId().equals(that.getWorkflowId()) && Objects.equals(getCorrelationId(), that.getCorrelationId()) && StringUtils.equals(getStartTime(), that.getStartTime()) && StringUtils.equals(getUpdateTime(), that.getUpdateTime()) && StringUtils.equals(getEndTime(), that.getEndTime()) && getStatus() == that.getStatus() && Objects.equals(getReasonForIncompletion(), that.getReasonForIncompletion()) && Objects.equals(getEvent(), that.getEvent()) && Objects.equals(getCreatedBy(), that.getCreatedBy());
    }

    public int hashCode() {
        return Objects.hash(getWorkflowType(), getVersion(), getWorkflowId(), getCorrelationId(), getStartTime(), getUpdateTime(), getEndTime(), getStatus(), getReasonForIncompletion(), getExecutionTime(), getEvent(), getPriority(), getCreatedBy());
    }
}
