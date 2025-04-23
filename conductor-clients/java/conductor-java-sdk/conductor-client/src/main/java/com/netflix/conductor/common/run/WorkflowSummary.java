/*
 * Copyright 2020 Conductor Authors.
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

import com.netflix.conductor.common.utils.SummaryUtil;

import lombok.*;

/**
 * Captures workflow summary info to be indexed in Elastic Search.
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

    public long getInputSize() {
        return input != null ? input.length() : 0;
    }

    public long getOutputSize() {
        return output != null ? output.length() : 0;
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
