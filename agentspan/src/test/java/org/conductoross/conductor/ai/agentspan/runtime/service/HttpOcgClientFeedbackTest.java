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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.conductoross.conductor.common.metadata.agent.LongTermMemoryConfig;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpOcgClientFeedbackTest {

    private static final String FEEDBACK_PATH = "/api/v1/memories/agent-run/feedback";
    private static final String MEMORY_PATH = "/api/v1/memories/run/";
    private static final OcgExecutionIdentity IDENTITY =
            new OcgExecutionIdentity(
                    "agent/a b", "user:nicholas+test", "session/123", "execution?456");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsUnratedFeedbackWithEncodedExecutionIdentityAndApiKey() throws Exception {
        try (FeedbackServer server = new FeedbackServer()) {
            OcgFeedback feedback =
                    client(server.url(), name -> "resolved-key", 500)
                            .getFeedback(config(server.url()), IDENTITY);

            assertThat(feedback).isEqualTo(new OcgFeedback(null, null, null));
            assertThat(server.apiKey.get()).isEqualTo("resolved-key");
            assertThat(server.rawQuery.get())
                    .contains("agent=agent%2Fa%20b")
                    .contains("user=user%3Anicholas%2Btest")
                    .contains("session_id=session%2F123")
                    .contains("execution_id=execution%3F456")
                    .doesNotContain("turn_id");
        }
    }

    @Test
    void readsExistingFeedbackIncludingReason() throws Exception {
        try (FeedbackServer server = new FeedbackServer()) {
            server.rating.set("negative");
            server.reason.set("It omitted the requested evidence.");
            server.submittedAt.set(Instant.parse("2026-07-31T20:15:00Z"));

            assertThat(
                            client(server.url(), name -> "key", 500)
                                    .getFeedback(config(server.url()), IDENTITY))
                    .isEqualTo(
                            new OcgFeedback(
                                    OcgFeedbackRating.NEGATIVE,
                                    "It omitted the requested evidence.",
                                    Instant.parse("2026-07-31T20:15:00Z")));
        }
    }

    @Test
    void readsExecutionMemoryWithEncodedIdentityAndApiKey() throws Exception {
        try (FeedbackServer server = new FeedbackServer()) {
            server.memorySummary.set("The agent resolved the incident.");

            assertThat(
                            client(server.url(), name -> "resolved-key", 500)
                                    .getExecutionMemory(config(server.url()), IDENTITY))
                    .isEqualTo(new OcgExecutionMemory("The agent resolved the incident."));
            assertThat(server.apiKey.get()).isEqualTo("resolved-key");
            assertThat(server.memoryPath.get()).isEqualTo(MEMORY_PATH + "execution%3F456");
            assertThat(server.rawQuery.get())
                    .contains("agent=agent%2Fa%20b")
                    .contains("user=user%3Anicholas%2Btest")
                    .doesNotContain("session_id")
                    .doesNotContain("turn_id");
        }
    }

    @Test
    void upsertsRepeatsAndReplacesRatingAndReasonCanonically() throws Exception {
        try (FeedbackServer server = new FeedbackServer()) {
            HttpOcgClient client = client(server.url(), name -> "resolved-key", 500);

            OcgFeedback positive =
                    client.setFeedback(
                            config(server.url()),
                            IDENTITY,
                            OcgFeedbackRating.POSITIVE,
                            "Resolved the issue.");
            OcgFeedback repeated =
                    client.setFeedback(
                            config(server.url()),
                            IDENTITY,
                            OcgFeedbackRating.POSITIVE,
                            "Resolved the issue.");
            OcgFeedback changedReason =
                    client.setFeedback(
                            config(server.url()),
                            IDENTITY,
                            OcgFeedbackRating.POSITIVE,
                            "Resolved the issue with clear next steps.");
            OcgFeedback replaced =
                    client.setFeedback(
                            config(server.url()),
                            IDENTITY,
                            OcgFeedbackRating.NEGATIVE,
                            "The final answer is incorrect.");

            assertThat(positive.rating()).isEqualTo(OcgFeedbackRating.POSITIVE);
            assertThat(repeated).isEqualTo(positive);
            assertThat(changedReason.submittedAt()).isAfter(positive.submittedAt());
            assertThat(replaced.rating()).isEqualTo(OcgFeedbackRating.NEGATIVE);
            assertThat(replaced.reason()).isEqualTo("The final answer is incorrect.");
            assertThat(server.apiKey.get()).isEqualTo("resolved-key");
            assertThat(server.lastPayload.get())
                    .containsEntry("agent", IDENTITY.agent())
                    .containsEntry("user", IDENTITY.user())
                    .containsEntry("session_id", IDENTITY.sessionId())
                    .containsEntry("execution_id", IDENTITY.executionId())
                    .containsEntry("rating", "negative")
                    .containsEntry("reason", "The final answer is incorrect.")
                    .doesNotContainKey("turn_id");
        }
    }

    @Test
    void supportsAnInitialNegativeUpsertAndOmitsAbsentUser() throws Exception {
        try (FeedbackServer server = new FeedbackServer()) {
            OcgExecutionIdentity withoutUser =
                    new OcgExecutionIdentity("agent", null, "session", "execution");

            OcgFeedback feedback =
                    client(server.url(), name -> "key", 500)
                            .setFeedback(
                                    config(server.url()),
                                    withoutUser,
                                    OcgFeedbackRating.NEGATIVE,
                                    "It did not answer the question.");

            assertThat(feedback.rating()).isEqualTo(OcgFeedbackRating.NEGATIVE);
            assertThat(server.lastPayload.get()).doesNotContainKey("user");
        }
    }

    @Test
    void rejectsRatedResponsesWithoutReason() throws Exception {
        try (FeedbackServer server = new FeedbackServer()) {
            server.rating.set("positive");
            server.includeReason.set(false);

            assertFailure(
                    () ->
                            client(server.url(), name -> "key", 500)
                                    .getFeedback(config(server.url()), IDENTITY),
                    OcgFeedbackClientException.Failure.INVALID_RESPONSE);
        }
    }

    @Test
    void reportsMissingCredentialWithoutCallingOcg() throws Exception {
        try (FeedbackServer server = new FeedbackServer()) {
            assertFailure(
                    () ->
                            client(server.url(), name -> null, 500)
                                    .getFeedback(config(server.url()), IDENTITY),
                    OcgFeedbackClientException.Failure.CREDENTIAL_UNAVAILABLE);
            assertThat(server.calls).hasValue(0);
        }
    }

    @Test
    void reportsOcgClientAndServerErrors() throws Exception {
        try (FeedbackServer server = new FeedbackServer()) {
            HttpOcgClient client = client(server.url(), name -> "key", 500);
            for (int status : List.of(400, 401, 403, 404, 503)) {
                server.status.set(status);
                assertFailure(
                        () -> client.getFeedback(config(server.url()), IDENTITY),
                        OcgFeedbackClientException.Failure.UPSTREAM_REJECTED,
                        status);
                assertFailure(
                        () ->
                                client.setFeedback(
                                        config(server.url()),
                                        IDENTITY,
                                        OcgFeedbackRating.POSITIVE,
                                        "Good result."),
                        OcgFeedbackClientException.Failure.UPSTREAM_REJECTED,
                        status);
            }
        }
    }

    @Test
    void reportsTimeoutAndUnavailableOcg() throws Exception {
        try (FeedbackServer server = new FeedbackServer()) {
            server.delayMillis.set(300);
            assertFailure(
                    () ->
                            client(server.url(), name -> "key", 50)
                                    .getFeedback(config(server.url()), IDENTITY),
                    OcgFeedbackClientException.Failure.UPSTREAM_TIMEOUT);
        }

        int unavailablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }
        String url = "http://127.0.0.1:" + unavailablePort;
        assertFailure(
                () -> client(url, name -> "key", 200).getFeedback(config(url), IDENTITY),
                OcgFeedbackClientException.Failure.UPSTREAM_UNAVAILABLE);
    }

    private HttpOcgClient client(
            String url, Function<String, String> credentialResolver, long timeoutMillis) {
        return new HttpOcgClient(
                mapper,
                credentialResolver,
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMillis)).build(),
                Duration.ofMillis(timeoutMillis),
                1);
    }

    private static LongTermMemoryConfig config(String url) {
        return LongTermMemoryConfig.builder()
                .ocgUrl(url)
                .credential("OCG_KEY")
                .agent("agent")
                .build();
    }

    private static void assertFailure(
            ThrowingCall call, OcgFeedbackClientException.Failure failure) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        OcgFeedbackClientException.class,
                        error -> assertThat(error.getFailure()).isEqualTo(failure));
    }

    private static void assertFailure(
            ThrowingCall call, OcgFeedbackClientException.Failure failure, int upstreamStatus) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        OcgFeedbackClientException.class,
                        error -> {
                            assertThat(error.getFailure()).isEqualTo(failure);
                            assertThat(error.getUpstreamStatus()).isEqualTo(upstreamStatus);
                        });
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }

    private final class FeedbackServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger status = new AtomicInteger(200);
        private final AtomicInteger delayMillis = new AtomicInteger();
        private final AtomicReference<String> apiKey = new AtomicReference<>();
        private final AtomicReference<String> rawQuery = new AtomicReference<>();
        private final AtomicReference<String> memoryPath = new AtomicReference<>();
        private final AtomicReference<String> rating = new AtomicReference<>();
        private final AtomicReference<String> reason = new AtomicReference<>();
        private final AtomicReference<Instant> submittedAt = new AtomicReference<>();
        private final AtomicReference<Map<String, Object>> lastPayload = new AtomicReference<>();
        private final AtomicReference<String> memorySummary = new AtomicReference<>();
        private final AtomicBoolean includeReason = new AtomicBoolean(true);

        private FeedbackServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(FEEDBACK_PATH, this::handle);
            server.createContext(MEMORY_PATH, this::handleMemory);
            server.start();
        }

        private String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) throws IOException {
            calls.incrementAndGet();
            apiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            if (delayMillis.get() > 0) {
                try {
                    TimeUnit.MILLISECONDS.sleep(delayMillis.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (status.get() != 200) {
                exchange.sendResponseHeaders(status.get(), -1);
                exchange.close();
                return;
            }
            if ("PUT".equals(exchange.getRequestMethod())) update(exchange);
            byte[] body = responseBody();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        private void update(HttpExchange exchange) throws IOException {
            Map<String, Object> payload =
                    mapper.readValue(
                            exchange.getRequestBody(), new TypeReference<Map<String, Object>>() {});
            lastPayload.set(payload);
            String nextRating = String.valueOf(payload.get("rating"));
            String nextReason = String.valueOf(payload.get("reason"));
            if (!nextRating.equals(rating.get()) || !nextReason.equals(reason.get())) {
                rating.set(nextRating);
                reason.set(nextReason);
                Instant previous = submittedAt.get();
                submittedAt.set(
                        previous == null
                                ? Instant.parse("2026-07-31T20:15:00Z")
                                : previous.plusSeconds(1));
            }
        }

        private void handleMemory(HttpExchange exchange) throws IOException {
            calls.incrementAndGet();
            apiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            memoryPath.set(exchange.getRequestURI().getRawPath());
            if (memorySummary.get() == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] body = mapper.writeValueAsBytes(Map.of("description", memorySummary.get()));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        private byte[] responseBody() throws IOException {
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("rating", rating.get());
            if (includeReason.get()) response.put("reason", reason.get());
            response.put(
                    "submitted_at",
                    submittedAt.get() == null ? null : submittedAt.get().toString());
            return mapper.writeValueAsBytes(response);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
