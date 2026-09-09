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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.conductoross.conductor.ai.model.ChatCompletion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class LlmJsonFilesTest {
    private static final String SCENARIO_NAME = "scenario";
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private final LlmJsonFiles jsonFiles = new LlmJsonFiles(objectMapper);

    @TempDir Path directory;

    @Test
    void savedResponsesJsonDoesNotChangeSharedMapperConfiguration() throws Exception {
        ObjectMapper shared = objectMapper;
        LlmSavedResponses savedResponses =
                new LlmSavedResponses(
                        LlmSavedResponses.SCHEMA_VERSION, "mapper_isolation", List.of());
        Path path = jsonFiles.writeRecording(directory, savedResponses);
        try (InputStream source = Files.newInputStream(path)) {
            assertEquals(savedResponses, objectMapper.readValue(source, LlmSavedResponses.class));
        }
        new LlmRequestResponseConverter().toSavedRequest(new Prompt("hello"), new ChatCompletion());
        assertFalse(shared.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        assertFalse(shared.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS));
        assertEquals(
                JsonInclude.Include.NON_NULL,
                shared.getSerializationConfig().getDefaultPropertyInclusion().getValueInclusion());
    }

    @Test
    void writesAndReadsSavedResponsesWithoutExposingTemporaryFiles() throws Exception {
        LlmSavedResponses savedResponses =
                new LlmSavedResponses(
                        LlmSavedResponses.SCHEMA_VERSION, "blocked_before_llm", List.of());
        Path target = jsonFiles.writeRecording(directory, savedResponses);
        try (InputStream source = Files.newInputStream(target)) {
            assertEquals(savedResponses, objectMapper.readValue(source, LlmSavedResponses.class));
        }
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(List.of(target), files.toList());
        }
    }

    @Test
    void writesSeparateFilesForTheSameScenario() throws Exception {
        LlmSavedResponses first =
                new LlmSavedResponses(LlmSavedResponses.SCHEMA_VERSION, SCENARIO_NAME, List.of());
        LlmSavedResponses second =
                new LlmSavedResponses(
                        LlmSavedResponses.SCHEMA_VERSION, SCENARIO_NAME, List.of(entry()));
        Path firstFile = jsonFiles.writeRecording(directory, first);
        Path secondFile = jsonFiles.writeRecording(directory, second);
        assertNotEquals(firstFile, secondFile);
        assertEquals(first, objectMapper.readValue(firstFile.toFile(), LlmSavedResponses.class));
        assertEquals(second, objectMapper.readValue(secondFile.toFile(), LlmSavedResponses.class));
    }

    @Test
    void concurrentPublishersWriteSeparateRecordings() throws Exception {
        LlmSavedResponses savedResponses =
                new LlmSavedResponses(
                        LlmSavedResponses.SCHEMA_VERSION, SCENARIO_NAME, List.of(entry()));
        Callable<Path> write = () -> jsonFiles.writeRecording(directory, savedResponses);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Path>> results = executor.invokeAll(List.of(write, write));
            assertNotEquals(results.get(0).get(), results.get(1).get());
            for (Future<Path> result : results) {
                assertEquals(
                        savedResponses,
                        objectMapper.readValue(result.get().toFile(), LlmSavedResponses.class));
            }
        }
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(2, files.count());
        }
    }

    private static LlmSavedResponses.Entry entry() {
        LlmRequestResponseConverter converter = new LlmRequestResponseConverter();
        LlmSavedResponses.Request request =
                converter.toSavedRequest(new Prompt("hello"), new ChatCompletion());
        ChatResponse response =
                new ChatResponse(
                        List.of(
                                new Generation(
                                        new AssistantMessage("hello"),
                                        ChatGenerationMetadata.builder()
                                                .finishReason("STOP")
                                                .build())));
        return new LlmSavedResponses.Entry(request, converter.toSavedResponse(response));
    }
}
