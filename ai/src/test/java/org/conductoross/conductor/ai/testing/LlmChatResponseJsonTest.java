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
package org.conductoross.conductor.ai.testing;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.providers.mock.MockLLM;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

class LlmChatResponseJsonTest {
    @TempDir Path directory;

    @Test
    void completeResponseSurvivesFileStorageAndPlayback() throws Exception {
        byte[] bytes = {1, 2, 3};
        AssistantMessage message =
                AssistantMessage.builder()
                        .content("answer")
                        .properties(Map.of("message_data", Map.of("nested", "value")))
                        .toolCalls(
                                List.of(
                                        new AssistantMessage.ToolCall(
                                                "original-call", "function", "weather", "{}")))
                        .media(
                                List.of(
                                        Media.builder()
                                                .mimeType(MimeTypeUtils.IMAGE_PNG)
                                                .data(bytes)
                                                .id("image-id")
                                                .name("image.png")
                                                .build(),
                                        new Media(
                                                MimeTypeUtils.IMAGE_PNG,
                                                URI.create("https://example.com/image.png"))))
                        .build();
        ChatResponse response =
                new ChatResponse(
                        List.of(
                                new Generation(
                                        message,
                                        ChatGenerationMetadata.builder()
                                                .finishReason("TOOL_CALLS")
                                                .contentFilters(Set.of("safe"))
                                                .metadata(
                                                        "generation_data",
                                                        List.of("first", "second"))
                                                .build())),
                        ChatResponseMetadata.builder()
                                .id("original-response")
                                .model("model")
                                .usage(new DefaultUsage(12, 13, 25, Map.of("cached_tokens", 4)))
                                .keyValue("response_id", "response-chain-id")
                                .keyValue("reasoning", "reasoning summary")
                                .keyValue("reasoning_tokens", 7)
                                .keyValue("provider_data", Map.of("nested", List.of(1, 2)))
                                .promptMetadata(
                                        PromptMetadata.of(
                                                PromptMetadata.PromptFilterMetadata.from(
                                                        0, Map.of("safe", true))))
                                .build());
        ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();
        LlmRequestResponseConverter converter = new LlmRequestResponseConverter();
        ChatCompletion input = new ChatCompletion();
        Prompt prompt = new Prompt("hello");
        JsonNode savedResponse = converter.toSavedResponse(response);
        new LlmJsonFiles(mapper)
                .writeRecording(
                        directory,
                        new LlmSavedResponses(
                                LlmSavedResponses.SCHEMA_VERSION,
                                "chat",
                                List.of(
                                        new LlmSavedResponses.Entry(
                                                converter.toSavedRequest(prompt, input),
                                                savedResponse))));

        ChatResponse replay = new MockLLM(directory, mapper).getChatModel(input).call(prompt);
        assertEquals("original-response", replay.getMetadata().getId());
        assertEquals("response-chain-id", replay.getMetadata().get("response_id"));
        assertEquals("reasoning summary", replay.getMetadata().get("reasoning"));
        assertEquals(7, (Integer) replay.getMetadata().get("reasoning_tokens"));
        assertEquals(25, replay.getMetadata().getUsage().getTotalTokens());
        assertEquals(Map.of("cached_tokens", 4), replay.getMetadata().getUsage().getNativeUsage());
        assertEquals(Map.of("nested", List.of(1, 2)), replay.getMetadata().get("provider_data"));
        assertEquals(Set.of("safe"), replay.getResult().getMetadata().getContentFilters());
        assertEquals(
                List.of("first", "second"),
                replay.getResult().getMetadata().get("generation_data"));
        assertEquals(
                Map.of("nested", "value"),
                replay.getResult().getOutput().getMetadata().get("message_data"));
        assertArrayEquals(
                bytes, replay.getResult().getOutput().getMedia().getFirst().getDataAsByteArray());
        assertEquals(
                "https://example.com/image.png",
                replay.getResult().getOutput().getMedia().getLast().getData());
        assertEquals(
                Map.of("safe", true),
                replay.getMetadata()
                        .getPromptMetadata()
                        .findByPromptIndex(0)
                        .orElseThrow()
                        .getContentFilterMetadata());

        String replayId = replay.getResult().getOutput().getToolCalls().getFirst().id();
        assertNotEquals("original-call", replayId);
        // The complete stored snapshot must match after replay, apart from the fresh tool ID.
        ((ObjectNode) savedResponse.at("/results/0/output/toolCalls/0")).put("id", replayId);
        assertEquals(savedResponse, converter.toSavedResponse(replay));
    }
}
