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
package org.conductoross.conductor.ai.tasks.mapper;

import java.util.List;

import org.conductoross.conductor.ai.models.LLMWorkerInput;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.model.TaskModel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
@Conditional(AIIntegrationEnabledCondition.class)
public abstract class AIModelTaskMapper<T extends LLMWorkerInput> implements TaskMapper {

    public static final String EMBEDDINGS = "embeddings";
    public static final String LLM_PROVIDER = "llmProvider";
    public static final String MODEL_NAME = "model";
    public static final String INDEX = "index";
    public static final String PROMPT_NAME_KEY = "promptName";
    public static final String VECTOR_DB = "vectorDB";
    protected final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    private final String taskType;
    private final TypeReference<T> type = new TypeReference<T>() {};

    @Override
    public String getTaskType() {
        return taskType;
    }

    @Override
    public final List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {
        TaskModel simpleTask = getMappedTask(taskMapperContext);
        return List.of(simpleTask);
    }

    protected TaskModel getMappedTask(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {
        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        int retryCount = taskMapperContext.getRetryCount();
        String retriedTaskId = taskMapperContext.getRetryTaskId();
        TaskDef taskDefinition = workflowTask.getTaskDefinition();
        if (taskDefinition == null) {
            taskDefinition = new TaskDef();
        }
        TaskModel simpleTask = taskMapperContext.createTaskModel();
        simpleTask.setTaskType(workflowTask.getType());
        simpleTask.setStartDelayInSeconds(workflowTask.getStartDelay());
        simpleTask.setStatus(TaskModel.Status.SCHEDULED);
        simpleTask.setRetryCount(retryCount);
        simpleTask.setCallbackAfterSeconds(workflowTask.getStartDelay());
        simpleTask.setResponseTimeoutSeconds(taskDefinition.getResponseTimeoutSeconds());
        simpleTask.setRetriedTaskId(retriedTaskId);
        simpleTask.setRateLimitPerFrequency(taskDefinition.getRateLimitPerFrequency());
        simpleTask.setRateLimitFrequencyInSeconds(taskDefinition.getRateLimitFrequencyInSeconds());
        simpleTask.setInputData(taskMapperContext.getTaskInput());
        simpleTask.setTaskDefName(getTaskType());
        return simpleTask;
    }
}
