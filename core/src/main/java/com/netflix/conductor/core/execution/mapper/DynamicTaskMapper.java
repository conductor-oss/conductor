/*
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

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.TerminateWorkflowException;
import com.netflix.conductor.dao.MetadataDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link TaskType#DYNAMIC}
 * to a {@link Task} based on definition derived from the dynamic task name defined in {@link WorkflowTask#getInputParameters()}
 */
public class DynamicTaskMapper implements TaskMapper {

    private static final Logger logger = LoggerFactory.getLogger(DynamicTaskMapper.class);

    private final ParametersUtils parametersUtils;
    private final MetadataDAO metadataDAO;

    public DynamicTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
    }

    /**
     * This method maps a dynamic task to a {@link Task} based on the input params
     *
     * @param taskMapperContext: A wrapper class containing the {@link WorkflowTask}, {@link WorkflowDef}, {@link Workflow} and a string representation of the TaskId
     * @return A {@link List} that contains a single {@link Task} with a {@link Task.Status#SCHEDULED}
     */
    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) throws TerminateWorkflowException {
        logger.debug("TaskMapperContext {} in DynamicTaskMapper", taskMapperContext);
        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Map<String, Object> taskInput = taskMapperContext.getTaskInput();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        int retryCount = taskMapperContext.getRetryCount();
        String retriedTaskId = taskMapperContext.getRetryTaskId();

        String taskNameParam = taskToSchedule.getDynamicTaskNameParam();
        String taskName = getDynamicTaskName(taskInput, taskNameParam);
        taskToSchedule.setName(taskName);
        TaskDef taskDefinition = getDynamicTaskDefinition(taskToSchedule);
        taskToSchedule.setTaskDefinition(taskDefinition);

        Map<String, Object> input = parametersUtils.getTaskInput(taskToSchedule.getInputParameters(), workflowInstance,
                taskDefinition, taskMapperContext.getTaskId());
        Task dynamicTask = new Task();
        dynamicTask.setStartDelayInSeconds(taskToSchedule.getStartDelay());
        dynamicTask.setTaskId(taskMapperContext.getTaskId());
        dynamicTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        dynamicTask.setInputData(input);
        dynamicTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        dynamicTask.setWorkflowType(workflowInstance.getWorkflowName());
        dynamicTask.setStatus(Task.Status.SCHEDULED);
        dynamicTask.setTaskType(taskToSchedule.getType());
        dynamicTask.setTaskDefName(taskToSchedule.getName());
        dynamicTask.setCorrelationId(workflowInstance.getCorrelationId());
        dynamicTask.setScheduledTime(System.currentTimeMillis());
        dynamicTask.setRetryCount(retryCount);
        dynamicTask.setCallbackAfterSeconds(taskToSchedule.getStartDelay());
        dynamicTask.setResponseTimeoutSeconds(taskDefinition.getResponseTimeoutSeconds());
        dynamicTask.setWorkflowTask(taskToSchedule);
        dynamicTask.setTaskType(taskName);
        dynamicTask.setRetriedTaskId(retriedTaskId);
        dynamicTask.setWorkflowPriority(workflowInstance.getPriority());
        return Collections.singletonList(dynamicTask);
    }

    /**
     * Helper method that looks into the input params and returns the dynamic task name
     *
     * @param taskInput:     a map which contains different input parameters and
     *                       also contains the mapping between the dynamic task name param and the actual name representing the dynamic task
     * @param taskNameParam: the key that is used to look up the dynamic task name.
     * @return The name of the dynamic task
     * @throws TerminateWorkflowException : In case is there is no value dynamic task name in the input parameters.
     */
    @VisibleForTesting
    String getDynamicTaskName(Map<String, Object> taskInput, String taskNameParam) throws TerminateWorkflowException {
        return Optional.ofNullable(taskInput.get(taskNameParam))
                .map(String::valueOf)
                .orElseThrow(() -> {
                    String reason = String.format("Cannot map a dynamic task based on the parameter and input. " +
                            "Parameter= %s, input= %s", taskNameParam, taskInput);
                    return new TerminateWorkflowException(reason);
                });
    }

    /**
     * This method gets the TaskDefinition for a specific {@link WorkflowTask}
     *
     * @param taskToSchedule: An instance of {@link WorkflowTask} which has the name of the using which the {@link TaskDef} can be retrieved.
     * @return An instance of TaskDefinition
     * @throws TerminateWorkflowException : in case of no workflow definition available
     */
    @VisibleForTesting
    TaskDef getDynamicTaskDefinition(WorkflowTask taskToSchedule) throws TerminateWorkflowException { //TODO this is a common pattern in code base can be moved to DAO
        return Optional.ofNullable(taskToSchedule.getTaskDefinition())
                .orElseGet(() -> Optional.ofNullable(metadataDAO.getTaskDef(taskToSchedule.getName()))
                        .orElseThrow(() -> {
                            String reason = String.format("Invalid task specified.  Cannot find task by name %s in the task definitions",
                                    taskToSchedule.getName());
                            return new TerminateWorkflowException(reason);
                        }));
    }
}
