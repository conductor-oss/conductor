/*
 * Copyright 2018 Netflix, Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.TerminateWorkflowException;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.dao.MetadataDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SubWorkflowTaskMapper implements TaskMapper {

    private static final Logger logger = LoggerFactory.getLogger(SubWorkflowTaskMapper.class);

    private final ParametersUtils parametersUtils;
    private final MetadataDAO metadataDAO;

    @Inject
    public SubWorkflowTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
    }

    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {
        logger.debug("TaskMapperContext {} in SubWorkflowTaskMapper", taskMapperContext);
        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();
        //Check if the are sub workflow parameters, if not throw an exception, cannot initiate a sub-workflow without workflow params
        SubWorkflowParams subWorkflowParams = getSubWorkflowParams(taskToSchedule);

        Map<String, Object> resolvedParams = getSubWorkflowInputParameters(workflowInstance, subWorkflowParams);

        String subWorkflowName = resolvedParams.get("name").toString();
        Integer subWorkflowVersion = getSubWorkflowVersion(resolvedParams, subWorkflowName);

        Object subWorkflowDefinition = resolvedParams.get("workflowDefinition");

        Map subWorkflowTaskToDomain = null;
        Object uncheckedTaskToDomain = resolvedParams.get("taskToDomain");
        if (uncheckedTaskToDomain instanceof Map) {
            subWorkflowTaskToDomain = (Map) uncheckedTaskToDomain;
        }

        Task subWorkflowTask = new Task();
        subWorkflowTask.setTaskType(SubWorkflow.NAME);
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
        subWorkflowTask.setStatus(Task.Status.SCHEDULED);
        subWorkflowTask.setWorkflowTask(taskToSchedule);
        subWorkflowTask.setWorkflowPriority(workflowInstance.getPriority());
        logger.debug("SubWorkflowTask {} created to be Scheduled", subWorkflowTask);
        return Collections.singletonList(subWorkflowTask);
    }

    @VisibleForTesting
    SubWorkflowParams getSubWorkflowParams(WorkflowTask taskToSchedule) {
        return Optional.ofNullable(taskToSchedule.getSubWorkflowParam())
                .orElseThrow(() -> {
                    String reason = String.format("Task %s is defined as sub-workflow and is missing subWorkflowParams. " +
                            "Please check the blueprint", taskToSchedule.getName());
                    logger.error(reason);
                    return new TerminateWorkflowException(reason);
                });
    }

    private Map<String, Object> getSubWorkflowInputParameters(Workflow workflowInstance, SubWorkflowParams subWorkflowParams) {
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

    private Integer getSubWorkflowVersion(Map<String, Object> resolvedParams, String subWorkflowName) {
        return Optional.ofNullable(resolvedParams.get("version"))
                .map(Object::toString)
                .map(Integer::parseInt)
                .orElseGet(
                        () -> metadataDAO.getLatestWorkflowDef(subWorkflowName)
                                .map(WorkflowDef::getVersion)
                                .orElseThrow(() -> {
                                    String reason = String.format("The Task %s defined as a sub-workflow has no workflow definition available ", subWorkflowName);
                                    logger.error(reason);
                                    return new TerminateWorkflowException(reason);
                                }));
    }
}
