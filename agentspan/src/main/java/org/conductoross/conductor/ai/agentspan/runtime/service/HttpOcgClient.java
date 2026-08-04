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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.conductoross.conductor.common.metadata.agent.LongTermMemoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Default OCG HTTP transport with server-side credentials, bounded retries, and redacted logs. */
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class HttpOcgClient implements OcgClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpOcgClient.class);
    private static final int MAX_REQUEST_BYTES = 10 * 1024 * 1024;
    private static final int TARGET_REQUEST_BYTES = 9_500_000;

    private final ObjectMapper mapper;
    private final Function<String, String> credentialResolver;
    private final HttpClient client;
    private final Duration timeout;
    private final int maxAttempts;

    @Autowired
    public HttpOcgClient(
            ObjectMapper mapper, CredentialResolutionService credentialResolutionService) {
        this(
                mapper,
                credentialResolutionService::resolve,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Duration.ofSeconds(5),
                2);
    }

    HttpOcgClient(
            ObjectMapper mapper,
            Function<String, String> credentialResolver,
            HttpClient client,
            Duration timeout,
            int maxAttempts) {
        this.mapper = mapper;
        this.credentialResolver = credentialResolver;
        this.client = client;
        this.timeout = timeout;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public CompletionStage<Void> exportAgentRun(
            LongTermMemoryConfig config, Map<String, Object> payload) {
        String workflowId = stringValue(payload.get("execution_id"));
        String sessionId = stringValue(payload.get("session_id"));
        try {
            String credential = credentialResolver.apply(config.getCredential());
            if (isBlank(credential)) {
                LOGGER.warn(
                        "Skipping OCG run capture for workflow {}: credential '{}' is unavailable",
                        workflowId,
                        config.getCredential());
                return CompletableFuture.completedFuture(null);
            }
            byte[] body = encodeWithinLimit(payload);
            if (body.length > MAX_REQUEST_BYTES) {
                LOGGER.warn(
                        "Skipping OCG run capture for workflow {}: input and result exceed the OCG request limit",
                        workflowId);
                return CompletableFuture.completedFuture(null);
            }
            URI endpoint =
                    URI.create(
                            config.getOcgUrl().replaceAll("/+$", "")
                                    + "/api/v1/memories/agent-run");
            HttpRequest request =
                    HttpRequest.newBuilder(endpoint)
                            .timeout(timeout)
                            .header("X-API-Key", credential)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                            .build();
            return send(request, workflowId, sessionId, 1);
        } catch (Exception e) {
            LOGGER.warn(
                    "Unable to prepare OCG run capture for workflow {}: {}",
                    workflowId,
                    rootMessage(e));
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public void captureAgentRun(LongTermMemoryConfig config, Map<String, Object> payload) {
        String executionId = stringValue(payload.get("execution_id"));
        try {
            String credential = credentialResolver.apply(config.getCredential());
            if (isBlank(credential)) {
                throw feedbackFailure(
                        OcgFeedbackClientException.Failure.CREDENTIAL_UNAVAILABLE, null, null);
            }
            byte[] body = encodeWithinLimit(payload);
            if (body.length > MAX_REQUEST_BYTES) {
                throw feedbackFailure(
                        OcgFeedbackClientException.Failure.INVALID_RESPONSE, null, null);
            }
            URI endpoint =
                    URI.create(
                            config.getOcgUrl().replaceAll("/+$", "")
                                    + "/api/v1/memories/agent-run");
            HttpResponse<byte[]> response =
                    client.send(
                            HttpRequest.newBuilder(endpoint)
                                    .timeout(timeout)
                                    .header("X-API-Key", credential)
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                                    .build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 202) {
                LOGGER.warn(
                        "OCG run capture for execution {} returned HTTP {}",
                        executionId,
                        response.statusCode());
                throw feedbackFailure(
                        OcgFeedbackClientException.Failure.UPSTREAM_REJECTED,
                        response.statusCode(),
                        null);
            }
        } catch (HttpTimeoutException e) {
            throw feedbackFailure(OcgFeedbackClientException.Failure.UPSTREAM_TIMEOUT, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw feedbackFailure(OcgFeedbackClientException.Failure.UPSTREAM_UNAVAILABLE, null, e);
        } catch (IOException e) {
            throw feedbackFailure(OcgFeedbackClientException.Failure.UPSTREAM_UNAVAILABLE, null, e);
        }
    }

    @Override
    public OcgFeedback getFeedback(LongTermMemoryConfig config, OcgExecutionIdentity identity) {
        StringBuilder endpoint = new StringBuilder(feedbackEndpoint(config)).append('?');
        appendQuery(endpoint, "agent", identity.agent());
        if (!isBlank(identity.user())) appendQuery(endpoint, "user", identity.user());
        appendQuery(endpoint, "session_id", identity.sessionId());
        appendQuery(endpoint, "execution_id", identity.executionId());

        HttpRequest request =
                feedbackRequest(config, URI.create(endpoint.toString())).GET().build();
        return executeFeedback(request, identity.executionId());
    }

    @Override
    public OcgExecutionMemory getExecutionMemory(
            LongTermMemoryConfig config, OcgExecutionIdentity identity) {
        OcgExecutionMemory memory = getExecutionMemory(config, identity, identity.user());
        // OCG assigns agent-scoped runs an inferred user when no user was supplied. Read that
        // trusted fallback so the execution memory remains visible to Conductor.
        return memory.summary() != null || !isBlank(identity.user())
                ? memory
                : getExecutionMemory(config, identity, "agent:" + identity.agent());
    }

    private OcgExecutionMemory getExecutionMemory(
            LongTermMemoryConfig config, OcgExecutionIdentity identity, String user) {
        StringBuilder endpoint =
                new StringBuilder(memoryEndpoint(config))
                        .append('/')
                        .append(encode(identity.executionId()))
                        .append("/memory")
                        .append('?');
        appendQuery(endpoint, "agent", identity.agent());
        if (!isBlank(user)) appendQuery(endpoint, "user", user);
        HttpRequest request =
                feedbackRequest(config, URI.create(endpoint.toString())).GET().build();
        return executeMemory(request, identity.executionId());
    }

    @Override
    public OcgFeedback setFeedback(
            LongTermMemoryConfig config,
            OcgExecutionIdentity identity,
            OcgFeedbackRating rating,
            String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent", identity.agent());
        if (!isBlank(identity.user())) payload.put("user", identity.user());
        payload.put("session_id", identity.sessionId());
        payload.put("execution_id", identity.executionId());
        payload.put("rating", rating.value());
        payload.put("reason", reason);
        try {
            HttpRequest request =
                    feedbackRequest(config, URI.create(feedbackEndpoint(config)))
                            .header("Content-Type", "application/json")
                            .PUT(
                                    HttpRequest.BodyPublishers.ofByteArray(
                                            mapper.writeValueAsBytes(payload)))
                            .build();
            return executeFeedback(request, identity.executionId());
        } catch (JsonProcessingException e) {
            throw feedbackFailure(OcgFeedbackClientException.Failure.INVALID_RESPONSE, null, e);
        }
    }

    private HttpRequest.Builder feedbackRequest(LongTermMemoryConfig config, URI endpoint) {
        String credential;
        try {
            credential = credentialResolver.apply(config.getCredential());
        } catch (Exception e) {
            throw feedbackFailure(
                    OcgFeedbackClientException.Failure.CREDENTIAL_UNAVAILABLE, null, e);
        }
        if (isBlank(credential)) {
            throw feedbackFailure(
                    OcgFeedbackClientException.Failure.CREDENTIAL_UNAVAILABLE, null, null);
        }
        return HttpRequest.newBuilder(endpoint).timeout(timeout).header("X-API-Key", credential);
    }

    private OcgFeedback executeFeedback(HttpRequest request, String executionId) {
        try {
            HttpResponse<byte[]> response =
                    client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn(
                        "OCG feedback for execution {} returned HTTP {}",
                        executionId,
                        response.statusCode());
                throw feedbackFailure(
                        OcgFeedbackClientException.Failure.UPSTREAM_REJECTED,
                        response.statusCode(),
                        null);
            }
            return parseFeedback(response.body());
        } catch (HttpTimeoutException e) {
            throw feedbackFailure(OcgFeedbackClientException.Failure.UPSTREAM_TIMEOUT, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw feedbackFailure(OcgFeedbackClientException.Failure.UPSTREAM_UNAVAILABLE, null, e);
        } catch (IOException e) {
            throw feedbackFailure(OcgFeedbackClientException.Failure.UPSTREAM_UNAVAILABLE, null, e);
        }
    }

    private OcgExecutionMemory executeMemory(HttpRequest request, String executionId) {
        try {
            HttpResponse<byte[]> response =
                    client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) return new OcgExecutionMemory(null);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn(
                        "OCG memory for execution {} returned HTTP {}",
                        executionId,
                        response.statusCode());
                throw feedbackFailure(
                        OcgFeedbackClientException.Failure.UPSTREAM_REJECTED,
                        response.statusCode(),
                        null);
            }
            JsonNode responseBody = mapper.readTree(response.body());
            JsonNode description = responseBody.get("description");
            if (description == null || description.isNull()) return new OcgExecutionMemory(null);
            if (!description.isTextual()) {
                throw feedbackFailure(
                        OcgFeedbackClientException.Failure.INVALID_RESPONSE, null, null);
            }
            return new OcgExecutionMemory(description.textValue());
        } catch (HttpTimeoutException e) {
            throw feedbackFailure(OcgFeedbackClientException.Failure.UPSTREAM_TIMEOUT, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw feedbackFailure(OcgFeedbackClientException.Failure.UPSTREAM_UNAVAILABLE, null, e);
        } catch (IOException e) {
            throw feedbackFailure(OcgFeedbackClientException.Failure.UPSTREAM_UNAVAILABLE, null, e);
        }
    }

    private OcgFeedback parseFeedback(byte[] body) {
        try {
            JsonNode response = mapper.readTree(body);
            JsonNode ratingNode = response.get("rating");
            if (ratingNode == null || (!ratingNode.isNull() && !ratingNode.isTextual())) {
                throw feedbackFailure(
                        OcgFeedbackClientException.Failure.INVALID_RESPONSE, null, null);
            }
            OcgFeedbackRating rating =
                    ratingNode.isNull()
                            ? null
                            : OcgFeedbackRating.fromValue(ratingNode.textValue());
            JsonNode reasonNode = response.get("reason");
            if (reasonNode != null && !reasonNode.isNull() && !reasonNode.isTextual()) {
                throw feedbackFailure(
                        OcgFeedbackClientException.Failure.INVALID_RESPONSE, null, null);
            }
            if (rating != null && (reasonNode == null || reasonNode.isNull())) {
                throw feedbackFailure(
                        OcgFeedbackClientException.Failure.INVALID_RESPONSE, null, null);
            }
            String reason =
                    reasonNode == null || reasonNode.isNull() ? null : reasonNode.textValue();
            JsonNode submittedNode = response.get("submitted_at");
            Instant submittedAt =
                    submittedNode == null || submittedNode.isNull()
                            ? null
                            : Instant.parse(submittedNode.textValue());
            return new OcgFeedback(rating, reason, submittedAt);
        } catch (IOException
                | DateTimeParseException
                | IllegalArgumentException
                | NullPointerException e) {
            throw feedbackFailure(OcgFeedbackClientException.Failure.INVALID_RESPONSE, null, e);
        }
    }

    private static String feedbackEndpoint(LongTermMemoryConfig config) {
        return config.getOcgUrl().replaceAll("/+$", "") + "/api/v1/memories/agent-run/feedback";
    }

    private static String memoryEndpoint(LongTermMemoryConfig config) {
        return config.getOcgUrl().replaceAll("/+$", "") + "/api/v1/agent-runs";
    }

    private static void appendQuery(StringBuilder endpoint, String name, String value) {
        if (endpoint.charAt(endpoint.length() - 1) != '?') endpoint.append('&');
        endpoint.append(encode(name)).append('=').append(encode(value));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static OcgFeedbackClientException feedbackFailure(
            OcgFeedbackClientException.Failure failure, Integer upstreamStatus, Throwable cause) {
        return new OcgFeedbackClientException(failure, upstreamStatus, cause);
    }

    private CompletionStage<Void> send(
            HttpRequest request, String workflowId, String sessionId, int attempt) {
        CompletableFuture<HttpResponse<Void>> response;
        try {
            response = client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            return failedAttempt(request, workflowId, sessionId, attempt, e);
        }
        return response.handle(
                        (value, error) -> {
                            if (error != null) {
                                return failedAttempt(
                                        request, workflowId, sessionId, attempt, error);
                            }
                            if (value.statusCode() >= 500 && attempt < maxAttempts) {
                                return send(request, workflowId, sessionId, attempt + 1);
                            }
                            if (value.statusCode() != 202) {
                                LOGGER.warn(
                                        "OCG run capture for workflow {} returned HTTP {}",
                                        workflowId,
                                        value.statusCode());
                            } else {
                                LOGGER.debug(
                                        "Queued OCG run capture for workflow {}, session {}",
                                        workflowId,
                                        sessionId);
                            }
                            return CompletableFuture.<Void>completedFuture(null);
                        })
                .thenCompose(Function.identity());
    }

    private CompletionStage<Void> failedAttempt(
            HttpRequest request,
            String workflowId,
            String sessionId,
            int attempt,
            Throwable error) {
        if (attempt < maxAttempts) return send(request, workflowId, sessionId, attempt + 1);
        LOGGER.warn(
                "OCG run capture unavailable for workflow {} after {} attempts: {}",
                workflowId,
                attempt,
                rootMessage(error));
        return CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings("unchecked")
    byte[] encodeWithinLimit(Map<String, Object> payload) throws JsonProcessingException {
        byte[] encoded = mapper.writeValueAsBytes(payload);
        if (encoded.length <= TARGET_REQUEST_BYTES) return encoded;
        Object eventValue = payload.get("events");
        if (!(eventValue instanceof List<?> rawEvents) || rawEvents.isEmpty()) return encoded;
        List<Map<String, Object>> events = (List<Map<String, Object>>) rawEvents;

        int perFieldChars = Math.max(256, TARGET_REQUEST_BYTES / (events.size() * 4));
        for (Map<String, Object> event : events) {
            event.put("detail", truncate(String.valueOf(event.get("detail")), perFieldChars));
            event.put("output", truncate(String.valueOf(event.get("output")), perFieldChars));
        }
        encoded = mapper.writeValueAsBytes(payload);
        while (encoded.length > TARGET_REQUEST_BYTES && perFieldChars > 256) {
            perFieldChars /= 2;
            for (Map<String, Object> event : events) {
                event.put("detail", truncate(String.valueOf(event.get("detail")), perFieldChars));
                event.put("output", truncate(String.valueOf(event.get("output")), perFieldChars));
            }
            encoded = mapper.writeValueAsBytes(payload);
        }
        return encoded;
    }

    private static String stringValue(Object value) {
        return value == null ? "unknown" : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "…[truncated]";
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
