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
    private long sequence;

    public FileLLMCallRecorder(Path directory, ObjectMapper objectMapper) throws IOException {
        Files.createDirectories(directory);
        this.directory = directory;
        this.objectMapper = objectMapper;
        // Each configured directory has its own numbering, continued across server restarts.
        try (var files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                int separator = name.indexOf('_');
                if (separator > 0) {
                    try {
                        sequence = Math.max(sequence, Long.parseLong(name.substring(0, separator)));
                    } catch (NumberFormatException ignored) {
                        // Legacy UUID filenames and unrelated names do not affect numbering.
                    }
                }
            }
        }
    }

    @Override
    public ChatModel wrap(AIModel provider, ChatCompletion input, ChatModel delegate) {
        if (MockLLM.NAME.equals(provider.getModelProvider())) {
            return delegate;
        }
        RecordedRequestNormalizer.RequestOptions options = RecordedRequestNormalizer.options(input);
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                // Each call owns its ID mappings; provider requests remain concurrent.
                RecordedRequestNormalizer normalizer = new RecordedRequestNormalizer();
                LLMRecording.Request request = normalizer.normalize(prompt, options);
                ChatResponse response = delegate.call(prompt);
                LLMRecording saved =
                        new LLMRecording(
                                LLMRecording.SCHEMA_VERSION,
                                request,
                                RecordedResponseJson.write(response));
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

    synchronized Path writeRecording(LLMRecording recording) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".llm-recording-", ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), recording);
            // Number publication order, including concurrent calls that finish out of order.
            Path target = directory.resolve(++sequence + "_" + UUID.randomUUID() + ".json");
            // Publish the complete file atomically without replacing an existing recording.
            Files.createLink(target, temporary);
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
