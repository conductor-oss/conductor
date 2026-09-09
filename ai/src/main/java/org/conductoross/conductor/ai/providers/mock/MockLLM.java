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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.EmbeddingGenRequest;
import org.conductoross.conductor.ai.testing.LlmJsonFiles;
import org.conductoross.conductor.ai.testing.LlmRequestResponseConverter;
import org.conductoross.conductor.ai.testing.LlmSavedResponses;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.image.ImageModel;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

/** Playback-only provider backed by recorded JSON responses. Never calls a real provider. */
public final class MockLLM implements AIModel {
    public static final String NAME = "mockLLM";
    private final Map<LlmSavedResponses.Request, LlmSavedResponses.Response> responses;
    private final Map<String, Boolean> assistantPrefillByModel;

    public MockLLM(Path directory) throws IOException {
        Map<LlmSavedResponses.Request, LlmSavedResponses.Response> loaded = new HashMap<>();
        Map<String, Boolean> policies = new HashMap<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file :
                    files.filter(path -> path.getFileName().toString().endsWith(".json"))
                            .filter(Files::isRegularFile)
                            .sorted()
                            .toList()) {
                try (InputStream source = Files.newInputStream(file)) {
                    LlmSavedResponses saved = LlmJsonFiles.read(source);
                    for (LlmSavedResponses.Entry entry : saved.entries()) {
                        LlmSavedResponses.Response existing =
                                loaded.putIfAbsent(entry.request(), entry.response());
                        if (existing != null && !existing.equals(entry.response())) {
                            throw new IllegalArgumentException(
                                    "Conflicting recorded responses for the same request");
                        }
                    }
                    LlmSavedResponses.ModelSettings settings = saved.modelSettings();
                    if (settings != null) {
                        String model = Objects.toString(settings.model(), "");
                        Boolean existing =
                                policies.putIfAbsent(model, settings.supportsAssistantPrefill());
                        if (existing != null && existing != settings.supportsAssistantPrefill()) {
                            throw new IllegalArgumentException(
                                    "Conflicting recorded history policies for model " + model);
                        }
                    }
                }
            }
        }
        this.responses = Map.copyOf(loaded);
        this.assistantPrefillByModel = Map.copyOf(policies);
    }

    @Override
    public String getModelProvider() {
        return NAME;
    }

    @Override
    public boolean supportsAssistantPrefill(ChatCompletion input) {
        Boolean policy = assistantPrefillByModel.get(Objects.toString(input.getModel(), ""));
        if (policy != null) return policy;
        if (assistantPrefillByModel.isEmpty()) return AIModel.super.supportsAssistantPrefill();
        throw new NonRetryableException("No recorded history policy for the selected model");
    }

    @Override
    public ChatModel getChatModel() {
        return getChatModel(new ChatCompletion());
    }

    @Override
    public ChatModel getChatModel(ChatCompletion input) {
        LlmRequestResponseConverter.RequestOptions options =
                LlmRequestResponseConverter.options(input);
        // Request options belong to this call's wrapper, never to the singleton provider.
        return prompt -> {
            LlmRequestResponseConverter converter = new LlmRequestResponseConverter();
            LlmSavedResponses.Request request = converter.toSavedRequest(prompt, options);
            LlmSavedResponses.Response response = responses.get(request);
            if (response == null)
                throw new NonRetryableException("No recorded response matches the LLM request");
            return converter.toChatResponse(response, UUID.randomUUID().toString());
        };
    }

    @Override
    public ImageModel getImageModel() {
        throw new UnsupportedOperationException("MockLLM only plays back recorded chat responses");
    }

    @Override
    public List<Float> generateEmbeddings(EmbeddingGenRequest request) {
        throw new UnsupportedOperationException("MockLLM only plays back recorded chat responses");
    }
}
