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
package com.netflix.conductor.model;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.utils.Utils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class WorkflowModel {

    public enum Status {
        RUNNING(false, false),
        COMPLETED(true, true),
        FAILED(true, false),
        TIMED_OUT(true, false),
        TERMINATED(true, false),
        PAUSED(false, true);

        private final boolean terminal;
        private final boolean successful;

        Status(boolean terminal, boolean successful) {
            this.terminal = terminal;
            this.successful = successful;
        }

        public boolean isTerminal() {
            return terminal;
        }

        public boolean isSuccessful() {
            return successful;
        }
    }

    private Status status = Status.RUNNING;

    private long endTime;

    private String workflowId;

    private String parentWorkflowId;

    private String parentWorkflowTaskId;

    private List<TaskModel> tasks = new LinkedList<>();

    private String correlationId;

    private String reRunFromWorkflowId;

    private String reasonForIncompletion;

    private String event;

    private Map<String, String> taskToDomain = new HashMap<>();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Set<String> failedReferenceTaskNames = new HashSet<>();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Set<String> failedTaskNames = new HashSet<>();

    private WorkflowDef workflowDefinition;

    private String externalInputPayloadStoragePath;

    private String externalOutputPayloadStoragePath;

    private int priority;

    private Map<String, Object> variables = new HashMap<>();

    private long lastRetriedTime;

    private String ownerApp;

    private Long createTime;

    private Long updatedTime;

    private String createdBy;

    private String updatedBy;

    // Capture the failed taskId if the workflow execution failed because of task failure
    private String failedTaskId;

    private Status previousStatus;

    @JsonIgnore private Map<String, Object> input = new HashMap<>();

    @JsonIgnore private Map<String, Object> output = new HashMap<>();

    @JsonIgnore private Map<String, Object> inputPayload = new HashMap<>();

    @JsonIgnore private Map<String, Object> outputPayload = new HashMap<>();

    public Status getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(Status status) {
        this.previousStatus = status;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        // update previous status if current status changed
        if (this.status != status) {
            setPreviousStatus(this.status);
        }
        this.status = status;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
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

    public List<TaskModel> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskModel> tasks) {
        this.tasks = tasks;
    }

    @JsonIgnore
    public Map<String, Object> getInput() {
        if (!inputPayload.isEmpty() && !input.isEmpty()) {
            input.putAll(inputPayload);
            inputPayload = new HashMap<>();
            return input;
        } else if (inputPayload.isEmpty()) {
            return input;
        } else {
            return inputPayload;
        }
    }

    @JsonIgnore
    public void setInput(Map<String, Object> input) {
        if (input == null) {
            input = new HashMap<>();
        }
        this.input = input;
    }

    @JsonIgnore
    public Map<String, Object> getOutput() {
        if (!outputPayload.isEmpty() && !output.isEmpty()) {
            output.putAll(outputPayload);
            outputPayload = new HashMap<>();
            return output;
        } else if (outputPayload.isEmpty()) {
            return output;
        } else {
            return outputPayload;
        }
    }

    @JsonIgnore
    public void setOutput(Map<String, Object> output) {
        if (output == null) {
            output = new HashMap<>();
        }
        this.output = output;
    }

    /**
     * @deprecated Used only for JSON serialization and deserialization.
     */
    @Deprecated
    @JsonProperty("input")
    public Map<String, Object> getRawInput() {
        return input;
    }

    /**
     * @deprecated Used only for JSON serialization and deserialization.
     */
    @Deprecated
    @JsonProperty("input")
    public void setRawInput(Map<String, Object> input) {
        setInput(input);
    }

    /**
     * @deprecated Used only for JSON serialization and deserialization.
     */
    @Deprecated
    @JsonProperty("output")
    public Map<String, Object> getRawOutput() {
        return output;
    }

    /**
     * @deprecated Used only for JSON serialization and deserialization.
     */
    @Deprecated
    @JsonProperty("output")
    public void setRawOutput(Map<String, Object> output) {
        setOutput(output);
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getReRunFromWorkflowId() {
        return reRunFromWorkflowId;
    }

    public void setReRunFromWorkflowId(String reRunFromWorkflowId) {
        this.reRunFromWorkflowId = reRunFromWorkflowId;
    }

    public String getReasonForIncompletion() {
        return reasonForIncompletion;
    }

    public void setReasonForIncompletion(String reasonForIncompletion) {
        this.reasonForIncompletion = reasonForIncompletion;
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

    public Set<String> getFailedReferenceTaskNames() {
        return failedReferenceTaskNames;
    }

    public void setFailedReferenceTaskNames(Set<String> failedReferenceTaskNames) {
        this.failedReferenceTaskNames = failedReferenceTaskNames;
    }

    public Set<String> getFailedTaskNames() {
        return failedTaskNames;
    }

    public void setFailedTaskNames(Set<String> failedTaskNames) {
        this.failedTaskNames = failedTaskNames;
    }

    public WorkflowDef getWorkflowDefinition() {
        return workflowDefinition;
    }

    public void setWorkflowDefinition(WorkflowDef workflowDefinition) {
        this.workflowDefinition = workflowDefinition;
    }

    public String getExternalInputPayloadStoragePath() {
        return externalInputPayloadStoragePath;
    }

    public void setExternalInputPayloadStoragePath(String externalInputPayloadStoragePath) {
        this.externalInputPayloadStoragePath = externalInputPayloadStoragePath;
    }

    public String getExternalOutputPayloadStoragePath() {
        return externalOutputPayloadStoragePath;
    }

    public void setExternalOutputPayloadStoragePath(String externalOutputPayloadStoragePath) {
        this.externalOutputPayloadStoragePath = externalOutputPayloadStoragePath;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        if (priority < 0 || priority > 99) {
            throw new IllegalArgumentException("priority MUST be between 0 and 99 (inclusive)");
        }
        this.priority = priority;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public long getLastRetriedTime() {
        return lastRetriedTime;
    }

    public void setLastRetriedTime(long lastRetriedTime) {
        this.lastRetriedTime = lastRetriedTime;
    }

    public String getOwnerApp() {
        return ownerApp;
    }

    public void setOwnerApp(String ownerApp) {
        this.ownerApp = ownerApp;
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

    public String getFailedTaskId() {
        return failedTaskId;
    }

    public void setFailedTaskId(String failedTaskId) {
        this.failedTaskId = failedTaskId;
    }

    /**
     * Convenience method for accessing the workflow definition name.
     *
     * @return the workflow definition name.
     */
    public String getWorkflowName() {
        Utils.checkNotNull(workflowDefinition, "Workflow definition is null");
        return workflowDefinition.getName();
    }

    /**
     * Convenience method for accessing the workflow definition version.
     *
     * @return the workflow definition version.
     */
    public int getWorkflowVersion() {
        Utils.checkNotNull(workflowDefinition, "Workflow definition is null");
        return workflowDefinition.getVersion();
    }

    public boolean hasParent() {
        return StringUtils.isNotEmpty(parentWorkflowId);
    }

    /**
     * A string representation of all relevant fields that identify this workflow. Intended for use
     * in log and other system generated messages.
     */
    public String toShortString() {
        String name = workflowDefinition != null ? workflowDefinition.getName() : null;
        Integer version = workflowDefinition != null ? workflowDefinition.getVersion() : null;
        return String.format("%s.%s/%s", name, version, workflowId);
    }

    public TaskModel getTaskByRefName(String refName) {
        if (refName == null) {
            throw new RuntimeException(
                    "refName passed is null.  Check the workflow execution.  For dynamic tasks, make sure referenceTaskName is set to a not null value");
        }
        LinkedList<TaskModel> found = new LinkedList<>();
        for (TaskModel task : tasks) {
            if (task.getReferenceTaskName() == null) {
                throw new RuntimeException(
                        "Task "
                                + task.getTaskDefName()
                                + ", seq="
                                + task.getSeq()
                                + " does not have reference name specified.");
            }
            if (task.getReferenceTaskName().equals(refName)) {
                found.add(task);
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        return found.getLast();
    }

    public void externalizeInput(String path) {
        this.inputPayload = this.input;
        this.input = new HashMap<>();
        this.externalInputPayloadStoragePath = path;
    }

    public void externalizeOutput(String path) {
        this.outputPayload = this.output;
        this.output = new HashMap<>();
        this.externalOutputPayloadStoragePath = path;
    }

    public void internalizeInput(Map<String, Object> data) {
        this.input = new HashMap<>();
        this.inputPayload = data;
    }

    public void internalizeOutput(Map<String, Object> data) {
        this.output = new HashMap<>();
        this.outputPayload = data;
    }

    @Override
    public String toString() {
        String name = workflowDefinition != null ? workflowDefinition.getName() : null;
        Integer version = workflowDefinition != null ? workflowDefinition.getVersion() : null;
        return String.format("%s.%s/%s.%s", name, version, workflowId, status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowModel that = (WorkflowModel) o;
        return getEndTime() == that.getEndTime()
                && getPriority() == that.getPriority()
                && getLastRetriedTime() == that.getLastRetriedTime()
                && getStatus() == that.getStatus()
                && Objects.equals(getWorkflowId(), that.getWorkflowId())
                && Objects.equals(getParentWorkflowId(), that.getParentWorkflowId())
                && Objects.equals(getParentWorkflowTaskId(), that.getParentWorkflowTaskId())
                && Objects.equals(getTasks(), that.getTasks())
                && Objects.equals(getInput(), that.getInput())
                && Objects.equals(output, that.output)
                && Objects.equals(outputPayload, that.outputPayload)
                && Objects.equals(getCorrelationId(), that.getCorrelationId())
                && Objects.equals(getReRunFromWorkflowId(), that.getReRunFromWorkflowId())
                && Objects.equals(getReasonForIncompletion(), that.getReasonForIncompletion())
                && Objects.equals(getEvent(), that.getEvent())
                && Objects.equals(getTaskToDomain(), that.getTaskToDomain())
                && Objects.equals(getFailedReferenceTaskNames(), that.getFailedReferenceTaskNames())
                && Objects.equals(getFailedTaskNames(), that.getFailedTaskNames())
                && Objects.equals(getWorkflowDefinition(), that.getWorkflowDefinition())
                && Objects.equals(
                        getExternalInputPayloadStoragePath(),
                        that.getExternalInputPayloadStoragePath())
                && Objects.equals(
                        getExternalOutputPayloadStoragePath(),
                        that.getExternalOutputPayloadStoragePath())
                && Objects.equals(getVariables(), that.getVariables())
                && Objects.equals(getOwnerApp(), that.getOwnerApp())
                && Objects.equals(getCreateTime(), that.getCreateTime())
                && Objects.equals(getUpdatedTime(), that.getUpdatedTime())
                && Objects.equals(getCreatedBy(), that.getCreatedBy())
                && Objects.equals(getUpdatedBy(), that.getUpdatedBy());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getStatus(),
                getEndTime(),
                getWorkflowId(),
                getParentWorkflowId(),
                getParentWorkflowTaskId(),
                getTasks(),
                getInput(),
                output,
                outputPayload,
                getCorrelationId(),
                getReRunFromWorkflowId(),
                getReasonForIncompletion(),
                getEvent(),
                getTaskToDomain(),
                getFailedReferenceTaskNames(),
                getFailedTaskNames(),
                getWorkflowDefinition(),
                getExternalInputPayloadStoragePath(),
                getExternalOutputPayloadStoragePath(),
                getPriority(),
                getVariables(),
                getLastRetriedTime(),
                getOwnerApp(),
                getCreateTime(),
                getUpdatedTime(),
                getCreatedBy(),
                getUpdatedBy());
    }

    public Workflow toWorkflow() {
        Workflow workflow = new Workflow();
        BeanUtils.copyProperties(this, workflow);
        workflow.setStatus(Workflow.WorkflowStatus.valueOf(this.status.name()));
        workflow.setTasks(tasks.stream().map(TaskModel::toTask).collect(Collectors.toList()));
        workflow.setUpdateTime(this.updatedTime);

        // ensure that input/output is properly represented
        if (externalInputPayloadStoragePath != null) {
            workflow.setInput(new HashMap<>());
        }
        if (externalOutputPayloadStoragePath != null) {
            workflow.setOutput(new HashMap<>());
        }
        return workflow;
    }

    public void addInput(String key, Object value) {
        this.input.put(key, value);
    }

    public void addInput(Map<String, Object> inputData) {
        if (inputData != null) {
            this.input.putAll(inputData);
        }
    }

    public void addOutput(String key, Object value) {
        this.output.put(key, value);
    }

    public void addOutput(Map<String, Object> outputData) {
        if (outputData != null) {
            this.output.putAll(outputData);
        }
    }
}
