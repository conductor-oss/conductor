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

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.conductoross.conductor.common.metadata.agent.LongTermMemoryConfig;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class OcgAgentRunExporterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void honorsWorkflowStatusListenerOptIn() {
        AtomicInteger exports = new AtomicInteger();
        OcgAgentRunExporter exporter =
                new OcgAgentRunExporter(
                        mapper,
                        new OcgClient() {
                            @Override
                            public CompletionStage<Void> exportAgentRun(
                                    LongTermMemoryConfig config, Map<String, Object> payload) {
                                exports.incrementAndGet();
                                return CompletableFuture.completedFuture(null);
                            }

                            @Override
                            public OcgFeedback getFeedback(
                                    LongTermMemoryConfig config, OcgExecutionIdentity identity) {
                                throw new UnsupportedOperationException();
                            }

                            @Override
                            public OcgFeedback setFeedback(
                                    LongTermMemoryConfig config,
                                    OcgExecutionIdentity identity,
                                    OcgFeedbackRating rating,
                                    String reason) {
                                throw new UnsupportedOperationException();
                            }
                        });
        WorkflowModel workflow = workflow("https://unused.example", "session", "turn");

        exporter.onWorkflowCompletedIfEnabled(workflow);
        exporter.onWorkflowTerminatedIfEnabled(workflow);
        assertThat(exports).hasValue(0);

        workflow.getWorkflowDefinition().setWorkflowStatusListenerEnabled(true);
        exporter.onWorkflowCompletedIfEnabled(workflow);
        exporter.onWorkflowTerminatedIfEnabled(workflow);
        assertThat(exports).hasValue(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsCompletedRunIncludingToolErrorsAndReturnedSubagents() {
        WorkflowModel workflow = workflow("https://unused.example", "session-7", "wf-turn-9");
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.setOutput(Map.of("result", "final answer"));

        TaskModel failedTool = task("CALL_MCP_TOOL", "call_search", 1);
        failedTool.setStatus(TaskModel.Status.FAILED);
        failedTool.setInputData(
                Map.of(
                        "toolName", "cg_search_memories",
                        "query", "prior work",
                        "headers", Map.of("X-API-Key", "must-not-leak")));
        failedTool.setReasonForIncompletion("OCG unavailable");

        TaskModel subagent = task("SUB_WORKFLOW", "delegate_researcher", 2);
        subagent.setStatus(TaskModel.Status.COMPLETED);
        subagent.setInputData(Map.of("subWorkflowName", "researcher", "prompt", "investigate"));
        subagent.setOutputData(Map.of("result", "returned research"));
        workflow.setTasks(List.of(failedTool, subagent));

        OcgAgentRunExporter exporter = exporter(name -> "secret-value", 1);
        Map<String, Object> payload =
                exporter.buildPayload(
                        workflow,
                        LongTermMemoryConfig.builder()
                                .agent("agentspan")
                                .user("user:alice")
                                .build());

        assertThat(payload)
                .containsEntry("agent", "agentspan")
                .containsEntry("user", "user:alice")
                .containsEntry("session_id", "session-7")
                .containsEntry("execution_id", "wf-turn-9")
                .containsEntry("visibility", "public")
                .containsEntry("input", "original request")
                .containsEntry("result", "final answer")
                .containsEntry("outcome", "success");
        assertThat(payload).doesNotContainKey("turn_id");
        List<Map<String, Object>> events = (List<Map<String, Object>>) payload.get("events");
        assertThat(events).hasSize(2);
        assertThat(events.get(0))
                .containsEntry("type", "tool_call")
                .containsEntry("name", "cg_search_memories")
                .containsEntry("output", "OCG unavailable")
                .containsEntry("is_error", true);
        assertThat(events.get(0).get("detail").toString())
                .contains("[REDACTED]")
                .doesNotContain("must-not-leak");
        assertThat(events.get(1))
                .containsEntry("type", "subagent")
                .containsEntry("name", "researcher")
                .containsEntry("is_error", false);
        assertThat(events.get(1).get("output").toString()).contains("returned research");
    }

    @Test
    void retriesWithIdenticalStableIdentityAndUsesApiKeyOnlyAsHeader() throws Exception {
        List<String> bodies = new ArrayList<>();
        List<String> credentials = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/api/v1/memories/agent-run",
                exchange -> {
                    bodies.add(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    credentials.add(exchange.getRequestHeaders().getFirst("X-API-Key"));
                    int status = calls.incrementAndGet() == 1 ? 503 : 202;
                    exchange.sendResponseHeaders(status, -1);
                    exchange.close();
                });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            WorkflowModel workflow = workflow(url, "stable-session", "stable-turn");
            workflow.setStatus(WorkflowModel.Status.COMPLETED);
            workflow.setOutput(Map.of("result", "done"));

            exporter(name -> "top-secret", 2)
                    .export(workflow)
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertThat(calls).hasValue(2);
            assertThat(bodies)
                    .hasSize(2)
                    .allSatisfy(body -> assertThat(body).doesNotContain("top-secret"));
            assertThat(bodies.get(0)).isEqualTo(bodies.get(1));
            Map<String, Object> sent =
                    mapper.readValue(bodies.get(0), new TypeReference<Map<String, Object>>() {});
            assertThat(sent)
                    .containsEntry("session_id", "stable-session")
                    .containsEntry("execution_id", "stable-turn")
                    .containsEntry("visibility", "public")
                    .doesNotContainKey("turn_id");
            assertThat(credentials).containsExactly("top-secret", "top-secret");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unavailabilityCompletesNormallyAndCannotFailTheAgentCallback() {
        WorkflowModel workflow = workflow("http://127.0.0.1:1", "session", "turn");
        workflow.setStatus(WorkflowModel.Status.FAILED);
        workflow.setReasonForIncompletion("agent failed");

        String credential = "not-logged-secret";
        OcgAgentRunExporter exporter = exporter(name -> credential, 1);
        List<String> logs = new ArrayList<>();
        AbstractAppender appender =
                new AbstractAppender(
                        "ocg-test",
                        null,
                        PatternLayout.createDefaultLayout(),
                        false,
                        Property.EMPTY_ARRAY) {
                    @Override
                    public void append(LogEvent event) {
                        logs.add(event.getMessage().getFormattedMessage());
                    }
                };
        Logger logger = (Logger) LogManager.getLogger(HttpOcgClient.class);
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatCode(
                            () ->
                                    exporter.export(workflow)
                                            .toCompletableFuture()
                                            .get(5, TimeUnit.SECONDS))
                    .doesNotThrowAnyException();
            assertThat(logs).allSatisfy(message -> assertThat(message).doesNotContain(credential));
            assertThatCode(() -> exporter.onWorkflowTerminatedIfEnabled(workflow))
                    .doesNotThrowAnyException();
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void oversizedPayloadReducesOnlyEventFields() throws Exception {
        HttpOcgClient client = client(name -> "secret", 1);
        String originalInput = "preserve this input exactly";
        String finalResult = "preserve this result exactly";
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "tool_call");
        event.put("name", "large_tool");
        event.put("detail", "d".repeat(6_000_000));
        event.put("output", "o".repeat(6_000_000));
        event.put("is_error", false);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent", "agentspan");
        payload.put("session_id", "session");
        payload.put("input", originalInput);
        payload.put("events", List.of(event));
        payload.put("result", finalResult);

        byte[] encoded = client.encodeWithinLimit(payload);
        Map<String, Object> reduced =
                mapper.readValue(encoded, new TypeReference<Map<String, Object>>() {});

        assertThat(encoded.length).isLessThan(10 * 1024 * 1024);
        assertThat(reduced)
                .containsEntry("input", originalInput)
                .containsEntry("result", finalResult);
        List<Map<String, Object>> events = (List<Map<String, Object>>) reduced.get("events");
        assertThat(events.get(0).get("detail").toString()).endsWith("…[truncated]");
        assertThat(events.get(0).get("output").toString()).endsWith("…[truncated]");
    }

    @Test
    @SuppressWarnings("unchecked")
    void honorsWorkflowMaskedFieldsAcrossRunAndToolPayloads() {
        WorkflowModel workflow = workflow("https://unused.example", "session", "turn");
        workflow.getWorkflowDefinition().setMaskedFields(List.of("customer_ssn", "result"));
        workflow.setInput(Map.of("prompt", "help", "customer_ssn", "111-22-3333"));
        workflow.setOutput(Map.of("result", "private final answer"));
        TaskModel tool = task("SIMPLE", "lookup", 1);
        tool.setInputData(Map.of("customer_ssn", "111-22-3333", "safe", "visible"));
        tool.setOutputData(Map.of("result", "private tool result", "safe", "visible"));
        workflow.setTasks(List.of(tool));

        Map<String, Object> payload =
                exporter(name -> "secret", 1)
                        .buildPayload(
                                workflow,
                                LongTermMemoryConfig.builder().agent("agentspan").build());

        assertThat(payload).containsEntry("user", "agent:agentspan");
        assertThat(payload.get("result")).isEqualTo("[REDACTED]");
        assertThat(payload.toString())
                .doesNotContain("111-22-3333", "private final answer", "private tool result")
                .contains("[REDACTED]", "visible");
        List<Map<String, Object>> events = (List<Map<String, Object>>) payload.get("events");
        assertThat(events).hasSize(1);
    }

    @Test
    void usesPrivateVisibilityOnlyWhenConfigured() {
        WorkflowModel workflow = workflow("https://unused.example", "session", "execution");

        Map<String, Object> payload =
                exporter(name -> "secret", 1)
                        .buildPayload(
                                workflow,
                                LongTermMemoryConfig.builder()
                                        .agent("agentspan")
                                        .visibility("private")
                                        .build());

        assertThat(payload).containsEntry("visibility", "private");
    }

    private OcgAgentRunExporter exporter(
            java.util.function.Function<String, String> credentialResolver, int attempts) {
        return new OcgAgentRunExporter(mapper, client(credentialResolver, attempts));
    }

    private HttpOcgClient client(
            java.util.function.Function<String, String> credentialResolver, int attempts) {
        return new HttpOcgClient(
                mapper,
                credentialResolver,
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)).build(),
                Duration.ofMillis(500),
                attempts);
    }

    private static WorkflowModel workflow(String ocgUrl, String sessionId, String workflowId) {
        LongTermMemoryConfig memory =
                LongTermMemoryConfig.builder()
                        .ocgUrl(ocgUrl)
                        .credential("OCG_KEY")
                        .agent("agentspan")
                        .user("user:alice")
                        .build();
        Map<String, Object> agentDef = new LinkedHashMap<>();
        agentDef.put("longTermMemory", new ObjectMapper().convertValue(memory, Map.class));
        WorkflowDef definition = new WorkflowDef();
        definition.setName("test_agent");
        definition.setVersion(1);
        definition.setMetadata(Map.of("agentDef", agentDef));

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(workflowId);
        workflow.setWorkflowDefinition(definition);
        workflow.setCreateTime(1_700_000_000_000L);
        workflow.setEndTime(1_700_000_001_000L);
        workflow.setInput(
                Map.of(
                        "prompt", "original request",
                        "session_id", sessionId,
                        "repo", "owner/repo",
                        "branch", "main",
                        "cwd", "/workspace"));
        return workflow;
    }

    private static TaskModel task(String type, String reference, int sequence) {
        TaskModel task = new TaskModel();
        task.setTaskType(type);
        task.setTaskDefName(type);
        task.setReferenceTaskName(reference);
        task.setSeq(sequence);
        return task;
    }
}
