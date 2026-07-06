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
package com.netflix.conductor.core.execution.mapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_ZOHO_BOOKS_FETCH;

@Component
public class ZohoBooksFetchTaskMapper implements TaskMapper {

    private final ParametersUtils parametersUtils;

    public ZohoBooksFetchTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public String getTaskType() {
        return TaskType.ZOHO_BOOKS_FETCH.name();
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {
        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();
        String taskId = taskMapperContext.getTaskId();

        Map<String, Object> taskInput =
                parametersUtils.getTaskInputV2(
                        workflowTask.getInputParameters(),
                        workflowModel,
                        taskId,
                        taskMapperContext.getTaskDefinition());

        TaskModel task = taskMapperContext.createTaskModel();
        task.setTaskType(TASK_TYPE_ZOHO_BOOKS_FETCH);
        task.setStartTime(System.currentTimeMillis());
        task.setInputData(taskInput);
        if (Objects.nonNull(taskMapperContext.getTaskDefinition())) {
            task.setIsolationGroupId(taskMapperContext.getTaskDefinition().getIsolationGroupId());
        }
        task.setStatus(TaskModel.Status.IN_PROGRESS);

        return List.of(task);
    }
}
