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
package org.conductoross.conductor.ai.providers.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.recording.LLMRecording;
import org.conductoross.conductor.ai.recording.RecordedRequestNormalizer;
import org.conductoross.conductor.ai.recording.RecordedResponseJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class MockLLMResponseValidationTest {
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    @TempDir Path directory;

    @Test
    void rejectsInvalidResponseObjectAtStartup() throws Exception {
        writeRecording("invalid-object.json", "{}");

        assertInvalidRecording("invalid-object.json", "response.results must be an array");
    }

    @Test
    void reportsFilenameForInvalidJson() throws Exception {
        Files.writeString(directory.resolve("invalid.json"), "not JSON");
        assertInvalidRecording("invalid.json", "Invalid LLM recording");
    }

    @Test
    void rejectsMissingResponsePropertiesAtStartup() throws Exception {
        writeRecording(
                "missing-properties.json", "{\"metadata\":{\"promptMetadata\":[]},\"results\":[]}");
        assertInvalidRecording(
                "missing-properties.json", "response.metadata.properties must be an object");
    }

    @Test
    void rejectsWrongResponseCollectionShapeAtStartup() throws Exception {
        writeRecording(
                "wrong-collection.json", "{\"metadata\":{\"promptMetadata\":{}},\"results\":[]}");

        assertInvalidRecording(
                "wrong-collection.json", "response.metadata.promptMetadata must be an array");
    }

    @Test
    void rejectsMissingRequiredNestedResponseFieldAtStartup() throws Exception {
        writeRecording(
                "missing-output.json",
                "{\"metadata\":{\"promptMetadata\":[]},\"results\":[{\"metadata\":{}}]}");

        assertInvalidRecording(
                "missing-output.json", "response.results[0].output must be an object");
    }

    @Test
    void acceptsValidStoredResponseAtStartup() throws Exception {
        ChatResponse response =
                new ChatResponse(
                        List.of(
                                new Generation(
                                        new AssistantMessage("answer"),
                                        ChatGenerationMetadata.builder().build())));
        writeRecording("valid.json", RecordedResponseJson.write(response));

        assertDoesNotThrow(() -> new MockLLM(directory, objectMapper));
    }

    private void assertInvalidRecording(String filename, String reason) {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> new MockLLM(directory, objectMapper));

        assertAll(
                () -> assertTrue(exception.getMessage().contains(filename)),
                () -> assertTrue(exception.getMessage().contains(reason)));
    }

    private void writeRecording(String filename, String response) throws Exception {
        writeRecording(filename, objectMapper.readTree(response));
    }

    private void writeRecording(String filename, JsonNode response) throws Exception {
        objectMapper.writeValue(
                directory.resolve(filename).toFile(),
                new LLMRecording(LLMRecording.SCHEMA_VERSION, request(), response, null));
    }

    private static LLMRecording.Request request() {
        return new RecordedRequestNormalizer().normalize(new Prompt("hello"), new ChatCompletion());
    }
}
