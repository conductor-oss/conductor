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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.Validate;
import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.LLMHelper;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.ChatMessage;
import org.conductoross.conductor.ai.model.EmbeddingGenRequest;
import org.conductoross.conductor.ai.recording.LLMPlaybackVerifier;
import org.conductoross.conductor.ai.recording.LLMRecording;
import org.conductoross.conductor.ai.recording.RecordedRequestNormalizer;
import org.conductoross.conductor.ai.recording.RecordedResponseJson;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.image.ImageModel;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Playback-only provider backed by recorded JSON responses. Never calls a real provider. */
public final class MockLLM implements AIModel, LLMPlaybackVerifier {
    private static final String UNSUPPORTED_OPERATION =
            "MockLLM only plays back recorded chat responses";

    public static final String NAME = "mock";
    private final Map<LLMRecording.Request, JsonNode> responses;

    private final Map<String, String> recordingDigests;
    private final Map<LLMRecording.Request, Set<String>> recordingFiles;
    private final Set<String> replayedFiles = ConcurrentHashMap.newKeySet();
    private final AtomicLong unmatchedRequests = new AtomicLong();

    public MockLLM(Path directory, ObjectMapper objectMapper) throws IOException {
        Map<LLMRecording.Request, JsonNode> loaded = new HashMap<>();
        Map<String, String> digests = new HashMap<>();
        Map<LLMRecording.Request, Set<String>> filesByRequest = new HashMap<>();
        // A single configured root can contain the same example folders for every SDK.
        try (var files = Files.walk(directory)) {
            for (Path file :
                    files.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".json"))
                            .sorted()
                            .toList()) {
                String relative = directory.relativize(file).toString().replace('\\', '/');
                try {
                    byte[] bytes = Files.readAllBytes(file);
                    LLMRecording saved = objectMapper.readValue(bytes, LLMRecording.class);
                    RecordedResponseJson.validate(saved.response());
                    LLMRecording.Request request = register(saved, loaded);
                    digests.put(relative, sha256(bytes));
                    filesByRequest
                            .computeIfAbsent(request, ignored -> new HashSet<>())
                            .add(relative);
                } catch (IOException | RuntimeException exception) {
                    throw new IllegalArgumentException(
                            "Invalid LLM recording in '"
                                    + relative
                                    + "': "
                                    + exception.getMessage(),
                            exception);
                }
            }
        }
        this.responses = Map.copyOf(loaded);
        this.recordingDigests = Map.copyOf(digests);
        filesByRequest.replaceAll((request, names) -> Set.copyOf(names));
        this.recordingFiles = Map.copyOf(filesByRequest);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @Override
    public Verification verify(Map<String, String> expectedRecordings) {
        List<String> missing = new ArrayList<>();
        List<String> different = new ArrayList<>();
        List<String> notReplayed = new ArrayList<>();
        int matched = 0;
        // Compare against bytes loaded at startup, not files that may have changed on disk.
        // CI calls this after its SDK has finished, on a server started for that CI run.
        for (String name : expectedRecordings.keySet().stream().sorted().toList()) {
            if (!recordingDigests.containsKey(name)) missing.add(name);
            else if (!recordingDigests.get(name).equals(expectedRecordings.get(name)))
                different.add(name);
            else if (!replayedFiles.contains(name)) notReplayed.add(name);
            else matched++;
        }
        return new Verification(
                expectedRecordings.size(),
                matched,
                unmatchedRequests.get(),
                List.copyOf(missing),
                List.copyOf(different),
                List.copyOf(notReplayed));
    }

    private static LLMRecording.Request register(
            LLMRecording saved, Map<LLMRecording.Request, JsonNode> responses) {
        // Identical responses merge; conflicting responses for the same request fail.
        LLMRecording.Request request =
                RecordedRequestNormalizer.normalizeTransportHistory(saved.request());
        JsonNode existingResponse = responses.putIfAbsent(request, saved.response());
        Validate.isTrue(
                existingResponse == null
                        || RecordedResponseJson.responseContent(existingResponse)
                                .equals(RecordedResponseJson.responseContent(saved.response())),
                "Conflicting recorded responses for the same request");
        return request;
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
            LLMRecording.Request request =
                    new RecordedRequestNormalizer().normalize(prompt, options);
            JsonNode response = responses.get(request);
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
                    request =
                            new RecordedRequestNormalizer()
                                    .normalize(new Prompt(messages, prompt.getOptions()), options);
                    response = responses.get(request);
                }
            }
            if (response == null) {
                unmatchedRequests.incrementAndGet();
                throw new NonRetryableException("No recorded response matches the LLM request");
            }
            ChatResponse result = RecordedResponseJson.read(response, UUID.randomUUID().toString());
            // Identical duplicate fixtures are aliases of one request. A successful replay covers
            // every alias; repeated calls cannot compensate for a different, unplayed request.
            replayedFiles.addAll(recordingFiles.get(request));
            return result;
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
