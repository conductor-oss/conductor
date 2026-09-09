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

import java.io.IOException;
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

/** JSON storage for Spring AI responses, including their extensible metadata maps. */
public final class RecordedResponseJson {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final ObjectMapper MAPPER = new ObjectMapperProvider().getObjectMapper().copy();

    static {
        // Media's convenience byte-array getter throws for URL media. Store its actual data
        // and distinguish bytes from URLs so both survive a JSON round trip.
        SimpleModule module = new SimpleModule();
        module.addSerializer(
                Media.class,
                new JsonSerializer<>() {
                    @Override
                    public void serialize(
                            Media media, JsonGenerator json, SerializerProvider provider)
                            throws IOException {
                        json.writeStartObject();
                        json.writeStringField("mimeType", media.getMimeType().toString());
                        json.writeStringField("id", media.getId());
                        json.writeStringField("name", media.getName());
                        json.writeBooleanField("binary", media.getData() instanceof byte[]);
                        json.writeObjectField("data", media.getData());
                        json.writeEndObject();
                    }
                });
        MAPPER.registerModule(module);
    }

    public static JsonNode write(ChatResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Cannot record an absent model response");
        }
        ObjectNode data = MAPPER.valueToTree(response);
        // getResult() duplicates the first item in getResults().
        data.remove("result");
        putProperties((ObjectNode) data.get("metadata"), response.getMetadata().entrySet());
        for (int i = 0; i < response.getResults().size(); i++) {
            putProperties(
                    (ObjectNode) data.get("results").get(i).get("metadata"),
                    response.getResults().get(i).getMetadata().entrySet());
        }
        try {
            // Materialize JSON types exactly as they will be read from disk (bytes, numbers).
            return MAPPER.readTree(data.toString());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize LLM response", e);
        }
    }

    /** Restore all response data, replacing only tool-call IDs for this playback invocation. */
    public static ChatResponse read(JsonNode data, String idPrefix) {
        List<Generation> generations = new ArrayList<>();
        int callIndex = 0;
        for (JsonNode result : data.get("results")) {
            JsonNode output = result.get("output");
            List<Media> media = new ArrayList<>();
            for (JsonNode item : output.get("media")) {
                media.add(
                        Media.builder()
                                .mimeType(
                                        MimeTypeUtils.parseMimeType(item.get("mimeType").asText()))
                                .id(item.path("id").asText(null))
                                .name(item.path("name").asText(null))
                                .data(
                                        item.get("binary").asBoolean()
                                                ? MAPPER.convertValue(
                                                        item.get("data"), byte[].class)
                                                : item.get("data").asText())
                                .build());
            }
            List<AssistantMessage.ToolCall> calls = new ArrayList<>();
            for (JsonNode call : output.get("toolCalls")) {
                calls.add(
                        new AssistantMessage.ToolCall(
                                idPrefix + "_" + callIndex++,
                                call.get("type").asText(),
                                call.get("name").asText(),
                                call.get("arguments").asText()));
            }
            JsonNode metadata = result.get("metadata");
            generations.add(
                    new Generation(
                            AssistantMessage.builder()
                                    .content(output.path("text").asText(null))
                                    .properties(MAPPER.convertValue(output.get("metadata"), MAP))
                                    .toolCalls(calls)
                                    .media(media)
                                    .build(),
                            ChatGenerationMetadata.builder()
                                    .finishReason(metadata.path("finishReason").asText(null))
                                    .contentFilters(
                                            MAPPER.convertValue(
                                                    metadata.get("contentFilters"),
                                                    new TypeReference<Set<String>>() {}))
                                    .metadata(MAPPER.convertValue(metadata.get("properties"), MAP))
                                    .build()));
        }
        JsonNode metadata = data.get("metadata");
        List<PromptMetadata.PromptFilterMetadata> filters = new ArrayList<>();
        for (JsonNode filter : metadata.get("promptMetadata")) {
            filters.add(
                    PromptMetadata.PromptFilterMetadata.from(
                            filter.get("promptIndex").asInt(),
                            MAPPER.convertValue(
                                    filter.get("contentFilterMetadata"), Object.class)));
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
                        .metadata(MAPPER.convertValue(metadata.get("properties"), MAP))
                        .build());
    }

    /** Ignore per-call IDs and usage when checking repeated recordings for conflicting answers. */
    public static JsonNode responseContent(JsonNode response) {
        JsonNode results = response.get("results").deepCopy();
        int callIndex = 0;
        for (JsonNode result : results) {
            for (JsonNode call : result.get("output").get("toolCalls")) {
                ((ObjectNode) call).put("id", "call_" + callIndex++);
            }
        }
        return results;
    }

    private static void putProperties(ObjectNode metadata, Set<Map.Entry<String, Object>> entries) {
        ObjectNode properties = metadata.putObject("properties");
        entries.forEach(
                entry -> properties.set(entry.getKey(), MAPPER.valueToTree(entry.getValue())));
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
