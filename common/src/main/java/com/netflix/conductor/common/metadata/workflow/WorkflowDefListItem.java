/*
 * Copyright 2026 Conductor Authors.
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
package com.netflix.conductor.common.metadata.workflow;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Read-only list projection of {@link WorkflowDef} used by the Workflow Definitions list UI.
 *
 * <p>This DTO deliberately omits the full task blueprint ({@code tasks}) and instead exposes only
 * lightweight summary fields plus derived {@code taskTypes} and {@code taskCount}. It exists to
 * avoid shipping very large responses when listing workflow definitions.
 *
 * <p>This projection must never be used to register or execute a workflow: it does not carry the
 * complete definition and is not a substitute for {@link WorkflowDef}.
 *
 * <p>Fields can be populated either by the {@link #fromWorkflowDef(WorkflowDef)} factory or by the
 * persistence layer setting them field-by-field from a query result row; the public setters are
 * retained for that reason.
 */
public class WorkflowDefListItem {

    private String name;

    private String description;

    private int version;

    private Long createTime;

    private int schemaVersion;

    private boolean restartable;

    private boolean workflowStatusListenerEnabled;

    private String ownerEmail;

    private List<String> inputParameters;

    private Map<String, Object> outputParameters;

    private WorkflowDef.TimeoutPolicy timeoutPolicy;

    private long timeoutSeconds;

    private Set<String> taskTypes;

    private int taskCount;

    private String failureWorkflow;

    private String classifier;

    public static WorkflowDefListItem fromWorkflowDef(WorkflowDef def) {
        WorkflowDefListItem item = new WorkflowDefListItem();
        item.setName(def.getName());
        item.setDescription(def.getDescription());
        item.setVersion(def.getVersion());
        item.setCreateTime(def.getCreateTime());
        item.setSchemaVersion(def.getSchemaVersion());
        item.setRestartable(def.isRestartable());
        item.setWorkflowStatusListenerEnabled(def.isWorkflowStatusListenerEnabled());
        item.setOwnerEmail(def.getOwnerEmail());
        item.setInputParameters(def.getInputParameters());
        item.setOutputParameters(def.getOutputParameters());
        item.setTimeoutPolicy(def.getTimeoutPolicy());
        item.setTimeoutSeconds(def.getTimeoutSeconds());
        item.setFailureWorkflow(def.getFailureWorkflow());
        item.setClassifier(WorkflowClassifier.classifierOf(def));

        List<WorkflowTask> tasks = def.getTasks();
        Set<String> taskTypes = new TreeSet<>();
        if (tasks == null) {
            item.setTaskTypes(taskTypes);
            item.setTaskCount(0);
        } else {
            for (WorkflowTask task : tasks) {
                if (task != null && task.getType() != null) {
                    taskTypes.add(task.getType());
                }
            }
            item.setTaskTypes(taskTypes);
            item.setTaskCount(tasks.size());
        }
        return item;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public boolean isRestartable() {
        return restartable;
    }

    public void setRestartable(boolean restartable) {
        this.restartable = restartable;
    }

    public boolean isWorkflowStatusListenerEnabled() {
        return workflowStatusListenerEnabled;
    }

    public void setWorkflowStatusListenerEnabled(boolean workflowStatusListenerEnabled) {
        this.workflowStatusListenerEnabled = workflowStatusListenerEnabled;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public List<String> getInputParameters() {
        return inputParameters;
    }

    public void setInputParameters(List<String> inputParameters) {
        this.inputParameters = inputParameters;
    }

    public Map<String, Object> getOutputParameters() {
        return outputParameters;
    }

    public void setOutputParameters(Map<String, Object> outputParameters) {
        this.outputParameters = outputParameters;
    }

    public WorkflowDef.TimeoutPolicy getTimeoutPolicy() {
        return timeoutPolicy;
    }

    public void setTimeoutPolicy(WorkflowDef.TimeoutPolicy timeoutPolicy) {
        this.timeoutPolicy = timeoutPolicy;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Set<String> getTaskTypes() {
        return taskTypes;
    }

    public void setTaskTypes(Set<String> taskTypes) {
        this.taskTypes = taskTypes;
    }

    public int getTaskCount() {
        return taskCount;
    }

    public void setTaskCount(int taskCount) {
        this.taskCount = taskCount;
    }

    public String getFailureWorkflow() {
        return failureWorkflow;
    }

    public void setFailureWorkflow(String failureWorkflow) {
        this.failureWorkflow = failureWorkflow;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }
}
