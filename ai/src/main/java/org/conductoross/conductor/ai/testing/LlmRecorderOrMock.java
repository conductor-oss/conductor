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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.conductoross.conductor.ai.model.ChatCompletion;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Records request/response pairs or replays them by normalized request, in any order. */
public final class LlmRecorderOrMock {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String scenario;
    private final boolean recording;
    private final Map<LlmSavedResponses.Request, LlmSavedResponses.Response> responses;

    private LlmRecorderOrMock(
            String scenario,
            boolean recording,
            Map<LlmSavedResponses.Request, LlmSavedResponses.Response> responses) {
        this.scenario = scenario;
        this.recording = recording;
        this.responses = responses;
    }

    public static LlmRecorderOrMock recording(String scenario) {
        new LlmSavedResponses(LlmSavedResponses.SCHEMA_VERSION, scenario, List.of());
        return new LlmRecorderOrMock(scenario, true, new ConcurrentHashMap<>());
    }

    public static LlmRecorderOrMock replaying(LlmSavedResponses savedResponses) {
        var responses =
                new java.util.HashMap<LlmSavedResponses.Request, LlmSavedResponses.Response>();
        for (var entry : snapshot(savedResponses).entries()) {
            addResponse(responses, entry.request(), entry.response());
        }
        return new LlmRecorderOrMock(savedResponses.scenario(), false, Map.copyOf(responses));
    }

    /** Wrap a real provider for recording, or pass null for replay. */
    public ChatModel modelFor(ChatCompletion input, ChatModel delegate) {
        if (recording && delegate == null) {
            throw new IllegalArgumentException("Recording requires a real chat model");
        }
        if (recording && "mockLLM".equals(input.getLlmProvider())) {
            throw new IllegalArgumentException("Cannot record mockLLM");
        }
        var constraints = LlmRequestResponseConverter.options(input);
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return invoke(constraints, delegate, prompt);
            }

            @Override
            public ChatOptions getDefaultOptions() {
                return recording
                        ? delegate.getDefaultOptions()
                        : ChatModel.super.getDefaultOptions();
            }
        };
    }

    private ChatResponse invoke(
            LlmRequestResponseConverter.RequestOptions input, ChatModel delegate, Prompt prompt) {
        // Full request history supplies tool-call identities; no state is shared between calls.
        var converter = new LlmRequestResponseConverter();
        var request = converter.toSavedRequest(prompt, input);
        if (recording) {
            var response = delegate.call(prompt);
            addResponse(responses, request, converter.toSavedResponse(response));
            return response;
        }
        var response = responses.get(request);
        if (response == null) {
            throw new NonRetryableException(
                    "No matching request in LLM saved responses " + scenario);
        }
        return converter.toChatResponse(response, UUID.randomUUID().toString());
    }

    private static void addResponse(
            Map<LlmSavedResponses.Request, LlmSavedResponses.Response> responses,
            LlmSavedResponses.Request request,
            LlmSavedResponses.Response response) {
        // A request must identify one response; reject ambiguity instead of choosing by call order.
        var existing = responses.putIfAbsent(request, response);
        if (existing != null && !existing.equals(response)) {
            throw new IllegalArgumentException(
                    "Conflicting responses for the same LLM saved responses request");
        }
    }

    /**
     * Snapshot after recording has finished. Sorting keeps file output independent of call order.
     */
    public LlmSavedResponses savedResponses() {
        var entries =
                responses.entrySet().stream()
                        .map(entry -> new LlmSavedResponses.Entry(entry.getKey(), entry.getValue()))
                        .sorted(
                                Comparator.comparing(
                                        entry -> MAPPER.valueToTree(entry.request()).toString()))
                        .toList();
        return snapshot(new LlmSavedResponses(LlmSavedResponses.SCHEMA_VERSION, scenario, entries));
    }

    private static LlmSavedResponses snapshot(LlmSavedResponses savedResponses) {
        return MAPPER.convertValue(savedResponses, LlmSavedResponses.class);
    }
}
