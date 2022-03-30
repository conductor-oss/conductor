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
package com.netflix.conductor.core.execution.mapper;

import java.util.Map;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/** Business Object class used for interaction between the DeciderService and Different Mappers */
public class TaskMapperContext {

    private final WorkflowModel workflowModel;
    private final TaskDef taskDefinition;
    private final WorkflowTask workflowTask;
    private final Map<String, Object> taskInput;
    private final int retryCount;
    private final String retryTaskId;
    private final String taskId;
    private final DeciderService deciderService;

    private TaskMapperContext(Builder builder) {
        workflowModel = builder.workflowModel;
        taskDefinition = builder.taskDefinition;
        workflowTask = builder.workflowTask;
        taskInput = builder.taskInput;
        retryCount = builder.retryCount;
        retryTaskId = builder.retryTaskId;
        taskId = builder.taskId;
        deciderService = builder.deciderService;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(TaskMapperContext copy) {
        Builder builder = new Builder();
        builder.workflowModel = copy.getWorkflowModel();
        builder.taskDefinition = copy.getTaskDefinition();
        builder.workflowTask = copy.getWorkflowTask();
        builder.taskInput = copy.getTaskInput();
        builder.retryCount = copy.getRetryCount();
        builder.retryTaskId = copy.getRetryTaskId();
        builder.taskId = copy.getTaskId();
        builder.deciderService = copy.getDeciderService();
        return builder;
    }

    public WorkflowDef getWorkflowDefinition() {
        return workflowModel.getWorkflowDefinition();
    }

    public WorkflowModel getWorkflowModel() {
        return workflowModel;
    }

    public TaskDef getTaskDefinition() {
        return taskDefinition;
    }

    public WorkflowTask getWorkflowTask() {
        return workflowTask;
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

    public TaskModel createTaskModel() {
        TaskModel taskModel = new TaskModel();
        taskModel.setReferenceTaskName(workflowTask.getTaskReferenceName());
        taskModel.setWorkflowInstanceId(workflowModel.getWorkflowId());
        taskModel.setWorkflowType(workflowModel.getWorkflowName());
        taskModel.setCorrelationId(workflowModel.getCorrelationId());
        taskModel.setScheduledTime(System.currentTimeMillis());

        taskModel.setTaskId(taskId);
        taskModel.setWorkflowTask(workflowTask);
        taskModel.setWorkflowPriority(workflowModel.getPriority());

        // the following properties are overridden by some TaskMapper implementations
        taskModel.setTaskType(workflowTask.getType());
        taskModel.setTaskDefName(workflowTask.getName());
        return taskModel;
    }

    @Override
    public String toString() {
        return "TaskMapperContext{"
                + "workflowDefinition="
                + getWorkflowDefinition()
                + ", workflowModel="
                + workflowModel
                + ", workflowTask="
                + workflowTask
                + ", taskInput="
                + taskInput
                + ", retryCount="
                + retryCount
                + ", retryTaskId='"
                + retryTaskId
                + '\''
                + ", taskId='"
                + taskId
                + '\''
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TaskMapperContext)) {
            return false;
        }

        TaskMapperContext that = (TaskMapperContext) o;

        if (getRetryCount() != that.getRetryCount()) {
            return false;
        }
        if (!getWorkflowDefinition().equals(that.getWorkflowDefinition())) {
            return false;
        }
        if (!getWorkflowModel().equals(that.getWorkflowModel())) {
            return false;
        }
        if (!getWorkflowTask().equals(that.getWorkflowTask())) {
            return false;
        }
        if (!getTaskInput().equals(that.getTaskInput())) {
            return false;
        }
        if (getRetryTaskId() != null
                ? !getRetryTaskId().equals(that.getRetryTaskId())
                : that.getRetryTaskId() != null) {
            return false;
        }
        return getTaskId().equals(that.getTaskId());
    }

    @Override
    public int hashCode() {
        int result = getWorkflowDefinition().hashCode();
        result = 31 * result + getWorkflowModel().hashCode();
        result = 31 * result + getWorkflowTask().hashCode();
        result = 31 * result + getTaskInput().hashCode();
        result = 31 * result + getRetryCount();
        result = 31 * result + (getRetryTaskId() != null ? getRetryTaskId().hashCode() : 0);
        result = 31 * result + getTaskId().hashCode();
        return result;
    }

    /** {@code TaskMapperContext} builder static inner class. */
    public static final class Builder {

        private WorkflowModel workflowModel;
        private TaskDef taskDefinition;
        private WorkflowTask workflowTask;
        private Map<String, Object> taskInput;
        private int retryCount;
        private String retryTaskId;
        private String taskId;
        private DeciderService deciderService;

        private Builder() {}

        /**
         * Sets the {@code workflowModel} and returns a reference to this Builder so that the
         * methods can be chained together.
         *
         * @param val the {@code workflowModel} to set
         * @return a reference to this Builder
         */
        public Builder withWorkflowModel(WorkflowModel val) {
            workflowModel = val;
            return this;
        }

        /**
         * Sets the {@code taskDefinition} and returns a reference to this Builder so that the
         * methods can be chained together.
         *
         * @param val the {@code taskDefinition} to set
         * @return a reference to this Builder
         */
        public Builder withTaskDefinition(TaskDef val) {
            taskDefinition = val;
            return this;
        }

        /**
         * Sets the {@code workflowTask} and returns a reference to this Builder so that the methods
         * can be chained together.
         *
         * @param val the {@code workflowTask} to set
         * @return a reference to this Builder
         */
        public Builder withWorkflowTask(WorkflowTask val) {
            workflowTask = val;
            return this;
        }

        /**
         * Sets the {@code taskInput} and returns a reference to this Builder so that the methods
         * can be chained together.
         *
         * @param val the {@code taskInput} to set
         * @return a reference to this Builder
         */
        public Builder withTaskInput(Map<String, Object> val) {
            taskInput = val;
            return this;
        }

        /**
         * Sets the {@code retryCount} and returns a reference to this Builder so that the methods
         * can be chained together.
         *
         * @param val the {@code retryCount} to set
         * @return a reference to this Builder
         */
        public Builder withRetryCount(int val) {
            retryCount = val;
            return this;
        }

        /**
         * Sets the {@code retryTaskId} and returns a reference to this Builder so that the methods
         * can be chained together.
         *
         * @param val the {@code retryTaskId} to set
         * @return a reference to this Builder
         */
        public Builder withRetryTaskId(String val) {
            retryTaskId = val;
            return this;
        }

        /**
         * Sets the {@code taskId} and returns a reference to this Builder so that the methods can
         * be chained together.
         *
         * @param val the {@code taskId} to set
         * @return a reference to this Builder
         */
        public Builder withTaskId(String val) {
            taskId = val;
            return this;
        }

        /**
         * Sets the {@code deciderService} and returns a reference to this Builder so that the
         * methods can be chained together.
         *
         * @param val the {@code deciderService} to set
         * @return a reference to this Builder
         */
        public Builder withDeciderService(DeciderService val) {
            deciderService = val;
            return this;
        }

        /**
         * Returns a {@code TaskMapperContext} built from the parameters previously set.
         *
         * @return a {@code TaskMapperContext} built with parameters of this {@code
         *     TaskMapperContext.Builder}
         */
        public TaskMapperContext build() {
            return new TaskMapperContext(this);
        }
    }
}
