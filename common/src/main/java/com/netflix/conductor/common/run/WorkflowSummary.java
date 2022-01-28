/*
 * Copyright 2020 Netflix, Inc.
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
import java.util.Objects;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.common.utils.SummaryUtil;

/** Captures workflow summary info to be indexed in Elastic Search. */
@ProtoMessage
public class WorkflowSummary {

    /** The time should be stored as GMT */
    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    @ProtoField(id = 1)
    private String workflowType;

    @ProtoField(id = 2)
    private int version;

    @ProtoField(id = 3)
    private String workflowId;

    @ProtoField(id = 4)
    private String correlationId;

    @ProtoField(id = 5)
    private String startTime;

    @ProtoField(id = 6)
    private String updateTime;

    @ProtoField(id = 7)
    private String endTime;

    @ProtoField(id = 8)
    private Workflow.WorkflowStatus status;

    @ProtoField(id = 9)
    private String input;

    @ProtoField(id = 10)
    private String output;

    @ProtoField(id = 11)
    private String reasonForIncompletion;

    @ProtoField(id = 12)
    private long executionTime;

    @ProtoField(id = 13)
    private String event;

    @ProtoField(id = 14)
    private String failedReferenceTaskNames = "";

    @ProtoField(id = 15)
    private String externalInputPayloadStoragePath;

    @ProtoField(id = 16)
    private String externalOutputPayloadStoragePath;

    @ProtoField(id = 17)
    private int priority;

    public WorkflowSummary() {}

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
        this.failedReferenceTaskNames =
                workflow.getFailedReferenceTaskNames().stream().collect(Collectors.joining(","));
        if (StringUtils.isNotBlank(workflow.getExternalInputPayloadStoragePath())) {
            this.externalInputPayloadStoragePath = workflow.getExternalInputPayloadStoragePath();
        }
        if (StringUtils.isNotBlank(workflow.getExternalOutputPayloadStoragePath())) {
            this.externalOutputPayloadStoragePath = workflow.getExternalOutputPayloadStoragePath();
        }
    }

    /** @return the workflowType */
    public String getWorkflowType() {
        return workflowType;
    }

    /** @return the version */
    public int getVersion() {
        return version;
    }

    /** @return the workflowId */
    public String getWorkflowId() {
        return workflowId;
    }

    /** @return the correlationId */
    public String getCorrelationId() {
        return correlationId;
    }

    /** @return the startTime */
    public String getStartTime() {
        return startTime;
    }

    /** @return the endTime */
    public String getEndTime() {
        return endTime;
    }

    /** @return the status */
    public WorkflowStatus getStatus() {
        return status;
    }

    /** @return the input */
    public String getInput() {
        return input;
    }

    public long getInputSize() {
        return input != null ? input.length() : 0;
    }

    /** @return the output */
    public String getOutput() {
        return output;
    }

    public long getOutputSize() {
        return output != null ? output.length() : 0;
    }

    /** @return the reasonForIncompletion */
    public String getReasonForIncompletion() {
        return reasonForIncompletion;
    }

    /** @return the executionTime */
    public long getExecutionTime() {
        return executionTime;
    }

    /** @return the updateTime */
    public String getUpdateTime() {
        return updateTime;
    }

    /** @return The event */
    public String getEvent() {
        return event;
    }

    /** @param event The event */
    public void setEvent(String event) {
        this.event = event;
    }

    public String getFailedReferenceTaskNames() {
        return failedReferenceTaskNames;
    }

    public void setFailedReferenceTaskNames(String failedReferenceTaskNames) {
        this.failedReferenceTaskNames = failedReferenceTaskNames;
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

    /** @return the external storage path of the workflow input payload */
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

    /** @return the external storage path of the workflow output payload */
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

    /** @return the priority to define on tasks */
    public int getPriority() {
        return priority;
    }

    /** @param priority priority of tasks (between 0 and 99) */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WorkflowSummary that = (WorkflowSummary) o;
        return getVersion() == that.getVersion()
                && getExecutionTime() == that.getExecutionTime()
                && getPriority() == that.getPriority()
                && getWorkflowType().equals(that.getWorkflowType())
                && getWorkflowId().equals(that.getWorkflowId())
                && Objects.equals(getCorrelationId(), that.getCorrelationId())
                && getStartTime().equals(that.getStartTime())
                && getUpdateTime().equals(that.getUpdateTime())
                && getEndTime().equals(that.getEndTime())
                && getStatus() == that.getStatus()
                && Objects.equals(getReasonForIncompletion(), that.getReasonForIncompletion())
                && Objects.equals(getEvent(), that.getEvent());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getWorkflowType(),
                getVersion(),
                getWorkflowId(),
                getCorrelationId(),
                getStartTime(),
                getUpdateTime(),
                getEndTime(),
                getStatus(),
                getReasonForIncompletion(),
                getExecutionTime(),
                getEvent(),
                getPriority());
    }
}
