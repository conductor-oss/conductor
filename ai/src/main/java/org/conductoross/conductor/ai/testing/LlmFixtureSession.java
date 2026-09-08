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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.conductoross.conductor.ai.model.ChatCompletion;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * One execution's recording or replay. Existing ChatModel wrappers keep model substitution below
 * Conductor response validation. Lifecycle/retention is owned by the caller, not a global registry.
 */
public final class LlmFixtureSession {
    private static final ObjectMapper MAPPER = new ObjectMapperProvider().getObjectMapper();

    private final String scenario;
    private final LlmFixture replay;
    private final Map<String, Stream> streams = new ConcurrentHashMap<>();
    private final AtomicReference<String> firstMismatch = new AtomicReference<>();

    private LlmFixtureSession(String scenario, LlmFixture replay) {
        this.scenario = scenario;
        this.replay = replay;
    }

    public static LlmFixtureSession recording(String scenario) {
        // Validate the name even when the first model call has not happened yet.
        new LlmFixture(LlmFixture.SCHEMA_VERSION, scenario, Map.of());
        return new LlmFixtureSession(scenario, null);
    }

    public static LlmFixtureSession replaying(LlmFixture fixture) {
        return new LlmFixtureSession(fixture.scenario(), snapshot(fixture));
    }

    /**
     * Wrap a real provider for recording, or pass null for replay (which never calls a provider).
     */
    public ChatModel modelFor(LlmCallContext context, ChatCompletion input, ChatModel delegate) {
        if (replay == null && delegate == null) {
            throw new IllegalArgumentException("Recording requires a real chat model");
        }
        if (replay == null && "mockLLM".equals(input.getLlmProvider())) {
            throw new IllegalArgumentException("Cannot record mockLLM");
        }
        // Freeze caller-owned options so later mutation cannot change an in-flight attempt.
        ChatCompletion constraints = MAPPER.convertValue(input, ChatCompletion.class);
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return invoke(context, constraints, delegate, prompt);
            }

            @Override
            public ChatOptions getDefaultOptions() {
                // ChatClient reads these before building the effective prompt. Recording must
                // preserve the real model's defaults; replay has no real provider to consult.
                return replay == null
                        ? delegate.getDefaultOptions()
                        : ChatModel.super.getDefaultOptions();
            }
        };
    }

    private ChatResponse invoke(
            LlmCallContext context, ChatCompletion input, ChatModel delegate, Prompt prompt) {
        if (replay != null && !replay.streams().containsKey(context.stream())) {
            throw mismatch(context.stream(), 0, "unexpected stream");
        }
        Stream stream = streams.computeIfAbsent(context.stream(), ignored -> new Stream());
        // Lock per logical stream, not per fixture: independent branches may call models in
        // parallel.
        synchronized (stream) {
            LlmFixtureNormalizer next = stream.normalizer.copy();
            LlmFixture.Request request = next.normalizeRequest(prompt, input);
            Cached cached = stream.attempts.get(context);
            if (cached != null) {
                requireMatch(context.stream(), cached.turn(), cached.request(), request);
                return cached.response();
            }
            int index = stream.turns.size();
            ChatResponse response;
            LlmFixture.Turn turn;
            if (replay == null) {
                response = delegate.call(prompt);
                turn = new LlmFixture.Turn(request, next.normalizeResponse(response));
            } else {
                List<LlmFixture.Turn> expected = replay.streams().get(context.stream());
                if (index >= expected.size()) {
                    throw mismatch(context.stream(), index, "unexpected extra call");
                }
                turn = expected.get(index);
                requireMatch(context.stream(), index, turn.request(), request);
                response =
                        next.replayResponse(
                                turn.response(), context.taskId() + "_" + context.retryAttempt());
            }
            // Commit only after normalization/matching and invocation succeed. Failed calls do not
            // consume a turn or leave partial ID mappings, and duplicate deliveries reuse a
            // response.
            stream.normalizer = next;
            stream.turns.add(turn);
            stream.attempts.put(context, new Cached(index, request, response));
            return response;
        }
    }

    /** Call after execution is terminal; an execution matching only a fixture prefix must fail. */
    public void verifyComplete() {
        if (replay == null) {
            throw new IllegalStateException("Completion verification requires a replay fixture");
        }
        // A later successful call must not hide an earlier mismatch or extra call, even if the
        // workflow catches task failure and eventually reaches its expected terminal status.
        if (firstMismatch.get() != null) {
            throw new NonRetryableException(firstMismatch.get());
        }
        replay.streams()
                .forEach(
                        (name, expected) -> {
                            Stream stream = streams.get(name);
                            int consumed = 0;
                            if (stream != null) {
                                synchronized (stream) {
                                    consumed = stream.turns.size();
                                }
                            }
                            if (consumed != expected.size()) {
                                throw new NonRetryableException(
                                        failureMessage(
                                                name,
                                                consumed,
                                                "unused expected turns: "
                                                        + (expected.size() - consumed)));
                            }
                        });
    }

    /** Snapshot recorded turns after the caller has awaited execution completion. */
    public LlmFixture fixture() {
        var turns = new TreeMap<String, List<LlmFixture.Turn>>();
        streams.forEach(
                (name, stream) -> {
                    synchronized (stream) {
                        turns.put(name, List.copyOf(stream.turns));
                    }
                });
        return snapshot(new LlmFixture(LlmFixture.SCHEMA_VERSION, scenario, turns));
    }

    private static LlmFixture snapshot(LlmFixture fixture) {
        return MAPPER.convertValue(fixture, LlmFixture.class);
    }

    private void requireMatch(
            String stream, int index, LlmFixture.Request expected, LlmFixture.Request actual) {
        String path =
                difference(MAPPER.valueToTree(expected), MAPPER.valueToTree(actual), "request");
        if (path != null) throw mismatch(stream, index, "mismatch at " + path);
    }

    private NonRetryableException mismatch(String stream, int turn, String detail) {
        String message = failureMessage(stream, turn, detail);
        firstMismatch.compareAndSet(null, message);
        return new NonRetryableException(message);
    }

    private String failureMessage(String stream, int turn, String detail) {
        String message =
                "LLM fixture " + scenario + ", stream " + stream + ", turn " + turn + ": " + detail;
        return message.length() <= 1024 ? message : message.substring(0, 1024) + "...";
    }

    private static String difference(JsonNode expected, JsonNode actual, String path) {
        if (expected.equals(actual)) return null;
        if (expected.isObject() && actual.isObject()) {
            var names = new java.util.TreeSet<String>();
            expected.fieldNames().forEachRemaining(names::add);
            actual.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (!expected.has(name) || !actual.has(name)) return path + "/" + name;
                String nested = difference(expected.get(name), actual.get(name), path + "/" + name);
                if (nested != null) return nested;
            }
        } else if (expected.isArray() && actual.isArray() && expected.size() == actual.size()) {
            for (int i = 0; i < expected.size(); i++) {
                String nested = difference(expected.get(i), actual.get(i), path + "/" + i);
                if (nested != null) return nested;
            }
        }
        return path;
    }

    private record Cached(int turn, LlmFixture.Request request, ChatResponse response) {}

    private static final class Stream {
        private LlmFixtureNormalizer normalizer = new LlmFixtureNormalizer();
        private final List<LlmFixture.Turn> turns = new ArrayList<>();
        private final Map<LlmCallContext, Cached> attempts = new HashMap<>();
    }
}
