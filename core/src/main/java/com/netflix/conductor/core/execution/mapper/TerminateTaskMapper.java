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

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_TERMINATE;

import static java.util.Collections.singletonList;

@Component
public class TerminateTaskMapper implements TaskMapper {

    public static final Logger logger = LoggerFactory.getLogger(TerminateTaskMapper.class);
    private final ParametersUtils parametersUtils;

    public TerminateTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.TERMINATE;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {

        logger.debug("TaskMapperContext {} in TerminateTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        WorkflowModel workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();

        Map<String, Object> taskInput =
                parametersUtils.getTaskInputV2(
                        taskMapperContext.getTaskToSchedule().getInputParameters(),
                        workflowInstance,
                        taskId,
                        null);

        TaskModel task = new TaskModel();
        task.setTaskType(TASK_TYPE_TERMINATE);
        task.setTaskDefName(taskToSchedule.getName());
        task.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        task.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        task.setWorkflowType(workflowInstance.getWorkflowName());
        task.setCorrelationId(workflowInstance.getCorrelationId());
        task.setScheduledTime(System.currentTimeMillis());
        task.setStartTime(System.currentTimeMillis());
        task.setInputData(taskInput);
        task.setTaskId(taskId);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setWorkflowTask(taskToSchedule);
        task.setWorkflowPriority(workflowInstance.getPriority());
        return singletonList(task);
    }
}
