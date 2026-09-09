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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.conductoross.conductor.ai.model.ChatCompletion;
import org.junit.jupiter.api.BeforeEach;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class FileLLMCallRecorderTest {
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();
    private FileLLMCallRecorder recorder;

    @TempDir Path directory;

    @BeforeEach
    void setUp() throws Exception {
        recorder = new FileLLMCallRecorder(directory, objectMapper);
    }

    @Test
    void recordingJsonDoesNotChangeSharedMapperConfiguration() throws Exception {
        ObjectMapper shared = objectMapper;
        LLMRecording recording = recording();
        Path path = recorder.writeRecording(recording);
        assertSaved(recording, path);
        new RecordedRequestNormalizer().normalize(new Prompt("hello"), new ChatCompletion());
        assertFalse(shared.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        assertFalse(shared.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS));
        assertEquals(
                JsonInclude.Include.NON_NULL,
                shared.getSerializationConfig().getDefaultPropertyInclusion().getValueInclusion());
    }

    @Test
    void writesAndReadsRecordingsWithoutExposingTemporaryFiles() throws Exception {
        LLMRecording recording = recording();
        Path target = recorder.writeRecording(recording);
        assertSaved(recording, target);
        JsonNode json = objectMapper.readTree(target.toFile());
        assertTrue(json.has("request"));
        assertTrue(json.has("response"));
        assertFalse(json.has("scenario"));
        assertFalse(json.has("entries"));
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(List.of(target), files.toList());
        }
    }

    @Test
    void writesSeparateFilesForTheSameRequest() throws Exception {
        LLMRecording first = recording();
        LLMRecording second = recording();
        Path firstFile = recorder.writeRecording(first);
        Path secondFile = recorder.writeRecording(second);
        assertNotEquals(firstFile, secondFile);
        assertSaved(first, firstFile);
        assertSaved(second, secondFile);
    }

    @Test
    void concurrentPublishersWriteSeparateRecordings() throws Exception {
        LLMRecording recording = recording();
        Callable<Path> write = () -> recorder.writeRecording(recording);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Path>> results = executor.invokeAll(List.of(write, write));
            assertNotEquals(results.get(0).get(), results.get(1).get());
            for (Future<Path> result : results) {
                assertSaved(recording, result.get());
            }
        }
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(2, files.count());
        }
    }

    private void assertSaved(LLMRecording expected, Path file) throws IOException {
        LLMRecording actual = objectMapper.readValue(file.toFile(), LLMRecording.class);
        assertEquals(expected.schemaVersion(), actual.schemaVersion());
        assertEquals(expected.request(), actual.request());
        assertEquals(expected.modelSettings(), actual.modelSettings());
        // Compare persisted JSON values: JSON does not preserve Java integer widths or byte arrays.
        assertEquals(
                objectMapper.readTree(objectMapper.writeValueAsBytes(expected.response())),
                actual.response());
    }

    private static LLMRecording recording() {
        RecordedRequestNormalizer normalizer = new RecordedRequestNormalizer();
        LLMRecording.Request request =
                normalizer.normalize(new Prompt("hello"), new ChatCompletion());
        ChatResponse response =
                new ChatResponse(
                        List.of(
                                new Generation(
                                        new AssistantMessage("hello"),
                                        ChatGenerationMetadata.builder()
                                                .finishReason("STOP")
                                                .build())));
        return new LLMRecording(
                LLMRecording.SCHEMA_VERSION, request, RecordedResponseJson.write(response), null);
    }
}
