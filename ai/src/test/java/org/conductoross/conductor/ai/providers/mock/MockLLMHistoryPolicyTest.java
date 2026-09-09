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

import java.nio.file.Path;
import java.util.List;

import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.recording.LLMRecording;
import org.conductoross.conductor.ai.recording.RecordedRequestNormalizer;
import org.conductoross.conductor.ai.recording.RecordedResponseJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class MockLLMHistoryPolicyTest {
    private static final String MODEL = "recorded-model";
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    @TempDir Path directory;

    @Test
    void acceptsMatchingHistoryPoliciesForTheSameModel() throws Exception {
        writeRecording("first.json", true);
        writeRecording("second.json", true);

        MockLLM mockLLM = new MockLLM(directory, objectMapper);
        ChatCompletion input = new ChatCompletion();
        input.setModel(MODEL);

        assertTrue(mockLLM.supportsAssistantPrefill(input));
    }

    @Test
    void rejectsConflictingHistoryPoliciesForTheSameModel() throws Exception {
        writeRecording("first.json", false);
        writeRecording("second.json", true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> new MockLLM(directory, objectMapper));

        assertAll(
                () -> assertTrue(exception.getMessage().contains(MODEL)),
                () -> assertTrue(exception.getMessage().contains("first.json")),
                () -> assertTrue(exception.getMessage().contains("second.json")));
    }

    private void writeRecording(String filename, boolean supportsAssistantPrefill)
            throws Exception {
        LLMRecording recording =
                new LLMRecording(
                        LLMRecording.SCHEMA_VERSION,
                        request(),
                        RecordedResponseJson.write(new ChatResponse(List.of())),
                        new LLMRecording.ModelSettings(MODEL, supportsAssistantPrefill));
        objectMapper.writeValue(directory.resolve(filename).toFile(), recording);
    }

    private static LLMRecording.Request request() {
        return new RecordedRequestNormalizer().normalize(new Prompt("hello"), new ChatCompletion());
    }
}
