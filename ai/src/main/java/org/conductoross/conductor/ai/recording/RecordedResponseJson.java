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
package org.conductoross.conductor.ai.recording;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

/** JSON storage for Spring AI responses, including their extensible metadata maps. */
public final class RecordedResponseJson {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final ObjectMapper MAPPER = new ObjectMapperProvider().getObjectMapper().copy();

    public static JsonNode write(ChatResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Cannot record an absent model response");
        }
        ObjectNode data = MAPPER.createObjectNode();
        data.set("metadata", responseMetadata(response));
        ArrayNode results = data.putArray("results");
        for (Generation generation : response.getResults()) {
            results.add(generation(generation));
        }
        return data;
    }

    /** Restore all response data, replacing only tool-call IDs for this playback invocation. */
    public static ChatResponse read(JsonNode data, String idPrefix) {
        ObjectNode response = requireObject(data, "response");
        ArrayNode results = requireArray(response.get("results"), "response.results");
        List<Generation> generations = new ArrayList<>();
        int callIndex = 0;
        int resultIndex = 0;
        for (JsonNode result : results) {
            String resultField = "response.results[" + resultIndex++ + "]";
            ObjectNode recordedResult = requireObject(result, resultField);
            ObjectNode output =
                    requireObject(recordedResult.get("output"), resultField + ".output");
            List<Media> media = new ArrayList<>();
            for (JsonNode item : requireArray(output.get("media"), resultField + ".output.media")) {
                ObjectNode recordedMedia = requireObject(item, resultField + ".output.media item");
                media.add(
                        Media.builder()
                                .mimeType(
                                        MimeTypeUtils.parseMimeType(
                                                requireText(
                                                        recordedMedia.get("mimeType"), "mimeType")))
                                .id(recordedMedia.path("id").asText(null))
                                .name(recordedMedia.path("name").asText(null))
                                .data(
                                        requireBoolean(recordedMedia.get("binary"), "binary")
                                                ? MAPPER.convertValue(
                                                        requireValue(
                                                                recordedMedia.get("data"), "data"),
                                                        byte[].class)
                                                : requireText(recordedMedia.get("data"), "data"))
                                .build());
            }
            List<AssistantMessage.ToolCall> calls = new ArrayList<>();
            for (JsonNode call :
                    requireArray(output.get("toolCalls"), resultField + ".output.toolCalls")) {
                ObjectNode recordedCall =
                        requireObject(call, resultField + ".output.toolCalls item");
                calls.add(
                        new AssistantMessage.ToolCall(
                                idPrefix + "_" + callIndex++,
                                requireText(recordedCall.get("type"), "type"),
                                requireText(recordedCall.get("name"), "name"),
                                requireText(recordedCall.get("arguments"), "arguments")));
            }
            ObjectNode metadata =
                    requireObject(recordedResult.get("metadata"), resultField + ".metadata");
            generations.add(
                    new Generation(
                            AssistantMessage.builder()
                                    .content(output.path("text").asText(null))
                                    .properties(
                                            MAPPER.convertValue(
                                                    requireObject(
                                                            output.get("metadata"),
                                                            resultField + ".output.metadata"),
                                                    MAP))
                                    .toolCalls(calls)
                                    .media(media)
                                    .build(),
                            ChatGenerationMetadata.builder()
                                    .finishReason(metadata.path("finishReason").asText(null))
                                    .contentFilters(
                                            MAPPER.convertValue(
                                                    metadata.get("contentFilters"),
                                                    new TypeReference<Set<String>>() {}))
                                    .metadata(
                                            MAPPER.convertValue(
                                                    requireObject(
                                                            metadata.get("properties"),
                                                            resultField + ".metadata.properties"),
                                                    MAP))
                                    .build()));
        }
        ObjectNode metadata = requireObject(response.get("metadata"), "response.metadata");
        List<PromptMetadata.PromptFilterMetadata> filters = new ArrayList<>();
        for (JsonNode filter :
                requireArray(metadata.get("promptMetadata"), "response.metadata.promptMetadata")) {
            ObjectNode promptFilter =
                    requireObject(filter, "response.metadata.promptMetadata item");
            filters.add(
                    PromptMetadata.PromptFilterMetadata.from(
                            requireValue(promptFilter.get("promptIndex"), "promptIndex").asInt(),
                            MAPPER.convertValue(
                                    promptFilter.get("contentFilterMetadata"), Object.class)));
        }
        return new ChatResponse(
                generations,
                ChatResponseMetadata.builder()
                        .id(metadata.path("id").asText(null))
                        .model(metadata.path("model").asText(null))
                        .usage(MAPPER.convertValue(metadata.get("usage"), DefaultUsage.class))
                        .rateLimit(
                                MAPPER.convertValue(
                                        metadata.get("rateLimit"), RecordedRateLimit.class))
                        .promptMetadata(PromptMetadata.of(filters))
                        .metadata(
                                MAPPER.convertValue(
                                        requireObject(
                                                metadata.get("properties"),
                                                "response.metadata.properties"),
                                        MAP))
                        .build());
    }

    /** Validate the stored response shape before it is accepted for playback. */
    public static void validate(JsonNode data) {
        read(data, "validation");
    }

    /**
     * Compare recorded responses without per-call values while retaining provider metadata that
     * affects the response exposed to callers.
     */
    public static JsonNode responseContent(JsonNode response) {
        ObjectNode content = response.deepCopy();
        ObjectNode metadata = (ObjectNode) content.get("metadata");
        metadata.remove("id");
        metadata.remove("usage");
        metadata.remove("rateLimit");
        // OpenAI Responses stores its per-call ID in both fields.
        ((ObjectNode) metadata.get("properties")).remove("response_id");
        // This is another provider usage counter rather than response content.
        ((ObjectNode) metadata.get("properties")).remove("reasoning_tokens");

        JsonNode results = content.get("results");
        int callIndex = 0;
        for (JsonNode result : results) {
            for (JsonNode call : result.get("output").get("toolCalls")) {
                ((ObjectNode) call).put("id", "call_" + callIndex++);
            }
        }
        return content;
    }

    private static ObjectNode responseMetadata(ChatResponse response) {
        ChatResponseMetadata metadata = response.getMetadata();
        ObjectNode data = MAPPER.createObjectNode();
        data.put("id", metadata.getId());
        data.put("model", metadata.getModel());
        data.set("usage", usage(metadata.getUsage()));
        data.set("rateLimit", rateLimit(metadata.getRateLimit()));
        ArrayNode promptMetadata = data.putArray("promptMetadata");
        for (PromptMetadata.PromptFilterMetadata filter : metadata.getPromptMetadata()) {
            ObjectNode item = promptMetadata.addObject();
            item.put("promptIndex", filter.getPromptIndex());
            item.set(
                    "contentFilterMetadata", MAPPER.valueToTree(filter.getContentFilterMetadata()));
        }
        data.set("properties", properties(metadata.entrySet()));
        return data;
    }

    private static ObjectNode requireObject(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return (ObjectNode) node;
    }

    private static ArrayNode requireArray(JsonNode node, String field) {
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return (ArrayNode) node;
    }

    private static String requireText(JsonNode node, String field) {
        if (node == null || !node.isTextual()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return node.asText();
    }

    private static boolean requireBoolean(JsonNode node, String field) {
        if (node == null || !node.isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return node.asBoolean();
    }

    private static JsonNode requireValue(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException(field + " must be present");
        }
        return node;
    }

    private static ObjectNode generation(Generation generation) {
        ObjectNode data = MAPPER.createObjectNode();
        AssistantMessage message = generation.getOutput();
        ObjectNode output = data.putObject("output");
        output.put("text", message.getText());
        output.set("metadata", MAPPER.valueToTree(message.getMetadata()));
        ArrayNode calls = output.putArray("toolCalls");
        for (AssistantMessage.ToolCall call : message.getToolCalls()) {
            ObjectNode item = calls.addObject();
            item.put("id", call.id());
            item.put("type", call.type());
            item.put("name", call.name());
            item.put("arguments", call.arguments());
        }
        ArrayNode media = output.putArray("media");
        for (Media item : message.getMedia()) {
            ObjectNode mediaItem = media.addObject();
            mediaItem.put("mimeType", item.getMimeType().toString());
            mediaItem.put("id", item.getId());
            mediaItem.put("name", item.getName());
            mediaItem.put("binary", item.getData() instanceof byte[]);
            mediaItem.set("data", MAPPER.valueToTree(item.getData()));
        }
        ChatGenerationMetadata metadata = generation.getMetadata();
        ObjectNode generationMetadata = data.putObject("metadata");
        generationMetadata.put("finishReason", metadata.getFinishReason());
        generationMetadata.set("contentFilters", MAPPER.valueToTree(metadata.getContentFilters()));
        generationMetadata.set("properties", properties(metadata.entrySet()));
        return data;
    }

    private static ObjectNode usage(org.springframework.ai.chat.metadata.Usage usage) {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("promptTokens", usage.getPromptTokens());
        data.put("completionTokens", usage.getCompletionTokens());
        data.put("totalTokens", usage.getTotalTokens());
        data.set("nativeUsage", MAPPER.valueToTree(usage.getNativeUsage()));
        return data;
    }

    private static ObjectNode rateLimit(RateLimit rateLimit) {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("requestsLimit", rateLimit.getRequestsLimit());
        data.put("requestsRemaining", rateLimit.getRequestsRemaining());
        data.set("requestsReset", MAPPER.valueToTree(rateLimit.getRequestsReset()));
        data.put("tokensLimit", rateLimit.getTokensLimit());
        data.put("tokensRemaining", rateLimit.getTokensRemaining());
        data.set("tokensReset", MAPPER.valueToTree(rateLimit.getTokensReset()));
        return data;
    }

    private static ObjectNode properties(Set<Map.Entry<String, Object>> entries) {
        ObjectNode properties = MAPPER.createObjectNode();
        entries.forEach(
                entry -> properties.set(entry.getKey(), MAPPER.valueToTree(entry.getValue())));
        return properties;
    }

    // Spring AI exposes rate limits through an interface with no general-purpose implementation.
    @Data
    public static class RecordedRateLimit implements RateLimit {
        private Long requestsLimit;
        private Long requestsRemaining;
        private Duration requestsReset;
        private Long tokensLimit;
        private Long tokensRemaining;
        private Duration tokensReset;
    }
}
