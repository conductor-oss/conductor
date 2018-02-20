/**
 * Copyright 2018 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.DeciderService;

import java.util.Map;

/**
 * Business Object class used for interaction between the DeciderService and Different Mappers
 */
public class TaskMapperContext {

    private WorkflowDef workflowDefinition;
    private Workflow workflowInstance;
    private WorkflowTask taskToSchedule;
    private Map<String, Object> taskInput;
    private int retryCount;
    private String retryTaskId;
    private String taskId;
    private DeciderService deciderService;


    public TaskMapperContext(WorkflowDef workflowDefinition, Workflow workflowInstance, WorkflowTask taskToSchedule,
                             Map<String, Object> taskInput, int retryCount, String retryTaskId, String taskId, DeciderService deciderService) {

        this.workflowDefinition = workflowDefinition;
        this.workflowInstance = workflowInstance;
        this.taskToSchedule = taskToSchedule;
        this.taskInput = taskInput;
        this.retryCount = retryCount;
        this.retryTaskId = retryTaskId;
        this.taskId = taskId;
        this.deciderService = deciderService;
    }

    public WorkflowDef getWorkflowDefinition() {
        return workflowDefinition;
    }

    public Workflow getWorkflowInstance() {
        return workflowInstance;
    }

    public WorkflowTask getTaskToSchedule() {
        return taskToSchedule;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getRetryTaskId() {
        return retryTaskId;
    }

    public String getTaskId() {
        return taskId;
    }

    public Map<String, Object> getTaskInput() {
        return taskInput;
    }

    public DeciderService getDeciderService() {
        return deciderService;
    }

    @Override
    public String toString() {
        return "TaskMapperContext{" +
                "workflowDefinition=" + workflowDefinition +
                ", workflowInstance=" + workflowInstance +
                ", taskToSchedule=" + taskToSchedule +
                ", taskInput=" + taskInput +
                ", retryCount=" + retryCount +
                ", retryTaskId='" + retryTaskId + '\'' +
                ", taskId='" + taskId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskMapperContext)) return false;

        TaskMapperContext that = (TaskMapperContext) o;

        if (getRetryCount() != that.getRetryCount()) return false;
        if (!getWorkflowDefinition().equals(that.getWorkflowDefinition())) return false;
        if (!getWorkflowInstance().equals(that.getWorkflowInstance())) return false;
        if (!getTaskToSchedule().equals(that.getTaskToSchedule())) return false;
        if (!getTaskInput().equals(that.getTaskInput())) return false;
        if (getRetryTaskId() != null ? !getRetryTaskId().equals(that.getRetryTaskId()) : that.getRetryTaskId() != null)
            return false;
        return getTaskId().equals(that.getTaskId());
    }

    @Override
    public int hashCode() {
        int result = getWorkflowDefinition().hashCode();
        result = 31 * result + getWorkflowInstance().hashCode();
        result = 31 * result + getTaskToSchedule().hashCode();
        result = 31 * result + getTaskInput().hashCode();
        result = 31 * result + getRetryCount();
        result = 31 * result + (getRetryTaskId() != null ? getRetryTaskId().hashCode() : 0);
        result = 31 * result + getTaskId().hashCode();
        return result;
    }
}
