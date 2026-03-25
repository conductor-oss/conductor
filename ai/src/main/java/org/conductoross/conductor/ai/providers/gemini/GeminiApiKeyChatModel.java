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
package org.conductoross.conductor.ai.providers.gemini;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;

/**
 * Spring AI {@link ChatModel} backed by the Google GenAI Java SDK ({@link Client}).
 *
 * <p>This is the <b>API-key path</b> for Gemini. It uses the REST-based
 * {@code generativelanguage.googleapis.com} endpoint which accepts Google AI Studio
 * API keys directly — no GCP IAM credentials or service accounts required.</p>
 *
 * <p>The standard {@link org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel}
 * always uses the Vertex AI gRPC endpoint which requires {@code GOOGLE_APPLICATION_CREDENTIALS}
 * or equivalent IAM auth. This class provides a lightweight alternative for users who
 * only have an API key.</p>
 *
 * @see GeminiVertex#getChatModel()
 */
class GeminiApiKeyChatModel implements ChatModel {

    private final Client client;

    GeminiApiKeyChatModel(Client client) {
        this.client = client;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        List<Content> contents = new ArrayList<>();
        String systemInstruction = null;

        for (var message : prompt.getInstructions()) {
            switch (message.getMessageType()) {
                case SYSTEM -> systemInstruction = message.getText();
                case USER -> contents.add(Content.fromParts(Part.fromText(message.getText())));
                case ASSISTANT -> contents.add(
                        Content.fromParts(Part.fromText(message.getText()))
                                .toBuilder()
                                .role("model")
                                .build());
                default -> contents.add(Content.fromParts(Part.fromText(message.getText())));
            }
        }

        // Model name from prompt options or default
        String modelName = "gemini-2.5-flash";
        if (prompt.getOptions() != null && prompt.getOptions().getModel() != null) {
            modelName = prompt.getOptions().getModel();
        }

        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();
        if (systemInstruction != null) {
            configBuilder.systemInstruction(Content.fromParts(Part.fromText(systemInstruction)));
        }

        GenerateContentResponse response =
                client.models.generateContent(modelName, contents, configBuilder.build());

        String text = response.text() != null ? response.text() : "";

        // Token usage
        int promptTokens = 0, completionTokens = 0, totalTokens = 0;
        if (response.usageMetadata().isPresent()) {
            var usage = response.usageMetadata().get();
            promptTokens = usage.promptTokenCount().orElse(0);
            completionTokens = usage.candidatesTokenCount().orElse(0);
            totalTokens = usage.totalTokenCount().orElse(promptTokens + completionTokens);
        }

        var genMetadata = ChatGenerationMetadata.builder().finishReason("STOP").build();
        var generation = new Generation(new AssistantMessage(text), genMetadata);

        var responseUsage = new DefaultUsage(promptTokens, completionTokens, totalTokens);
        var responseMetadata =
                ChatResponseMetadata.builder().usage(responseUsage).model(modelName).build();

        return new ChatResponse(List.of(generation), responseMetadata);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return null;
    }
}
