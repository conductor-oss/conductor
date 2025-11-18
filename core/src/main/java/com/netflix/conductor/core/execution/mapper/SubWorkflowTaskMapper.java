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

import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

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
    public String getTaskType() {
        return TaskType.SUB_WORKFLOW.name();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {
        LOGGER.debug("TaskMapperContext {} in SubWorkflowTaskMapper", taskMapperContext);
        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();
        String taskId = taskMapperContext.getTaskId();
        // Check if there are sub workflow parameters, if not throw an exception, cannot initiate a
        // sub-workflow without workflow params
        SubWorkflowParams subWorkflowParams = getSubWorkflowParams(workflowTask);

        Map<String, Object> resolvedParams =
                getSubWorkflowInputParameters(workflowModel, subWorkflowParams);

        String subWorkflowName = resolvedParams.get("name").toString();
        Integer subWorkflowVersion = getSubWorkflowVersion(resolvedParams, subWorkflowName);

        Object subWorkflowDefinition = resolvedParams.get("workflowDefinition");

        Map subWorkflowTaskToDomain = null;
        Object uncheckedTaskToDomain = resolvedParams.get("taskToDomain");
        if (uncheckedTaskToDomain instanceof Map) {
            subWorkflowTaskToDomain = (Map) uncheckedTaskToDomain;
        }

        TaskModel subWorkflowTask = taskMapperContext.createTaskModel();
        subWorkflowTask.setTaskType(TASK_TYPE_SUB_WORKFLOW);
        subWorkflowTask.addInput("subWorkflowName", subWorkflowName);
        subWorkflowTask.addInput("priority", resolvedParams.get("priority"));
        subWorkflowTask.addInput("subWorkflowVersion", subWorkflowVersion);
        subWorkflowTask.addInput("subWorkflowTaskToDomain", subWorkflowTaskToDomain);
        subWorkflowTask.addInput("subWorkflowDefinition", subWorkflowDefinition);
        subWorkflowTask.addInput("workflowInput", taskMapperContext.getTaskInput());
        subWorkflowTask.setStatus(TaskModel.Status.SCHEDULED);
        subWorkflowTask.setCallbackAfterSeconds(workflowTask.getStartDelay());
        if (subWorkflowParams.getPriority() != null
                && !StringUtils.isEmpty(subWorkflowParams.getPriority().toString())) {
            int priority = Integer.parseInt(subWorkflowParams.getPriority().toString());
            subWorkflowTask.setWorkflowPriority(priority);
        }
        LOGGER.debug("SubWorkflowTask {} created to be Scheduled", subWorkflowTask);
        return List.of(subWorkflowTask);
    }

    @VisibleForTesting
    SubWorkflowParams getSubWorkflowParams(WorkflowTask workflowTask) {
        return Optional.ofNullable(workflowTask.getSubWorkflowParam())
                .orElseThrow(
                        () -> {
                            String reason =
                                    String.format(
                                            "Task %s is defined as sub-workflow and is missing subWorkflowParams. "
                                                    + "Please check the workflow definition",
                                            workflowTask.getName());
                            LOGGER.error(reason);
                            return new TerminateWorkflowException(reason);
                        });
    }

    private Map<String, Object> getSubWorkflowInputParameters(
            WorkflowModel workflowModel, SubWorkflowParams subWorkflowParams) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", subWorkflowParams.getName());
        params.put("priority", subWorkflowParams.getPriority());

        Integer version = subWorkflowParams.getVersion();
        if (version != null) {
            params.put("version", version);
        }
        Map<String, String> taskToDomain = subWorkflowParams.getTaskToDomain();
        if (taskToDomain != null) {
            params.put("taskToDomain", taskToDomain);
        }

        params = parametersUtils.getTaskInputV2(params, workflowModel, null, null);

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
