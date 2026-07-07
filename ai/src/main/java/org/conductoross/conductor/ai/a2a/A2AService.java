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
package org.conductoross.conductor.ai.a2a;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.a2a.model.Artifact;
import org.conductoross.conductor.ai.a2a.model.Part;
import org.conductoross.conductor.ai.a2a.model.TaskStatus;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Minimal A2A (Agent2Agent) protocol client for talking to remote agents.
 *
 * <p>Hand-rolled JSON-RPC 2.0 over the shared {@code conductorAiHttpClient} (OkHttp) + Jackson —
 * the same approach as {@link org.conductoross.conductor.ai.mcp.MCPService}. Targets the A2A v0.3.x
 * wire model (and tolerates v1.0 enum spellings via {@link
 * org.conductoross.conductor.ai.a2a.model.TaskState}). Supports agent-card discovery and the {@code
 * message/send}, {@code tasks/get}, and {@code tasks/cancel} methods.
 *
 * <p>Transport/5xx/transient errors raise {@link A2AException} (retryable); client/protocol errors
 * (4xx, method-not-found, task-not-found, …) raise {@link NonRetryableException} (terminal).
 */
@Component
@Conditional(AIIntegrationEnabledCondition.class)
public class A2AService {

    private static final Logger log = LoggerFactory.getLogger(A2AService.class);
    private static final MediaType JSON = MediaType.get("application/json");
    private static final int MAX_ERROR_BODY = 500;

    /**
     * The only agent runtime implemented in OSS today. Native runtimes (langgraph, openai, …) are
     * planned.
     */
    public static final String AGENT_TYPE_A2A = "a2a";

    /** Whether {@code agentType} selects the A2A runtime — null/blank defaults to A2A. */
    public static boolean isA2aAgentType(String agentType) {
        return agentType == null
                || agentType.isBlank()
                || AGENT_TYPE_A2A.equalsIgnoreCase(agentType);
    }

    /** JSON-RPC / A2A error codes that are not worth retrying. */
    private static final Set<Integer> TERMINAL_RPC_CODES =
            Set.of(
                    -32700, // parse error
                    -32600, // invalid request
                    -32601, // method not found
                    -32602, // invalid params
                    -32001, // TaskNotFoundError
                    -32002, // TaskNotCancelableError
                    -32003, // PushNotificationNotSupportedError
                    -32004, // UnsupportedOperationError
                    -32005, // ContentTypeNotSupportedError
                    -32007); // AuthenticatedExtendedCardNotConfiguredError

    /** Known IPv6 cloud metadata addresses, always blocked (resolved from literals, no DNS). */
    private static final java.util.Set<InetAddress> METADATA_IPV6 = resolveMetadataIpv6();

    private static java.util.Set<InetAddress> resolveMetadataIpv6() {
        java.util.Set<InetAddress> set = new java.util.HashSet<>();
        for (String literal : new String[] {"fd00:ec2::254", "fe80::a9fe:a9fe"}) {
            try {
                set.add(InetAddress.getByName(literal));
            } catch (Exception ignored) {
                // IPv6 literals don't hit DNS; ignore if a JVM somehow rejects one.
            }
        }
        return set;
    }

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    /** Serializer that drops null fields, so outgoing requests stay clean for strict agents. */
    private final ObjectMapper sendMapper =
            objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /** Property: opt in to calling agents on loopback/RFC-1918/link-local addresses. */
    public static final String ALLOW_PRIVATE_NETWORK_PROPERTY =
            "conductor.a2a.client.allow-private-network";

    private final OkHttpClient httpClient;
    private final AtomicLong idCounter = new AtomicLong(1);

    /**
     * When true, the SSRF guard permits private/loopback addresses — for deployments where A2A
     * agents legitimately run on a trusted private network (and required for localhost
     * demos/tests). Cloud metadata endpoints (169.254.x.x) remain blocked even so.
     */
    private final boolean allowPrivateNetwork;

    /** Test/standalone constructor — SSRF guard fully enabled. */
    public A2AService(OkHttpClient conductorAiHttpClient) {
        this(conductorAiHttpClient, false);
    }

    public A2AService(OkHttpClient conductorAiHttpClient, boolean allowPrivateNetwork) {
        // Disable auto-redirects for A2A calls: validateAgentUrl() checks only the initial URL, so
        // a 30x to a private/metadata host would otherwise bypass the SSRF guard. A2A endpoints are
        // not expected to redirect. newBuilder() inherits the shared client's timeouts/pool/
        // interceptors, so this is isolated to the A2A client.
        this.httpClient =
                conductorAiHttpClient
                        .newBuilder()
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .build();
        this.allowPrivateNetwork = allowPrivateNetwork;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public A2AService(
            OkHttpClient conductorAiHttpClient,
            org.springframework.core.env.Environment environment) {
        this(
                conductorAiHttpClient,
                environment != null
                        && Boolean.parseBoolean(
                                environment.getProperty(ALLOW_PRIVATE_NETWORK_PROPERTY, "false")));
    }

    /**
     * Fetches a remote agent's {@link AgentCard} for discovery.
     *
     * <p>If {@code agentUrl} points directly at a {@code .json} document it is used as-is;
     * otherwise the standard well-known paths are tried in order: {@code
     * /.well-known/agent-card.json} (v0.3.x+) then {@code /.well-known/agent.json} (v0.2.5).
     */
    public AgentCard getAgentCard(String agentUrl, Map<String, String> headers) {
        validateAgentUrl(agentUrl);
        RuntimeException last = null;
        for (String url : agentCardUrls(agentUrl)) {
            try {
                Request.Builder rb =
                        new Request.Builder().url(url).get().header("Accept", "application/json");
                addHeaders(rb, headers);
                try (Response response = httpClient.newCall(rb.build()).execute()) {
                    String body = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        last = httpError(url, "GET agent card", response.code(), body);
                        continue;
                    }
                    log.debug("Resolved agent card from {}", url);
                    return objectMapper.readValue(body, AgentCard.class);
                }
            } catch (Exception e) {
                last =
                        new A2AException(
                                "Failed to fetch agent card from " + url + ": " + e.getMessage(),
                                e);
            }
        }
        throw last != null
                ? last
                : new A2AException("Could not resolve agent card from " + agentUrl);
    }

    /**
     * Sends a message to a remote agent ({@code message/send}).
     *
     * @param endpoint the agent's JSON-RPC endpoint URL
     * @param message the message to send
     * @param configuration optional {@code MessageSendConfiguration} (historyLength,
     *     pushNotificationConfig, …); may be null
     * @param headers optional HTTP headers (e.g. {@code Authorization})
     * @return the result, which is either a direct {@link A2AMessage} reply or an {@link A2ATask}
     */
    public SendResult sendMessage(
            String endpoint,
            A2AMessage message,
            Map<String, Object> configuration,
            Map<String, String> headers) {
        ObjectNode params = objectMapper.createObjectNode();
        params.set("message", sendMapper.valueToTree(message));
        if (configuration != null && !configuration.isEmpty()) {
            params.set("configuration", sendMapper.valueToTree(configuration));
        }
        JsonNode result = jsonRpc(endpoint, "message/send", params, headers);
        return SendResult.from(result, objectMapper);
    }

    /**
     * Sends a message and consumes the SSE stream ({@code message/stream}), aggregating the
     * streamed events (task, status-update, artifact-update chunks) into a final {@link
     * SendResult}.
     *
     * <p>The connection is held open until the agent signals the final status event, so this is
     * best for interactive/short streams; for very long-running work prefer send+poll or push.
     */
    public SendResult streamMessage(
            String endpoint,
            A2AMessage message,
            Map<String, Object> configuration,
            Map<String, String> headers) {
        validateAgentUrl(endpoint); // SSRF guard — must run on the streaming path too
        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.set("message", sendMapper.valueToTree(message));
            if (configuration != null && !configuration.isEmpty()) {
                params.set("configuration", sendMapper.valueToTree(configuration));
            }
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", idCounter.getAndIncrement());
            request.put("method", "message/stream");
            request.set("params", params);

            Request.Builder rb =
                    new Request.Builder()
                            .url(endpoint)
                            .post(
                                    RequestBody.create(
                                            objectMapper.writeValueAsString(request), JSON))
                            .header("Content-Type", "application/json")
                            .header("Accept", "text/event-stream");
            addHeaders(rb, headers);

            try (Response response = httpClient.newCall(rb.build()).execute()) {
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    throw httpError(endpoint, "message/stream", response.code(), body);
                }
                return aggregateStream(response);
            }
        } catch (A2AException | NonRetryableException e) {
            throw e;
        } catch (Exception e) {
            throw new A2AException("A2A stream to " + endpoint + " failed: " + e.getMessage(), e);
        }
    }

    private SendResult aggregateStream(Response response) throws Exception {
        StreamAggregator aggregator = new StreamAggregator();
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                response.body().byteStream(), StandardCharsets.UTF_8))) {
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (data.length() > 0) {
                        processStreamEvent(data.toString(), aggregator);
                        data.setLength(0);
                        if (aggregator.done) {
                            break;
                        }
                    }
                } else if (line.startsWith("data:")) {
                    String chunk = line.substring(5).trim();
                    if (!chunk.isEmpty() && !chunk.equals("[DONE]")) {
                        data.append(chunk);
                    }
                }
            }
            if (!aggregator.done && data.length() > 0) {
                processStreamEvent(data.toString(), aggregator);
            }
        }
        return aggregator.toResult();
    }

    private void processStreamEvent(String json, StreamAggregator aggregator) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.has("error") && !node.get("error").isNull()) {
                throw jsonRpcError("stream", "message/stream", node.get("error"));
            }
            JsonNode result = node.has("result") ? node.get("result") : node;
            aggregator.accept(result, objectMapper);
        } catch (A2AException | NonRetryableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Unparsable SSE event (stream may be incomplete): {}", e.getMessage());
        }
    }

    /** Retrieves the current state of a task ({@code tasks/get}). */
    public A2ATask getTask(
            String endpoint, String taskId, Integer historyLength, Map<String, String> headers) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("id", taskId);
        if (historyLength != null) {
            params.put("historyLength", historyLength);
        }
        JsonNode result = jsonRpc(endpoint, "tasks/get", params, headers);
        return objectMapper.convertValue(result, A2ATask.class);
    }

    /** Requests cancellation of a task ({@code tasks/cancel}); best-effort. */
    public A2ATask cancelTask(String endpoint, String taskId, Map<String, String> headers) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("id", taskId);
        JsonNode result = jsonRpc(endpoint, "tasks/cancel", params, headers);
        return objectMapper.convertValue(result, A2ATask.class);
    }

    /** Performs a single JSON-RPC 2.0 POST and returns the {@code result} node. */
    private JsonNode jsonRpc(
            String endpoint, String method, JsonNode params, Map<String, String> headers) {
        validateAgentUrl(endpoint);
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", idCounter.getAndIncrement());
            request.put("method", method);
            if (params != null) {
                request.set("params", params);
            }

            Request.Builder rb =
                    new Request.Builder()
                            .url(endpoint)
                            .post(
                                    RequestBody.create(
                                            objectMapper.writeValueAsString(request), JSON))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json, text/event-stream");
            addHeaders(rb, headers);

            try (Response response = httpClient.newCall(rb.build()).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw httpError(endpoint, method, response.code(), body);
                }
                JsonNode json = parseBody(response, body);
                if (json.has("error") && !json.get("error").isNull()) {
                    throw jsonRpcError(endpoint, method, json.get("error"));
                }
                if (!json.has("result")) {
                    throw new A2AException(
                            "Invalid JSON-RPC response from "
                                    + endpoint
                                    + " for '"
                                    + method
                                    + "': missing 'result' field");
                }
                return json.get("result");
            }
        } catch (A2AException | NonRetryableException e) {
            throw e;
        } catch (Exception e) {
            // IO/timeout/parse errors are transient — let the task retry.
            throw new A2AException(
                    "A2A call '" + method + "' to " + endpoint + " failed: " + e.getMessage(), e);
        }
    }

    private JsonNode parseBody(Response response, String body) throws Exception {
        String contentType = response.header("Content-Type", "application/json");
        if (contentType != null && contentType.contains("text/event-stream")) {
            return parseSseResponse(body);
        }
        return objectMapper.readTree(body);
    }

    /**
     * Guards against SSRF: rejects URLs whose hostname resolves to an RFC-1918 address, loopback,
     * link-local (169.254.x.x — AWS/GCP/Azure metadata), or any non-http(s) scheme.
     *
     * <p>Note: DNS resolution is performed once here. A sufficiently hostile DNS server could
     * rebind the name to a private IP after this check (TOCTOU). For stronger protection, deploy
     * behind a network-layer firewall that blocks egress to private ranges.
     */
    public void validateAgentUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new NonRetryableException("agentUrl must not be blank");
        }
        try {
            URL url = new URL(rawUrl.trim());
            String scheme = url.getProtocol();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new NonRetryableException("agentUrl must use http or https, got: " + scheme);
            }
            String host = url.getHost();
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                // Cloud metadata endpoints are blocked even when private networks are allowed.
                if (isMetadataAddress(addr)) {
                    A2AMetrics.ssrfBlocked();
                    throw new NonRetryableException(
                            "agentUrl resolves to a cloud metadata address — SSRF blocked: "
                                    + addr.getHostAddress());
                }
                if (allowPrivateNetwork) {
                    continue;
                }
                if (addr.isLoopbackAddress()
                        || addr.isSiteLocalAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isAnyLocalAddress()
                        || isUniqueLocalIpv6(addr)) {
                    A2AMetrics.ssrfBlocked();
                    throw new NonRetryableException(
                            "agentUrl resolves to a private/reserved address — SSRF blocked: "
                                    + addr.getHostAddress()
                                    + " (set "
                                    + ALLOW_PRIVATE_NETWORK_PROPERTY
                                    + "=true to allow private-network agents)");
                }
            }
        } catch (NonRetryableException e) {
            throw e;
        } catch (Exception e) {
            throw new NonRetryableException(
                    "agentUrl is not a valid URL: " + rawUrl + " — " + e.getMessage(), e);
        }
    }

    /**
     * Cloud metadata endpoints, blocked even when private networks are allowed: IPv4 link-local
     * 169.254.0.0/16 (AWS IMDS 169.254.169.254, ECS 169.254.170.2) and the IPv6 metadata addresses
     * (AWS {@code fd00:ec2::254}, link-local {@code fe80::a9fe:a9fe}).
     */
    private static boolean isMetadataAddress(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            return (b[0] & 0xFF) == 169 && (b[1] & 0xFF) == 254;
        }
        return METADATA_IPV6.contains(addr);
    }

    /**
     * IPv6 Unique Local Address fc00::/7 — private, but NOT covered by Java's isSiteLocalAddress.
     */
    private static boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] b = addr.getAddress();
        return b.length == 16 && (b[0] & 0xFE) == 0xFC;
    }

    /** Builds the candidate agent-card URLs for {@code agentUrl}. */
    private List<String> agentCardUrls(String agentUrl) {
        String url = agentUrl.trim();
        List<String> candidates = new ArrayList<>();
        if (url.endsWith(".json")) {
            candidates.add(url);
            return candidates;
        }
        String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        candidates.add(base + "/.well-known/agent-card.json");
        candidates.add(base + "/.well-known/agent.json");
        return candidates;
    }

    private RuntimeException httpError(String endpoint, String op, int code, String body) {
        String message =
                String.format(
                        "HTTP %d from A2A agent (%s %s): %s", code, op, endpoint, truncate(body));
        boolean terminal = !isRetryableHttp(code);
        A2AMetrics.rpcError(op, terminal);
        return terminal ? new NonRetryableException(message) : new A2AException(message);
    }

    private RuntimeException jsonRpcError(String endpoint, String op, JsonNode error) {
        int code = error.path("code").asInt(0);
        String message = error.path("message").asText("");
        String data = error.has("data") ? error.get("data").toString() : "";
        String full =
                String.format(
                                "A2A JSON-RPC error %d from %s (%s): %s %s",
                                code, endpoint, op, message, data)
                        .trim();
        boolean terminal = TERMINAL_RPC_CODES.contains(code);
        A2AMetrics.rpcError(op, terminal);
        return terminal ? new NonRetryableException(full) : new A2AException(full);
    }

    private boolean isRetryableHttp(int code) {
        return code == 408 || code == 429 || code >= 500;
    }

    private void addHeaders(Request.Builder builder, Map<String, String> headers) {
        if (headers != null && !headers.isEmpty()) {
            headers.forEach(
                    (key, value) -> {
                        if (key != null && value != null) {
                            builder.header(key, value);
                        }
                    });
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > MAX_ERROR_BODY ? body.substring(0, MAX_ERROR_BODY) + "…" : body;
    }

    /**
     * Parses an SSE response, concatenating the JSON from {@code data:} lines (some A2A agents
     * reply to non-streaming calls with {@code text/event-stream}).
     */
    private JsonNode parseSseResponse(String sseBody) throws Exception {
        StringBuilder jsonData = new StringBuilder();
        for (String line : sseBody.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                String data = trimmed.substring(5).trim();
                if (!data.isEmpty() && !data.equals("[DONE]")) {
                    jsonData.append(data);
                }
            }
        }
        if (jsonData.length() == 0) {
            throw new A2AException("No data found in SSE response: " + truncate(sseBody));
        }
        return objectMapper.readTree(jsonData.toString());
    }

    /** Accumulates A2A streaming events into a single task (or direct message) result. */
    private static final class StreamAggregator {

        private String id;
        private String contextId;
        private TaskStatus status;
        private final Map<String, Artifact> artifacts = new LinkedHashMap<>();
        private A2AMessage message;
        private boolean done;

        void accept(JsonNode result, ObjectMapper objectMapper) {
            String kind = result.path("kind").asText("");
            if ("status-update".equals(kind)) {
                mergeIds(result);
                this.status = objectMapper.convertValue(result.get("status"), TaskStatus.class);
                if (result.path("final").asBoolean(false)) {
                    done = true;
                }
            } else if ("artifact-update".equals(kind)) {
                mergeIds(result);
                Artifact incoming =
                        objectMapper.convertValue(result.get("artifact"), Artifact.class);
                if (incoming != null && incoming.getArtifactId() != null) {
                    boolean append = result.path("append").asBoolean(false);
                    Artifact existing = artifacts.get(incoming.getArtifactId());
                    if (append && existing != null && existing.getParts() != null) {
                        List<Part> merged = new ArrayList<>(existing.getParts());
                        if (incoming.getParts() != null) {
                            merged.addAll(incoming.getParts());
                        }
                        existing.setParts(merged);
                    } else {
                        artifacts.put(incoming.getArtifactId(), incoming);
                    }
                }
            } else if ("task".equals(kind) || result.has("status")) {
                A2ATask task = objectMapper.convertValue(result, A2ATask.class);
                if (task.getId() != null) {
                    id = task.getId();
                }
                if (task.getContextId() != null) {
                    contextId = task.getContextId();
                }
                if (task.getStatus() != null) {
                    status = task.getStatus();
                }
                if (task.getArtifacts() != null) {
                    for (Artifact artifact : task.getArtifacts()) {
                        if (artifact.getArtifactId() != null) {
                            artifacts.put(artifact.getArtifactId(), artifact);
                        }
                    }
                }
            } else if ("message".equals(kind) || result.has("parts")) {
                this.message = objectMapper.convertValue(result, A2AMessage.class);
            }
        }

        private void mergeIds(JsonNode result) {
            if (result.hasNonNull("taskId")) {
                id = result.get("taskId").asText();
            }
            if (result.hasNonNull("contextId")) {
                contextId = result.get("contextId").asText();
            }
        }

        SendResult toResult() {
            if (status != null || !artifacts.isEmpty() || id != null) {
                A2ATask task = new A2ATask();
                task.setId(id);
                task.setContextId(contextId);
                task.setStatus(status);
                task.setArtifacts(new ArrayList<>(artifacts.values()));
                task.setKind("task");
                return SendResult.ofTask(task);
            }
            return SendResult.ofMessage(message);
        }
    }

    /**
     * The outcome of {@code message/send}: a remote agent returns either a direct {@link
     * A2AMessage} reply or an {@link A2ATask} (for tracked/long-running work).
     */
    public static final class SendResult {

        private final A2ATask task;
        private final A2AMessage message;

        private SendResult(A2ATask task, A2AMessage message) {
            this.task = task;
            this.message = message;
        }

        public static SendResult ofTask(A2ATask task) {
            return new SendResult(task, null);
        }

        public static SendResult ofMessage(A2AMessage message) {
            return new SendResult(null, message);
        }

        public boolean isTask() {
            return task != null;
        }

        public A2ATask getTask() {
            return task;
        }

        public A2AMessage getMessage() {
            return message;
        }

        static SendResult from(JsonNode result, ObjectMapper objectMapper) {
            String kind = result.path("kind").asText("");
            boolean looksLikeTask = "task".equalsIgnoreCase(kind) || result.has("status");
            if (looksLikeTask) {
                return ofTask(objectMapper.convertValue(result, A2ATask.class));
            }
            return ofMessage(objectMapper.convertValue(result, A2AMessage.class));
        }
    }
}
