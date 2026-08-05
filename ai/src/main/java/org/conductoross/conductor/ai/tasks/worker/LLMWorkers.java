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
package org.conductoross.conductor.ai.tasks.worker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.LLMs;
import org.conductoross.conductor.ai.model.AudioGenRequest;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.ChatMessage;
import org.conductoross.conductor.ai.model.EmbeddingGenRequest;
import org.conductoross.conductor.ai.model.ImageGenRequest;
import org.conductoross.conductor.ai.model.LLMResponse;
import org.conductoross.conductor.ai.model.TextCompletion;
import org.conductoross.conductor.ai.model.VideoGenRequest;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.conductoross.conductor.core.execution.tasks.AnnotatedSystemTaskWorker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;
import com.netflix.conductor.sdk.workflow.task.OutputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
@Component
@Conditional(AIIntegrationEnabledCondition.class)
public class LLMWorkers implements AnnotatedSystemTaskWorker {

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private final LLMs llm;
    private final Map<String, TaskMapper> taskMappers;
    private final ParametersUtils parametersUtils;
    private final ExecutionDAOFacade executionDAOFacade;

    public LLMWorkers(
            LLMs llm,
            @Qualifier("taskMappersByTaskType") Map<String, TaskMapper> taskMappers,
            ParametersUtils parametersUtils,
            ExecutionDAOFacade executionDAOFacade) {
        this.llm = llm;
        this.taskMappers = taskMappers;
        this.parametersUtils = parametersUtils;
        this.executionDAOFacade = executionDAOFacade;
        log.info("AI Workers initialized {}", llm.getClass());
    }

    private ChatCompletion resolveChatCompletion(Task task) {
        WorkflowModel workflow =
                executionDAOFacade.getWorkflowModel(task.getWorkflowInstanceId(), true);
        TaskModel taskModel =
                workflow.getTasks().stream()
                        .filter(candidate -> task.getTaskId().equals(candidate.getTaskId()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Cannot find task %s in workflow %s"
                                                        .formatted(
                                                                task.getTaskId(),
                                                                task.getWorkflowInstanceId())));
        TaskMapper taskMapper = taskMappers.get(taskModel.getTaskType());
        if (taskMapper == null) {
            throw new IllegalStateException(
                    "No task mapper registered for " + taskModel.getTaskType());
        }
        var input =
                parametersUtils.getTaskInput(
                        taskModel.getWorkflowTask().getInputParameters(),
                        workflow,
                        taskModel.getWorkflowTask().getTaskDefinition(),
                        taskModel.getTaskId());
        TaskModel rematerialized =
                taskMapper
                        .getMappedTasks(
                                TaskMapperContext.newBuilder()
                                        .withWorkflowModel(workflow)
                                        .withWorkflowTask(taskModel.getWorkflowTask())
                                        .withTaskDefinition(
                                                taskModel.getWorkflowTask().getTaskDefinition())
                                        .withTaskInput(input)
                                        .withTaskId(taskModel.getTaskId())
                                        .withRetryCount(taskModel.getRetryCount())
                                        .withRetryTaskId(taskModel.getRetriedTaskId())
                                        .build())
                        .getFirst();
        return objectMapper.convertValue(rematerialized.getInputData(), ChatCompletion.class);
    }

    @WorkerTask(value = "GENERATE_IMAGE")
    public LLMResponse generateImage(ImageGenRequest input) {
        return llm.generateImage(TaskContext.get().getTask(), input);
    }

    @WorkerTask(value = "GENERATE_AUDIO")
    public LLMResponse generateAudio(AudioGenRequest input) {
        return llm.generateAudio(TaskContext.get().getTask(), input);
    }

    @WorkerTask(value = "GENERATE_VIDEO")
    @SuppressWarnings("all")
    public TaskResult generateVideo(VideoGenRequest input) {
        Task task = TaskContext.get().getTask();
        String jobId = (String) task.getOutputData().get("jobId");
        if (jobId == null) {
            // start generation
            LLMResponse response = llm.generateVideo(task, input);
            TaskResult result = new TaskResult(task);
            result.setCallbackAfterSeconds(5L);
            result.setStatus(TaskResult.Status.IN_PROGRESS);
            result.getOutputData().putAll(objectMapper.convertValue(response, Map.class));
            result.getOutputData().put("jobId", response.getJobId());
            return result;
        }
        input.setJobId(jobId);
        LLMResponse response = llm.checkVideoStatus(task, input);
        TaskResult result = new TaskResult(task);
        result.setCallbackAfterSeconds(5L);
        result.getOutputData().putAll(objectMapper.convertValue(response, Map.class));
        if ("COMPLETED".equals(response.getFinishReason())) {
            result.setStatus(TaskResult.Status.COMPLETED);
        } else if ("FAILED".equals(response.getFinishReason())) {
            result.setStatus(TaskResult.Status.FAILED);
        }

        return result;
    }

    @WorkerTask(value = "LLM_TEXT_COMPLETE")
    public LLMResponse textCompletion(TextCompletion input) {
        ChatCompletion chatCompletion = new ChatCompletion();

        boolean jsonOutput = input.isJsonOutput();
        chatCompletion.setTemperature(input.getTemperature());
        chatCompletion.setMaxResults(input.getMaxResults());
        chatCompletion.setMaxTokens(input.getMaxTokens());
        chatCompletion.setTopP(input.getTopP());
        chatCompletion.setStopWords(input.getStopWords());
        chatCompletion.setLlmProvider(input.getLlmProvider());
        chatCompletion.setModel(input.getModel());
        chatCompletion.setJsonOutput(jsonOutput);
        chatCompletion.setInstructions(
                input.getPromptName() != null ? input.getPromptName() : input.getPrompt());
        chatCompletion.setPromptVersion(input.getPromptVersion());
        chatCompletion.setPromptVariables(input.getPromptVariables());
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(
                new ChatMessage(
                        ChatMessage.Role.user,
                        "use the instructions given to generate the response."));
        chatCompletion.setMessages(messages);
        return llm.chatComplete(TaskContext.get().getTask(), chatCompletion);
    }

    @SneakyThrows
    @WorkerTask("LLM_CHAT_COMPLETE")
    public LLMResponse chatCompletion(ChatCompletion chatCompletion) {
        Task task = TaskContext.get().getTask();
        return llm.chatComplete(task, resolveChatCompletion(task));
    }

    @WorkerTask("LLM_GENERATE_EMBEDDINGS")
    public @OutputParam("result") List<Float> generateEmbeddings(EmbeddingGenRequest input) {
        if (isBlank(input.getText())) {
            throw new NonRetryableException("No input text provided to generate embeddings");
        }
        String llmProvider = input.getLlmProvider();
        return generateEmbeddings(
                TaskContext.get().getTask(),
                llmProvider,
                input.getModel(),
                input.getText(),
                input.getDimensions());
    }

    private List<Float> generateEmbeddings(
            Task task,
            String embeddingModelProvider,
            String embeddingModel,
            String text,
            Integer dimensions) {
        EmbeddingGenRequest request =
                EmbeddingGenRequest.builder()
                        .model(embeddingModel)
                        .dimensions(dimensions)
                        .text(text)
                        .build();
        request.setLlmProvider(embeddingModelProvider);
        return llm.generateEmbeddings(task, request);
    }
}
