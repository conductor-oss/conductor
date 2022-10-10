/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.core.execution;

import java.util.Map;
import java.util.Objects;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

public class StartWorkflowInput {

    private String name;
    private Integer version;
    private WorkflowDef workflowDefinition;
    private Map<String, Object> workflowInput;
    private String externalInputPayloadStoragePath;
    private String correlationId;
    private Integer priority;
    private String parentWorkflowId;
    private String parentWorkflowTaskId;
    private String event;
    private Map<String, String> taskToDomain;
    private String workflowId;
    private String triggeringWorkflowId;

    public StartWorkflowInput() {}

    public StartWorkflowInput(StartWorkflowRequest startWorkflowRequest) {
        this.name = startWorkflowRequest.getName();
        this.version = startWorkflowRequest.getVersion();
        this.workflowDefinition = startWorkflowRequest.getWorkflowDef();
        this.correlationId = startWorkflowRequest.getCorrelationId();
        this.priority = startWorkflowRequest.getPriority();
        this.workflowInput = startWorkflowRequest.getInput();
        this.externalInputPayloadStoragePath =
                startWorkflowRequest.getExternalInputPayloadStoragePath();
        this.taskToDomain = startWorkflowRequest.getTaskToDomain();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public WorkflowDef getWorkflowDefinition() {
        return workflowDefinition;
    }

    public void setWorkflowDefinition(WorkflowDef workflowDefinition) {
        this.workflowDefinition = workflowDefinition;
    }

    public Map<String, Object> getWorkflowInput() {
        return workflowInput;
    }

    public void setWorkflowInput(Map<String, Object> workflowInput) {
        this.workflowInput = workflowInput;
    }

    public String getExternalInputPayloadStoragePath() {
        return externalInputPayloadStoragePath;
    }

    public void setExternalInputPayloadStoragePath(String externalInputPayloadStoragePath) {
        this.externalInputPayloadStoragePath = externalInputPayloadStoragePath;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getParentWorkflowId() {
        return parentWorkflowId;
    }

    public void setParentWorkflowId(String parentWorkflowId) {
        this.parentWorkflowId = parentWorkflowId;
    }

    public String getParentWorkflowTaskId() {
        return parentWorkflowTaskId;
    }

    public void setParentWorkflowTaskId(String parentWorkflowTaskId) {
        this.parentWorkflowTaskId = parentWorkflowTaskId;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public Map<String, String> getTaskToDomain() {
        return taskToDomain;
    }

    public void setTaskToDomain(Map<String, String> taskToDomain) {
        this.taskToDomain = taskToDomain;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getTriggeringWorkflowId() {
        return triggeringWorkflowId;
    }

    public void setTriggeringWorkflowId(String triggeringWorkflowId) {
        this.triggeringWorkflowId = triggeringWorkflowId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StartWorkflowInput that = (StartWorkflowInput) o;
        return Objects.equals(name, that.name)
                && Objects.equals(version, that.version)
                && Objects.equals(workflowDefinition, that.workflowDefinition)
                && Objects.equals(workflowInput, that.workflowInput)
                && Objects.equals(
                        externalInputPayloadStoragePath, that.externalInputPayloadStoragePath)
                && Objects.equals(correlationId, that.correlationId)
                && Objects.equals(priority, that.priority)
                && Objects.equals(parentWorkflowId, that.parentWorkflowId)
                && Objects.equals(parentWorkflowTaskId, that.parentWorkflowTaskId)
                && Objects.equals(event, that.event)
                && Objects.equals(taskToDomain, that.taskToDomain)
                && Objects.equals(triggeringWorkflowId, that.triggeringWorkflowId)
                && Objects.equals(workflowId, that.workflowId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name,
                version,
                workflowDefinition,
                workflowInput,
                externalInputPayloadStoragePath,
                correlationId,
                priority,
                parentWorkflowId,
                parentWorkflowTaskId,
                event,
                taskToDomain,
                triggeringWorkflowId,
                workflowId);
    }
}
