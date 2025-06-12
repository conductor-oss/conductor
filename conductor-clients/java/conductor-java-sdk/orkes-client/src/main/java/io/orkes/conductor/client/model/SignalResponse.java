/*
 * Copyright 2025 Conductor Authors.
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

import java.util.List;
import java.util.Map;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;

import io.orkes.conductor.client.enums.ReturnStrategy;

import lombok.*;

@EqualsAndHashCode
@ToString
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SignalResponse {

    // Common fields in all responses
    private ReturnStrategy responseType;
    private String targetWorkflowId;
    private String targetWorkflowStatus;
    private String workflowId;
    private Map<String, Object> input;
    private Map<String, Object> output;
    private Integer priority;
    private Map<String, Object> variables;

    // Fields specific to TARGET_WORKFLOW & BLOCKING_WORKFLOW
    private List<Task> tasks;
    private String createdBy;
    private Long createTime;
    private String status;
    private Long updateTime;

    // Fields specific to BLOCKING_TASK & BLOCKING_TASK_INPUT
    private String taskType;
    private String taskId;
    private String referenceTaskName;
    private Integer retryCount;
    private String taskDefName;
    private String workflowType;

    // Helper methods to check response type
    public boolean isTargetWorkflow() {
        return ReturnStrategy.TARGET_WORKFLOW.equals(responseType);
    }

    public boolean isBlockingWorkflow() {
        return ReturnStrategy.BLOCKING_WORKFLOW.equals(responseType);
    }

    public boolean isBlockingTask() {
        return ReturnStrategy.BLOCKING_TASK.equals(responseType);
    }

    public boolean isBlockingTaskInput() {
        return ReturnStrategy.BLOCKING_TASK_INPUT.equals(responseType);
    }

    // Extraction methods
    public Workflow getWorkflow() {
        if (!isTargetWorkflow() && !isBlockingWorkflow()) {
            throw new IllegalStateException(
                    String.format("Response type %s does not contain workflow details", responseType)
            );
        }

        Workflow workflow = new Workflow();
        workflow.setWorkflowId(workflowId);
        workflow.setStatus(Workflow.WorkflowStatus.valueOf(status));
        workflow.setTasks(tasks);
        workflow.setCreatedBy(createdBy);
        workflow.setCreateTime(createTime);
        workflow.setUpdateTime(updateTime);
        workflow.setInput(input);
        workflow.setOutput(output);
        workflow.setVariables(variables);
        workflow.setPriority(priority);

        return workflow;
    }

    public Task getBlockingTask() {
        if (!isBlockingTask() && !isBlockingTaskInput()) {
            throw new IllegalStateException(
                    String.format("Response type %s does not contain task details", responseType)
            );
        }

        Task task = new Task();
        task.setTaskId(taskId);
        task.setTaskType(taskType);
        task.setTaskDefName(taskDefName);
        task.setWorkflowType(workflowType);
        task.setReferenceTaskName(referenceTaskName);
        task.setRetryCount(retryCount);
        if (status != null) {
            task.setStatus(Task.Status.valueOf(status));
        }
        task.setInputData(input);
        task.setOutputData(output);

        return task;
    }

    public Map<String, Object> getTaskInput() {
        if (!isBlockingTaskInput()) {
            throw new IllegalStateException(
                    String.format("Response type %s does not contain task input details", responseType)
            );
        }

        return input;
    }
}