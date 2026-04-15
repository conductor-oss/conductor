/*
 * Copyright 2022 Conductor Authors.
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
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link
 * TaskType#DYNAMIC} to a {@link TaskModel} based on definition derived from the dynamic task name
 * defined in {@link WorkflowTask#getInputParameters()}
 */
@Component
public class DynamicTaskMapper implements TaskMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicTaskMapper.class);

    private final ParametersUtils parametersUtils;
    private final MetadataDAO metadataDAO;

    public DynamicTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
    }

    @Override
    public String getTaskType() {
        return TaskType.DYNAMIC.name();
    }

    /**
     * This method maps a dynamic task to a {@link TaskModel} based on the input params
     *
     * @param taskMapperContext: A wrapper class containing the {@link WorkflowTask}, {@link
     *     WorkflowDef}, {@link WorkflowModel} and a string representation of the TaskId
     * @return A {@link List} that contains a single {@link TaskModel} with a {@link
     *     TaskModel.Status#SCHEDULED}
     */
    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {
        LOGGER.debug("TaskMapperContext {} in DynamicTaskMapper", taskMapperContext);
        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        Map<String, Object> taskInput = taskMapperContext.getTaskInput();
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();
        int retryCount = taskMapperContext.getRetryCount();
        String retriedTaskId = taskMapperContext.getRetryTaskId();

        String taskNameParam = workflowTask.getDynamicTaskNameParam();
        String taskName = getDynamicTaskName(taskInput, taskNameParam);
        workflowTask.setName(taskName);
        TaskDef taskDefinition = getDynamicTaskDefinition(workflowTask);
        workflowTask.setTaskDefinition(taskDefinition);

        Map<String, Object> input =
                parametersUtils.getTaskInput(
                        workflowTask.getInputParameters(),
                        workflowModel,
                        taskDefinition,
                        taskMapperContext.getTaskId());

        // IMPORTANT: The WorkflowTask that is inside TaskMapperContext is changed above
        // createTaskModel() must be called here so the changes are reflected in the created
        // TaskModel
        TaskModel dynamicTask = taskMapperContext.createTaskModel();
        dynamicTask.setStartDelayInSeconds(workflowTask.getStartDelay());
        dynamicTask.setInputData(input);
        dynamicTask.setStatus(TaskModel.Status.SCHEDULED);
        dynamicTask.setRetryCount(retryCount);
        dynamicTask.setCallbackAfterSeconds(workflowTask.getStartDelay());
        dynamicTask.setResponseTimeoutSeconds(taskDefinition.getResponseTimeoutSeconds());
        dynamicTask.setTaskType(taskName);
        dynamicTask.setRetriedTaskId(retriedTaskId);
        dynamicTask.setWorkflowPriority(workflowModel.getPriority());
        return Collections.singletonList(dynamicTask);
    }

    /**
     * Helper method that looks into the input params and returns the dynamic task name
     *
     * @param taskInput: a map which contains different input parameters and also contains the
     *     mapping between the dynamic task name param and the actual name representing the dynamic
     *     task
     * @param taskNameParam: the key that is used to look up the dynamic task name.
     * @return The name of the dynamic task
     * @throws TerminateWorkflowException : In case is there is no value dynamic task name in the
     *     input parameters.
     */
    @VisibleForTesting
    String getDynamicTaskName(Map<String, Object> taskInput, String taskNameParam)
            throws TerminateWorkflowException {
        return Optional.ofNullable(taskInput.get(taskNameParam))
                .map(String::valueOf)
                .orElseThrow(
                        () -> {
                            String reason =
                                    String.format(
                                            "Cannot map a dynamic task based on the parameter and input. "
                                                    + "Parameter= %s, input= %s",
                                            taskNameParam, taskInput);
                            return new TerminateWorkflowException(reason);
                        });
    }

    /**
     * This method gets the TaskDefinition for a specific {@link WorkflowTask}
     *
     * @param workflowTask: An instance of {@link WorkflowTask} which has the name of the using
     *     which the {@link TaskDef} can be retrieved.
     * @return An instance of TaskDefinition
     * @throws TerminateWorkflowException : in case of no workflow definition available
     */
    @VisibleForTesting
    TaskDef getDynamicTaskDefinition(WorkflowTask workflowTask)
            throws TerminateWorkflowException { // TODO this is a common pattern in code base can
        // be moved to DAO
        return Optional.ofNullable(workflowTask.getTaskDefinition())
                .orElseGet(
                        () ->
                                Optional.ofNullable(metadataDAO.getTaskDef(workflowTask.getName()))
                                        .orElseThrow(
                                                () -> {
                                                    String reason =
                                                            String.format(
                                                                    "Invalid task specified.  Cannot find task by name %s in the task definitions",
                                                                    workflowTask.getName());
                                                    return new TerminateWorkflowException(reason);
                                                }));
    }
}
