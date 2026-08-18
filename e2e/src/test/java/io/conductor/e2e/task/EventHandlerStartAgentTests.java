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
package io.conductor.e2e.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import com.netflix.conductor.client.http.EventClient;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import io.conductor.e2e.util.ApiUtil;
import io.orkes.conductor.client.AgentClient;
import io.orkes.conductor.client.model.agent.AgentRequest;
import io.orkes.conductor.client.model.agent.StartResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Black-box, real-server coverage of the {@code start_agent} EventHandler action against an agent
 * deployed through the real agent-config pipeline — the same {@code AgentClient.deployAgent}
 * pipeline {@link AgentTaskTests} uses for the {@code AGENT} task type, here triggered by an {@code
 * EVENT} task / {@code EventHandler} instead.
 */
@DisabledIfSystemProperty(
        named = "E2E_DISABLED_CAPABILITIES",
        matches = ".*\\bai\\b.*",
        disabledReason = "target server runs without AI integrations (no agent endpoints)")
class EventHandlerStartAgentTests {

    private static final MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;
    private static final WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
    private static final AgentClient agentClient = ApiUtil.AGENT_CLIENT;
    private static final EventClient eventClient = ApiUtil.EVENT_CLIENT;
    private static final String MODEL =
            System.getenv().getOrDefault("AGENT_E2E_MODEL", "OpenAI/gpt-4o-mini");

    private static final String TRIGGER_WORKFLOW_NAME = "event_handler_start_agent_trigger_e2e";
    private static final String TRIGGER_TASK_REF = "fire";
    private static final String TRIGGER_EVENT_NAME =
            "conductor:" + TRIGGER_WORKFLOW_NAME + ":" + TRIGGER_TASK_REF;
    private static final String SHARED_HANDLER_NAME = "start_agent_trigger_handler_e2e";

    @BeforeAll
    static void registerSharedTriggerAndHandler() {
        registerTriggerWorkflow();
        registerSharedEventHandler();
        warmUpSharedEventHandler();
    }

    /**
     * {@code DefaultEventQueueManager} only picks up a newly-registered {@code EventHandler}'s
     * queue every 60s. Repeatedly firing the trigger workflow at a throwaway agent — instead of a
     * blind sleep — turns that fixed wait into a bounded, self-verifying one: once the throwaway
     * execution shows up, the shared handler is confirmed live for every real test in this class.
     */
    private static void warmUpSharedEventHandler() {
        String warmupAgentName = "warmup_agent_e2e_" + UUID.randomUUID();
        String warmupIdempotencyKey = "warmup_" + UUID.randomUUID();
        deployAgent(bogusModelAgentConfig(warmupAgentName));

        Awaitility.await()
                .atMost(90, TimeUnit.SECONDS)
                .pollInterval(5, TimeUnit.SECONDS)
                .until(
                        () -> {
                            fireStartAgent(
                                    warmupAgentName, "warm up", "warmup-session", warmupIdempotencyKey);
                            return !workflowClient
                                    .getWorkflows(warmupAgentName, warmupIdempotencyKey, true, false)
                                    .isEmpty();
                        });
    }

    @Test
    void startAgentActionOnRealPipelineAgentSurfacesFailure() {
        String agentName = "failing_agent_e2e_" + UUID.randomUUID();
        String idempotencyKey = "idempotency_" + UUID.randomUUID();
        deployAgent(bogusModelAgentConfig(agentName));

        fireStartAgent(agentName, "trigger the failure path", "session_e2e", idempotencyKey);
        Workflow execution = awaitAgentExecution(agentName, idempotencyKey, 60);

        Workflow completed = awaitTerminal(execution.getWorkflowId(), 60);
        assertEquals(Workflow.WorkflowStatus.FAILED, completed.getStatus());
    }

    @Test
    void startAgentActionOnRealPipelineAgentCanBeTerminated() {
        String agentName = "blocking_agent_e2e_" + UUID.randomUUID();
        String idempotencyKey = "idempotency_" + UUID.randomUUID();
        deployAgent(blockingAgentConfig(agentName));

        fireStartAgent(agentName, "trigger the blocking path", "session_e2e", idempotencyKey);
        Workflow execution = awaitAgentExecution(agentName, idempotencyKey, 60);
        String executionId = execution.getWorkflowId();

        awaitRunning(executionId, 30);

        workflowClient.terminateWorkflow(executionId, "test: cancel via EventHandler-started agent");

        Workflow terminated = awaitTerminal(executionId, 30);
        assertEquals(Workflow.WorkflowStatus.TERMINATED, terminated.getStatus());
    }

    private static void awaitRunning(String workflowId, int timeoutSeconds) {
        Awaitility.await()
                .atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> workflowClient.getWorkflow(workflowId, false).getStatus()
                        == Workflow.WorkflowStatus.RUNNING);
    }

    private static Workflow awaitTerminal(String workflowId, int timeoutSeconds) {
        Workflow[] latest = new Workflow[1];
        Awaitility.await()
                .atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, false);
                            latest[0] = wf;
                            return wf != null && wf.getStatus() != null && wf.getStatus().isTerminal();
                        });
        return latest[0];
    }

    // ── trigger + read-back ────────────────────────────────────────────────────

    private static void fireStartAgent(
            String agentName, String prompt, String sessionId, String idempotencyKey) {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(TRIGGER_WORKFLOW_NAME);
        request.setVersion(1);
        request.setInput(
                Map.of(
                        "agentName", agentName,
                        "prompt", prompt,
                        "sessionId", sessionId,
                        "idempotencyKey", idempotencyKey));
        workflowClient.startWorkflow(request);
    }

    private static Workflow awaitAgentExecution(
            String agentName, String idempotencyKey, int timeoutSeconds) {
        List<Workflow> found = new ArrayList<>();
        Awaitility.await()
                .atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(
                        () -> {
                            found.clear();
                            found.addAll(
                                    workflowClient.getWorkflows(agentName, idempotencyKey, true, true));
                            return !found.isEmpty();
                        });
        return found.get(0);
    }

    // ── shared trigger workflow + shared EventHandler ─────────────────────────

    private static void registerTriggerWorkflow() {
        WorkflowTask fire = new WorkflowTask();
        fire.setName("event");
        fire.setTaskReferenceName(TRIGGER_TASK_REF);
        fire.setType("EVENT");
        fire.setSink("conductor");
        fire.setInputParameters(
                Map.of(
                        "agentName", "${workflow.input.agentName}",
                        "prompt", "${workflow.input.prompt}",
                        "sessionId", "${workflow.input.sessionId}",
                        "idempotencyKey", "${workflow.input.idempotencyKey}"));

        WorkflowDef def = new WorkflowDef();
        def.setName(TRIGGER_WORKFLOW_NAME);
        def.setVersion(1);
        def.setOwnerEmail("agent-e2e@conductor.test");
        def.setTasks(List.of(fire));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    private static void registerSharedEventHandler() {
        EventHandler.StartAgent startAgent = new EventHandler.StartAgent();
        startAgent.setName("${agentName}");
        startAgent.setPrompt("${prompt}");
        startAgent.setSessionId("${sessionId}");
        startAgent.setIdempotencyKey("${idempotencyKey}");

        EventHandler.Action action = new EventHandler.Action();
        action.setAction(EventHandler.Action.Type.start_agent);
        action.setStart_agent(startAgent);

        EventHandler eventHandler = new EventHandler();
        eventHandler.setName(SHARED_HANDLER_NAME);
        eventHandler.setEvent(TRIGGER_EVENT_NAME);
        eventHandler.setActive(true);
        eventHandler.setActions(List.of(action));

        try {
            eventClient.registerEventHandler(eventHandler);
        } catch (Exception ignored) {
            // already registered by a prior run against this server
            eventClient.updateEventHandler(eventHandler);
        }
    }

    // ── agent registration (ported from AgentTaskTests) ──────────────────────

    private static StartResponse deployAgent(Map<String, Object> config) {
        StartResponse response = agentClient.deployAgent(AgentRequest.nativeAgent(config).build());
        assertEquals(config.get("name"), response.getAgentName());
        assertEquals(null, response.getExecutionId(), "deploy must not start an execution");
        assertControllerDeployedAgent(String.valueOf(config.get("name")));
        return response;
    }

    private static void assertControllerDeployedAgent(String agentName) {
        WorkflowDef definition = metadataClient.getWorkflowDef(agentName, 1);
        assertNotNull(definition);
        assertNotNull(definition.getTasks());
        assertTrue(!definition.getTasks().isEmpty(), "AgentController must compile agent tasks");
        assertNotNull(definition.getMetadata());
        assertEquals("conductor", definition.getMetadata().get("agent_sdk"));
        assertTrue(
                definition.getMetadata().get("agentDef") instanceof Map<?, ?>,
                "AgentController must persist the full agentDef");
        Map<?, ?> agentDef = (Map<?, ?>) definition.getMetadata().get("agentDef");
        assertEquals(agentName, agentDef.get("name"));
        assertNotNull(agentDef.get("model"));
    }

    private static Map<String, Object> basicAgentConfig(String name, String instructions) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("name", name);
        config.put("model", MODEL);
        config.put("instructions", instructions);
        config.put("maxTurns", 3);
        config.put("timeoutSeconds", 120);
        config.put("temperature", 0.0);
        return config;
    }

    private static Map<String, Object> bogusModelAgentConfig(String name) {
        Map<String, Object> config =
                basicAgentConfig(name, "This agent intentionally targets an unknown provider.");
        config.put("model", "unknown_e2e_provider/unknown_model");
        return config;
    }

    private static Map<String, Object> blockingAgentConfig(String agentName) {
        String taskType = "agent_pending_work_e2e_" + UUID.randomUUID();
        Map<String, Object> config =
                basicAgentConfig(
                        agentName,
                        "Use the prefilled work result as context, then answer in one sentence.");
        config.put("tools", List.of(workerTool(taskType, false)));
        config.put(
                "prefillTools",
                List.of(Map.of("toolName", taskType, "arguments", Map.of("prompt", "durable work"))));
        return config;
    }

    private static Map<String, Object> workerTool(String name, boolean approvalRequired) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", "Complete deterministic work for the agent lifecycle E2E.");
        tool.put("toolType", "worker");
        tool.put("approvalRequired", approvalRequired);
        tool.put(
                "inputSchema",
                Map.of("type", "object", "properties", Map.of("prompt", Map.of("type", "string"))));
        return tool;
    }
}
