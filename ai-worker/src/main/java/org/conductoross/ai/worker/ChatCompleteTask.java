/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.ai.worker;

import java.util.ArrayList;
import java.util.List;

import org.conductoross.ai.LLMProvider;
import org.conductoross.ai.model.ChatCompletion;
import org.conductoross.ai.model.ChatMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

public class ChatCompleteTask extends WorkflowSystemTask {

    public static final String TASK_TYPE = "CHAT_COMPLETE";
    private final ObjectMapper objectMapper;
    private final LLMProvider llmProvider;

    public ChatCompleteTask(LLMProvider llmProvider, ObjectMapper objectMapper) {
        super(TASK_TYPE);
        this.llmProvider = llmProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        return super.execute(workflow, task, workflowExecutor);
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        ChatCompletion chatCompletion = objectMapper.convertValue(task.getInputData(), ChatCompletion.class);
        ChatModel chatModel = llmProvider.getChatModel(chatCompletion.getLlmProvider());
        Prompt prompt = toPrompt(chatCompletion);
        ChatResponse response = chatModel.call(prompt);
        AssistantMessage output = response.getResult()
            .getOutput();
        //TODO:
        // Extract the output
        // If the output is a media then use the external storage to store the output
        // finally complete the task
        super.start(workflow, task, workflowExecutor);
    }


    private Prompt toPrompt(ChatCompletion chatCompletion) {
        List<Message> promptMessages = new ArrayList<>();
        for (ChatMessage message : chatCompletion.getMessages()) {
            Message msg = switch (message.getRole()) {
                case user -> new UserMessage(message.getMessage());
                case assistant -> new AssistantMessage(message.getMessage());
                case system -> new SystemMessage(message.getMessage());
            };
            promptMessages.add(msg);
        }
        ChatOptions options = ChatOptions.builder()
            .model(chatCompletion.getModel())
            .frequencyPenalty(chatCompletion.getFrequencyPenalty())
            .maxTokens(chatCompletion.getMaxTokens())
            .temperature(chatCompletion.getTemperature())
            .presencePenalty(chatCompletion.getPresencePenalty())
            .topP(chatCompletion.getTopP())
            .stopSequences(chatCompletion.getStopWords())
            .topK(chatCompletion.getTopK())
            .build();
        return new Prompt(promptMessages, options);
    }

    @Override
    public boolean isAsync() {
        return true;
    }
}
