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
package org.conductoross.conductor.ai.providers.anthropic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.models.ToolSpec;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.Tool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.image.ImageModel;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anthropic implements AIModel {

    public static final String NAME = "anthropic";
    private final AnthropicConfiguration config;

    private final AnthropicMessagesApi messagesApi;
    private final AnthropicChatModel chatModel;

    @SuppressWarnings("unchecked")
    public Anthropic(AnthropicConfiguration config) {
        this.config = config;
        long timeoutSecs = config.getTimeout() != null ? config.getTimeout().getSeconds() : 600;

        this.messagesApi =
                new AnthropicMessagesApi(
                        config.getApiKey(), config.getBaseURL(), config.getVersion(), timeoutSecs);
        this.chatModel = new AnthropicChatModel(messagesApi);
    }

    @Override
    public String getModelProvider() {
        return NAME;
    }

    @Override
    public List<Float> generateEmbeddings(EmbeddingGenRequest embeddingGenRequest) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public ChatModel getChatModel() {
        return this.chatModel;
    }

    @Override
    public ChatOptions getChatOptions(ChatCompletion input) {
        List<Tool> tools = convertTools(input);
        Double temperature = input.getTemperature();
        Integer thinkingBudget = null;

        if (input.getThinkingTokenLimit() > 0) {
            thinkingBudget = input.getThinkingTokenLimit();
            temperature = 1.0; // Thinking mode requires temperature=1
        }

        return AnthropicChatOptions.builder()
                .model(input.getModel())
                .maxTokens(input.getMaxTokens())
                .temperature(temperature)
                .topP(input.getTopP())
                .topK(input.getTopK())
                .stopSequences(input.getStopWords())
                .thinkingBudgetTokens(thinkingBudget)
                .tools(tools.isEmpty() ? null : tools)
                .build();
    }

    @Override
    public ImageModel getImageModel() {
        throw new UnsupportedOperationException("Image generation not supported by the model yet");
    }

    // -- Helpers --

    @SuppressWarnings("unchecked")
    private List<Tool> convertTools(ChatCompletion input) {
        List<Tool> tools = new ArrayList<>();

        // Built-in tools
        if (input.isWebSearch()) {
            tools.add(Tool.webSearch());
        }
        if (input.isCodeInterpreter()) {
            tools.add(Tool.codeExecution());
        }

        // Convert Conductor ToolSpecs to Anthropic function tools
        if (input.getTools() != null) {
            for (ToolSpec toolSpec : input.getTools()) {
                Map<String, Object> schema =
                        toolSpec.getInputSchema() != null
                                ? toolSpec.getInputSchema()
                                : Map.of("type", "object");
                tools.add(Tool.function(toolSpec.getName(), toolSpec.getDescription(), schema));
            }
        }

        return tools;
    }
}
