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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.Validate;
import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.LLMHelper;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.ChatMessage;
import org.conductoross.conductor.ai.model.EmbeddingGenRequest;
import org.conductoross.conductor.ai.recording.LLMRecording;
import org.conductoross.conductor.ai.recording.RecordedRequestNormalizer;
import org.conductoross.conductor.ai.recording.RecordedResponseJson;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.image.ImageModel;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Playback-only provider backed by recorded JSON responses. Never calls a real provider. */
public final class MockLLM implements AIModel {
    private static final String UNSUPPORTED_OPERATION =
            "MockLLM only plays back recorded chat responses";

    public static final String NAME = "mock";
    private final Map<LLMRecording.Request, JsonNode> responses;

    public MockLLM(Path directory, ObjectMapper objectMapper) throws IOException {
        Map<LLMRecording.Request, JsonNode> loaded = new HashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                try {
                    LLMRecording saved = objectMapper.readValue(file.toFile(), LLMRecording.class);
                    RecordedResponseJson.validate(saved.response());
                    register(saved, loaded);
                } catch (IOException | RuntimeException exception) {
                    throw new IllegalArgumentException(
                            "Invalid LLM recording in '"
                                    + file.getFileName()
                                    + "': "
                                    + exception.getMessage(),
                            exception);
                }
            }
        }
        this.responses = Map.copyOf(loaded);
    }

    private static void register(
            LLMRecording saved, Map<LLMRecording.Request, JsonNode> responses) {
        // Identical responses merge; conflicting responses for the same request fail.
        JsonNode existingResponse = responses.putIfAbsent(saved.request(), saved.response());
        Validate.isTrue(
                existingResponse == null
                        || RecordedResponseJson.responseContent(existingResponse)
                                .equals(RecordedResponseJson.responseContent(saved.response())),
                "Conflicting recorded responses for the same request");
    }

    @Override
    public String getModelProvider() {
        return NAME;
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
            JsonNode response =
                    responses.get(new RecordedRequestNormalizer().normalize(prompt, options));
            if (response == null) {
                // Some providers omit prior loop replies. Try that recorded history too, while
                // preserving explicit assistant messages and participant/tool history.
                var messages = new ArrayList<>(prompt.getInstructions());
                messages.removeIf(
                        message ->
                                Boolean.TRUE.equals(
                                        message.getMetadata().get(ChatMessage.LOOP_HISTORY)));
                if (messages.size() != prompt.getInstructions().size()) {
                    LLMHelper.ensureLastMessageIsFromUser(messages);
                    response =
                            responses.get(
                                    new RecordedRequestNormalizer()
                                            .normalize(
                                                    new Prompt(messages, prompt.getOptions()),
                                                    options));
                }
            }
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
