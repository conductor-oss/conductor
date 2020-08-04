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

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.TerminateWorkflowException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link TaskType#SIMPLE}
 * to a {@link Task} with status {@link Task.Status#SCHEDULED}. <b>NOTE:</b> There is not type defined for simples task.
 */
public class SimpleTaskMapper implements TaskMapper {

    public static final Logger logger = LoggerFactory.getLogger(SimpleTaskMapper.class);

    private final ParametersUtils parametersUtils;

    public SimpleTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    /**
     * This method maps a {@link WorkflowTask} of type {@link TaskType#SIMPLE}
     * to a {@link Task}
     *
     * @param taskMapperContext: A wrapper class containing the {@link WorkflowTask}, {@link WorkflowDef}, {@link Workflow} and a string representation of the TaskId
     * @throws TerminateWorkflowException In case if the task definition does not exist
     * @return: a List with just one simple task
     */
    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) throws TerminateWorkflowException {

        logger.debug("TaskMapperContext {} in SimpleTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        int retryCount = taskMapperContext.getRetryCount();
        String retriedTaskId = taskMapperContext.getRetryTaskId();

        TaskDef taskDefinition = Optional.ofNullable(taskToSchedule.getTaskDefinition())
                .orElseThrow(() -> {
                    String reason = String.format("Invalid task. Task %s does not have a definition", taskToSchedule.getName());
                    return new TerminateWorkflowException(reason);
                });

        Map<String, Object> input = parametersUtils.getTaskInput(taskToSchedule.getInputParameters(), workflowInstance, taskDefinition, taskMapperContext.getTaskId());
        Task simpleTask = new Task();
        simpleTask.setStartDelayInSeconds(taskToSchedule.getStartDelay());
        simpleTask.setTaskId(taskMapperContext.getTaskId());
        simpleTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        simpleTask.setInputData(input);
        simpleTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        simpleTask.setWorkflowType(workflowInstance.getWorkflowName());
        simpleTask.setStatus(Task.Status.SCHEDULED);
        simpleTask.setTaskType(taskToSchedule.getName());
        simpleTask.setTaskDefName(taskToSchedule.getName());
        simpleTask.setCorrelationId(workflowInstance.getCorrelationId());
        simpleTask.setScheduledTime(System.currentTimeMillis());
        simpleTask.setRetryCount(retryCount);
        simpleTask.setCallbackAfterSeconds(taskToSchedule.getStartDelay());
        simpleTask.setResponseTimeoutSeconds(taskDefinition.getResponseTimeoutSeconds());
        simpleTask.setWorkflowTask(taskToSchedule);
        simpleTask.setRetriedTaskId(retriedTaskId);
        simpleTask.setWorkflowPriority(workflowInstance.getPriority());
        simpleTask.setRateLimitPerFrequency(taskDefinition.getRateLimitPerFrequency());
        simpleTask.setRateLimitFrequencyInSeconds(taskDefinition.getRateLimitFrequencyInSeconds());
        return Collections.singletonList(simpleTask);
    }
}
