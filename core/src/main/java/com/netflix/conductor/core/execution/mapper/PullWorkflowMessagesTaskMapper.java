/*
 * Copyright 2024 Conductor Authors.
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

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.tasks.PullWorkflowMessages;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * {@link TaskMapper} for the {@code PULL_WORKFLOW_MESSAGES} system task type.
 *
 * <p>Creates an {@code IN_PROGRESS} task with the caller's input parameters (notably {@code
 * batchSize}) resolved. The system task worker picks it up and calls {@link
 * PullWorkflowMessages#execute} on each poll cycle until messages arrive.
 */
@Component
@ConditionalOnProperty(name = "conductor.workflow-message-queue.enabled", havingValue = "true")
public class PullWorkflowMessagesTaskMapper implements TaskMapper {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PullWorkflowMessagesTaskMapper.class);

    private final ParametersUtils parametersUtils;

    public PullWorkflowMessagesTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public String getTaskType() {
        return TaskType.PULL_WORKFLOW_MESSAGES.name();
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {
        LOGGER.debug("TaskMapperContext {} in PullWorkflowMessagesTaskMapper", taskMapperContext);

        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();
        String taskId = taskMapperContext.getTaskId();

        Map<String, Object> input =
                parametersUtils.getTaskInputV2(
                        workflowTask.getInputParameters(), workflowModel, taskId, null);

        TaskModel task = taskMapperContext.createTaskModel();
        task.setTaskType(PullWorkflowMessages.TASK_TYPE);
        task.setInputData(input);
        task.setStartTime(System.currentTimeMillis());
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        return List.of(task);
    }
}
