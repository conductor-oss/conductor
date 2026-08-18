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

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

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
    private static final String MODEL =
            System.getenv().getOrDefault("AGENT_E2E_MODEL", "OpenAI/gpt-4o-mini");

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
