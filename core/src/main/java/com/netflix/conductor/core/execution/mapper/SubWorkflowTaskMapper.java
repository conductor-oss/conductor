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

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.google.common.annotations.VisibleForTesting;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;

@Component
public class SubWorkflowTaskMapper implements TaskMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubWorkflowTaskMapper.class);

    private final ParametersUtils parametersUtils;
    private final MetadataDAO metadataDAO;

    public SubWorkflowTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.SUB_WORKFLOW;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {
        LOGGER.debug("TaskMapperContext {} in SubWorkflowTaskMapper", taskMapperContext);
        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        WorkflowModel workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();
        // Check if there are sub workflow parameters, if not throw an exception, cannot initiate a
        // sub-workflow without workflow params
        SubWorkflowParams subWorkflowParams = getSubWorkflowParams(taskToSchedule);

        Map<String, Object> resolvedParams =
                getSubWorkflowInputParameters(workflowInstance, subWorkflowParams);

        String subWorkflowName = resolvedParams.get("name").toString();
        Integer subWorkflowVersion = getSubWorkflowVersion(resolvedParams, subWorkflowName);

        Object subWorkflowDefinition = resolvedParams.get("workflowDefinition");

        Map subWorkflowTaskToDomain = null;
        Object uncheckedTaskToDomain = resolvedParams.get("taskToDomain");
        if (uncheckedTaskToDomain instanceof Map) {
            subWorkflowTaskToDomain = (Map) uncheckedTaskToDomain;
        }

        TaskModel subWorkflowTask = new TaskModel();
        subWorkflowTask.setTaskType(TASK_TYPE_SUB_WORKFLOW);
        subWorkflowTask.setTaskDefName(taskToSchedule.getName());
        subWorkflowTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        subWorkflowTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        subWorkflowTask.setWorkflowType(workflowInstance.getWorkflowName());
        subWorkflowTask.setCorrelationId(workflowInstance.getCorrelationId());
        subWorkflowTask.setScheduledTime(System.currentTimeMillis());
        subWorkflowTask.getInputData().put("subWorkflowName", subWorkflowName);
        subWorkflowTask.getInputData().put("subWorkflowVersion", subWorkflowVersion);
        subWorkflowTask.getInputData().put("subWorkflowTaskToDomain", subWorkflowTaskToDomain);
        subWorkflowTask.getInputData().put("subWorkflowDefinition", subWorkflowDefinition);
        subWorkflowTask.getInputData().put("workflowInput", taskMapperContext.getTaskInput());
        subWorkflowTask.setTaskId(taskId);
        subWorkflowTask.setStatus(TaskModel.Status.SCHEDULED);
        subWorkflowTask.setWorkflowTask(taskToSchedule);
        subWorkflowTask.setWorkflowPriority(workflowInstance.getPriority());
        subWorkflowTask.setCallbackAfterSeconds(taskToSchedule.getStartDelay());
        LOGGER.debug("SubWorkflowTask {} created to be Scheduled", subWorkflowTask);
        return Collections.singletonList(subWorkflowTask);
    }

    @VisibleForTesting
    SubWorkflowParams getSubWorkflowParams(WorkflowTask taskToSchedule) {
        return Optional.ofNullable(taskToSchedule.getSubWorkflowParam())
                .orElseThrow(
                        () -> {
                            String reason =
                                    String.format(
                                            "Task %s is defined as sub-workflow and is missing subWorkflowParams. "
                                                    + "Please check the blueprint",
                                            taskToSchedule.getName());
                            LOGGER.error(reason);
                            return new TerminateWorkflowException(reason);
                        });
    }

    private Map<String, Object> getSubWorkflowInputParameters(
            WorkflowModel workflowInstance, SubWorkflowParams subWorkflowParams) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", subWorkflowParams.getName());

        Integer version = subWorkflowParams.getVersion();
        if (version != null) {
            params.put("version", version);
        }
        Map<String, String> taskToDomain = subWorkflowParams.getTaskToDomain();
        if (taskToDomain != null) {
            params.put("taskToDomain", taskToDomain);
        }

        params = parametersUtils.getTaskInputV2(params, workflowInstance, null, null);

        // do not resolve params inside subworkflow definition
        Object subWorkflowDefinition = subWorkflowParams.getWorkflowDefinition();
        if (subWorkflowDefinition != null) {
            params.put("workflowDefinition", subWorkflowDefinition);
        }

        return params;
    }

    private Integer getSubWorkflowVersion(
            Map<String, Object> resolvedParams, String subWorkflowName) {
        return Optional.ofNullable(resolvedParams.get("version"))
                .map(Object::toString)
                .map(Integer::parseInt)
                .orElseGet(
                        () ->
                                metadataDAO
                                        .getLatestWorkflowDef(subWorkflowName)
                                        .map(WorkflowDef::getVersion)
                                        .orElseThrow(
                                                () -> {
                                                    String reason =
                                                            String.format(
                                                                    "The Task %s defined as a sub-workflow has no workflow definition available ",
                                                                    subWorkflowName);
                                                    LOGGER.error(reason);
                                                    return new TerminateWorkflowException(reason);
                                                }));
    }
}
