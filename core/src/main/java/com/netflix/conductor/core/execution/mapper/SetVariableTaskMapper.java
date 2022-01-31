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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

@Component
public class SetVariableTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(SetVariableTaskMapper.class);

    @Override
    public TaskType getTaskType() {
        return TaskType.SET_VARIABLE;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {
        LOGGER.debug("TaskMapperContext {} in SetVariableMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        WorkflowModel workflowInstance = taskMapperContext.getWorkflowInstance();
        Map<String, Object> taskInput = taskMapperContext.getTaskInput();
        String taskId = taskMapperContext.getTaskId();

        TaskModel varTask = new TaskModel();
        varTask.setTaskType(taskToSchedule.getType());
        varTask.setTaskDefName(taskToSchedule.getName());
        varTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        varTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        varTask.setWorkflowType(workflowInstance.getWorkflowName());
        varTask.setCorrelationId(workflowInstance.getCorrelationId());
        varTask.setStartTime(System.currentTimeMillis());
        varTask.setScheduledTime(System.currentTimeMillis());
        varTask.setInputData(taskInput);
        varTask.setTaskId(taskId);
        varTask.setStatus(TaskModel.Status.IN_PROGRESS);
        varTask.setWorkflowTask(taskToSchedule);
        varTask.setWorkflowPriority(workflowInstance.getPriority());

        return Collections.singletonList(varTask);
    }
}
