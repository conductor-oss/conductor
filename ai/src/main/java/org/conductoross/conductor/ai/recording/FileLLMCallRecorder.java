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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.providers.mock.MockLLM;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Writes an independent JSON file for each real model response. */
public final class FileLLMCallRecorder implements LLMCallRecorder {

    private final Path directory;
    private final ObjectMapper objectMapper;

    public FileLLMCallRecorder(Path directory, ObjectMapper objectMapper) throws IOException {
        Files.createDirectories(directory);
        this.directory = directory;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatModel wrap(AIModel provider, ChatCompletion input, ChatModel delegate) {
        if (MockLLM.NAME.equals(provider.getModelProvider())) {
            return delegate;
        }
        RecordedRequestNormalizer.RequestOptions options = RecordedRequestNormalizer.options(input);
        LLMRecording.ModelSettings settings =
                new LLMRecording.ModelSettings(
                        input.getModel(), provider.supportsAssistantPrefill(input));
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                // Each call owns its ID mappings and file; provider calls never share a lock.
                RecordedRequestNormalizer normalizer = new RecordedRequestNormalizer();
                LLMRecording.Request request = normalizer.normalize(prompt, options);
                ChatResponse response = delegate.call(prompt);
                LLMRecording saved =
                        new LLMRecording(
                                LLMRecording.SCHEMA_VERSION,
                                request,
                                RecordedResponseJson.write(response),
                                settings);
                try {
                    writeRecording(saved);
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot write LLM recording", e);
                }
                return response;
            }

            @Override
            public ChatOptions getDefaultOptions() {
                return delegate.getDefaultOptions();
            }
        };
    }

    Path writeRecording(LLMRecording recording) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(UUID.randomUUID() + ".json");
        Path temporary = Files.createTempFile(directory, ".llm-recording-", ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), recording);
            // Publish the complete file atomically without replacing an existing recording.
            Files.createLink(target, temporary);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }
}
