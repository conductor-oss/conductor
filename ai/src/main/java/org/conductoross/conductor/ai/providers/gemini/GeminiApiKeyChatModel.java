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
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;

/**
 * Spring AI {@link ChatModel} backed by the Google GenAI Java SDK ({@link Client}).
 *
 * <p>This is the <b>API-key path</b> for Gemini. It uses the REST-based {@code
 * generativelanguage.googleapis.com} endpoint which accepts Google AI Studio API keys directly — no
 * GCP IAM credentials or service accounts required.
 *
 * <p>Supports tool/function calling: Spring AI tool definitions are converted to GenAI {@link
 * FunctionDeclaration} objects, and GenAI {@link FunctionCall} responses are mapped back to Spring
 * AI {@link AssistantMessage.ToolCall} objects.
 *
 * @see GeminiVertex#getChatModel()
 */
class GeminiApiKeyChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(GeminiApiKeyChatModel.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Client client;

    GeminiApiKeyChatModel(Client client) {
        this.client = client;
    }

    @Override
    public @NotNull ChatResponse call(Prompt prompt) {
        List<Content> contents = new ArrayList<>();
        String systemInstruction = null;

        for (var message : prompt.getInstructions()) {
            switch (message.getMessageType()) {
                case SYSTEM -> systemInstruction = message.getText();
                case USER -> contents.add(Content.fromParts(Part.fromText(message.getText())));
                case ASSISTANT -> {
                    var assistantMsg = (AssistantMessage) message;
                    // Check if assistant message has tool calls (for multi-turn tool conversations)
                    if (assistantMsg.getToolCalls() != null
                            && !assistantMsg.getToolCalls().isEmpty()) {
                        List<Part> parts = new ArrayList<>();
                        for (var tc : assistantMsg.getToolCalls()) {
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> args =
                                        MAPPER.readValue(tc.arguments(), Map.class);
                                parts.add(Part.fromFunctionCall(tc.name(), args));
                            } catch (Exception e) {
                                parts.add(Part.fromText(tc.arguments()));
                            }
                        }
                        contents.add(
                                Content.fromParts(parts.toArray(new Part[0])).toBuilder()
                                        .role("model")
                                        .build());
                    } else {
                        contents.add(
                                Content.fromParts(Part.fromText(message.getText())).toBuilder()
                                        .role("model")
                                        .build());
                    }
                }
                case TOOL -> {
                    // Tool result message: convert to function response
                    String toolName =
                            message.getMetadata().getOrDefault("toolName", "unknown").toString();
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> resultMap =
                                MAPPER.readValue(message.getText(), Map.class);
                        contents.add(
                                Content.fromParts(Part.fromFunctionResponse(toolName, resultMap)));
                    } catch (Exception e) {
                        contents.add(
                                Content.fromParts(
                                        Part.fromFunctionResponse(
                                                toolName, Map.of("result", message.getText()))));
                    }
                }
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

        // Convert Spring AI tool callbacks to GenAI tool declarations
        List<Tool> tools = extractTools(prompt);
        if (!tools.isEmpty()) {
            configBuilder.tools(tools);
        }

        GenerateContentResponse response =
                client.models.generateContent(modelName, contents, configBuilder.build());

        // Extract text and tool calls from response
        String text = "";
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        String finishReason = "STOP";

        if (response.candidates().isPresent()) {
            for (Candidate candidate : response.candidates().get()) {
                if (candidate.content().isPresent()
                        && candidate.content().get().parts().isPresent()) {
                    for (Part part : candidate.content().get().parts().get()) {
                        if (part.text().isPresent()) {
                            text += part.text().get();
                        }
                        if (part.functionCall().isPresent()) {
                            FunctionCall fc = part.functionCall().get();
                            String name = fc.name().orElse("unknown");
                            String args = "{}";
                            if (fc.args().isPresent()) {
                                try {
                                    args = MAPPER.writeValueAsString(fc.args().get());
                                } catch (Exception e) {
                                    args = fc.args().get().toString();
                                }
                            }
                            String id = fc.id().orElse(UUID.randomUUID().toString());
                            toolCalls.add(
                                    new AssistantMessage.ToolCall(id, "function", name, args));
                            finishReason = "TOOL_CALLS";
                        }
                    }
                }
            }
        }

        // Token usage
        int promptTokens = 0, completionTokens = 0, totalTokens = 0;
        if (response.usageMetadata().isPresent()) {
            var usage = response.usageMetadata().get();
            promptTokens = usage.promptTokenCount().orElse(0);
            completionTokens = usage.candidatesTokenCount().orElse(0);
            totalTokens = usage.totalTokenCount().orElse(promptTokens + completionTokens);
        }

        var genMetadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();
        var assistantMessageBuilder = AssistantMessage.builder().content(text);
        if (!toolCalls.isEmpty()) {
            assistantMessageBuilder.toolCalls(toolCalls);
        }
        var assistantMessage = assistantMessageBuilder.build();
        var generation = new Generation(assistantMessage, genMetadata);

        var responseUsage = new DefaultUsage(promptTokens, completionTokens, totalTokens);
        var responseMetadata =
                ChatResponseMetadata.builder().usage(responseUsage).model(modelName).build();

        return new ChatResponse(List.of(generation), responseMetadata);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return null;
    }

    /** Extract tool definitions from the prompt's options and convert to GenAI Tool format. */
    @SuppressWarnings("unchecked")
    private List<Tool> extractTools(Prompt prompt) {
        if (prompt.getOptions() == null) return List.of();

        List<ToolCallback> callbacks = List.of();
        try {
            var options = prompt.getOptions();
            var method = options.getClass().getMethod("getToolCallbacks");
            callbacks = (List<ToolCallback>) method.invoke(options);
        } catch (Exception e) {
            log.warn("Failed to extract tool callbacks from options: {}", e.getMessage());
            return List.of();
        }

        log.info("Extracted {} tool callbacks from prompt options", callbacks.size());
        if (callbacks == null || callbacks.isEmpty()) return List.of();

        List<FunctionDeclaration> declarations = new ArrayList<>();
        for (ToolCallback cb : callbacks) {
            var toolDef = cb.getToolDefinition();
            FunctionDeclaration.Builder fdBuilder =
                    FunctionDeclaration.builder()
                            .name(toolDef.name())
                            .description(toolDef.description());

            // Convert JSON Schema string to GenAI Schema
            String inputSchema = toolDef.inputSchema();
            if (inputSchema != null && !inputSchema.isBlank()) {
                try {
                    Map<String, Object> schemaMap = MAPPER.readValue(inputSchema, Map.class);
                    fdBuilder.parameters(Schema.fromJson(MAPPER.writeValueAsString(schemaMap)));
                } catch (Exception e) {
                    // Skip schema if parsing fails
                }
            }

            declarations.add(fdBuilder.build());
        }

        return List.of(Tool.builder().functionDeclarations(declarations).build());
    }
}
