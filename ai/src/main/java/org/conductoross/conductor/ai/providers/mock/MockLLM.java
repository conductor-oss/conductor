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
import org.conductoross.conductor.ai.recording.LLMRecording;
import org.conductoross.conductor.ai.recording.RecordedRequestNormalizer;
import org.conductoross.conductor.ai.recording.RecordedResponseJson;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.image.ImageModel;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Playback-only provider backed by recorded JSON responses. Never calls a real provider. */
public final class MockLLM implements AIModel {
    private static final String UNSUPPORTED_OPERATION =
            "MockLLM only plays back recorded chat responses";

    public static final String NAME = "mockLLM";
    private final Map<LLMRecording.Request, JsonNode> responses;
    private final Map<String, Boolean> assistantPrefillByModel;

    public MockLLM(Path directory, ObjectMapper objectMapper) throws IOException {
        Map<LLMRecording.Request, JsonNode> loaded = new HashMap<>();
        Map<String, Boolean> policies = new HashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                LLMRecording saved = objectMapper.readValue(file.toFile(), LLMRecording.class);
                register(saved, loaded, policies);
            }
        }
        this.responses = Map.copyOf(loaded);
        this.assistantPrefillByModel = Map.copyOf(policies);
    }

    private static void register(
            LLMRecording saved,
            Map<LLMRecording.Request, JsonNode> responses,
            Map<String, Boolean> policies) {
        // Identical responses merge; conflicting responses for the same request fail.
        for (LLMRecording.Entry entry : saved.entries()) {
            JsonNode existing = responses.putIfAbsent(entry.request(), entry.response());
            Validate.isTrue(
                    existing == null
                            || RecordedResponseJson.responseContent(existing)
                                    .equals(RecordedResponseJson.responseContent(entry.response())),
                    "Conflicting recorded responses for the same request");
        }
        LLMRecording.ModelSettings settings = saved.modelSettings();
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
        throw new NonRetryableException("No recorded history policy for the selected model");
    }

    @Override
    public ChatModel getChatModel() {
        return getChatModel(new ChatCompletion());
    }

    @Override
    public ChatModel getChatModel(ChatCompletion input) {
        RecordedRequestNormalizer.RequestOptions options = RecordedRequestNormalizer.options(input);
        // Request options belong to this call's wrapper, never to the singleton provider.
        return prompt -> {
            RecordedRequestNormalizer normalizer = new RecordedRequestNormalizer();
            LLMRecording.Request request = normalizer.normalize(prompt, options);
            JsonNode response = responses.get(request);
            if (response == null)
                throw new NonRetryableException("No recorded response matches the LLM request");
            return RecordedResponseJson.read(response, UUID.randomUUID().toString());
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
