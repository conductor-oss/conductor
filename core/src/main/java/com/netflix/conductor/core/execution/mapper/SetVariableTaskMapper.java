/*
 * Copyright 2020 Netflix, Inc.
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

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.TerminateWorkflowException;

public class SetVariableTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(SetVariableTaskMapper.class);

    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) throws TerminateWorkflowException {
        LOGGER.debug("TaskMapperContext {} in SetVariableMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        Map<String, Object> taskInput = taskMapperContext.getTaskInput();
        String taskId = taskMapperContext.getTaskId();

        Task varTask = new Task();
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
        varTask.setStatus(Task.Status.IN_PROGRESS);
        varTask.setWorkflowTask(taskToSchedule);
        varTask.setWorkflowPriority(workflowInstance.getPriority());

        return Collections.singletonList(varTask);
    }
}
