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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.providers.mock.MockLLM;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Writes an independent JSON file for each real model response. */
public final class JsonFileLlmCallRecorder implements LlmCallRecorder {

    private final Path directory;
    private final LlmJsonFiles files;

    public JsonFileLlmCallRecorder(Path directory, ObjectMapper objectMapper) throws IOException {
        Files.createDirectories(directory);
        this.directory = directory;
        this.files = new LlmJsonFiles(objectMapper);
    }

    @Override
    public ChatModel wrap(AIModel provider, ChatCompletion input, ChatModel delegate) {
        if (MockLLM.NAME.equals(provider.getModelProvider())) {
            return delegate;
        }
        LlmRequestResponseConverter.RequestOptions options =
                LlmRequestResponseConverter.options(input);
        LlmSavedResponses.ModelSettings settings =
                new LlmSavedResponses.ModelSettings(
                        input.getModel(), provider.supportsAssistantPrefill(input));
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                // Each call owns its ID mappings and file; provider calls never share a lock.
                LlmRequestResponseConverter converter = new LlmRequestResponseConverter();
                LlmSavedResponses.Request request = converter.toSavedRequest(prompt, options);
                ChatResponse response = delegate.call(prompt);
                LlmSavedResponses saved =
                        new LlmSavedResponses(
                                LlmSavedResponses.SCHEMA_VERSION,
                                "chat",
                                List.of(
                                        new LlmSavedResponses.Entry(
                                                request, converter.toSavedResponse(response))),
                                settings);
                try {
                    files.writeRecording(directory, saved);
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
}
