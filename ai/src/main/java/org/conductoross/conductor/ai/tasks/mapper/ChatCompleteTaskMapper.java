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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.AIModelProvider;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.ChatMessage;
import org.conductoross.conductor.ai.models.LLMResponse;
import org.conductoross.conductor.ai.models.Media;
import org.conductoross.conductor.ai.models.ToolCall;
import org.conductoross.conductor.common.utils.StringTemplate;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import lombok.extern.slf4j.Slf4j;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_HTTP;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SIMPLE;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;

@Component
@Conditional(AIIntegrationEnabledCondition.class)
@Slf4j
public class ChatCompleteTaskMapper extends AIModelTaskMapper<ChatCompletion> {

    private static final Set<String> toolTaskTypes =
            Set.of(TASK_TYPE_HTTP, TASK_TYPE_SIMPLE, "MCP", "CALL_MCP_TOOL");

    /**
     * Nullable: present at runtime under Spring (auto-wired via the AI integration condition);
     * nullable in unit tests that instantiate the mapper directly. When null, the mapper falls back
     * to the historical "every provider supports prefill" assumption — same behavior as before this
     * dependency was introduced.
     */
    @Nullable private final AIModelProvider aiModelProvider;

    public ChatCompleteTaskMapper() {
        this(null);
    }

    @Autowired
    public ChatCompleteTaskMapper(@Nullable AIModelProvider aiModelProvider) {
        super(ChatCompletion.NAME);
        this.aiModelProvider = aiModelProvider;
    }

    @Override
    protected TaskModel getMappedTask(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {
        TaskModel taskModel = super.getMappedTask(taskMapperContext);
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();

        try {
            ChatCompletion chatCompletion =
                    objectMapper.convertValue(taskModel.getInputData(), ChatCompletion.class);
            List<ChatMessage> history = chatCompletion.getMessages();
            if (chatCompletion.getUserInput() != null && chatCompletion.getMessages().isEmpty()) {
                history.add(new ChatMessage(ChatMessage.Role.user, chatCompletion.getUserInput()));
            }
            // getHistory() internally skips prior loop-iteration assistant messages
            // for this same task refName when (a) previousResponseId is in play
            // (OpenAI Responses API server-side store owns the prior turns) or
            // (b) the provider declares it doesn't accept assistant-message
            // prefill (AIModel.supportsAssistantPrefill — e.g. Anthropic, where
            // Claude Sonnet 4.6+ rejects prefill outright). Participants, tool
            // calls, and sub-workflow context are still preserved in both cases.
            getHistory(workflowModel, taskModel, chatCompletion);
            updateTaskModel(chatCompletion, taskModel);

        } catch (Exception e) {
            if (e instanceof TerminateWorkflowException) {
                throw (TerminateWorkflowException) e;
            } else {
                log.error("input: {}", taskModel.getInputData());
                log.error(e.getMessage(), e);
                throw new TerminateWorkflowException(
                        String.format(
                                "Error preparing chat completion task input: %s", e.getMessage()));
            }
        }
        return taskModel;
    }

    protected void updateTaskModel(ChatCompletion chatCompletion, TaskModel simpleTask) {
        Map<String, Object> paramReplacement = chatCompletion.getPromptVariables();
        if (paramReplacement == null) {
            paramReplacement = new HashMap<>();
        }
        List<ChatMessage> messages = chatCompletion.getMessages();
        if (messages == null) {
            messages = new ArrayList<>();
        }
        for (ChatMessage message : messages) {
            String msgText = message.getMessage();
            if (msgText != null) {
                msgText = StringTemplate.fString(msgText, paramReplacement);
                message.setMessage(msgText);
            }
        }
        simpleTask.getInputData().put("messages", messages);
        simpleTask.getInputData().put("tools", chatCompletion.getTools());
    }

    /**
     * Resolves the configured {@link AIModel} for this chat completion and asks it whether it
     * accepts assistant-message prefill. Returns {@code true} (the historical default) if no
     * provider registry is wired in (unit tests), if the request specifies no provider, or if the
     * provider name doesn't match a registered model — those cases preserve the pre-capability
     * behavior so we don't silently change history-injection semantics for unrelated callers.
     */
    private boolean providerSupportsAssistantPrefill(ChatCompletion chatCompletion) {
        if (aiModelProvider == null || chatCompletion.getLlmProvider() == null) {
            return true;
        }
        try {
            AIModel model = aiModelProvider.getModel(chatCompletion);
            return model.supportsAssistantPrefill();
        } catch (RuntimeException unknownProvider) {
            log.debug(
                    "Provider '{}' not registered; defaulting supportsAssistantPrefill=true",
                    chatCompletion.getLlmProvider());
            return true;
        }
    }

    private void getHistory(
            WorkflowModel workflow, TaskModel chatCompleteTask, ChatCompletion chatCompletion) {
        Map<String, List<TaskModel>> refNameToTask = new HashMap<>();
        for (TaskModel task : workflow.getTasks()) {
            refNameToTask
                    .computeIfAbsent(
                            task.getWorkflowTask().getTaskReferenceName(), k -> new ArrayList<>())
                    .add(task);
        }

        /*
         Notes:
         If the chat complete task is running in a loop, then use the history from the loop
         If the chat complete task has a parent task reference, then collect history from the all the executions of the parent task reference
           which also includes the tool calls
        */
        String historyContextTaskRefName =
                chatCompleteTask.getWorkflowTask().getTaskReferenceName();
        if (chatCompleteTask.getParentTaskReferenceName() != null) {
            historyContextTaskRefName = chatCompleteTask.getParentTaskReferenceName();
        }
        // Suppress the same-refName loop-iteration assistant injection when either:
        //   (a) previousResponseId is set — OpenAI's Responses API server-side store
        //       already has every prior turn for this loop; re-injecting them
        //       duplicates context and, because we only emit the assistant side
        //       (not the matching user prompt), leaves the model staring at
        //       orphaned replies.
        //   (b) the provider declares it doesn't accept assistant-message prefill
        //       (see AIModel.supportsAssistantPrefill). The loop-iteration messages
        //       arrive as a trailing assistant turn on the next call — if the
        //       provider's API rejects that shape (e.g. Anthropic Sonnet 4.6+:
        //       400 "This model does not support assistant message prefill. The
        //       conversation must end with a user message."), the whole turn
        //       fails. Workflow authors who need prior-iteration state should
        //       template it into the user message via ${...output.result}.
        // Participants, tool calls, and sub-workflow context are not suppressed
        // in either case — those are legitimate conversation turns the server
        // (or model) has never seen.
        String prevRespId = chatCompletion.getPreviousResponseId();
        boolean suppressLoopAssistantHistory =
                (prevRespId != null && !prevRespId.isBlank())
                        || !providerSupportsAssistantPrefill(chatCompletion);
        List<ChatMessage> history = new ArrayList<>();
        for (TaskModel task : workflow.getTasks()) {
            if (!task.getStatus().isTerminal()) {
                continue;
            }
            boolean skipTask = true;
            ChatMessage.Role role = ChatMessage.Role.assistant;
            if (task.getParentTaskReferenceName() != null
                    && task.getParentTaskReferenceName().equals(historyContextTaskRefName)) {
                skipTask = false;
            } else if (task.isLoopOverTask()
                    && task.getWorkflowTask()
                            .getTaskReferenceName()
                            .equals(historyContextTaskRefName)) {
                // Same-refName loop iterations are exactly the assistant-message
                // duplication the Responses API has already absorbed; skip them.
                skipTask = suppressLoopAssistantHistory;
            } else if (chatCompletion.getParticipants() != null) {
                ChatMessage.Role participantRole =
                        chatCompletion
                                .getParticipants()
                                .get(task.getWorkflowTask().getTaskReferenceName());
                if (participantRole != null) {
                    role = participantRole;
                    skipTask = false;
                }
            }

            if (skipTask) {
                continue;
            }
            log.trace(
                    "\nTask {} - {} will be used for history",
                    task.getReferenceTaskName(),
                    task.getTaskType());
            LLMResponse response = null;

            try {
                response = objectMapper.convertValue(task.getOutputData(), LLMResponse.class);
            } catch (Exception ignore) {
                response = LLMResponse.builder().result(task.getOutputData()).build();
            }

            if (toolTaskTypes.contains(task.getWorkflowTask().getType())) {
                // This is a tool call
                ToolCall toolCall =
                        ToolCall.builder()
                                .inputParameters(task.getInputData())
                                .name(task.getTaskDefName())
                                .taskReferenceName(task.getReferenceTaskName())
                                .type(task.getTaskType())
                                .output(task.getOutputData())
                                .build();

                history.add(new ChatMessage(ChatMessage.Role.tool, toolCall));

            } else if (TASK_TYPE_SUB_WORKFLOW.equals(task.getWorkflowTask().getType())) {
                Object subWorkflowDef = task.getInputData().get("subWorkflowDefinition");
                Map<String, Object> input = Map.of();
                if (subWorkflowDef != null) {
                    WorkflowDef subWorkflow =
                            objectMapper.convertValue(subWorkflowDef, WorkflowDef.class);
                    input =
                            subWorkflow.getTasks().stream()
                                    .collect(
                                            Collectors.toMap(
                                                    WorkflowTask::getTaskReferenceName,
                                                    WorkflowTask::getInputParameters));
                }
                // This is a tool call
                ToolCall toolCall =
                        ToolCall.builder()
                                .inputParameters(input)
                                .name(task.getTaskDefName())
                                .taskReferenceName(task.getReferenceTaskName())
                                .type(task.getTaskType())
                                .build();
                history.add(new ChatMessage(ChatMessage.Role.tool_call, toolCall));

                ToolCall toolCallExecution =
                        ToolCall.builder()
                                .inputParameters(input)
                                .name(task.getTaskDefName())
                                .taskReferenceName(task.getReferenceTaskName())
                                .type(task.getTaskType())
                                .output(task.getOutputData())
                                .build();
                history.add(new ChatMessage(ChatMessage.Role.tool, toolCallExecution));

            } else if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {
                for (ToolCall toolCall : response.getToolCalls()) {
                    String toolRefName = toolCall.getTaskReferenceName();
                    List<TaskModel> toolModels =
                            refNameToTask.getOrDefault(toolRefName, new ArrayList<>());
                    for (TaskModel toolModel : toolModels) {
                        if (toolModel.getStatus().isTerminal()
                                && toolModel.getStatus().isSuccessful()) {
                            history.add(new ChatMessage(ChatMessage.Role.tool_call, toolCall));
                            ToolCall toolCallResult =
                                    ToolCall.builder()
                                            .inputParameters(toolModel.getInputData())
                                            .name(toolModel.getTaskDefName())
                                            .taskReferenceName(
                                                    toolModel
                                                            .getWorkflowTask()
                                                            .getTaskReferenceName())
                                            .type(toolModel.getTaskType())
                                            .output(toolModel.getOutputData())
                                            .build();
                            history.add(new ChatMessage(ChatMessage.Role.tool, toolCallResult));
                        }
                    }
                }

            } else {
                if (response.getResult() != null) {
                    Object resultObj = response.getResult();
                    if (resultObj instanceof Map<?, ?>) {
                        if (((Map<?, ?>) resultObj).containsKey("response")) {
                            resultObj = ((Map<?, ?>) resultObj).get("response");
                        }
                    }
                    var msg = new ChatMessage(role, String.valueOf(resultObj));
                    if (response.getMedia() != null) {
                        msg.setMedia(response.getMedia().stream().map(Media::getLocation).toList());
                    }
                    history.add(msg);
                }
            }
        }
        chatCompletion.getMessages().addAll(history);
    }
}
