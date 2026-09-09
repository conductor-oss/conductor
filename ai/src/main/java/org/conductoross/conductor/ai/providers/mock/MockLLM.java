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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.Validate;
import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.EmbeddingGenRequest;
import org.conductoross.conductor.ai.testing.LlmRequestResponseConverter;
import org.conductoross.conductor.ai.testing.LlmSavedResponses;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.image.ImageModel;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Playback-only provider backed by recorded JSON responses. Never calls a real provider. */
public final class MockLLM implements AIModel {
    private static final String CONFLICTING_RESPONSES =
            "Conflicting recorded responses for the same request";
    private static final String MISSING_HISTORY_POLICY =
            "No recorded history policy for the selected model";
    private static final String MISSING_RESPONSE = "No recorded response matches the LLM request";
    private static final String UNSUPPORTED_OPERATION =
            "MockLLM only plays back recorded chat responses";

    public static final String NAME = "mockLLM";
    private static final String JSON_GLOB = "*.json";
    private final Map<LlmSavedResponses.Request, LlmSavedResponses.Response> responses;
    private final Map<String, Boolean> assistantPrefillByModel;

    public MockLLM(Path directory, ObjectMapper objectMapper) throws IOException {
        Map<LlmSavedResponses.Request, LlmSavedResponses.Response> loaded = new HashMap<>();
        Map<String, Boolean> policies = new HashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, JSON_GLOB)) {
            for (Path file : files) {
                LlmSavedResponses saved =
                        objectMapper.readValue(file.toFile(), LlmSavedResponses.class);
                register(saved, loaded, policies);
            }
        }
        this.responses = Map.copyOf(loaded);
        this.assistantPrefillByModel = Map.copyOf(policies);
    }

    private static void register(
            LlmSavedResponses saved,
            Map<LlmSavedResponses.Request, LlmSavedResponses.Response> responses,
            Map<String, Boolean> policies) {
        // Identical responses merge; conflicting responses for the same request fail.
        for (LlmSavedResponses.Entry entry : saved.entries()) {
            LlmSavedResponses.Response existing =
                    responses.putIfAbsent(entry.request(), entry.response());
            Validate.isTrue(
                    existing == null || existing.equals(entry.response()), CONFLICTING_RESPONSES);
        }
        LlmSavedResponses.ModelSettings settings = saved.modelSettings();
        if (settings != null && settings.model() != null) {
            policies.putIfAbsent(settings.model(), settings.supportsAssistantPrefill());
        }
    }

    @Override
    public String getModelProvider() {
        return NAME;
    }

    @Override
    public boolean supportsAssistantPrefill(ChatCompletion input) {
        Boolean policy =
                input.getModel() == null ? null : assistantPrefillByModel.get(input.getModel());
        if (policy != null) return policy;
        if (assistantPrefillByModel.isEmpty()) return AIModel.super.supportsAssistantPrefill();
        throw new NonRetryableException(MISSING_HISTORY_POLICY);
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
            if (response == null) throw new NonRetryableException(MISSING_RESPONSE);
            return converter.toChatResponse(response, UUID.randomUUID().toString());
        };
    }

    @Override
    public ImageModel getImageModel() {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION);
    }

    @Override
    public List<Float> generateEmbeddings(EmbeddingGenRequest request) {
        throw new UnsupportedOperationException(UNSUPPORTED_OPERATION);
    }
}
