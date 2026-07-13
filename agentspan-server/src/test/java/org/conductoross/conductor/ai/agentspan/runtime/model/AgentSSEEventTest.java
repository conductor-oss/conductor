/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.model;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

class AgentSSEEventTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void thinkingEvent() {
        AgentSSEEvent event = AgentSSEEvent.thinking("wf-1", "agent_llm");

        assertThat(event.getType()).isEqualTo("thinking");
        assertThat(event.getExecutionId()).isEqualTo("wf-1");
        assertThat(event.getContent()).isEqualTo("agent_llm");
        assertThat(event.getTimestamp()).isGreaterThan(0);
    }

    @Test
    void toolCallEvent() {
        Map<String, Object> args = Map.of("query", "hello");
        AgentSSEEvent event = AgentSSEEvent.toolCall("wf-1", "search", args);

        assertThat(event.getType()).isEqualTo("tool_call");
        assertThat(event.getToolName()).isEqualTo("search");
        assertThat(event.getArgs()).isEqualTo(args);
    }

    @Test
    void toolResultEvent() {
        AgentSSEEvent event = AgentSSEEvent.toolResult("wf-1", "search", "result data");

        assertThat(event.getType()).isEqualTo("tool_result");
        assertThat(event.getToolName()).isEqualTo("search");
        assertThat(event.getResult()).isEqualTo("result data");
    }

    @Test
    void handoffEvent() {
        AgentSSEEvent event = AgentSSEEvent.handoff("wf-1", "support_agent");

        assertThat(event.getType()).isEqualTo("handoff");
        assertThat(event.getTarget()).isEqualTo("support_agent");
    }

    @Test
    void waitingEvent() {
        Map<String, Object> pending = Map.of("tool_name", "approve", "taskRefName", "hitl_task");
        AgentSSEEvent event = AgentSSEEvent.waiting("wf-1", pending);

        assertThat(event.getType()).isEqualTo("waiting");
        assertThat(event.getExecutionId()).isEqualTo("wf-1");
        assertThat(event.getPendingTool()).isEqualTo(pending);
    }

    @Test
    void guardrailPassEvent() {
        AgentSSEEvent event = AgentSSEEvent.guardrailPass("wf-1", "content_filter");

        assertThat(event.getType()).isEqualTo("guardrail_pass");
        assertThat(event.getGuardrailName()).isEqualTo("content_filter");
    }

    @Test
    void guardrailFailEvent() {
        AgentSSEEvent event = AgentSSEEvent.guardrailFail("wf-1", "content_filter", "Blocked");

        assertThat(event.getType()).isEqualTo("guardrail_fail");
        assertThat(event.getGuardrailName()).isEqualTo("content_filter");
        assertThat(event.getContent()).isEqualTo("Blocked");
    }

    @Test
    void errorEvent() {
        AgentSSEEvent event = AgentSSEEvent.error("wf-1", "task_ref", "Something failed");

        assertThat(event.getType()).isEqualTo("error");
        assertThat(event.getToolName()).isEqualTo("task_ref");
        assertThat(event.getContent()).isEqualTo("Something failed");
    }

    @Test
    void doneEvent() {
        Map<String, Object> output = Map.of("result", "Final answer");
        AgentSSEEvent event = AgentSSEEvent.done("wf-1", output);

        assertThat(event.getType()).isEqualTo("done");
        assertThat(event.getOutput()).isEqualTo(output);
    }

    @Test
    void toJsonIncludesNonNullFieldsOnly() throws Exception {
        AgentSSEEvent event = AgentSSEEvent.thinking("wf-1", "llm_task");
        event.setId(5);
        String json = event.toJson();

        var node = MAPPER.readTree(json);
        assertThat(node.get("type").asText()).isEqualTo("thinking");
        assertThat(node.get("executionId").asText()).isEqualTo("wf-1");
        assertThat(node.get("content").asText()).isEqualTo("llm_task");
        assertThat(node.get("id").asLong()).isEqualTo(5);
        // Null fields should be absent
        assertThat(node.has("toolName")).isFalse();
        assertThat(node.has("args")).isFalse();
        assertThat(node.has("result")).isFalse();
        assertThat(node.has("target")).isFalse();
        assertThat(node.has("output")).isFalse();
        assertThat(node.has("guardrailName")).isFalse();
        assertThat(node.has("pendingTool")).isFalse();
    }

    @Test
    void toJsonToolCallRoundTrip() throws Exception {
        Map<String, Object> args = Map.of("query", "test");
        AgentSSEEvent event = AgentSSEEvent.toolCall("wf-1", "search", args);
        event.setId(10);

        String json = event.toJson();
        var node = MAPPER.readTree(json);

        assertThat(node.get("type").asText()).isEqualTo("tool_call");
        assertThat(node.get("toolName").asText()).isEqualTo("search");
        assertThat(node.get("args").get("query").asText()).isEqualTo("test");
    }

    @Test
    void settersAndGetters() {
        AgentSSEEvent event = new AgentSSEEvent();
        event.setId(42);
        event.setType("custom");
        event.setExecutionId("wf-99");
        event.setContent("msg");
        event.setToolName("tool");
        event.setArgs("argval");
        event.setResult("resval");
        event.setTarget("tgt");
        event.setOutput("out");
        event.setGuardrailName("guard");
        event.setPendingTool(Map.of("k", "v"));
        event.setTimestamp(12345L);

        assertThat(event.getId()).isEqualTo(42);
        assertThat(event.getType()).isEqualTo("custom");
        assertThat(event.getExecutionId()).isEqualTo("wf-99");
        assertThat(event.getContent()).isEqualTo("msg");
        assertThat(event.getToolName()).isEqualTo("tool");
        assertThat(event.getArgs()).isEqualTo("argval");
        assertThat(event.getResult()).isEqualTo("resval");
        assertThat(event.getTarget()).isEqualTo("tgt");
        assertThat(event.getOutput()).isEqualTo("out");
        assertThat(event.getGuardrailName()).isEqualTo("guard");
        assertThat(event.getPendingTool()).containsEntry("k", "v");
        assertThat(event.getTimestamp()).isEqualTo(12345L);
    }
}
